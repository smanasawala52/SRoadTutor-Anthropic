package com.sroadtutor.telemetry.service;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryServiceTest {

    @Mock TelemetryEventRepository telemetryRepo;
    @Mock SessionMistakeRepository mistakeRepo;
    @Mock LessonSessionRepository sessionRepo;
    @Mock SchoolRepository schoolRepo;
    @Mock InstructorRepository instructorRepo;

    @InjectMocks TelemetryService service;

    @Test
    void attach_persistsEventForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID mistakeId = UUID.randomUUID();

        SessionMistake mistake = SessionMistake.builder()
                .id(mistakeId).sessionId(sessionId).studentId(UUID.randomUUID())
                .mistakeCategoryId(UUID.randomUUID()).count(1).loggedAt(Instant.now()).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).studentId(UUID.randomUUID())
                .instructorId(UUID.randomUUID()).status("SCHEDULED")
                .scheduledAt(Instant.now()).durationMins(60).build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(mistakeRepo.findById(mistakeId)).thenReturn(Optional.of(mistake));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(telemetryRepo.save(any(TelemetryEvent.class))).thenAnswer(inv -> {
            TelemetryEvent t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        AttachTelemetryRequest req = new AttachTelemetryRequest(
                "Tesla", "Model 3", 2024,
                Map.of("steeringAngle", 12.5, "brakePressure", 0.6),
                -250L);

        TelemetryEventResponse resp = service.attach(Role.OWNER, ownerId, mistakeId, req);
        assertThat(resp.sessionMistakeId()).isEqualTo(mistakeId);
        assertThat(resp.vehicleMake()).isEqualTo("Tesla");
        assertThat(resp.vehicleModel()).isEqualTo("Model 3");
        assertThat(resp.vehicleYear()).isEqualTo(2024);
        assertThat(resp.offsetMs()).isEqualTo(-250L);
        assertThat(resp.telemetryJson()).contains("steeringAngle");
    }

    @Test
    void attach_rejectsEmptyTelemetry() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID mistakeId = UUID.randomUUID();

        SessionMistake mistake = SessionMistake.builder()
                .id(mistakeId).sessionId(sessionId).studentId(UUID.randomUUID())
                .mistakeCategoryId(UUID.randomUUID()).count(1).loggedAt(Instant.now()).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(mistakeRepo.findById(mistakeId)).thenReturn(Optional.of(mistake));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        AttachTelemetryRequest req = new AttachTelemetryRequest(
                null, null, null, Map.of(), null);

        assertThatThrownBy(() -> service.attach(Role.OWNER, ownerId, mistakeId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("EMPTY_TELEMETRY"));
    }

    @Test
    void attach_403ForUnrelatedRole() {
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID mistakeId = UUID.randomUUID();
        SessionMistake mistake = SessionMistake.builder()
                .id(mistakeId).sessionId(sessionId).studentId(UUID.randomUUID()).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).build();

        when(mistakeRepo.findById(mistakeId)).thenReturn(Optional.of(mistake));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        AttachTelemetryRequest req = new AttachTelemetryRequest(
                null, null, null, Map.of("k", "v"), null);

        assertThatThrownBy(() -> service.attach(Role.STUDENT, UUID.randomUUID(), mistakeId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void attach_404IfMistakeMissing() {
        when(mistakeRepo.findById(any())).thenReturn(Optional.empty());
        AttachTelemetryRequest req = new AttachTelemetryRequest(
                null, null, null, Map.of("k", "v"), null);
        assertThatThrownBy(() -> service.attach(Role.OWNER, UUID.randomUUID(), UUID.randomUUID(), req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void datasetSummary_403ForNonOwner() {
        assertThatThrownBy(() -> service.datasetSummary(Role.INSTRUCTOR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void datasetSummary_returnsCount() {
        when(telemetryRepo.count()).thenReturn(42L);
        TelemetryDatasetSummary summary = service.datasetSummary(Role.OWNER);
        assertThat(summary.totalEvents()).isEqualTo(42L);
        assertThat(summary.generatedAt()).isNotNull();
    }

    @Test
    void listForMistake_returnsRows() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID mistakeId = UUID.randomUUID();
        SessionMistake mistake = SessionMistake.builder()
                .id(mistakeId).sessionId(sessionId).studentId(UUID.randomUUID()).build();
        LessonSession session = LessonSession.builder()
                .id(sessionId).schoolId(schoolId).status("SCHEDULED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();
        TelemetryEvent ev = TelemetryEvent.builder()
                .id(UUID.randomUUID()).sessionMistakeId(mistakeId)
                .telemetryJson("{\"k\":1}").syncedAt(Instant.now()).build();

        when(mistakeRepo.findById(mistakeId)).thenReturn(Optional.of(mistake));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(telemetryRepo.findBySessionMistakeIdOrderBySyncedAtAsc(mistakeId)).thenReturn(List.of(ev));

        List<TelemetryEventResponse> list = service.listForMistake(Role.OWNER, ownerId, mistakeId);
        assertThat(list).hasSize(1);
    }
}
