package com.sroadtutor.payment.dto;

import com.sroadtutor.payment.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID schoolId,
        UUID studentId,
        UUID sessionId,
        BigDecimal amount,
        String currency,
        String method,
        String status,
        Instant paidAt,
        String stripePaymentId,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse fromEntity(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getSchoolId(),
                p.getStudentId(),
                p.getSessionId(),
                p.getAmount(),
                p.getCurrency(),
                p.getMethod(),
                p.getStatus(),
                p.getPaidAt(),
                p.getStripePaymentId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
