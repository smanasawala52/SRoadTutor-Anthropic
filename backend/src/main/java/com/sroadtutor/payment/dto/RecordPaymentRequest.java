package com.sroadtutor.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Body for {@code POST /api/payments} — record a manual payment.
 *
 * <p>{@code studentId} is required. {@code sessionId} is optional — payments
 * tied to a specific lesson reference it; package prepays leave it null.</p>
 *
 * <p>Status is forced based on whether {@code paidAt} is supplied:
 * supplied → {@code PAID}; null → {@code UNPAID}. The service refuses to
 * accept a {@code STRIPE} method here — those rows arrive via the Stripe
 * webhook only.</p>
 */
public record RecordPaymentRequest(
        @NotNull UUID studentId,

        UUID sessionId,

        @NotNull
        @Digits(integer = 10, fraction = 2)
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal amount,

        @Size(min = 3, max = 3) String currency,

        @Pattern(regexp = "^(CASH|ETRANSFER|OTHER)$",
                message = "method must be one of CASH, ETRANSFER, OTHER (Stripe rows are webhook-only)")
        String method,

        /** When non-null, payment is created PAID at this instant. When null, UNPAID. */
        Instant paidAt,

        @Size(max = 4000) String notes
) {}
