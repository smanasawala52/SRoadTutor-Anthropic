package com.sroadtutor.invitation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned at invitation create time. Carries the raw token (TOKEN mode only)
 * so the SPA can construct an accept URL until SMTP infra is wired up.
 *
 * <p>For DUMMY_PWD mode, {@code rawToken} and {@code acceptUrlForDev} are
 * null and {@code acceptedUserId} is set — the user account already exists
 * and the invitee logs in directly with the dummy password.</p>
 */
public record CreateInvitationResponse(
        UUID invitationId,
        String email,
        String role,
        String deliveryMode,
        String status,

        /** Raw accept token. Null when deliveryMode = DUMMY_PWD. */
        String rawToken,

        /** Pre-built accept URL for dev. Null when deliveryMode = DUMMY_PWD. */
        String acceptUrlForDev,

        /** Set immediately for DUMMY_PWD; null until accept for TOKEN mode. */
        UUID acceptedUserId,

        Instant expiresAt,
        Instant createdAt
) {}
