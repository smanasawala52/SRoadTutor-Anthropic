package com.sroadtutor.report.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.dto.SessionMistakeResponse;
import com.sroadtutor.evaluation.service.MistakeLogService;
import com.sroadtutor.payment.dto.PaymentResponse;
import com.sroadtutor.payment.dto.StudentLedgerResponse;
import com.sroadtutor.payment.service.PaymentService;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdfReportServiceTest {

    @Mock StudentRepository studentRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock UserRepository userRepo;
    @Mock MistakeLogService mistakeLogService;
    @Mock PaymentService paymentService;

    @InjectMocks PdfReportService service;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    @Test
    void buildStudentReport_returnsValidPdfBytes() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();

        Student student = Student.builder()
                .id(studentId).userId(studentUserId).schoolId(schoolId)
                .packageTotalLessons(10).lessonsRemaining(7)
                .status("ACTIVE").build();
        School school = School.builder()
                .id(schoolId).ownerId(ownerId).name("ABC Driving School")
                .timezone("America/Regina").active(true).build();
        User studentUser = User.builder()
                .id(studentUserId).email("kid@x.com").fullName("Tom Kid")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepo.findById(studentUserId)).thenReturn(Optional.of(studentUser));

        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 2, 87.5, false, List.of(
                        new ReadinessScoreResponse.PerSessionScore(UUID.randomUUID(), 90, 10, false),
                        new ReadinessScoreResponse.PerSessionScore(UUID.randomUUID(), 85, 15, false))));
        when(mistakeLogService.listForStudent(any(), any(), any())).thenReturn(List.of(
                new SessionMistakeResponse(
                        UUID.randomUUID(), UUID.randomUUID(), studentId,
                        UUID.randomUUID(), "Failure to signal", "MAJOR", 5,
                        1, "Drifted into bike lane", Instant.now())));
        when(paymentService.getStudentLedger(any(), any(), any())).thenReturn(
                new StudentLedgerResponse(
                        studentId,
                        new BigDecimal("180.00"),
                        new BigDecimal("60.00"),
                        "CAD",
                        List.of(new PaymentResponse(UUID.randomUUID(), schoolId, studentId, null,
                                new BigDecimal("60.00"), "CAD", "CASH", "PAID",
                                Instant.now(), null, Instant.now(), Instant.now()))));

        byte[] bytes = service.buildStudentReport(Role.OWNER, ownerId, studentId);

        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(500);
        // Every PDF starts with the %PDF-x.y magic header.
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            assertThat(bytes[i]).as("byte %d", i).isEqualTo(PDF_MAGIC[i]);
        }
    }

    @Test
    void buildStudentReport_handlesEmptyHistoryAndZeroPayments() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();

        Student student = Student.builder()
                .id(studentId).userId(studentUserId).schoolId(schoolId)
                .packageTotalLessons(0).lessonsRemaining(0).status("ACTIVE").build();
        School school = School.builder()
                .id(schoolId).ownerId(ownerId).name("X").active(true).timezone("America/Regina").build();
        User studentUser = User.builder()
                .id(studentUserId).email("kid@x.com").role(Role.STUDENT)
                .authProvider(AuthProvider.LOCAL).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepo.findById(studentUserId)).thenReturn(Optional.of(studentUser));
        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 0, 100.0, false, List.of()));
        when(mistakeLogService.listForStudent(any(), any(), any())).thenReturn(List.of());
        when(paymentService.getStudentLedger(any(), any(), any())).thenReturn(
                new StudentLedgerResponse(
                        studentId, BigDecimal.ZERO, BigDecimal.ZERO, "CAD", List.of()));

        byte[] bytes = service.buildStudentReport(Role.OWNER, ownerId, studentId);
        assertThat(bytes.length).isGreaterThan(200);
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            assertThat(bytes[i]).isEqualTo(PDF_MAGIC[i]);
        }
    }
}
