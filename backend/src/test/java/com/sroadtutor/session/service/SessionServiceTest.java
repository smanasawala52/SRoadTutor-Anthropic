package com.sroadtutor.session.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
class SessionServiceTest {

    @Mock LessonSessionRepository sessionRepo;
    @Mock SchoolRepository        schoolRepo;
    @Mock InstructorRepository    instructorRepo;
    @Mock StudentRepository       studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;

    @InjectMocks SessionService service;

    // Helpers ----------------------------------------------------------

    /** Returns a future Instant for the next given DayOfWeek at the given local time, in America/Regina. */
    private static Instant nextWeekdayAt(DayOfWeek dow, LocalTime time) {
        ZoneId zone = ZoneId.of("America/Regina");
        LocalDate today = LocalDate.now(zone);
        int delta = dow.getValue() - today.getDayOfWeek().getValue();
        if (delta <= 0) delta += 7;
        return LocalDateTime.of(today.plusDays(delta), time).atZone(zone).toInstant();
    }

    /** Standard happy-path setup: school + instructor + student all owned by the same OWNER, instructor works Mon 09:00–17:00. */
    private record HappyPath(UUID ownerId, UUID schoolId, UUID instructorUserId, UUID instructorId,
                             UUID studentUserId, UUID studentId, School school, Instructor instructor, Student student) {}

