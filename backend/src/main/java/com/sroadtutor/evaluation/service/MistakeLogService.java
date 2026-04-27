package com.sroadtutor.evaluation.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.evaluation.dto.LogMistakeRequest;
import com.sroadtutor.evaluation.dto.MistakeCategoryResponse;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.dto.SessionMistakeResponse;
import com.sroadtutor.evaluation.model.MistakeCategory;
import com.sroadtutor.evaluation.model.SessionMistake;
import com.sroadtutor.evaluation.repository.MistakeCategoryRepository;
import com.sroadtutor.evaluation.repository.SessionMistakeRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mistake logging + readiness scoring. Locked at PR13:
 *
 * <ul>
 *   <li><b>Per-session score</b> = {@code max(0, 100 - Σ (category.points × count))}.
 *       Demerit weight comes from {@link MistakeCategory#getPoints}; severity
 *       FAIL contributes a heavy weight (typically 25, seeded in V5/V6) so
 *       a single FAIL mistake usually drops a session well below the
 *       readiness threshold.</li>
 *   <li><b>Cumulative readiness</b> = average of the last N sessions (default 5).
 *       If fewer than N sessions exist, average all of them. Empty history → 100.</li>
 *   <li><b>FAIL flag</b> — {@code anyFailMistakeRecently} is true if any of
 *       the considered sessions logged a FAIL mistake. The SPA may use this
 *       to override a "ready" signal even when the average is high.</li>
 *   <li><b>Scope</b> — log: OWNER of school OR assigned INSTRUCTOR. Read:
 *       above + the student themselves + linked PARENT.</li>
 * </ul>
 */
@Service
public class MistakeLogService {

    private static final Logger log = LoggerFactory.getLogger(MistakeLogService.class);

    /** Number of recent sessions to roll up into the cumulative score. */
    public static final int DEFAULT_RECENT_SESSIONS = 5;

    /** Score floor — a session can't go below 0 even with many demerits. */
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    private final SessionMistakeRepository mistakeRepo;
    private final MistakeCategoryRepository categoryRepo;
    private final LessonSessionRepository sessionRepo;
    private final SchoolRepository schoolRepo;
    private final InstructorRepository instructorRepo;
    private final StudentRepository studentRepo;
    private final ParentStudentRepository parentLinkRepo;

    public MistakeLogService(SessionMistakeRepository mistakeRepo,
                              MistakeCategoryRepository categoryRepo,
                              LessonSessionRepository sessionRepo,
                              SchoolRepository schoolRepo,
                              InstructorRepository instructorRepo,
                              StudentRepository studentRepo,
                              ParentStudentRepository parentLinkRepo) {
        this.mistakeRepo = mistakeRepo;
        this.categoryRepo = categoryRepo;
        this.sessionRepo = sessionRepo;
        this.schoolRepo = schoolRepo;
        this.instructorRepo = instructorRepo;
        this.studentRepo = studentRepo;
        this.parentLinkRepo = parentLinkRepo;
    }

    // ============================================================
    // Catalog
    // ============================================================

    @Transactional(readOnly = true)
    public List<MistakeCategoryResponse> listCategories(String jurisdiction) {
        String j = jurisdiction == null || jurisdiction.isBlank() ? "SGI" : jurisdiction.toUpperCase();
        return categoryRepo.findByJurisdictionAndActiveTrueOrderByDisplayOrderAsc(j).stream()
                .map(MistakeCategoryResponse::fromEntity)
                .toList();
    }

    // ============================================================
    // Log
    // ============================================================

    @Transactional
    public SessionMistakeResponse log(Role role, UUID currentUserId, UUID sessionId, LogMistakeRequest req) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        // Mistakes can only be logged on SCHEDULED or COMPLETED sessions —
        // CANCELLED / NO_SHOW sessions never had an in-car portion.
        if (!LessonSession.STATUS_SCHEDULED.equals(session.getStatus())
                && !LessonSession.STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BadRequestException(
                    "SESSION_NOT_LOGGABLE",
                    "Mistakes can only be logged on SCHEDULED or COMPLETED sessions");
        }

        requireOwnerOrAssignedInstructor(role, currentUserId, session);

        MistakeCategory category = categoryRepo.findById(req.mistakeCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mistake category not found: " + req.mistakeCategoryId()));
        if (!category.isActive()) {
            throw new BadRequestException(
                    "CATEGORY_INACTIVE",
                    "Mistake category is no longer active");
        }

        int count = req.count() == null ? 1 : req.count();
        SessionMistake mistake = SessionMistake.builder()
                .sessionId(sessionId)
                .studentId(session.getStudentId())
                .mistakeCategoryId(category.getId())
                .count(count)
                .instructorNotes(nullIfBlank(req.instructorNotes()))
                .build();
        mistake = mistakeRepo.save(mistake);

        log.info("Mistake logged session={} category={} count={} by {}={}",
                sessionId, category.getCategoryName(), count, role, currentUserId);

        return SessionMistakeResponse.from(mistake,
                category.getCategoryName(), category.getSeverity(), category.getPoints());
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public List<SessionMistakeResponse> listForSession(Role role, UUID currentUserId, UUID sessionId) {
        LessonSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        requireParticipantScope(role, currentUserId, session);

        List<SessionMistake> rows = mistakeRepo.findBySessionIdOrderByLoggedAtAsc(sessionId);
        return enrichWithCategory(rows);
    }

    @Transactional(readOnly = true)
    public List<SessionMistakeResponse> listForStudent(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireStudentReadScope(role, currentUserId, student);

        List<SessionMistake> rows = mistakeRepo.findByStudentIdOrderByLoggedAtAsc(studentId);
        return enrichWithCategory(rows);
    }

    // ============================================================
    // Readiness score
    // ============================================================

    @Transactional(readOnly = true)
    public ReadinessScoreResponse readinessForStudent(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        requireStudentReadScope(role, currentUserId, student);

        // Walk recent-first; group by sessionId; stop after DEFAULT_RECENT_SESSIONS
        // distinct sessions. Empty history → score 100.
        List<SessionMistake> rows = mistakeRepo.findByStudentIdRecentFirst(studentId);
        if (rows.isEmpty()) {
            return new ReadinessScoreResponse(
                    studentId, 0, MAX_SCORE, false, List.of());
        }

        // Pre-load category points for the rows we'll see (avoids N+1).
        Map<UUID, MistakeCategory> categoryById = preloadCategoriesFor(rows);

        // Group by session, in order of first appearance (which is recent-first).
        LinkedHashMap<UUID, List<SessionMistake>> bySession = new LinkedHashMap<>();
        for (SessionMistake m : rows) {
            bySession.computeIfAbsent(m.getSessionId(), k -> new ArrayList<>()).add(m);
            if (bySession.size() >= DEFAULT_RECENT_SESSIONS && !bySession.containsKey(m.getSessionId())) {
                // (not reachable — added before the size check) safety guard
                break;
            }
        }

        List<ReadinessScoreResponse.PerSessionScore> perSession = new ArrayList<>();
        int totalScore = 0;
        boolean anyFail = false;
        int considered = 0;
        for (Map.Entry<UUID, List<SessionMistake>> e : bySession.entrySet()) {
            if (considered >= DEFAULT_RECENT_SESSIONS) break;
            int demerits = 0;
            boolean hadFail = false;
            for (SessionMistake m : e.getValue()) {
                MistakeCategory c = categoryById.get(m.getMistakeCategoryId());
                if (c == null) continue; // category missing — defensive, ignore
                demerits += c.getPoints() * m.getCount();
                if (MistakeCategory.SEVERITY_FAIL.equals(c.getSeverity())) hadFail = true;
            }
            int score = Math.max(MIN_SCORE, MAX_SCORE - demerits);
            perSession.add(new ReadinessScoreResponse.PerSessionScore(
                    e.getKey(), score, demerits, hadFail));
            totalScore += score;
            if (hadFail) anyFail = true;
            considered++;
        }
        double average = considered == 0 ? MAX_SCORE : (double) totalScore / considered;

        return new ReadinessScoreResponse(studentId, considered, average, anyFail, perSession);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private List<SessionMistakeResponse> enrichWithCategory(List<SessionMistake> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, MistakeCategory> byId = preloadCategoriesFor(rows);
        List<SessionMistakeResponse> out = new ArrayList<>(rows.size());
        for (SessionMistake m : rows) {
            MistakeCategory c = byId.get(m.getMistakeCategoryId());
            String name = c == null ? "(unknown)" : c.getCategoryName();
            String sev = c == null ? "MINOR" : c.getSeverity();
            int pts = c == null ? 0 : c.getPoints();
            out.add(SessionMistakeResponse.from(m, name, sev, pts));
        }
        return out;
    }

    private Map<UUID, MistakeCategory> preloadCategoriesFor(List<SessionMistake> rows) {
        Map<UUID, MistakeCategory> byId = new HashMap<>();
        for (SessionMistake m : rows) {
            UUID cid = m.getMistakeCategoryId();
            if (!byId.containsKey(cid)) {
                categoryRepo.findById(cid).ifPresent(c -> byId.put(cid, c));
            }
        }
        return byId;
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
        throw new AccessDeniedException("Only OWNER or assigned INSTRUCTOR can log mistakes");
    }

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
        throw new AccessDeniedException("Caller cannot read mistakes for this session");
    }

    private void requireStudentReadScope(Role role, UUID currentUserId, Student student) {
        switch (role) {
            case OWNER -> {
                Optional<School> s = schoolRepo.findById(student.getSchoolId());
                if (s.isPresent() && currentUserId.equals(s.get().getOwnerId())) return;
            }
            case INSTRUCTOR -> {
                Optional<Instructor> me = instructorRepo.findByUserId(currentUserId);
                if (me.isPresent() && me.get().getId().equals(student.getInstructorId())) return;
            }
            case STUDENT -> {
                if (currentUserId.equals(student.getUserId())) return;
            }
            case PARENT -> {
                if (parentLinkRepo.existsByParentUserIdAndStudentId(currentUserId, student.getId())) return;
            }
        }
        throw new AccessDeniedException("Caller cannot read this student's mistakes");
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
