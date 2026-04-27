package com.sroadtutor.marketplace.dto;

import com.sroadtutor.marketplace.model.InstructorPayout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InstructorPayoutResponse(
        UUID id,
        UUID instructorId,
        UUID leadId,
        BigDecimal payoutAmount,
        String status,
        String eTransferRef,
        Instant paidAt,
        Instant createdAt
) {

    public static InstructorPayoutResponse fromEntity(InstructorPayout p) {
        return new InstructorPayoutResponse(
                p.getId(),
                p.getInstructorId(),
                p.getLeadId(),
                p.getPayoutAmount(),
                p.getStatus(),
                p.getETransferRef(),
                p.getPaidAt(),
                p.getCreatedAt()
        );
    }
}
