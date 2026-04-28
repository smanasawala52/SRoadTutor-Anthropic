package com.sroadtutor.risk.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.service.MistakeLogService;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.risk.dto.RiskAggregateResponse;
import com.sroadtutor.risk.dto.RiskScoreResponse;
import com.sroadtutor.risk.model.RiskScore;
import com.sroadtutor.risk.repository.RiskScoreRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskScoreServiceTest {

    @Mock RiskScoreRepository riskRepo;
    @Mock StudentRepository studentRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock MistakeLogService mistakeLogService;

    /** Salt has to be ≥ 32 bytes — matching JwtService validation. */
    private static final String SALT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private RiskScoreService newService() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(SALT, 15, 30, "iss"),
                new AppProperties.OAuth(new AppProperties.OAuth.Google("cid", "csec")),
                new AppProperties.Cors(List.of("*"), "GET", "*", true, 3600),
                new AppProperties.Stripe(null, null, null, null,
                        new AppProperties.Stripe.Prices(null, null, null)));
        return new RiskScoreService(riskRepo, studentRepo, schoolRepo, mistakeLogService, props);
    }

    @Test
    void generate_classifiesLow() {
        RiskScoreService service = newService();
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .jurisdiction("SGI").province("SK").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 5, 95.0, false, List.of()));
        when(riskRepo.findByStudentAnonymizedHash(any())).thenReturn(Optional.empty());
        when(riskRepo.save(any(RiskScore.class))).thenAnswer(inv -> {
            RiskScore r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        RiskScoreResponse resp = service.generateForStudent(Role.OWNER, ownerId, studentId);
        assertThat(resp.riskTier()).isEqualTo("LOW");
        assertThat(resp.studentAnonymizedHash()).hasSize(64); // SHA-256 hex
        assertThat(resp.mistakeProfileJson()).contains("\"province\":\"SK\"");
    }

    @Test
    void generate_critUpgradesOnAnyFailRegardlessOfAverage() {
        RiskScoreService service = newService();
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .jurisdiction("SGI").province("SK").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        // Average 95 is great, but anyFailRecently=true → CRITICAL
        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 5, 95.0, true, List.of()));
        when(riskRepo.findByStudentAnonymizedHash(any())).thenReturn(Optional.empty());
        when(riskRepo.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskScoreResponse resp = service.generateForStudent(Role.OWNER, ownerId, studentId);
        assertThat(resp.riskTier()).isEqualTo("CRITICAL");
    }

    @Test
    void generate_classifiesHigh() {
        RiskScoreService service = newService();
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("ACTIVE").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 5, 55.0, false, List.of()));
        when(riskRepo.findByStudentAnonymizedHash(any())).thenReturn(Optional.empty());
        when(riskRepo.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskScoreResponse resp = service.generateForStudent(Role.OWNER, ownerId, studentId);
        assertThat(resp.riskTier()).isEqualTo("HIGH");
    }

    @Test
    void generate_overwritesExistingRow() {
        RiskScoreService service = newService();
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();
        UUID existingId = UUID.randomUUID();
        RiskScore existing = RiskScore.builder()
                .id(existingId).studentAnonymizedHash("HASH").riskTier("HIGH")
                .mistakeProfileJson("{}").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(mistakeLogService.readinessForStudent(any(), any(), any())).thenReturn(
                new ReadinessScoreResponse(studentId, 5, 95.0, false, List.of()));
        when(riskRepo.findByStudentAnonymizedHash(any())).thenReturn(Optional.of(existing));
        when(riskRepo.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskScoreResponse resp = service.generateForStudent(Role.OWNER, ownerId, studentId);
        assertThat(resp.id()).isEqualTo(existingId);  // updated row, not new
        assertThat(resp.riskTier()).isEqualTo("LOW");
    }

    @Test
    void generate_403ForUnrelatedOwner() {
        RiskScoreService service = newService();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).build();
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.generateForStudent(Role.OWNER, UUID.randomUUID(), studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByHash_404IfMissing() {
        RiskScoreService service = newService();
        when(riskRepo.findByStudentAnonymizedHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByHash(Role.OWNER, "unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByHash_403ForNonOwner() {
        RiskScoreService service = newService();
        assertThatThrownBy(() -> service.getByHash(Role.INSTRUCTOR, "abc"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aggregate_sumsCounts() {
        RiskScoreService service = newService();
        when(riskRepo.countsByTier()).thenReturn(List.of(
                new Object[]{"LOW", 10L},
                new Object[]{"HIGH", 5L}));
        RiskAggregateResponse resp = service.aggregate(Role.OWNER);
        assertThat(resp.totalDrivers()).isEqualTo(15);
        assertThat(resp.countsByTier()).containsEntry("LOW", 10L).containsEntry("HIGH", 5L);
    }

    @Test
    void anonymize_isDeterministicAndOpaque() {
        RiskScoreService service = newService();
        UUID id = UUID.randomUUID();
        String h1 = service.anonymize(id);
        String h2 = service.anonymize(id);
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
        // Hash never echoes the raw UUID string.
        assertThat(h1).doesNotContain(id.toString());
    }
}
