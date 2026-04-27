package com.sroadtutor.marketplace.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/marketplace/payouts/{id}/mark-paid}. The
 * Interac e-Transfer reference is captured for audit; absence is OK.
 */
public record MarkPayoutPaidRequest(
        @Size(max = 128) String eTransferRef
) {}