    private HappyPath buildHappyPath() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instUserId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        School school = School.builder().id(schoolId).ownerId(ownerId)
                .active(true).timezone("America/Regina").build();
        WorkingHours hours = new WorkingHours(Map.of(
                DayOfWeek.MONDAY,    List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))),
                DayOfWeek.TUESDAY,   List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))),
                DayOfWeek.WEDNESDAY, List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))),
                DayOfWeek.THURSDAY,  List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))),
                DayOfWeek.FRIDAY,    List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))));
        Instructor instructor = Instructor.builder().id(instructorId).userId(instUserId)
                .schoolId(schoolId).active(true).workingHoursJson(hours.toJson()).build();
        Student student = Student.builder().id(studentId).userId(studentUserId).schoolId(schoolId)
                .packageTotalLessons(10).lessonsRemaining(10).status(Student.STATUS_ACTIVE).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(instructorRepo.findByUserId(instUserId)).thenReturn(Optional.of(instructor));
        when(studentRepo.findByUserId(studentUserId)).thenReturn(Optional.of(student));
        when(sessionRepo.save(any(LessonSession.class))).thenAnswer(inv -> {
            LessonSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(sessionRepo.findInstructorActiveInRange(any(), any(), any())).thenReturn(List.of());
        when(sessionRepo.findStudentActiveInRange(any(), any(), any())).thenReturn(List.of());

        return new HappyPath(ownerId, schoolId, instUserId, instructorId,
                studentUserId, studentId, school, instructor, student);
    }

    // ============================================================
    // Book — happy path
    // ============================================================

    @Test
    void book_happyPath_owner() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, "Walmart parking lot", null, null);

        LessonSession s = service.book(Role.OWNER, h.ownerId, req);
        assertThat(s.getStatus()).isEqualTo("SCHEDULED");
        assertThat(s.getInstructorId()).isEqualTo(h.instructorId);
        assertThat(s.getStudentId()).isEqualTo(h.studentId);
        assertThat(s.getDurationMins()).isEqualTo(60);
        assertThat(s.getCreatedByUserId()).isEqualTo(h.ownerId);
    }

    @Test
    void book_happyPath_studentSelf() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.TUESDAY, LocalTime.of(11, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        LessonSession s = service.book(Role.STUDENT, h.studentUserId, req);
        assertThat(s.getCreatedByUserId()).isEqualTo(h.studentUserId);
    }

    // ============================================================
    // Book — guards
    // ============================================================

    @Test
    void book_rejectsPastDatetime() {
        HappyPath h = buildHappyPath();
        var req = new BookSessionRequest(h.instructorId, h.studentId,
                Instant.now().minusSeconds(60), 60, null, null, null);
        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("PAST_DATETIME"));
    }

    @Test
    void book_rejectsCrossSchoolInstructor() {
        HappyPath h = buildHappyPath();
        h.instructor.setSchoolId(UUID.randomUUID()); // different school
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_WRONG_SCHOOL"));
    }

    @Test
    void book_rejectsOutsideWorkingHours() {
        HappyPath h = buildHappyPath();
        // Saturday, instructor doesn't work
        Instant when = nextWeekdayAt(DayOfWeek.SATURDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("OUTSIDE_WORKING_HOURS"));
    }

    @Test
    void book_rejectsLessonSpillingPastWorkingHoursEnd() {
        HappyPath h = buildHappyPath();
        // Mon 16:30 + 60 min = 17:30 → past 17:00 cutoff
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(16, 30));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("OUTSIDE_WORKING_HOURS"));
    }

    @Test
    void book_ownerOverridesOutsideHoursWithForce() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.SATURDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, true);

        LessonSession s = service.book(Role.OWNER, h.ownerId, req);
        assertThat(s.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void book_nonOwnerCannotForce() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.SATURDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, true);

        assertThatThrownBy(() -> service.book(Role.INSTRUCTOR, h.instructorUserId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("FORCE_REQUIRES_OWNER"));
    }

    @Test
    void book_detectsInstructorConflict() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        // Existing 60-min session at 09:30 (overlaps the 10:00 booking)
        LessonSession existing = LessonSession.builder()
                .id(UUID.randomUUID()).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(UUID.randomUUID())
                .scheduledAt(when.minusSeconds(30 * 60)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findInstructorActiveInRange(any(), any(), any())).thenReturn(List.of(existing));

        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);
        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_BUSY"));
    }

    @Test
    void book_detectsStudentConflict() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        LessonSession existing = LessonSession.builder()
                .id(UUID.randomUUID()).schoolId(h.schoolId)
                .instructorId(UUID.randomUUID()).studentId(h.studentId)
                .scheduledAt(when).durationMins(45)
                .status("SCHEDULED").build();
        when(sessionRepo.findStudentActiveInRange(any(), any(), any())).thenReturn(List.of(existing));

        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);
        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("STUDENT_BUSY"));
    }

    @Test
    void book_cancelledRowDoesNotConflict() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        // findInstructorActiveInRange should never return CANCELLED rows in
        // production; mock returning [] to match.
        when(sessionRepo.findInstructorActiveInRange(any(), any(), any())).thenReturn(List.of());

        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);
        LessonSession s = service.book(Role.OWNER, h.ownerId, req);
        assertThat(s.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void book_rejectsInactiveSchool() {
        HappyPath h = buildHappyPath();
        h.school.setActive(false);
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_INACTIVE"));
    }

    @Test
    void book_rejectsInactiveInstructor() {
        HappyPath h = buildHappyPath();
        h.instructor.setActive(false);
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_INACTIVE"));
    }

    @Test
    void book_rejectsInactiveStudent() {
        HappyPath h = buildHappyPath();
        h.student.setStatus(Student.STATUS_DROPPED);
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, h.ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("STUDENT_NOT_ACTIVE"));
    }

    @Test
    void book_403ForUnrelatedOwner() {
        HappyPath h = buildHappyPath();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.OWNER, UUID.randomUUID(), req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void book_403ForStudentBookingForSomeoneElse() {
        HappyPath h = buildHappyPath();
        UUID otherStudentUserId = UUID.randomUUID();
        Instant when = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        var req = new BookSessionRequest(h.instructorId, h.studentId, when, 60, null, null, null);

        assertThatThrownBy(() -> service.book(Role.STUDENT, otherStudentUserId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // Reschedule
    // ============================================================

    @Test
    void reschedule_updatesInPlace() {
        HappyPath h = buildHappyPath();
        Instant origWhen = nextWeekdayAt(DayOfWeek.MONDAY, LocalTime.of(10, 0));
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(origWhen).durationMins(60).status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        Instant newWhen = nextWeekdayAt(DayOfWeek.TUESDAY, LocalTime.of(11, 0));
        var req = new RescheduleSessionRequest(null, newWhen, 90, null, null, null);

        LessonSession after = service.reschedule(Role.OWNER, h.ownerId, sessionId, req);
        assertThat(after.getId()).isEqualTo(sessionId);
        assertThat(after.getScheduledAt()).isEqualTo(newWhen);
        assertThat(after.getDurationMins()).isEqualTo(90);
    }

    @Test
    void reschedule_rejectsNonScheduledSession() {
        HappyPath h = buildHappyPath();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().plusSeconds(3600)).durationMins(60)
                .status("COMPLETED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        Instant newWhen = nextWeekdayAt(DayOfWeek.TUESDAY, LocalTime.of(11, 0));
        var req = new RescheduleSessionRequest(null, newWhen, null, null, null, null);

        assertThatThrownBy(() -> service.reschedule(Role.OWNER, h.ownerId, sessionId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SESSION_NOT_RESCHEDULABLE"));
    }

    // ============================================================
    // Cancel
    // ============================================================

    @Test
    void cancel_setsCancelledAtAndIsIdempotent() {
        HappyPath h = buildHappyPath();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().plusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        LessonSession after = service.cancel(Role.OWNER, h.ownerId, sessionId);
        assertThat(after.getStatus()).isEqualTo("CANCELLED");
        assertThat(after.getCancelledAt()).isNotNull();
        assertThat(after.getCancelledByUserId()).isEqualTo(h.ownerId);

        // second call (now CANCELLED) is idempotent
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(after));
        LessonSession again = service.cancel(Role.OWNER, h.ownerId, sessionId);
        assertThat(again.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_rejectsCompletedSession() {
        HappyPath h = buildHappyPath();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().minusSeconds(3600)).durationMins(60)
                .status("COMPLETED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(Role.OWNER, h.ownerId, sessionId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SESSION_NOT_CANCELLABLE"));
    }

    @Test
    void cancel_canBeDoneByLinkedParent() {
        HappyPath h = buildHappyPath();
        UUID parentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().plusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, h.studentId)).thenReturn(true);

        LessonSession after = service.cancel(Role.PARENT, parentId, sessionId);
        assertThat(after.getStatus()).isEqualTo("CANCELLED");
    }

    // ============================================================
    // Complete + No-show
    // ============================================================

    @Test
    void complete_decrementsLessonsRemaining() {
        HappyPath h = buildHappyPath();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().minusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));
        when(studentRepo.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        LessonSession after = service.complete(Role.OWNER, h.ownerId, sessionId);
        assertThat(after.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<Student> stCap = ArgumentCaptor.forClass(Student.class);
        verify(studentRepo).save(stCap.capture());
        assertThat(stCap.getValue().getLessonsRemaining()).isEqualTo(9);
    }

    @Test
    void complete_rejectsZeroLessonsRemaining() {
        HappyPath h = buildHappyPath();
        h.student.setLessonsRemaining(0);
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().minusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.complete(Role.OWNER, h.ownerId, sessionId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("NO_LESSONS_REMAINING"));
    }

    @Test
    void complete_403ForStudent() {
        HappyPath h = buildHappyPath();
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().minusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.complete(Role.STUDENT, h.studentUserId, sessionId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noShow_decrementsAndIsAllowedAtZero() {
        HappyPath h = buildHappyPath();
        h.student.setLessonsRemaining(0);
        UUID sessionId = UUID.randomUUID();
        LessonSession existing = LessonSession.builder()
                .id(sessionId).schoolId(h.schoolId)
                .instructorId(h.instructorId).studentId(h.studentId)
                .scheduledAt(Instant.now().minusSeconds(3600)).durationMins(60)
                .status("SCHEDULED").build();
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));

        LessonSession after = service.markNoShow(Role.OWNER, h.ownerId, sessionId);
        assertThat(after.getStatus()).isEqualTo("NO_SHOW");
        // remaining was already 0 — no decrement
        verify(studentRepo, never()).save(any(Student.class));
    }

    // ============================================================
    // Calendar
    // ============================================================

    @Test
    void calendar_owner_filtersBySchool() {
        HappyPath h = buildHappyPath();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(7 * 86400);
        when(sessionRepo.findForSchoolInRange(h.schoolId, from, to)).thenReturn(List.of());

        List<LessonSession> list = service.calendar(Role.OWNER, h.ownerId, h.schoolId, null, null, from, to);
        assertThat(list).isEmpty();
    }

    @Test
    void calendar_owner_requiresSchoolId() {
        HappyPath h = buildHappyPath();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(7 * 86400);

        assertThatThrownBy(() -> service.calendar(Role.OWNER, h.ownerId, null, null, null, from, to))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_ID_REQUIRED"));
    }

    @Test
    void calendar_instructor_seesOnlyOwnSessions() {
        HappyPath h = buildHappyPath();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(7 * 86400);
        when(sessionRepo.findForInstructorInRange(h.instructorId, from, to)).thenReturn(List.of());

        List<LessonSession> list = service.calendar(Role.INSTRUCTOR, h.instructorUserId, null, null, null, from, to);
        assertThat(list).isEmpty();
        verify(sessionRepo).findForInstructorInRange(h.instructorId, from, to);
    }

    @Test
    void calendar_parent_requiresLinkedStudent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(7 * 86400);
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.calendar(Role.PARENT, parentId, null, null, studentId, from, to))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void calendar_rejectsInvertedRange() {
        Instant from = Instant.now().plusSeconds(86400);
        Instant to = Instant.now();

        assertThatThrownBy(() -> service.calendar(Role.OWNER, UUID.randomUUID(), UUID.randomUUID(), null, null, from, to))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVALID_DATE_RANGE"));
    }
}
