package com.sroadtutor.payment.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.payment.dto.MarkPaidRequest;
import com.sroadtutor.payment.dto.PaymentResponse;
import com.sroadtutor.payment.dto.RecordPaymentRequest;
import com.sroadtutor.payment.dto.StudentLedgerResponse;
import com.sroadtutor.payment.model.Payment;
import com.sroadtutor.payment.repository.PaymentRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
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

import java.math.BigDecimal;
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
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock InstructorRepository instructorRepo;
    @Mock StudentRepository studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;
    @Mock LessonSessionRepository sessionRepo;

    @InjectMocks PaymentService service;

    // ============================================================
    // createForCompletedSession
    // ============================================================

    @Test
    void createForCompletedSession_calculatesAmountFromHourlyRate() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instructor instructor = Instructor.builder().id(instructorId)
                .userId(UUID.randomUUID()).schoolId(schoolId)
                .hourlyRate(new BigDecimal("60.00")).active(true).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).instructorId(instructorId)
                .studentId(studentId).durationMins(90)
                .scheduledAt(Instant.now()).status("COMPLETED").build();

        when(paymentRepo.findFirstBySessionId(sessionId)).thenReturn(Optional.empty());
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        Payment payment = service.createForCompletedSession(session);
        assertThat(payment.getAmount()).isEqualByComparingTo("90.00");  // 60 * 1.5h = 90
        assertThat(payment.getStatus()).isEqualTo("UNPAID");
        assertThat(payment.getMethod()).isNull();
        assertThat(payment.getSessionId()).isEqualTo(sessionId);
        assertThat(payment.getStudentId()).isEqualTo(studentId);
    }

    @Test
    void createForCompletedSession_zeroAmountWhenHourlyRateMissing() {
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instructor instructor = Instructor.builder().id(instructorId)
                .userId(UUID.randomUUID()).schoolId(schoolId).hourlyRate(null).active(true).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).instructorId(instructorId)
                .studentId(UUID.randomUUID()).durationMins(60)
                .scheduledAt(Instant.now()).status("COMPLETED").build();

        when(paymentRepo.findFirstBySessionId(sessionId)).thenReturn(Optional.empty());
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = service.createForCompletedSession(session);
        assertThat(payment.getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void createForCompletedSession_idempotentWhenRowExists() {
        UUID sessionId = UUID.randomUUID();
        Payment existing = Payment.builder().id(UUID.randomUUID()).sessionId(sessionId).build();
        when(paymentRepo.findFirstBySessionId(sessionId)).thenReturn(Optional.of(existing));

        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(UUID.randomUUID()).instructorId(UUID.randomUUID())
                .studentId(UUID.randomUUID()).durationMins(60)
                .scheduledAt(Instant.now()).status("COMPLETED").build();

        Payment p = service.createForCompletedSession(session);
        assertThat(p).isSameAs(existing);
        verify(paymentRepo, never()).save(any());
    }

    // ============================================================
    // record (manual)
    // ============================================================

    @Test
    void record_succeedsForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        var req = new RecordPaymentRequest(
                studentId, null, new BigDecimal("300.00"), "CAD", "ETRANSFER",
                Instant.now(), "10-lesson package");

        Payment p = service.record(Role.OWNER, ownerId, req);
        assertThat(p.getStatus()).isEqualTo("PAID");
        assertThat(p.getMethod()).isEqualTo("ETRANSFER");
        assertThat(p.getAmount()).isEqualByComparingTo("300.00");
        assertThat(p.getCurrency()).isEqualTo("CAD");
    }

    @Test
    void record_unpaidWhenPaidAtIsNull() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new RecordPaymentRequest(
                studentId, null, new BigDecimal("100"), null, "CASH", null, null);

        Payment p = service.record(Role.OWNER, ownerId, req);
        assertThat(p.getStatus()).isEqualTo("UNPAID");
        assertThat(p.getPaidAt()).isNull();
    }

    @Test
    void record_rejectsCrossStudentSession() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        LessonSession otherSession = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(UUID.randomUUID())  // different student
                .instructorId(UUID.randomUUID()).durationMins(60)
                .scheduledAt(Instant.now()).status("SCHEDULED").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(otherSession));

        var req = new RecordPaymentRequest(
                studentId, sessionId, new BigDecimal("100"), null, "CASH", null, null);

        assertThatThrownBy(() -> service.record(Role.OWNER, ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SESSION_WRONG_STUDENT"));
    }

    @Test
    void record_rejectsNegativeAmount() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        var req = new RecordPaymentRequest(
                studentId, null, new BigDecimal("-1.00"), null, "CASH", null, null);

        assertThatThrownBy(() -> service.record(Role.OWNER, ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVALID_AMOUNT"));
    }

    @Test
    void record_403ForUnrelatedOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        var req = new RecordPaymentRequest(
                studentId, null, new BigDecimal("100"), null, "CASH", null, null);

        assertThatThrownBy(() -> service.record(Role.OWNER, UUID.randomUUID(), req))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // markPaid
    // ============================================================

    @Test
    void markPaid_flipsUnpaidToPaid() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment p = Payment.builder().id(paymentId).schoolId(schoolId).studentId(studentId)
                .amount(new BigDecimal("60")).currency("CAD").status("UNPAID").build();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(p));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new MarkPaidRequest("ETRANSFER", null, "Received via Interac");

        Payment after = service.markPaid(Role.OWNER, ownerId, paymentId, req);
        assertThat(after.getStatus()).isEqualTo("PAID");
        assertThat(after.getMethod()).isEqualTo("ETRANSFER");
        assertThat(after.getPaidAt()).isNotNull();
    }

    @Test
    void markPaid_idempotentOnAlreadyPaid() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment p = Payment.builder().id(paymentId).schoolId(schoolId).studentId(studentId)
                .amount(new BigDecimal("60")).currency("CAD").status("PAID")
                .method("CASH").paidAt(Instant.now()).build();

        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(p));

        var req = new MarkPaidRequest("ETRANSFER", null, null);
        Payment after = service.markPaid(Role.OWNER, ownerId, paymentId, req);
        assertThat(after.getMethod()).isEqualTo("CASH");  // unchanged
        verify(paymentRepo, never()).save(any());
    }

    // ============================================================
    // Reads + scope
    // ============================================================

    @Test
    void ledger_returnsTotalsAndPayments() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Payment p1 = Payment.builder().id(UUID.randomUUID()).schoolId(schoolId).studentId(studentId)
                .amount(new BigDecimal("60")).currency("CAD").status("PAID").method("CASH").build();
        Payment p2 = Payment.builder().id(UUID.randomUUID()).schoolId(schoolId).studentId(studentId)
                .amount(new BigDecimal("60")).currency("CAD").status("UNPAID").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(paymentRepo.findByStudentId(studentId)).thenReturn(List.of(p1, p2));
        when(paymentRepo.sumPaidForStudent(studentId)).thenReturn(new BigDecimal("60"));
        when(paymentRepo.sumOutstandingForStudent(studentId)).thenReturn(new BigDecimal("60"));

        StudentLedgerResponse ledger = service.getStudentLedger(Role.OWNER, ownerId, studentId);
        assertThat(ledger.totalPaid()).isEqualByComparingTo("60");
        assertThat(ledger.totalOutstanding()).isEqualByComparingTo("60");
        assertThat(ledger.payments()).hasSize(2);
    }

    @Test
    void ledger_succeedsForLinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(true);
        when(paymentRepo.findByStudentId(studentId)).thenReturn(List.of());
        when(paymentRepo.sumPaidForStudent(studentId)).thenReturn(BigDecimal.ZERO);
        when(paymentRepo.sumOutstandingForStudent(studentId)).thenReturn(BigDecimal.ZERO);

        StudentLedgerResponse ledger = service.getStudentLedger(Role.PARENT, parentId, studentId);
        assertThat(ledger.studentId()).isEqualTo(studentId);
    }

    @Test
    void ledger_403ForUnlinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(schoolId).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.getStudentLedger(Role.PARENT, parentId, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void outstanding_403ForNonOwner() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.getOutstandingForSchool(
                Role.INSTRUCTOR, UUID.randomUUID(), schoolId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
