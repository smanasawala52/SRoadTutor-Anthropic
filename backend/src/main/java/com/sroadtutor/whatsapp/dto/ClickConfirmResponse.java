package com.sroadtutor.whatsapp.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for {@code POST /api/whatsapp/links/{logId}/click-confirm}. Reports
 * whether the click flipped the phone's {@code verifiedAt} for the first time —
 * the SPA uses this to show the "verified ✓" badge without a follow-up GET.
 */
public record ClickConfirmResponse(
        UUID logId,
        UUID recipientPhoneId,
        Instant clickedAt,
        boolean phoneVerifiedNow,
        Instant phoneVerifiedAt
) {}
