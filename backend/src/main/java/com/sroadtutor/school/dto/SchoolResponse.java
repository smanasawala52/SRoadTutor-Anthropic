package com.sroadtutor.school.dto;

import com.sroadtutor.school.model.School;

import java.time.Instant;
import java.util.UUID;

/**
 * Full read projection of {@link School} — used by the OWNER's settings screens.
 * Non-owner roles get the leaner {@link SchoolMeResponse} from {@code GET /me}
 * to avoid leaking tax IDs.
 */
public record SchoolResponse(
        UUID id,
        String name,
        UUID ownerId,
        String planTier,
        String stripeCustomerId,
        String province,
        String jurisdiction,
        String gstNumber,
        String pstNumber,
        String hstNumber,
        String businessRegistrationNumber,
        boolean active,
        boolean synthetic,
        Instant createdAt,
        Instant updatedAt
) {

    public static SchoolResponse fromEntity(School s) {
        return new SchoolResponse(
                s.getId(),
                s.getName(),
                s.getOwnerId(),
                s.getPlanTier(),
                s.getStripeCustomerId(),
                s.getProvince(),
                s.getJurisdiction(),
                s.getGstNumber(),
                s.getPstNumber(),
                s.getHstNumber(),
                s.getBusinessRegistrationNumber(),
                s.isActive(),
                s.isSynthetic(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
