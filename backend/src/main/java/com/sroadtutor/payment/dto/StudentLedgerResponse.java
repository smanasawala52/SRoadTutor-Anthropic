package com.sroadtutor.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /api/students/{id}/payments} — student's full
 * ledger with summary totals.
 */
public record StudentLedgerResponse(
        UUID studentId,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        String currency,
        List<PaymentResponse> payments
) {}
