package com.sroadtutor.session.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.WorkingHours;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.dto.BookSessionRequest;
import com.sroadtutor.session.dto.RescheduleSessionRequest;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lesson scheduling: book / reschedule / cancel / complete / no-show + calendar reads.
 *
 * <p>Locked at PR9 kickoff:
 * <ul>
 *   <li><b>Conflict detection always-on</b> — instructor double-bookings and
 *       student double-bookings are rejected with {@code INSTRUCTOR_BUSY} /
 *       {@code STUDENT_BUSY}. Cancelled and no-show rows do NOT count as
 *       conflicts.</li>
 *   <li><b>Working hours enforcement</b> — the booked window
 *       {@code [scheduledAt, scheduledAt+duration)} must fit entirely within
 *       a {@link WorkingHours} {@link WorkingHours.TimeRange} on that
 *       weekday in the school's local timezone. {@code forceOutsideHours=true}
 *       overrides — OWNER only.</li>
 *   <li><b>Past-datetime block</b> — booking and rescheduling reject
 *       {@code scheduledAt} that is in the past relative to now.</li>
 *   <li><b>Lessons-remaining policy</b>:
 *     <ul>
 *       <li>Book — no change.</li>
 *       <li>Cancel a SCHEDULED row — no change.</li>
 *       <li>Complete — decrement by 1 (refused if {@code lessons_remaining == 0}).</li>
 *       <li>No-show — decrement by 1 (treat as consumed).</li>
 *     </ul>
 *   </li>
 *   <li><b>Reschedule = update in place</b> — preserves session id + audit.
 *       Same conflict checks re-apply; refused if status is not SCHEDULED.</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Bookings narrower than this look silly; rejected by Bean Validation already. */
    static final int MIN_DURATION_MINS = 15;
    /** Bookings wider than this need a different product (a road trip). */
    static final int MAX_DURATION_MINS = 360;
    /** Default lesson length when caller doesn't specify. */
    static final int DEFAULT_DURATION_MINS = 60;
    /** How wide a window we fetch around a candidate to evaluate overlap. */
    private static final Duration COLLISION_FETCH_PADDING = Duration.ofHours(MAX_DURATION_MINS / 60 + 1);

    private final LessonSessionRepository sessionRepo;
    private final SchoolRepository        schoolRepo;
    private final InstructorRepository    instructorRepo;
    private final StudentRepository       studentRepo;
    private final ParentStudentRepository parentLinkRepo;

    public SessionService(LessonSessionRepository sessionRepo,
                          SchoolRepository schoolRepo,
                          InstructorRepository instructorRepo,
                          StudentRepository studentRepo,
                          ParentStudentRepository parentLinkRepo) {
        this.sessionRepo = sessionRepo;
        this.schoolRepo = schoolRepo;
        this.instructorRepo = instructorRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
    }

    // ============================================================
    // Book
    // ============================================================

    @Transactional
    public LessonSession book(Role role, UUID currentUserId, BookSessionRequest req) {
        Instant now = Instant.now();
        if (!req.scheduledAt().isAfter(now)) {
            throw new BadRequestException(
                    "PAST_DATETIME",
                    "Cannot book a session in the past");
        }
        int duration = req.durationMins() == null ? DEFAULT_DURATION_MINS : req.durationMins();
        validateDuration(duration);

        Instructor instructor = instructorRepo.findById(req.instructorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + req.instructorId()));
        if (!instructor.isActive()) {
            throw new BadRequestException(
                    "INSTRUCTOR_INACTIVE",
                    "Instructor is deactivated");
        }
        Student student = studentRepo.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + req.studentId()));
        if (!Student.STATUS_ACTIVE.equals(student.getStatus())) {
            throw new BadRequestException(
                    "STUDENT_NOT_ACTIVE",
                    "Student is not active (status=" + student.getStatus() + ")");
        }
        if (instructor.getSchoolId() != null
                && !instructor.getSchoolId().equals(student.getSchoolId())) {
            throw new BadRequestException(
                    "INSTRUCTOR_WRONG_SCHOOL",
                    "Instructor and student belong to different schools");
        }

        UUID schoolId = student.getSchoolId();
        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        if (!school.isActive()) {
            throw new BadRequestException(
                    "SCHOOL_INACTIVE",
                    "Cannot book at a deactivated school");
        }

        requireBookingScope(role, currentUserId, school, instructor, student);

        Instant endAt = req.scheduledAt().plus(Duration.ofMinutes(duration));
        boolean force = Boolean.TRUE.equals(req.forceOutsideHours());
        if (force && role != Role.OWNER) {
            throw new BadRequestException(
                    "FORCE_REQUIRES_OWNER",
                    "Only OWNER can override working-hours validation");
        }
        if (!force) {
            requireWithinWorkingHours(instructor, school, req.scheduledAt(), endAt);
        }
        requireNoConflicts(instructor.getId(), student.getId(), null, req.scheduledAt(), endAt);

        LessonSession session = LessonSession.builder()
                .schoolId(schoolId)
                .instructorId(instructor.getId())
                .studentId(student.getId())
                .scheduledAt(req.scheduledAt())
                .durationMins(duration)
                .status(LessonSession.STATUS_SCHEDULED)
                .location(nullIfBlank(req.location()))
                .notes(nullIfBlank(req.notes()))
                .createdByUserId(currentUserId)
                .build();
        session = sessionRepo.save(session);

        log.info("Session {} booked by {}={} for instructor={} student={} at {} ({} min)",
                session.getId(), role, currentUserId, instructor.getId(), student.getId(),
                session.getScheduledAt(), duration);
        return session;
    }

    // ============================================================
    // Reschedule
    // ============================================================

    @Transactional
    public LessonSession reschedule(Role role, UUID currentUserId, UUID sessionId, RescheduleSessionRequest req) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (!LessonSession.STATUS_SCHEDULED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "SESSION_NOT_RESCHEDULABLE",
                    "Only SCHEDULED sessions can be rescheduled");
        }

        // Resolve target tuple
        UUID targetInstructorId = req.instructorId() == null ? session.getInstructorId() : req.instructorId();
        Instant targetScheduledAt = req.scheduledAt() == null ? session.getScheduledAt() : req.scheduledAt();
        int targetDuration = req.durationMins() == null ? session.getDurationMins() : req.durationMins();
        validateDuration(targetDuration);

        Instant now = Instant.now();
        if (!targetScheduledAt.isAfter(now)) {
            throw new BadRequestException(
                    "PAST_DATETIME",
                    "Cannot reschedule into the past");
        }

        Instructor instructor = instructorRepo.findById(targetInstructorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instructor not found: " + targetInstructorId));
        if (!instructor.isActive()) {
            throw new BadRequestException("INSTRUCTOR_INACTIVE", "Instructor is deactivated");
        }
        Student student = studentRepo.findById(session.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + session.getStudentId()));
        School school = schoolRepo.findById(session.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + session.getSchoolId()));
        if (!school.isActive()) {
            throw new BadRequestException("SCHOOL_INACTIVE", "Cannot reschedule at a deactivated school");
        }
        if (instructor.getSchoolId() != null
                && !instructor.getSchoolId().equals(student.getSchoolId())) {
            throw new BadRequestException(
                    "INSTRUCTOR_WRONG_SCHOOL",
                    "Instructor and student belong to different schools");
        }

        requireBookingScope(role, currentUserId, school, instructor, student);

        Instant endAt = targetScheduledAt.plus(Duration.ofMinutes(targetDuration));
        boolean force = Boolean.TRUE.equals(req.forceOutsideHours());
        if (force && role != Role.OWNER) {
            throw new BadRequestException(
                    "FORCE_REQUIRES_OWNER",
                    "Only OWNER can override working-hours validation");
        }
        if (!force) {
            requireWithinWorkingHours(instructor, school, targetScheduledAt, endAt);
        }
        requireNoConflicts(instructor.getId(), student.getId(), session.getId(), targetScheduledAt, endAt);

        session.setInstructorId(instructor.getId());
        session.setScheduledAt(targetScheduledAt);
        session.setDurationMins(targetDuration);
        if (req.location() != null) session.setLocation(nullIfBlank(req.location()));
        if (req.notes() != null)    session.setNotes(nullIfBlank(req.notes()));

        log.info("Session {} rescheduled by {}={} to {} ({} min) instructor={}",
                session.getId(), role, currentUserId, targetScheduledAt, targetDuration, instructor.getId());

        return sessionRepo.save(session);
    }

    // ============================================================
    // Cancel
    // ============================================================

    @Transactional
    public LessonSession cancel(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (LessonSession.STATUS_CANCELLED.equals(session.getStatus())) {
            return session; // idempotent
        }
        if (!LessonSession.STATUS_SCHEDULED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "SESSION_NOT_CANCELLABLE",
                    "Only SCHEDULED sessions can be cancelled (current=" + session.getStatus() + ")");
        }
        requireParticipantScope(role, currentUserId, session);

        Instant now = Instant.now();
        session.setStatus(LessonSession.STATUS_CANCELLED);
        session.setCancelledAt(now);
        session.setCancelledByUserId(currentUserId);

        log.info("Session {} cancelled by {}={}", sessionId, role, currentUserId);
        return sessionRepo.save(session);
    }

    // ============================================================
    // Complete
    // ============================================================

    @Transactional
    public LessonSession complete(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (!LessonSession.STATUS_SCHEDULED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "SESSION_NOT_COMPLETABLE",
                    "Only SCHEDULED sessions can be completed (current=" + session.getStatus() + ")");
        }
        requireOwnerOrAssignedInstructor(role, currentUserId, session);

        Student student = studentRepo.findById(session.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + session.getStudentId()));
        if (student.getLessonsRemaining() <= 0) {
            throw new BadRequestException(
                    "NO_LESSONS_REMAINING",
                    "Student has no lessons remaining; replenish the package before completing.");
        }
        student.setLessonsRemaining(student.getLessonsRemaining() - 1);
        studentRepo.save(student);

        session.setStatus(LessonSession.STATUS_COMPLETED);
        log.info("Session {} completed by {}={} (student lessons left = {})",
                sessionId, role, currentUserId, student.getLessonsRemaining());
        return sessionRepo.save(session);
    }

    // ============================================================
    // Mark no-show
    // ============================================================

    @Transactional
    public LessonSession markNoShow(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (!LessonSession.STATUS_SCHEDULED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "SESSION_NOT_NOSHOWABLE",
                    "Only SCHEDULED sessions can be marked NO_SHOW (current=" + session.getStatus() + ")");
        }
        requireOwnerOrAssignedInstructor(role, currentUserId, session);

        Student student = studentRepo.findById(session.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + session.getStudentId()));
        // Per S4: no-show decrements remaining (treat as consumed). Allow it
        // even if remaining==0 (avoid a deadlock where a stale no-show can't
        // be recorded because the package ran dry).
        if (student.getLessonsRemaining() > 0) {
            student.setLessonsRemaining(student.getLessonsRemaining() - 1);
            studentRepo.save(student);
        }

        session.setStatus(LessonSession.STATUS_NO_SHOW);
        log.info("Session {} marked NO_SHOW by {}={} (student lessons left = {})",
                sessionId, role, currentUserId, student.getLessonsRemaining());
        return sessionRepo.save(session);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public LessonSession getById(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        requireParticipantScope(role, currentUserId, session);
        return session;
    }

    @Transactional(readOnly = true)
    public List<LessonSession> calendar(Role role, UUID currentUserId,
                                        UUID schoolId, UUID instructorIdFilter, UUID studentIdFilter,
                                        Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BadRequestException(
                    "INVALID_DATE_RANGE",
                    "from and to are required and from must be before to");
        }

        switch (role) {
            case OWNER -> {
                if (schoolId == null) {
                    throw new BadRequestException(
                            "SCHOOL_ID_REQUIRED",
                            "OWNER must supply schoolId for calendar queries");
                }
                School school = schoolRepo.findById(schoolId)
                        .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
                if (!currentUserId.equals(school.getOwnerId())) {
                    throw new AccessDeniedException("OWNER can only view their own school's calendar");
                }
                if (instructorIdFilter != null) {
                    return sessionRepo.findForInstructorInRange(instructorIdFilter, from, to);
                }
                if (studentIdFilter != null) {
                    return sessionRepo.findForStudentInRange(studentIdFilter, from, to);
                }
                return sessionRepo.findForSchoolInRange(schoolId, from, to);
            }
            case INSTRUCTOR -> {
                Instructor me = instructorRepo.findByUserId(currentUserId)
                        .orElseThrow(() -> new AccessDeniedException("Caller has no instructor profile"));
                return sessionRepo.findForInstructorInRange(me.getId(), from, to);
            }
            case STUDENT -> {
                Student me = studentRepo.findByUserId(currentUserId)
                        .orElseThrow(() -> new AccessDeniedException("Caller has no student profile"));
                return sessionRepo.findForStudentInRange(me.getId(), from, to);
            }
            case PARENT -> {
                if (studentIdFilter == null) {
                    throw new BadRequestException(
                            "STUDENT_ID_REQUIRED",
                            "PARENT must supply studentId for calendar queries");
                }
                if (!parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, studentIdFilter)) {
                    throw new AccessDeniedException("Student is not linked to this parent");
                }
                return sessionRepo.findForStudentInRange(studentIdFilter, from, to);
            }
        }
        throw new AccessDeniedException("Unknown role for calendar query");
    }

    // ============================================================
    // Conflict + working-hours guards
    // ============================================================

    private void requireNoConflicts(UUID instructorId, UUID studentId,
                                    UUID excludeSessionId,
                                    Instant from, Instant to) {
        // Pad the candidate range so a session that started before `from` but
        // ends after `from` is still in the fetched set.
        Instant fetchFrom = from.minus(COLLISION_FETCH_PADDING);
        Instant fetchTo   = to;

        for (LessonSession s : sessionRepo.findInstructorActiveInRange(instructorId, fetchFrom, fetchTo)) {
            if (excludeSessionId != null && excludeSessionId.equals(s.getId())) continue;
            if (overlaps(s, from, to)) {
                throw new BadRequestException(
                        "INSTRUCTOR_BUSY",
                        "Instructor already has a session that overlaps this window");
            }
        }
        for (LessonSession s : sessionRepo.findStudentActiveInRange(studentId, fetchFrom, fetchTo)) {
            if (excludeSessionId != null && excludeSessionId.equals(s.getId())) continue;
            if (overlaps(s, from, to)) {
                throw new BadRequestException(
                        "STUDENT_BUSY",
                        "Student already has a session that overlaps this window");
            }
        }
    }

    private static boolean overlaps(LessonSession s, Instant from, Instant to) {
        // Half-open intervals: [s.start, s.end) overlaps [from, to)
        // iff s.start < to AND s.end > from.
        return s.getScheduledAt().isBefore(to) && s.getEndAt().isAfter(from);
    }

    private void requireWithinWorkingHours(Instructor instructor, School school,
                                            Instant from, Instant to) {
        WorkingHours wh = WorkingHours.fromJson(instructor.getWorkingHoursJson());
        if (wh == null || wh.schedule() == null || wh.schedule().isEmpty()) {
            throw new BadRequestException(
                    "INSTRUCTOR_NO_HOURS_SET",
                    "Instructor has no working hours configured. Use forceOutsideHours=true (OWNER only) to override.");
        }

        ZoneId zone = resolveZone(school.getTimezone());
        LocalDateTime localFrom = LocalDateTime.ofInstant(from, zone);
        LocalDateTime localTo   = LocalDateTime.ofInstant(to, zone);

        // For V1, refuse cross-day windows — keeps working-hours math local
        // to a single weekday list. (A 22:00 → 02:00 lesson would need
        // multi-day window logic that's overkill for now.)
        if (!localFrom.toLocalDate().equals(localTo.toLocalDate())) {
            throw new BadRequestException(
                    "OUTSIDE_WORKING_HOURS",
                    "Lesson cannot span across midnight in the school's local time");
        }

        DayOfWeek dow = localFrom.getDayOfWeek();
        List<WorkingHours.TimeRange> ranges = wh.schedule().get(dow);
        if (ranges == null || ranges.isEmpty()) {
            throw new BadRequestException(
                    "OUTSIDE_WORKING_HOURS",
                    "Instructor does not work on " + dow);
        }

        LocalTime fromTime = localFrom.toLocalTime();
        LocalTime toTime   = localTo.toLocalTime();
        for (WorkingHours.TimeRange r : ranges) {
            if (!r.start().isAfter(fromTime) && !r.end().isBefore(toTime)) {
                return; // inside this range
            }
        }
        throw new BadRequestException(
                "OUTSIDE_WORKING_HOURS",
                "Lesson window " + fromTime + "–" + toTime
                        + " falls outside instructor's hours on " + dow);
    }

    private static ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? "America/Regina" : tz);
        } catch (Exception ex) {
            // Defensive — fall back to school's intended default rather than
            // crashing the booking call. The school update endpoint validates
            // before persisting, so this branch should rarely fire.
            return ZoneId.of("America/Regina");
        }
    }

    // ============================================================
    // Scope rules
    // ============================================================

    private void requireBookingScope(Role role, UUID currentUserId,
                                      School school, Instructor instructor, Student student) {
        switch (role) {
            case OWNER -> {
                if (currentUserId.equals(school.getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                // Instructor can book sessions for themselves only.
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(instructor.getId())) return;
            }
            case STUDENT -> {
                // Student can book a session as themselves.
                Optional<Student> me = studentRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(student.getId())) return;
            }
            default -> { /* deny */ }
        }
        throw new AccessDeniedException("Caller cannot book this session");
    }

    /**
     * Read / cancel scope: any participant (OWNER of school, instructor on the
     * row, student on the row, parent linked to the student).
     */
    private void requireParticipantScope(Role role, UUID currentUserId, LessonSession session) {
        switch (role) {
            case OWNER -> {
                Optional<School> s = schoolRepo.findById(session.getSchoolId());
                if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(session.getInstructorId())) return;
            }
            case STUDENT -> {
                Optional<Student> me = studentRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(session.getStudentId())) return;
            }
            case PARENT -> {
                if (parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, session.getStudentId())) return;
            }
        }
        throw new AccessDeniedException("Caller cannot view / cancel this session");
    }

    /**
     * Complete / no-show: only the OWNER of the school OR the assigned
     * instructor. Students/parents cannot mark a session done.
     */
    private void requireOwnerOrAssignedInstructor(Role role, UUID currentUserId, LessonSession session) {
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
        throw new AccessDeniedException("Only an OWNER or assigned INSTRUCTOR can complete / no-show a session");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static void validateDuration(int duration) {
        if (duration < MIN_DURATION_MINS || duration > MAX_DURATION_MINS) {
            throw new BadRequestException(
                    "INVALID_DURATION",
                    "durationMins must be between " + MIN_DURATION_MINS + " and " + MAX_DURATION_MINS);
        }
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
