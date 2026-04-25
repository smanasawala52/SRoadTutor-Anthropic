package com.sroadtutor.school.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.school.dto.SchoolCreateRequest;
import com.sroadtutor.school.dto.SchoolUpdateRequest;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link SchoolService}. Verifies the one-school-per-owner
 * rule, the user.school_id back-link, owner-only edit/deactivate scope, the
 * inactive-school edit lock, and reactivate idempotency.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolServiceTest {

    @Mock SchoolRepository schoolRepo;
    @Mock UserRepository   userRepo;

    @InjectMocks SchoolService service;

    // ---------------- create ----------------

    @Test
    void create_succeedsForOwnerWithoutSchool() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder()
                .id(ownerId).email("o@x.com").username("o_aaaaaaaa")
                .role(Role.OWNER).authProvider(AuthProvider.LOCAL).active(true).build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.existsByOwnerId(ownerId)).thenReturn(false);
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        var req = new SchoolCreateRequest("ABC Driving", "SGI", "SK",
                null, null, null, null);

        School result = service.createForCurrentOwner(Role.OWNER, ownerId, req);

        assertThat(result.getName()).isEqualTo("ABC Driving");
        assertThat(result.getOwnerId()).isEqualTo(ownerId);
        assertThat(result.getJurisdiction()).isEqualTo("SGI");
        assertThat(result.getProvince()).isEqualTo("SK");
        assertThat(result.getPlanTier()).isEqualTo("FREE");
        assertThat(result.isActive()).isTrue();
        assertThat(result.isSynthetic()).isFalse();

        // back-link asserted explicitly
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().getSchoolId()).isEqualTo(result.getId());
    }

    @Test
    void create_trimsName() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).active(true).build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.existsByOwnerId(ownerId)).thenReturn(false);
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        var req = new SchoolCreateRequest("  Padded School  ", null, null,
                null, null, null, null);

        School result = service.createForCurrentOwner(Role.OWNER, ownerId, req);
        assertThat(result.getName()).isEqualTo("Padded School");
        assertThat(result.getJurisdiction()).isEqualTo("SGI"); // default
    }

    @Test
    void create_normalisesBlankTaxIdsToNull() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).active(true).build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.existsByOwnerId(ownerId)).thenReturn(false);
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        var req = new SchoolCreateRequest("X School", "SGI", "SK",
                "   ", "", "  GST123  ", null);

        School result = service.createForCurrentOwner(Role.OWNER, ownerId, req);
        assertThat(result.getGstNumber()).isNull();
        assertThat(result.getPstNumber()).isNull();
        assertThat(result.getHstNumber()).isEqualTo("GST123");
        assertThat(result.getBusinessRegistrationNumber()).isNull();
    }

    @Test
    void create_rejectsNonOwnerRole() {
        UUID userId = UUID.randomUUID();
        var req = new SchoolCreateRequest("X", "SGI", "SK", null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentOwner(Role.INSTRUCTOR, userId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("NOT_OWNER"));

        verify(schoolRepo, never()).save(any());
    }

    @Test
    void create_rejectsOwnerWhoAlreadyHasSchool() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).active(true).build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.existsByOwnerId(ownerId)).thenReturn(true);

        var req = new SchoolCreateRequest("Second School", "SGI", "SK",
                null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentOwner(Role.OWNER, ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("ALREADY_OWN_SCHOOL"));

        verify(schoolRepo, never()).save(any());
    }

    @Test
    void create_rejectsOwnerWhoIsAlreadyInAnotherSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID otherSchool = UUID.randomUUID();
        User owner = User.builder()
                .id(ownerId).role(Role.OWNER)
                .authProvider(AuthProvider.LOCAL).active(true)
                .schoolId(otherSchool)
                .build();
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        when(schoolRepo.existsByOwnerId(ownerId)).thenReturn(false);

        var req = new SchoolCreateRequest("X", "SGI", "SK", null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentOwner(Role.OWNER, ownerId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("USER_ALREADY_IN_SCHOOL"));
    }

    @Test
    void create_throwsWhenCallerUserNotFound() {
        UUID ownerId = UUID.randomUUID();
        when(userRepo.findById(ownerId)).thenReturn(Optional.empty());

        var req = new SchoolCreateRequest("X", "SGI", "SK", null, null, null, null);

        assertThatThrownBy(() -> service.createForCurrentOwner(Role.OWNER, ownerId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------- getMine ----------------

    @Test
    void getMine_returnsCallerSchool() {
        UUID userId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        User caller = User.builder().id(userId).schoolId(schoolId)
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).build();
        School school = School.builder().id(schoolId).name("X").ownerId(UUID.randomUUID()).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(caller));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThat(service.getMine(userId).getId()).isEqualTo(schoolId);
    }

    @Test
    void getMine_404IfUserHasNoSchool() {
        UUID userId = UUID.randomUUID();
        User caller = User.builder().id(userId).role(Role.STUDENT)
                .authProvider(AuthProvider.LOCAL).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.getMine(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------- getById ----------------

    @Test
    void getById_succeedsForOwnerOfThisSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId).name("X").build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThat(service.getById(Role.OWNER, ownerId, schoolId)).isSameAs(school);
    }

    @Test
    void getById_403ForOwnerOfDifferentSchool() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).name("X").build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.getById(Role.OWNER, UUID.randomUUID(), schoolId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_403ForNonOwnerRole() {
        UUID userId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(userId).name("X").build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.getById(Role.INSTRUCTOR, userId, schoolId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------- update ----------------

    @Test
    void update_appliesPartialFields() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("Old").jurisdiction("SGI").province("SK").active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new SchoolUpdateRequest("New Name", null, "AB",
                "GST-1", null, null, null);

        School updated = service.update(Role.OWNER, ownerId, schoolId, req);
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getJurisdiction()).isEqualTo("SGI"); // unchanged
        assertThat(updated.getProvince()).isEqualTo("AB");
        assertThat(updated.getGstNumber()).isEqualTo("GST-1");
    }

    @Test
    void update_blankTaxIdClearsValue() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("X").gstNumber("OLD-GST").active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new SchoolUpdateRequest(null, null, null,
                "   ", null, null, null);

        School updated = service.update(Role.OWNER, ownerId, schoolId, req);
        assertThat(updated.getGstNumber()).isNull();
    }

    @Test
    void update_rejectsInactiveSchool() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("X").active(false).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        var req = new SchoolUpdateRequest("New", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(Role.OWNER, ownerId, schoolId, req))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("SCHOOL_INACTIVE"));
    }

    @Test
    void update_403ForOwnerOfDifferentSchool() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID())
                .name("X").active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        var req = new SchoolUpdateRequest("New", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(Role.OWNER, UUID.randomUUID(), schoolId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------- deactivate / reactivate ----------------

    @Test
    void deactivate_flipsActiveAndIsIdempotent() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("X").active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        // first call deactivates
        School after = service.deactivate(Role.OWNER, ownerId, schoolId);
        assertThat(after.isActive()).isFalse();

        // second call is a no-op (verify save was only called once)
        service.deactivate(Role.OWNER, ownerId, schoolId);
        verify(schoolRepo, org.mockito.Mockito.times(1)).save(any(School.class));
    }

    @Test
    void reactivate_flipsActiveAndIsIdempotent() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(ownerId)
                .name("X").active(false).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepo.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        School after = service.reactivate(Role.OWNER, ownerId, schoolId);
        assertThat(after.isActive()).isTrue();

        service.reactivate(Role.OWNER, ownerId, schoolId);
        verify(schoolRepo, org.mockito.Mockito.times(1)).save(any(School.class));
    }

    @Test
    void deactivate_403ForOwnerOfDifferentSchool() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID())
                .name("X").active(true).build();
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.deactivate(Role.OWNER, UUID.randomUUID(), schoolId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
