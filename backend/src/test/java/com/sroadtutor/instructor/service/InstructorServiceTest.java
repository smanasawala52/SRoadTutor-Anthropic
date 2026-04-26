package com.sroadtutor.instructor.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.instructor.dto.InstructorCreateRequest;
import com.sroadtutor.instructor.dto.InstructorUpdateRequest;
import com.sroadtutor.instructor.model.Instructor;
import com.sroadtutor.instructor.model.InstructorSchool;
import com.sroadtutor.instructor.model.InstructorSchoolId;
import com.sroadtutor.instructor.model.WorkingHours;
import com.sroadtutor.instructor.repository.InstructorRepository;
import com.sroadtutor.instructor.repository.InstructorSchoolRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructorServiceTest {

    @Mock InstructorRepository       instructorRepo;
    @Mock InstructorSchoolRepository linkRepo;
    @Mock SchoolRepository           schoolRepo;
    @Mock UserRepository             userRepo;

    @InjectMocks InstructorService service;

    // ---------------- create ----------------

    @Test
    void create_succeedsForInstructorRole() {
        UUID userId = UUID.randomUUID();
        WorkingHours hours = new WorkingHours(Map.of(
                DayOfWeek.MONDAY, List.of(new WorkingHours.TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))));
        var req = new InstructorCreateRequest(
                "L123", "SGI-CERT", "Toyota", "Corolla", 2022, "ABC123",
                "Hi I'm a great instructor",
                new BigDecimal("65.00"), hours);

        when(instructorRepo.existsByUserId(userId)).thenReturn(false);
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });

        Instructor result = service.createForCurrentUser(Role.INSTRUCTOR, userId, req);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getLicenseNo()).isEqualTo("L123");
        assertThat(result.getHourlyRate()).isEqualByComparingTo(new BigDecimal("65.00"));
        assertThat(result.isActive()).isTrue();
        assertThat(result.getWorkingHoursJson()).isNotNull();

        WorkingHours parsed = WorkingHours.fromJson(result.getWorkingHoursJson());
        assertThat(parsed.schedule()).containsKey(DayOfWeek.MONDAY);
    }

    @Test
    void create_rejectsNonInstructorRole() {
        UUID userId = UUID.randomUUID();
        var req = new InstructorCreateRequest(null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentUser(Role.OWNER, userId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("NOT_INSTRUCTOR"));

        verify(instructorRepo, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateProfile() {
        UUID userId = UUID.randomUUID();
        when(instructorRepo.existsByUserId(userId)).thenReturn(true);

        var req = new InstructorCreateRequest(null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentUser(Role.INSTRUCTOR, userId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_ALREADY_EXISTS"));
    }

    @Test
    void create_normalisesBlankFieldsToNull() {
        UUID userId = UUID.randomUUID();
        when(instructorRepo.existsByUserId(userId)).thenReturn(false);
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new InstructorCreateRequest(
                "  ", "", null, "Civic", null, " ", "  bio  ", null, null);

        Instructor result = service.createForCurrentUser(Role.INSTRUCTOR, userId, req);
        assertThat(result.getLicenseNo()).isNull();
        assertThat(result.getSgiCert()).isNull();
        assertThat(result.getVehicleMake()).isNull();
        assertThat(result.getVehicleModel()).isEqualTo("Civic");
        assertThat(result.getVehiclePlate()).isNull();
        assertThat(result.getBio()).isEqualTo("bio");
    }

    // ---------------- update ----------------

    @Test
    void update_appliesPartialFieldsBySelf() {
        UUID userId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        Instructor i = Instructor.builder()
                .id(instructorId).userId(userId).licenseNo("OLD").active(true).build();
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new InstructorUpdateRequest("NEW-LIC", null, null, null, null, null, null,
                new BigDecimal("75.50"), null);

        Instructor updated = service.update(Role.INSTRUCTOR, userId, instructorId, req);
        assertThat(updated.getLicenseNo()).isEqualTo("NEW-LIC");
        assertThat(updated.getHourlyRate()).isEqualByComparingTo(new BigDecimal("75.50"));
    }

    @Test
    void update_403WhenNotSelfAndNotOwnerOfAttachedSchool() {
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        Instructor i = Instructor.builder().id(instructorId).userId(otherUser).active(true).build();
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(linkRepo.findByIdInstructorIdAndLeftAtIsNull(instructorId)).thenReturn(List.of());

        var req = new InstructorUpdateRequest("X", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(Role.OWNER, userId, instructorId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_succeedsForOwnerOfAttachedSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        Instructor i = Instructor.builder().id(instructorId).userId(otherUser).active(true).build();
        InstructorSchool link = InstructorSchool.builder()
                .id(new InstructorSchoolId(instructorId, schoolId))
                .joinedAt(Instant.now())
                .build();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();

        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(linkRepo.findByIdInstructorIdAndLeftAtIsNull(instructorId)).thenReturn(List.of(link));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new InstructorUpdateRequest(null, null, null, null, null, null, "Updated bio",
                null, null);

        Instructor updated = service.update(Role.OWNER, ownerId, instructorId, req);
        assertThat(updated.getBio()).isEqualTo("Updated bio");
    }

    @Test
    void update_rejectsInactiveProfile() {
        UUID userId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        Instructor i = Instructor.builder().id(instructorId).userId(userId).active(false).build();
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));

        var req = new InstructorUpdateRequest("X", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(Role.INSTRUCTOR, userId, instructorId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSTRUCTOR_INACTIVE"));
    }

    // ---------------- attach / detach ----------------

    @Test
    void attach_createsLinkAndBackLinksUser() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Instructor i = Instructor.builder().id(instructorId).userId(instructorUserId).active(true).build();
        User instructorUser = User.builder()
                .id(instructorUserId).role(Role.INSTRUCTOR)
                .authProvider(AuthProvider.LOCAL).active(true).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(linkRepo.findById(any(InstructorSchoolId.class))).thenReturn(Optional.empty());
        when(linkRepo.save(any(InstructorSchool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById(instructorUserId)).thenReturn(Optional.of(instructorUser));
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        InstructorSchool link = service.attachToSchool(Role.OWNER, ownerId, schoolId, instructorId, "HEAD");

        assertThat(link.getId().getInstructorId()).isEqualTo(instructorId);
        assertThat(link.getId().getSchoolId()).isEqualTo(schoolId);
        assertThat(link.getRoleAtSchool()).isEqualTo("HEAD");
        assertThat(link.getLeftAt()).isNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().getSchoolId()).isEqualTo(schoolId);
    }

    @Test
    void attach_rejectsAlreadyActiveLink() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Instructor i = Instructor.builder().id(instructorId).userId(UUID.randomUUID()).active(true).build();
        InstructorSchool existing = InstructorSchool.builder()
                .id(new InstructorSchoolId(instructorId, schoolId))
                .joinedAt(Instant.now()).build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(linkRepo.findById(any(InstructorSchoolId.class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.attachToSchool(Role.OWNER, ownerId, schoolId, instructorId, null))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("ALREADY_ATTACHED"));
    }

    @Test
    void attach_rehydratesOldLeftLink() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        Instructor i = Instructor.builder().id(instructorId).userId(instructorUserId).active(true).build();
        InstructorSchool old = InstructorSchool.builder()
                .id(new InstructorSchoolId(instructorId, schoolId))
                .joinedAt(Instant.now().minusSeconds(86400))
                .leftAt(Instant.now().minusSeconds(3600))
                .roleAtSchool("REGULAR")
                .build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(instructorRepo.findById(instructorId)).thenReturn(Optional.of(i));
        when(linkRepo.findById(any(InstructorSchoolId.class))).thenReturn(Optional.of(old));
        when(linkRepo.save(any(InstructorSchool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById(instructorUserId)).thenReturn(Optional.empty());
        when(instructorRepo.save(any(Instructor.class))).thenAnswer(inv -> inv.getArgument(0));

        InstructorSchool link = service.attachToSchool(Role.OWNER, ownerId, schoolId, instructorId, "HEAD");
        assertThat(link.getLeftAt()).isNull();
        assertThat(link.getRoleAtSchool()).isEqualTo("HEAD");
    }

    @Test
    void attach_403ForNonOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(UUID.randomUUID()).active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.attachToSchool(
                Role.INSTRUCTOR, UUID.randomUUID(), schoolId, instructorId, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void attach_rejectsInactiveSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(false).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.attachToSchool(Role.OWNER, ownerId, schoolId, instructorId, null))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_INACTIVE"));
    }

    @Test
    void detach_setsLeftAtAndIsIdempotent() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        School s = School.builder().id(schoolId).ownerId(ownerId).active(true).build();
        InstructorSchool link = InstructorSchool.builder()
                .id(new InstructorSchoolId(instructorId, schoolId))
                .joinedAt(Instant.now())
                .build();

        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(s));
        when(linkRepo.findById(any(InstructorSchoolId.class))).thenReturn(Optional.of(link));
        when(linkRepo.save(any(InstructorSchool.class))).thenAnswer(inv -> inv.getArgument(0));

        InstructorSchool after = service.detachFromSchool(Role.OWNER, ownerId, schoolId, instructorId);
        assertThat(after.getLeftAt()).isNotNull();

        // second call: leftAt already set → no save
        when(linkRepo.findById(any(InstructorSchoolId.class))).thenReturn(Optional.of(after));
        service.detachFromSchool(Role.OWNER, ownerId, schoolId, instructorId);
        verify(linkRepo, times(1)).save(any(InstructorSchool.class));
    }

    // ---------------- getMine ----------------

    @Test
    void getMine_returnsCallerProfile() {
        UUID userId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        Instructor i = Instructor.builder().id(instructorId).userId(userId).build();
        when(instructorRepo.findByUserId(userId)).thenReturn(Optional.of(i));

        assertThat(service.getMine(userId).getId()).isEqualTo(instructorId);
    }

    @Test
    void getMine_404IfMissing() {
        UUID userId = UUID.randomUUID();
        when(instructorRepo.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMine(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
