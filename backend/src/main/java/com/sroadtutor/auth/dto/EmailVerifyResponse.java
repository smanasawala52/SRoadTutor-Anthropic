package com.sroadtutor.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/auth/email-verify/send} — V1.
 *
 * <p>Until email infra is wired up, the raw token is included in the response
 * so the SPA / dev tools can construct the verify URL. Once SMTP is connected
 * (PR-email), {@code rawToken} and {@code verifyUrlForDev} will be stripped
 * from the response and only delivered via the user's inbox.</p>
 */
public record EmailVerifyResponse(
        UUID tokenId,
        String rawToken,
        String verifyUrlForDev,
        Instant expiresAt,
        boolean reissued
) {}
