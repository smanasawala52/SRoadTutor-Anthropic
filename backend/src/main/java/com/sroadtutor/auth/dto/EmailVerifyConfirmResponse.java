package com.sroadtutor.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/auth/email-verify/{token}/confirm}. The token
 * was consumed; the user's {@code email_verified_at} is now set.
 */
public record EmailVerifyConfirmResponse(
        UUID userId,
        String email,
        Instant emailVerifiedAt
) {}
