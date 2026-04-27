package com.sroadtutor.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body for {@code PUT /api/payments/{id}/mark-paid} — flip an UNPAID row to
 * PAID with a method + timestamp. Stripe rows are webhook-driven and never
 * pass through this endpoint.
 */
public record MarkPaidRequest(
        @NotNull
        @Pattern(regexp = "^(CASH|ETRANSFER|OTHER)$",
                message = "method must be one of CASH, ETRANSFER, OTHER")
        String method,

        /** Defaults to now if null. */
        Instant paidAt,

        @Size(max = 4000) String notes
) {}
