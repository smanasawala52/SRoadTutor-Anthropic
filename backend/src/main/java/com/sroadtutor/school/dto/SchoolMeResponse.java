package com.sroadtutor.school.dto;

import com.sroadtutor.school.model.School;

import java.util.UUID;

/**
 * Minimal projection for {@code GET /api/schools/me} — anyone in the school
 * (OWNER, INSTRUCTOR, STUDENT, PARENT) can read this. Tax IDs and stripe
 * customer id are deliberately omitted; only the OWNER's full {@code GET /{id}}
 * surfaces those.
 */
public record SchoolMeResponse(
        UUID id,
        String name,
        String jurisdiction,
        String province,
        String planTier,
        boolean active
) {

    public static SchoolMeResponse fromEntity(School s) {
        return new SchoolMeResponse(
                s.getId(),
                s.getName(),
                s.getJurisdiction(),
                s.getProvince(),
                s.getPlanTier(),
                s.isActive()
        );
    }
}
