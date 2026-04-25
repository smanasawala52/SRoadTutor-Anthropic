package com.sroadtutor.phone.dto;

import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.model.PhoneOwnerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side projection of {@link PhoneNumber}. The {@code ownerType} field is
 * derived from whichever FK column is non-null (the {@code phone_owner_exactly_one}
 * CHECK guarantees exactly one).
 */
public record PhoneNumberResponse(
        UUID id,
        PhoneOwnerType ownerType,
        UUID ownerId,
        String countryCode,
        String nationalNumber,
        String e164,
        String label,
        boolean primary,
        boolean whatsapp,
        boolean whatsappOptIn,
        Instant verifiedAt,
        boolean verified,
        Instant createdAt,
        Instant updatedAt
) {

    public static PhoneNumberResponse fromEntity(PhoneNumber p) {
        PhoneOwnerType type;
        UUID ownerId;
        if (p.getUserId() != null) {
            type = PhoneOwnerType.USER;
            ownerId = p.getUserId();
        } else if (p.getSchoolId() != null) {
            type = PhoneOwnerType.SCHOOL;
            ownerId = p.getSchoolId();
        } else if (p.getInstructorId() != null) {
            type = PhoneOwnerType.INSTRUCTOR;
            ownerId = p.getInstructorId();
        } else if (p.getStudentId() != null) {
            type = PhoneOwnerType.STUDENT;
            ownerId = p.getStudentId();
        } else {
            // The CHECK constraint guarantees this is unreachable — but if a
            // bug ever produces an orphan row we want a clear error, not NPE.
            throw new IllegalStateException(
                    "PhoneNumber " + p.getId() + " has no owner FK set — DB CHECK should have rejected this");
        }
        return new PhoneNumberResponse(
                p.getId(),
                type,
                ownerId,
                p.getCountryCode(),
                p.getNationalNumber(),
                p.getE164(),
                p.getLabel(),
                p.isPrimary(),
                p.isWhatsapp(),
                p.isWhatsappOptIn(),
                p.getVerifiedAt(),
                p.isVerified(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
