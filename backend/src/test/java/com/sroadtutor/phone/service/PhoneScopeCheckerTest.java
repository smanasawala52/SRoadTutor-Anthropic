package com.sroadtutor.phone.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.model.PhoneOwnerType;
import com.sroadtutor.phone.repository.PhoneOwnershipLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests covering the role × ownerType matrix for {@link PhoneScopeChecker}.
 * Each cell either passes silently or throws {@link AccessDeniedException}
 * (or {@link BadRequestException} for null inputs).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhoneScopeCheckerTest {

    @Mock PhoneOwnershipLookup lookup;
    @InjectMocks PhoneScopeChecker checker;

    // ============================================================
    // Null-guard
    // ============================================================

    @Test
    void requireWriteScope_rejectsNullOwnerType() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ownerType");
    }

    @Test
    void requireWriteScope_rejectsNullOwnerId() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, UUID.randomUUID(), PhoneOwnerType.USER, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ownerId");
    }

    // ============================================================
    // OWNER role
    // ============================================================

    @Test
    void owner_canManageUserInOwnSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.userSchoolId(otherUserId)).thenReturn(Optional.of(schoolId));

        assertThatCode(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.USER, otherUserId))
                .doesNotThrowAnyException();
    }

    @Test
    void owner_cannotManageUserAtDifferentSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolA));
        when(lookup.userSchoolId(otherUserId)).thenReturn(Optional.of(schoolB));

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.USER, otherUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("different school");
    }

    @Test
    void owner_withoutSchoolIsRejected() {
        UUID ownerUserId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.USER, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no school");
    }

    @Test
    void owner_targetUserMissingThrowsResourceNotFound() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID missingUser = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.userSchoolId(missingUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.USER, missingUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void owner_canManageOwnSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));

        assertThatCode(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.SCHOOL, schoolId))
                .doesNotThrowAnyException();
    }

    @Test
    void owner_cannotManageDifferentSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID otherSchoolId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.SCHOOL, otherSchoolId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("their own school");
    }

    @Test
    void owner_canManageInstructorAtOwnSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.instructorAtSchool(instructorId, schoolId)).thenReturn(true);

        assertThatCode(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.INSTRUCTOR, instructorId))
                .doesNotThrowAnyException();
    }

    @Test
    void owner_cannotManageInstructorAtOtherSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.instructorAtSchool(instructorId, schoolId)).thenReturn(false);

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.INSTRUCTOR, instructorId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void owner_canManageStudentAtOwnSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.studentAtSchool(studentId, schoolId)).thenReturn(true);

        assertThatCode(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.STUDENT, studentId))
                .doesNotThrowAnyException();
    }

    @Test
    void owner_cannotManageStudentAtOtherSchool() {
        UUID ownerUserId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.userSchoolId(ownerUserId)).thenReturn(Optional.of(schoolId));
        when(lookup.studentAtSchool(studentId, schoolId)).thenReturn(false);

        assertThatThrownBy(() -> checker.requireWriteScope(Role.OWNER, ownerUserId, PhoneOwnerType.STUDENT, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // INSTRUCTOR role
    // ============================================================

    @Test
    void instructor_canManageOwnUserPhones() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> checker.requireWriteScope(Role.INSTRUCTOR, userId, PhoneOwnerType.USER, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void instructor_cannotManageOtherUserPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.INSTRUCTOR, UUID.randomUUID(), PhoneOwnerType.USER, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void instructor_canManageOwnInstructorRow() {
        UUID userId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        when(lookup.instructorBelongsToUser(instructorId, userId)).thenReturn(true);

        assertThatCode(() -> checker.requireWriteScope(Role.INSTRUCTOR, userId, PhoneOwnerType.INSTRUCTOR, instructorId))
                .doesNotThrowAnyException();
    }

    @Test
    void instructor_cannotManageOtherInstructorRow() {
        UUID userId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        when(lookup.instructorBelongsToUser(instructorId, userId)).thenReturn(false);

        assertThatThrownBy(() -> checker.requireWriteScope(Role.INSTRUCTOR, userId, PhoneOwnerType.INSTRUCTOR, instructorId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void instructor_cannotManageSchoolPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.INSTRUCTOR, UUID.randomUUID(), PhoneOwnerType.SCHOOL, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void instructor_cannotManageStudentPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.INSTRUCTOR, UUID.randomUUID(), PhoneOwnerType.STUDENT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // STUDENT role
    // ============================================================

    @Test
    void student_canManageOwnUserPhones() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> checker.requireWriteScope(Role.STUDENT, userId, PhoneOwnerType.USER, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void student_cannotManageOtherUserPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.STUDENT, UUID.randomUUID(), PhoneOwnerType.USER, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void student_canManageOwnStudentRow() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.studentBelongsToUser(studentId, userId)).thenReturn(true);

        assertThatCode(() -> checker.requireWriteScope(Role.STUDENT, userId, PhoneOwnerType.STUDENT, studentId))
                .doesNotThrowAnyException();
    }

    @Test
    void student_cannotManageOtherStudentRow() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.studentBelongsToUser(studentId, userId)).thenReturn(false);

        assertThatThrownBy(() -> checker.requireWriteScope(Role.STUDENT, userId, PhoneOwnerType.STUDENT, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void student_cannotManageSchoolPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.STUDENT, UUID.randomUUID(), PhoneOwnerType.SCHOOL, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void student_cannotManageInstructorPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.STUDENT, UUID.randomUUID(), PhoneOwnerType.INSTRUCTOR, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // PARENT role
    // ============================================================

    @Test
    void parent_canManageOwnUserPhones() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> checker.requireWriteScope(Role.PARENT, userId, PhoneOwnerType.USER, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void parent_cannotManageOtherUserPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.PARENT, UUID.randomUUID(), PhoneOwnerType.USER, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void parent_canManageLinkedStudent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.parentLinkedToStudent(parentId, studentId)).thenReturn(true);

        assertThatCode(() -> checker.requireWriteScope(Role.PARENT, parentId, PhoneOwnerType.STUDENT, studentId))
                .doesNotThrowAnyException();
    }

    @Test
    void parent_cannotManageUnlinkedStudent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(lookup.parentLinkedToStudent(parentId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> checker.requireWriteScope(Role.PARENT, parentId, PhoneOwnerType.STUDENT, studentId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void parent_cannotManageSchoolPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.PARENT, UUID.randomUUID(), PhoneOwnerType.SCHOOL, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void parent_cannotManageInstructorPhones() {
        assertThatThrownBy(() -> checker.requireWriteScope(Role.PARENT, UUID.randomUUID(), PhoneOwnerType.INSTRUCTOR, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // Entity-overload + ownerOf
    // ============================================================

    @Test
    void requireWriteScope_entityOverload_resolvesUserOwner() {
        UUID userId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();

        assertThatCode(() -> checker.requireWriteScope(Role.STUDENT, userId, phone))
                .doesNotThrowAnyException();
    }

    @Test
    void requireReadScope_isAliasForWriteScope() {
        UUID userId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID()) // someone else's phone
                .countryCode("1").nationalNumber("3065551234").e164("+13065551234")
                .build();

        assertThatThrownBy(() -> checker.requireReadScope(Role.STUDENT, userId, phone))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerOf_picksUserWhenSet() {
        UUID userId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder().userId(userId).build();

        PhoneScopeChecker.OwnerRef ref = checker.ownerOf(phone);
        assertThat(ref.type()).isEqualTo(PhoneOwnerType.USER);
        assertThat(ref.id()).isEqualTo(userId);
    }

    @Test
    void ownerOf_picksSchoolWhenSet() {
        UUID schoolId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder().schoolId(schoolId).build();

        PhoneScopeChecker.OwnerRef ref = checker.ownerOf(phone);
        assertThat(ref.type()).isEqualTo(PhoneOwnerType.SCHOOL);
        assertThat(ref.id()).isEqualTo(schoolId);
    }

    @Test
    void ownerOf_picksInstructorWhenSet() {
        UUID instructorId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder().instructorId(instructorId).build();

        PhoneScopeChecker.OwnerRef ref = checker.ownerOf(phone);
        assertThat(ref.type()).isEqualTo(PhoneOwnerType.INSTRUCTOR);
        assertThat(ref.id()).isEqualTo(instructorId);
    }

    @Test
    void ownerOf_picksStudentWhenSet() {
        UUID studentId = UUID.randomUUID();
        PhoneNumber phone = PhoneNumber.builder().studentId(studentId).build();

        PhoneScopeChecker.OwnerRef ref = checker.ownerOf(phone);
        assertThat(ref.type()).isEqualTo(PhoneOwnerType.STUDENT);
        assertThat(ref.id()).isEqualTo(studentId);
    }

    @Test
    void ownerOf_throwsWhenNoOwnerSet() {
        PhoneNumber phone = PhoneNumber.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> checker.ownerOf(phone))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no owner FK");
    }
}
