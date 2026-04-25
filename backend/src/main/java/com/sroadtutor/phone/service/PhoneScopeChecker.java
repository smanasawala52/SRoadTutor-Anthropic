package com.sroadtutor.phone.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.model.PhoneOwnerType;
import com.sroadtutor.phone.repository.PhoneOwnershipLookup;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * "Standard scope" RBAC matrix for the phone-numbers resource (locked at PR4
 * kickoff):
 *
 * <pre>
 *               USER         SCHOOL          INSTRUCTOR              STUDENT
 *   OWNER       in school    own school      in own school           in own school
 *   INSTRUCTOR  self only    —               self (instructors.user_id) —
 *   STUDENT     self only    —               —                       self (students.user_id)
 *   PARENT      self only    —               —                       linked-student via parent_student
 * </pre>
 *
 * <p>Read scope and write scope are identical for V1 — anyone who can see a
 * phone can edit it. (The PRD never asked for an audit-only viewer role.) If
 * that splits later, this is the layer to refine.</p>
 *
 * <p>Throws {@link AccessDeniedException} (403 via GlobalExceptionHandler) for
 * scope violations and {@link BadRequestException} for invalid combinations
 * (e.g. INSTRUCTOR trying to write a SCHOOL phone).</p>
 */
@Service
public class PhoneScopeChecker {

    private final PhoneOwnershipLookup lookup;

    public PhoneScopeChecker(PhoneOwnershipLookup lookup) {
        this.lookup = lookup;
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Verify the caller is allowed to create or read a phone for the given
     * {@code (ownerType, ownerId)} pair. Throws on violation.
     */
    public void requireWriteScope(Role role, UUID currentUserId,
                                  PhoneOwnerType ownerType, UUID ownerId) {
        if (ownerType == null || ownerId == null) {
            throw new BadRequestException(
                    "INVALID_OWNER",
                    "ownerType and ownerId are both required");
        }

        switch (role) {
            case OWNER       -> requireOwnerScope(currentUserId, ownerType, ownerId);
            case INSTRUCTOR  -> requireInstructorScope(currentUserId, ownerType, ownerId);
            case STUDENT     -> requireStudentScope(currentUserId, ownerType, ownerId);
            case PARENT      -> requireParentScope(currentUserId, ownerType, ownerId);
        }
    }

    /** Scope check for an existing row — derives owner from the entity itself. */
    public void requireWriteScope(Role role, UUID currentUserId, PhoneNumber phone) {
        OwnerRef ref = ownerOf(phone);
        requireWriteScope(role, currentUserId, ref.type(), ref.id());
    }

    /**
     * Read scope is currently identical to write scope, but exposing both
     * names lets future split-permission work stay surgical.
     */
    public void requireReadScope(Role role, UUID currentUserId, PhoneNumber phone) {
        requireWriteScope(role, currentUserId, phone);
    }

    // ============================================================
    // Per-role logic
    // ============================================================

    /** OWNER: any phone whose owner resolves to a row inside the OWNER's school. */
    private void requireOwnerScope(UUID ownerUserId, PhoneOwnerType type, UUID ownerId) {
        UUID schoolId = lookup.userSchoolId(ownerUserId)
                .orElseThrow(() -> new AccessDeniedException(
                        "OWNER has no school assigned — cannot manage phones"));

        switch (type) {
            case USER -> {
                UUID otherSchool = lookup.userSchoolId(ownerId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found: " + ownerId));
                if (!schoolId.equals(otherSchool)) {
                    throw new AccessDeniedException("User belongs to a different school");
                }
            }
            case SCHOOL -> {
                if (!schoolId.equals(ownerId)) {
                    throw new AccessDeniedException("OWNER can only manage their own school's phones");
                }
            }
            case INSTRUCTOR -> {
                if (!lookup.instructorAtSchool(ownerId, schoolId)) {
                    throw new AccessDeniedException("Instructor is not at OWNER's school");
                }
            }
            case STUDENT -> {
                if (!lookup.studentAtSchool(ownerId, schoolId)) {
                    throw new AccessDeniedException("Student is not at OWNER's school");
                }
            }
        }
    }

    /** INSTRUCTOR: own user phones + own instructor row's phones. */
    private void requireInstructorScope(UUID userId, PhoneOwnerType type, UUID ownerId) {
        switch (type) {
            case USER -> {
                if (!userId.equals(ownerId)) {
                    throw new AccessDeniedException("INSTRUCTOR can only manage their own user phones");
                }
            }
            case INSTRUCTOR -> {
                if (!lookup.instructorBelongsToUser(ownerId, userId)) {
                    throw new AccessDeniedException("Instructor row does not belong to caller");
                }
            }
            case SCHOOL, STUDENT -> throw new AccessDeniedException(
                    "INSTRUCTOR cannot manage " + type + " phones");
        }
    }

    /** STUDENT: own user phones + own student row's phones. */
    private void requireStudentScope(UUID userId, PhoneOwnerType type, UUID ownerId) {
        switch (type) {
            case USER -> {
                if (!userId.equals(ownerId)) {
                    throw new AccessDeniedException("STUDENT can only manage their own user phones");
                }
            }
            case STUDENT -> {
                if (!lookup.studentBelongsToUser(ownerId, userId)) {
                    throw new AccessDeniedException("Student row does not belong to caller");
                }
            }
            case SCHOOL, INSTRUCTOR -> throw new AccessDeniedException(
                    "STUDENT cannot manage " + type + " phones");
        }
    }

    /** PARENT: own user phones + linked-student phones. */
    private void requireParentScope(UUID userId, PhoneOwnerType type, UUID ownerId) {
        switch (type) {
            case USER -> {
                if (!userId.equals(ownerId)) {
                    throw new AccessDeniedException("PARENT can only manage their own user phones");
                }
            }
            case STUDENT -> {
                if (!lookup.parentLinkedToStudent(userId, ownerId)) {
                    throw new AccessDeniedException("Student is not linked to this parent");
                }
            }
            case SCHOOL, INSTRUCTOR -> throw new AccessDeniedException(
                    "PARENT cannot manage " + type + " phones");
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Resolves a phone row's {@code (ownerType, ownerId)}. The DB CHECK
     * guarantees exactly one FK column is set.
     */
    public OwnerRef ownerOf(PhoneNumber p) {
        if (p.getUserId() != null)       return new OwnerRef(PhoneOwnerType.USER,       p.getUserId());
        if (p.getSchoolId() != null)     return new OwnerRef(PhoneOwnerType.SCHOOL,     p.getSchoolId());
        if (p.getInstructorId() != null) return new OwnerRef(PhoneOwnerType.INSTRUCTOR, p.getInstructorId());
        if (p.getStudentId() != null)    return new OwnerRef(PhoneOwnerType.STUDENT,    p.getStudentId());
        throw new IllegalStateException(
                "PhoneNumber " + p.getId() + " has no owner FK — DB CHECK should have rejected this");
    }

    public record OwnerRef(PhoneOwnerType type, UUID id) {}
}
