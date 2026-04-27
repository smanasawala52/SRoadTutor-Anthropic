package com.sroadtutor.invitation.dto;

import com.sroadtutor.invitation.model.Invitation;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of {@link Invitation}. The raw token is NOT included —
 * it's only ever surfaced once (in {@link CreateInvitationResponse}) and
 * never returned to listings or status queries.
 */
public record InvitationResponse(
        UUID id,
        UUID schoolId,
        UUID invitedByUserId,
        String email,
        String username,
        String role,
        String deliveryMode,
        String status,
        Instant expiresAt,
        Instant acceptedAt,
        UUID acceptedUserId,
        Instant createdAt
) {

    public static InvitationResponse fromEntity(Invitation i) {
        return new InvitationResponse(
                i.getId(),
                i.getSchoolId(),
                i.getInvitedByUserId(),
                i.getEmail(),
                i.getUsername(),
                i.getRole(),
                i.getDeliveryMode(),
                i.getStatus(),
                i.getExpiresAt(),
                i.getAcceptedAt(),
                i.getAcceptedUserId(),
                i.getCreatedAt()
        );
    }
}
