package com.sroadtutor.evaluation.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.evaluation.dto.LogMistakeRequest;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.dto.SessionMistakeResponse;
import com.sroadtutor.evaluation.model.MistakeCategory;
import com.sroadtutor.evaluation.model.SessionMistake;
import com.sroadtutor.evaluation.repository.MistakeCategoryRepository;
import com.sroadtutor.evaluation.repository.SessionMistakeRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MistakeLogServiceTest {

    @Mock SessionMistakeRepository mistakeRepo;
    @Mock MistakeCategoryRepository categoryRepo;
    @Mock LessonSessionRepository sessionRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock InstructorRepository instructorRepo;
    @Mock StudentRepository studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;

    @InjectMocks MistakeLogService service;

    private MistakeCategory cat(String name, String severity, int points) {
        return MistakeCategory.builder()
                .id(UUID.randomUUID()).jurisdiction("SGI")
                .categoryName(name).severity(severity).points(points)
                .active(true).displayOrder(0).build();
    }

    // ============================================================
    // log
    // ============================================================

    @Test
    void log_succeedsForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        MistakeCategory category = cat("Failure to signal", "MAJOR", 5);
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(studentId)
                .instructorId(instructorId).status("SCHEDULED")
                .scheduledAt(Instant.now()).durationMins(60).build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(categoryRepo.findById(category.getId())).thenReturn(Optional.of(category));
        when(mistakeRepo.save(any(SessionMistake.class))).thenAnswer(inv -> {
            SessionMistake m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        var req = new LogMistakeRequest(category.getId(), 2, "Drifted into bike lane");
        SessionMistakeResponse resp = service.log(Role.OWNER, ownerId, sessionId, req);

        assertThat(resp.categoryName()).isEqualTo("Failure to signal");
        assertThat(resp.severity()).isEqualTo("MAJOR");
        assertThat(resp.points()).isEqualTo(5);
        assertThat(resp.count()).isEqualTo(2);
    }

    @Test
    void log_rejectsCancelledSession() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).status("CANCELLED")
                .scheduledAt(Instant.now()).durationMins(60).build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));

        var req = new LogMistakeRequest(UUID.randomUUID(), 1, null);

        assertThatThrownBy(() -> service.log(Role.OWNER, ownerId, sessionId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SESSION_NOT_LOGGABLE"));
    }

    @Test
    void log_403ForStudent() {
        UUID studentUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).status("SCHEDULED")
                .scheduledAt(Instant.now()).durationMins(60).build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(
                School.builder().id(schoolId).ownerId(UUID.randomUUID()).build()));

        var req = new LogMistakeRequest(UUID.randomUUID(), 1, null);

        assertThatThrownBy(() -> service.log(Role.STUDENT, studentUserId, sessionId, req))
                .isInstanceOf(AccessDeniedException.class);

        verify(mistakeRepo, never()).save(any());
    }

    @Test
    void log_rejectsInactiveCategory() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).status("SCHEDULED")
                .scheduledAt(Instant.now()).durationMins(60).build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();
        MistakeCategory category = MistakeCategory.builder()
                .id(UUID.randomUUID()).jurisdiction("SGI").categoryName("Old")
                .severity("MINOR").points(2).active(false).build();

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(categoryRepo.findById(category.getId())).thenReturn(Optional.of(category));

        var req = new LogMistakeRequest(category.getId(), 1, null);

        assertThatThrownBy(() -> service.log(Role.OWNER, ownerId, sessionId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("CATEGORY_INACTIVE"));
    }

    // ============================================================
    // readinessForStudent
    // ============================================================

    @Test
    void readiness_returns100WhenNoHistory() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(mistakeRepo.findByStudentIdRecentFirst(studentId)).thenReturn(List.of());

        ReadinessScoreResponse r = service.readinessForStudent(Role.OWNER, ownerId, studentId);
        assertThat(r.averageScore()).isEqualTo(100.0);
        assertThat(r.sessionsConsidered()).isZero();
        assertThat(r.anyFailMistakeRecently()).isFalse();
    }

    @Test
    void readiness_computesPerSessionScore() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        MistakeCategory minor = cat("Minor", "MINOR", 1);
        MistakeCategory major = cat("Major", "MAJOR", 5);
        MistakeCategory fail  = cat("Fail",  "FAIL",  25);

        // Session 1: 2× minor + 1× major = 1×2 + 5 = 7 demerits → 93
        // Session 2: 1× fail = 25 demerits → 75; FAIL flag true
        SessionMistake m11 = SessionMistake.builder().id(UUID.randomUUID())
                .sessionId(s1).studentId(studentId)
                .mistakeCategoryId(minor.getId()).count(2).loggedAt(Instant.now()).build();
        SessionMistake m12 = SessionMistake.builder().id(UUID.randomUUID())
                .sessionId(s1).studentId(studentId)
                .mistakeCategoryId(major.getId()).count(1).loggedAt(Instant.now()).build();
        SessionMistake m21 = SessionMistake.builder().id(UUID.randomUUID())
                .sessionId(s2).studentId(studentId)
                .mistakeCategoryId(fail.getId()).count(1).loggedAt(Instant.now().minusSeconds(86400)).build();

        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(mistakeRepo.findByStudentIdRecentFirst(studentId)).thenReturn(List.of(m11, m12, m21));
        when(categoryRepo.findById(minor.getId())).thenReturn(Optional.of(minor));
        when(categoryRepo.findById(major.getId())).thenReturn(Optional.of(major));
        when(categoryRepo.findById(fail.getId())).thenReturn(Optional.of(fail));

        ReadinessScoreResponse r = service.readinessForStudent(Role.OWNER, ownerId, studentId);
        assertThat(r.sessionsConsidered()).isEqualTo(2);
        assertThat(r.anyFailMistakeRecently()).isTrue();
        // Average of 93 and 75 = 84
        assertThat(r.averageScore()).isEqualTo(84.0);
    }

    @Test
    void readiness_floorIsZero() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        MistakeCategory fail = cat("Fail", "FAIL", 25);

        // 5× fail = 125 demerits → would be -25, floored to 0
        SessionMistake m = SessionMistake.builder().id(UUID.randomUUID())
                .sessionId(s1).studentId(studentId)
                .mistakeCategoryId(fail.getId()).count(5).loggedAt(Instant.now()).build();

        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(
                School.builder().id(schoolId).ownerId(ownerId).build()));
        when(mistakeRepo.findByStudentIdRecentFirst(studentId)).thenReturn(List.of(m));
        when(categoryRepo.findById(fail.getId())).thenReturn(Optional.of(fail));

        ReadinessScoreResponse r = service.readinessForStudent(Role.OWNER, ownerId, studentId);
        assertThat(r.averageScore()).isEqualTo(0.0);
        assertThat(r.perSession()).hasSize(1);
        assertThat(r.perSession().get(0).score()).isZero();
    }

    @Test
    void readiness_succeedsForLinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(true);
        when(mistakeRepo.findByStudentIdRecentFirst(studentId)).thenReturn(List.of());

        ReadinessScoreResponse r = service.readinessForStudent(Role.PARENT, parentId, studentId);
        assertThat(r.averageScore()).isEqualTo(100.0);
    }

    @Test
    void readiness_403ForUnlinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.readinessForStudent(Role.PARENT, parentId, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
