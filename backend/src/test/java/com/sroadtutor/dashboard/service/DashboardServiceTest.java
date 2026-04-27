package com.sroadtutor.dashboard.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.dashboard.dto.DashboardResponse;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.payment.model.Payment;
import com.sroadtutor.payment.repository.PaymentRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.session.model.LessonSession;
import com.sroadtutor.session.repository.LessonSessionRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.service.PlanLimitsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock SchoolRepository schoolRepo;
    @Mock UserRepository userRepo;
    @Mock StudentRepository studentRepo;
    @Mock InstructorRepository instructorRepo;
    @Mock LessonSessionRepository sessionRepo;
    @Mock PaymentRepository paymentRepo;
    @Mock PlanLimitsService planLimits;

    @InjectMocks DashboardService service;

    @Test
    void ownerDashboard_aggregatesCounts() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).schoolId(schoolId)
                .role(Role.OWNER).authProvider(AuthProvider.LOCAL).build();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("ABC Driving").active(true).timezone("America/Regina").planTier("PRO").build();

        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(planLimits.currentPlan(schoolId)).thenReturn(PlanTier.PRO);

        // Active students
        Student stu = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        when(studentRepo.findBySchoolIdAndStatus(schoolId, "ACTIVE")).thenReturn(List.of(stu));
        when(studentRepo.findBySchoolId(schoolId)).thenReturn(List.of(stu));

        // Sessions
        LessonSession upcoming = LessonSession.builder()
                .id(UUID.randomUUID()).schoolId(schoolId).studentId(studentId)
                .instructorId(instructorId).status("SCHEDULED")
                .scheduledAt(Instant.now().plusSeconds(86400)).durationMins(60).build();
        LessonSession recentDone = LessonSession.builder()
                .id(UUID.randomUUID()).schoolId(schoolId).studentId(studentId)
                .instructorId(instructorId).status("COMPLETED")
                .scheduledAt(Instant.now().minusSeconds(86400)).durationMins(60).build();

        when(sessionRepo.findForSchoolInRange(any(), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    Instant to   = inv.getArgument(2);
                    Instant nowMid = Instant.now();
                    if (from.isBefore(nowMid) && to.isBefore(nowMid.plusSeconds(60))) {
                        return List.of(recentDone);
                    }
                    return List.of(upcoming);
                });

        // Payment rollups
        when(paymentRepo.sumPaidForStudent(studentId)).thenReturn(new BigDecimal("180.00"));
        Payment unpaid = Payment.builder()
                .id(UUID.randomUUID()).schoolId(schoolId).studentId(studentId)
                .amount(new BigDecimal("60.00")).currency("CAD").status("UNPAID").build();
        when(paymentRepo.findOutstandingForSchool(schoolId)).thenReturn(List.of(unpaid));

        // Instructor + workloads
        Instructor inst = Instructor.builder().id(instructorId).userId(instructorUserId)
                .schoolId(schoolId).active(true).build();
        when(instructorRepo.findActiveBySchool(schoolId)).thenReturn(List.of(inst));
        when(userRepo.findById(instructorUserId)).thenReturn(Optional.of(
                User.builder().id(instructorUserId).fullName("Jane Inst")
                        .role(Role.INSTRUCTOR).authProvider(AuthProvider.LOCAL).build()));
        when(sessionRepo.findForInstructorInRange(any(), any(), any())).thenAnswer(inv -> {
            Instant from = inv.getArgument(1);
            Instant nowMid = Instant.now();
            if (from.isBefore(nowMid)) {
                return List.of(recentDone);
            }
            return List.of(upcoming);
        });
        when(studentRepo.findByInstructorId(instructorId)).thenReturn(List.of(stu));

        DashboardResponse resp = service.getForCurrentOwner(Role.OWNER, ownerId);

        assertThat(resp.schoolId()).isEqualTo(schoolId);
        assertThat(resp.schoolName()).isEqualTo("ABC Driving");
        assertThat(resp.planTier()).isEqualTo("PRO");
        assertThat(resp.totalRevenuePaid()).isEqualByComparingTo("180.00");
        assertThat(resp.totalOutstanding()).isEqualByComparingTo("60.00");
        assertThat(resp.activeStudentCount()).isEqualTo(1);
        assertThat(resp.upcomingSessionsCount()).isEqualTo(1);
        assertThat(resp.completedSessionsInWindow()).isEqualTo(1);
        assertThat(resp.monthlyRecurringRevenue()).isEqualByComparingTo("29.00");

        assertThat(resp.instructorWorkloads()).hasSize(1);
        DashboardResponse.InstructorWorkload w = resp.instructorWorkloads().get(0);
        assertThat(w.instructorId()).isEqualTo(instructorId);
        assertThat(w.instructorName()).isEqualTo("Jane Inst");
        assertThat(w.activeStudentsAssigned()).isEqualTo(1);
    }

    @Test
    void ownerDashboard_403ForNonOwner() {
        assertThatThrownBy(() -> service.getForCurrentOwner(Role.INSTRUCTOR, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerDashboard_404IfOwnerHasNoSchool() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.getForCurrentOwner(Role.OWNER, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ownerDashboard_403ForOwnerOfDifferentSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).schoolId(schoolId)
                .role(Role.OWNER).authProvider(AuthProvider.LOCAL).build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).build();

        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.getForCurrentOwner(Role.OWNER, ownerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerDashboard_zeroValuesWhenNoData() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).schoolId(schoolId)
                .role(Role.OWNER).authProvider(AuthProvider.LOCAL).build();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("X").active(true).timezone("America/Regina").planTier("FREE").build();

        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(planLimits.currentPlan(schoolId)).thenReturn(PlanTier.FREE);
        when(studentRepo.findBySchoolIdAndStatus(any(), any())).thenReturn(List.of());
        when(studentRepo.findBySchoolId(any())).thenReturn(List.of());
        when(sessionRepo.findForSchoolInRange(any(), any(), any())).thenReturn(List.of());
        when(paymentRepo.findOutstandingForSchool(any())).thenReturn(List.of());
        when(instructorRepo.findActiveBySchool(any())).thenReturn(List.of());

        DashboardResponse resp = service.getForCurrentOwner(Role.OWNER, ownerId);
        assertThat(resp.totalRevenuePaid()).isEqualByComparingTo("0");
        assertThat(resp.totalOutstanding()).isEqualByComparingTo("0");
        assertThat(resp.activeStudentCount()).isZero();
        assertThat(resp.upcomingSessionsCount()).isZero();
        assertThat(resp.completedSessionsInWindow()).isZero();
        assertThat(resp.monthlyRecurringRevenue()).isEqualByComparingTo("0.00");
        assertThat(resp.instructorWorkloads()).isEmpty();
    }
}
