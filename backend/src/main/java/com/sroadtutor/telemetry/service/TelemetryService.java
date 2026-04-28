package com.sroadtutor.telemetry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.evaluation.model.SessionMistake;
import com.sroadtutor.evaluation.repository.SessionMistakeRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.telemetry.dto.AttachTelemetryRequest;
import com.sroadtutor.telemetry.dto.TelemetryDatasetSummary;
import com.sroadtutor.telemetry.dto.TelemetryEventResponse;
import com.sroadtutor.telemetry.model.TelemetryEvent;
import com.sroadtutor.telemetry.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 3 telemetry sync. Locked at PR20:
 *
 * <ul>
 *   <li><b>Attach</b> ({@link #attach}) — owner / assigned-instructor
 *       attaches vehicle telemetry to a logged mistake. Multiple events
 *       per mistake are allowed (lead-up + recovery snapshots, etc.).
 *       The {@code session_mistake_id} FK has {@code ON DELETE CASCADE}
 *       at the DB; deleting the mistake frees the telemetry rows.</li>
 *   <li><b>Read</b> ({@link #listForMistake}) — same scope as
 *       {@code MistakeLogService.listForSession} — any participant.</li>
 *   <li><b>Dataset summary</b> ({@link #datasetSummary}) — counts only.
 *       The B2B-API key gate that grants AV-research firms their
 *       licensed slice is tracked as TD; V1 keeps the endpoint
 *       OWNER-only as a safe stub.</li>
 * </ul>
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule()).build();

    private final TelemetryEventRepository telemetryRepo;
    private final SessionMistakeRepository mistakeRepo;
    private final LessonSessionRepository sessionRepo;
    private final SchoolRepository schoolRepo;
    private final InstructorRepository instructorRepo;

    public TelemetryService(TelemetryEventRepository telemetryRepo,
                              SessionMistakeRepository mistakeRepo,
                              LessonSessionRepository sessionRepo,
                              SchoolRepository schoolRepo,
                              InstructorRepository instructorRepo) {
        this.telemetryRepo = telemetryRepo;
        this.mistakeRepo = mistakeRepo;
        this.sessionRepo = sessionRepo;
        this.schoolRepo = schoolRepo;
        this.instructorRepo = instructorRepo;
    }

    // ============================================================
    // Attach
    // ============================================================

    @Transactional
    public TelemetryEventResponse attach(Role role, UUID currentUserId,
                                           UUID sessionMistakeId, AttachTelemetryRequest req) {
        SessionMistake mistake = mistakeRepo.findById(sessionMistakeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session mistake not found: " + sessionMistakeId));
        LessonSession session = sessionRepo.findById(mistake.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session disappeared: " + mistake.getSessionId()));
        requireOwnerOrAssignedInstructor(role, currentUserId, session);

        if (req.telemetry() == null || req.telemetry().isEmpty()) {
            throw new BadRequestException(
                    "EMPTY_TELEMETRY",
                    "telemetry payload must not be empty");
        }

        TelemetryEvent ev = TelemetryEvent.builder()
                .sessionMistakeId(sessionMistakeId)
                .vehicleMake(nullIfBlank(req.vehicleMake()))
                .vehicleModel(nullIfBlank(req.vehicleModel()))
                .vehicleYear(req.vehicleYear())
                .telemetryJson(serialize(req.telemetry()))
                .offsetMs(req.offsetMs())
                .build();
        ev = telemetryRepo.save(ev);

        log.info("Telemetry attached: mistake={} make={} model={} offsetMs={}",
                sessionMistakeId, ev.getVehicleMake(), ev.getVehicleModel(), ev.getOffsetMs());
        return TelemetryEventResponse.fromEntity(ev);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<TelemetryEventResponse> listForMistake(Role role, UUID currentUserId, UUID sessionMistakeId) {
        SessionMistake mistake = mistakeRepo.findById(sessionMistakeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session mistake not found: " + sessionMistakeId));
        LessonSession session = sessionRepo.findById(mistake.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session disappeared: " + mistake.getSessionId()));
        requireOwnerOrAssignedInstructor(role, currentUserId, session);

        return telemetryRepo.findBySessionMistakeIdOrderBySyncedAtAsc(sessionMistakeId).stream()
                .map(TelemetryEventResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public TelemetryDatasetSummary datasetSummary(Role role) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can read the telemetry summary in V1");
        }
        return new TelemetryDatasetSummary(telemetryRepo.count(), Instant.now());
    }

    // ============================================================
    // Scope
    // ============================================================

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
        throw new AccessDeniedException("Only OWNER or assigned INSTRUCTOR can manage telemetry");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static String serialize(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException(
                    "INVALID_TELEMETRY",
                    "Could not serialise telemetry: " + ex.getMessage());
        }
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
