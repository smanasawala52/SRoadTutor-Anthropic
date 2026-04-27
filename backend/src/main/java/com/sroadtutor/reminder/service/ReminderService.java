package com.sroadtutor.reminder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.repository.PhoneNumberRepository;
import com.sroadtutor.reminder.dto.ReminderResponse;
import com.sroadtutor.reminder.model.Reminder;
import com.sroadtutor.reminder.repository.ReminderRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.ParentStudent;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import com.sroadtutor.whatsapp.model.WhatsappMessageLog;
import com.sroadtutor.whatsapp.model.WhatsappTemplate;
import com.sroadtutor.whatsapp.repository.WhatsappMessageLogRepository;
import com.sroadtutor.whatsapp.repository.WhatsappTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lesson reminders. Locked at PR10:
 *
 * <ul>
 *   <li><b>Cron pre-generates</b> ({@link #sweepUpcomingSessions}) — every
 *       15 min, scans SCHEDULED sessions whose {@code scheduledAt} falls in
 *       the next [23.5h, 24.5h] window and creates one PENDING reminder per
 *       recipient (the student + each linked parent) IF none already
 *       exists. Idempotent thanks to the partial unique index in V11.</li>
 *   <li><b>Manual fire</b> ({@link #fire}) — instructor or owner taps "send"
 *       in their app; we mark the reminder SENT and create a
 *       {@link WhatsappMessageLog} row for audit consistency.</li>
 *   <li><b>Sync on cancel/reschedule</b> ({@link #cancelForSession}) — when
 *       {@code SessionService.cancel} or {@code SessionService.reschedule}
 *       fires, all PENDING reminders for that session are flipped to
 *       CANCELLED. The next cron sweep recreates fresh PENDING rows for the
 *       new time on rescheduled sessions.</li>
 *   <li><b>Failure modes</b> — recipient with no primary phone, no WhatsApp
 *       opt-in, or no template available results in a single FAILED row
 *       with the reason in {@code failed_reason}, NOT a swallowed error.
 *       Owners can see why their reminders didn't go.</li>
 * </ul>
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** S5 — V1 ships only the 24h reminder. */
    public static final Duration LEAD_TIME = Duration.ofHours(24);
    /** Sweep tolerance — half-hour pad on each side of the 24h target. */
    public static final Duration SWEEP_PAD = Duration.ofMinutes(30);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule()).build();

    /** Hardcoded fallback when no whatsapp_templates row exists for {@code lesson_reminder}. */
    private static final String FALLBACK_REMINDER_BODY =
            "Hi {{studentName}}, this is a reminder of your driving lesson with {{instructorName}} "
                    + "on {{lessonTimeLocal}} ({{lessonDuration}} min){{locationSuffix}}. See you then!";

    private final ReminderRepository reminderRepo;
    private final LessonSessionRepository sessionRepo;
    private final StudentRepository studentRepo;
    private final ParentStudentRepository parentLinkRepo;
    private final InstructorRepository instructorRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final PhoneNumberRepository phoneRepo;
    private final WhatsappTemplateRepository templateRepo;
    private final WhatsappMessageLogRepository logRepo;

    public ReminderService(ReminderRepository reminderRepo,
                            LessonSessionRepository sessionRepo,
                            StudentRepository studentRepo,
                            ParentStudentRepository parentLinkRepo,
                            InstructorRepository instructorRepo,
                            SchoolRepository schoolRepo,
                            UserRepository userRepo,
                            PhoneNumberRepository phoneRepo,
                            WhatsappTemplateRepository templateRepo,
                            WhatsappMessageLogRepository logRepo) {
        this.reminderRepo = reminderRepo;
        this.sessionRepo = sessionRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
        this.instructorRepo = instructorRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.phoneRepo = phoneRepo;
        this.templateRepo = templateRepo;
        this.logRepo = logRepo;
    }

    // ============================================================
    // Sweep — cron entry point
    // ============================================================

    /**
     * Generates PENDING reminders for SCHEDULED sessions whose
     * {@code scheduledAt} is approximately {@link #LEAD_TIME} away.
     *
     * <p>Window: {@code [now + lead - pad, now + lead + pad]}. Idempotent —
     * the partial unique index on (session, recipient, kind, status) blocks
     * duplicates, and the service-side check skips re-create.</p>
     */
    @Transactional
    public int sweepUpcomingSessions() {
        Instant now = Instant.now();
        Instant from = now.plus(LEAD_TIME).minus(SWEEP_PAD);
        Instant to   = now.plus(LEAD_TIME).plus(SWEEP_PAD);

        // The session repo's range query is school-scoped; we want every
        // school. There's no "all schools in range" finder — so we scan
        // each active school individually. For V1 traffic this is
        // negligible; revisit when the platform has > 100 schools.
        int created = 0;
        for (School school : schoolRepo.findAll()) {
            if (!school.isActive()) continue;
            List<LessonSession> upcoming = sessionRepo.findForSchoolInRange(school.getId(), from, to);
            for (LessonSession s : upcoming) {
                if (!LessonSession.STATUS_SCHEDULED.equals(s.getStatus())) continue;
                created += generatePendingForSessionIfMissing(s, school);
            }
        }
        log.info("Reminder sweep created {} PENDING rows (window {}..{})", created, from, to);
        return created;
    }

    /**
     * Public wrapper used at session-create time to pre-generate reminders
     * eagerly. Dedupes against existing PENDING/SENT rows.
     */
    @Transactional
    public int generatePendingForSession(UUID sessionId) {
        LessonSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (!LessonSession.STATUS_SCHEDULED.equals(s.getStatus())) return 0;
        School school = schoolRepo.findById(s.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + s.getSchoolId()));
        return generatePendingForSessionIfMissing(s, school);
    }

    private int generatePendingForSessionIfMissing(LessonSession session, School school) {
        // Skip if any non-terminal reminder already exists.
        List<Reminder> active = reminderRepo.findBySessionIdAndStatusIn(
                session.getId(), List.of(Reminder.STATUS_PENDING, Reminder.STATUS_SENT));
        if (!active.isEmpty()) return 0;

        Student student = studentRepo.findById(session.getStudentId()).orElse(null);
        if (student == null) {
            log.warn("Reminder sweep skipped session {} — student missing", session.getId());
            return 0;
        }

        // Recipients: student's own user + every linked parent's user
        List<UUID> recipients = new ArrayList<>();
        recipients.add(student.getUserId());
        for (ParentStudent link : parentLinkRepo.findByStudentId(student.getId())) {
            if (!recipients.contains(link.getParentUserId())) {
                recipients.add(link.getParentUserId());
            }
        }

        Instant scheduledFor = session.getScheduledAt().minus(LEAD_TIME);
        int created = 0;
        for (UUID recipientUserId : recipients) {
            // Belt-and-braces: re-check the partial index isn't already
            // populated for this triple.
            Optional<Reminder> existing = reminderRepo
                    .findBySessionIdAndRecipientUserIdAndReminderKindAndStatusIn(
                            session.getId(), recipientUserId, Reminder.KIND_LESSON_24H,
                            List.of(Reminder.STATUS_PENDING, Reminder.STATUS_SENT));
            if (existing.isPresent()) continue;

            Reminder reminder = buildReminderForRecipient(session, school, student, recipientUserId, scheduledFor);
            reminderRepo.save(reminder);
            created++;
        }
        return created;
    }

    private Reminder buildReminderForRecipient(LessonSession session, School school,
                                                Student student, UUID recipientUserId,
                                                Instant scheduledFor) {
        // Resolve recipient phone — primary + WhatsApp opted-in.
        Optional<PhoneNumber> recipientPhone = phoneRepo.findByUserIdAndPrimaryTrue(recipientUserId);
        if (recipientPhone.isEmpty() || !recipientPhone.get().isWhatsapp()
                || !recipientPhone.get().isWhatsappOptIn()) {
            return failedReminder(session, recipientUserId, scheduledFor,
                    "No WhatsApp-opted-in primary phone for recipient");
        }

        // Resolve template body — school override → platform default → hardcoded fallback.
        String body = resolveBody(school);
        if (body == null) {
            return failedReminder(session, recipientUserId, scheduledFor,
                    "No lesson_reminder template available");
        }

        // Resolve placeholder values
        String studentName = userRepo.findById(student.getUserId()).map(User::getFullName).orElse("there");
        Instructor instructor = instructorRepo.findById(session.getInstructorId()).orElse(null);
        String instructorName = instructor == null ? "your instructor"
                : userRepo.findById(instructor.getUserId()).map(User::getFullName).orElse("your instructor");

        ZoneId zone = resolveZone(school.getTimezone());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM 'at' h:mma", Locale.ENGLISH);
        String localTime = ZonedDateTime.ofInstant(session.getScheduledAt(), zone).format(fmt);
        String locationSuffix = session.getLocation() == null || session.getLocation().isBlank()
                ? "" : " at " + session.getLocation();

        Map<String, String> placeholders = Map.of(
                "studentName", studentName,
                "instructorName", instructorName,
                "lessonTimeLocal", localTime,
                "lessonDuration", String.valueOf(session.getDurationMins()),
                "locationSuffix", locationSuffix);
        String renderedBody = renderPlaceholders(body, placeholders);

        // Build wa.me URL — strip "+" from e164 per wa.me spec
        String digits = recipientPhone.get().getCountryCode() + recipientPhone.get().getNationalNumber();
        String waMeUrl = "https://wa.me/" + digits + "?text="
                + URLEncoder.encode(renderedBody, StandardCharsets.UTF_8);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("waMeUrl", waMeUrl);
        payload.put("renderedBody", renderedBody);
        payload.put("recipientPhoneId", recipientPhone.get().getId().toString());
        payload.put("schoolId", session.getSchoolId().toString());

        return Reminder.builder()
                .sessionId(session.getId())
                .recipientUserId(recipientUserId)
                .channel(Reminder.CHANNEL_WHATSAPP)
                .reminderKind(Reminder.KIND_LESSON_24H)
                .status(Reminder.STATUS_PENDING)
                .scheduledFor(scheduledFor)
                .payloadJson(serialize(payload))
                .build();
    }

    private Reminder failedReminder(LessonSession session, UUID recipientUserId,
                                     Instant scheduledFor, String reason) {
        log.info("Reminder for session={} recipient={} marked FAILED: {}",
                session.getId(), recipientUserId, reason);
        return Reminder.builder()
                .sessionId(session.getId())
                .recipientUserId(recipientUserId)
                .channel(Reminder.CHANNEL_WHATSAPP)
                .reminderKind(Reminder.KIND_LESSON_24H)
                .status(Reminder.STATUS_FAILED)
                .scheduledFor(scheduledFor)
                .failedReason(reason)
                .build();
    }

    // ============================================================
    // Cancel cascade
    // ============================================================

    @Transactional
    public int cancelForSession(UUID sessionId) {
        int cancelled = 0;
        for (Reminder r : reminderRepo.findBySessionId(sessionId)) {
            if (Reminder.STATUS_PENDING.equals(r.getStatus())) {
                r.setStatus(Reminder.STATUS_CANCELLED);
                reminderRepo.save(r);
                cancelled++;
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} PENDING reminders for session {}", cancelled, sessionId);
        }
        return cancelled;
    }

    // ============================================================
    // Manual fire
    // ============================================================

    @Transactional
    public ReminderResponse fire(Role role, UUID currentUserId, UUID reminderId) {
        Reminder reminder = reminderRepo.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found: " + reminderId));
        if (Reminder.STATUS_SENT.equals(reminder.getStatus())) {
            // Idempotent — already sent.
            return toResponse(reminder);
        }
        if (!Reminder.STATUS_PENDING.equals(reminder.getStatus())) {
            throw new BadRequestException(
                    "REMINDER_NOT_FIREABLE",
                    "Only PENDING reminders can be fired (current=" + reminder.getStatus() + ")");
        }

        UUID sessionId = reminder.getSessionId();
        LessonSession session = sessionRepo.findById(reminder.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session disappeared: " + sessionId));
        requireFireScope(role, currentUserId, session);

        Map<String, Object> payload = parse(reminder.getPayloadJson());
        UUID recipientPhoneId = uuidOrNull(payload.get("recipientPhoneId"));
        String renderedBody = stringOrNull(payload.get("renderedBody"));
        if (recipientPhoneId == null || renderedBody == null) {
            throw new BadRequestException(
                    "REMINDER_PAYLOAD_INVALID",
                    "Reminder is missing wa.me payload fields");
        }

        // Audit row in whatsapp_message_log — keeps parity with manual wa.me sends.
        WhatsappMessageLog audit = WhatsappMessageLog.builder()
                .senderUserId(currentUserId)
                .recipientPhoneId(recipientPhoneId)
                .renderedBody(renderedBody)
                .schoolId(session.getSchoolId())
                .correlationId("reminder:" + reminder.getId())
                .build();
        audit = logRepo.save(audit);

        Instant now = Instant.now();
        reminder.setStatus(Reminder.STATUS_SENT);
        reminder.setSentAt(now);
        reminder.setWaMeLogId(audit.getId());
        reminder = reminderRepo.save(reminder);

        log.info("Reminder {} fired by {}={} (session={}, recipient={}, log={})",
                reminder.getId(), role, currentUserId, session.getId(),
                reminder.getRecipientUserId(), audit.getId());

        return toResponse(reminder);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<ReminderResponse> listPendingForCurrentUser(UUID currentUserId) {
        Instant now = Instant.now();
        List<Reminder> rows = reminderRepo.findPendingForRecipient(currentUserId, now);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> listForSession(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        requireFireScope(role, currentUserId, session);
        return reminderRepo.findBySessionId(sessionId).stream().map(this::toResponse).toList();
    }

    // ============================================================
    // Scope
    // ============================================================

    /**
     * Fire / list scope: OWNER of session's school OR the assigned
     * INSTRUCTOR. Students/parents do not fire reminders (they receive them).
     */
    private void requireFireScope(Role role, UUID currentUserId, LessonSession session) {
        switch (role) {
            case OWNER -> {
                Optional<School> s = schoolRepo.findById(session.getSchoolId());
                if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(session.getInstructorId())) return;
            }
            default -> { /* deny */ }
        }
        throw new AccessDeniedException("Only OWNER or assigned INSTRUCTOR can manage reminders");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String resolveBody(School school) {
        Optional<WhatsappTemplate> override =
                templateRepo.findActiveSchoolOverride(school.getId(), "lesson_reminder", "en");
        if (override.isPresent()) return override.get().getBody();
        Optional<WhatsappTemplate> def =
                templateRepo.findActivePlatformDefault("lesson_reminder", "en");
        return def.map(WhatsappTemplate::getBody).orElse(FALLBACK_REMINDER_BODY);
    }

    private static String renderPlaceholders(String body, Map<String, String> placeholders) {
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder out = new StringBuilder(body.length() + 32);
        while (m.find()) {
            String key = m.group(1);
            String value = placeholders.getOrDefault(key, "");
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? "America/Regina" : tz);
        } catch (Exception ex) {
            return ZoneId.of("America/Regina");
        }
    }

    private ReminderResponse toResponse(Reminder r) {
        Map<String, Object> p = parse(r.getPayloadJson());
        return ReminderResponse.from(
                r,
                stringOrNull(p.get("waMeUrl")),
                stringOrNull(p.get("renderedBody")),
                uuidOrNull(p.get("recipientPhoneId")));
    }

    private static String serialize(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reminder payload", e);
        }
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JSON.readValue(json, Map.class);
            return m;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null) return null;
        try {
            return UUID.fromString(o.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
