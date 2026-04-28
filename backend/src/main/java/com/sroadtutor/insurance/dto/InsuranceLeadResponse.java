package com.sroadtutor.insurance.dto;

import com.sroadtutor.insurance.model.InsuranceLead;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InsuranceLeadResponse(
        UUID id,
        UUID studentId,
        UUID brokerId,
        String status,
        BigDecimal bountyAmount,
        Instant quotedAt,
        Instant convertedAt,
        Instant createdAt
) {

    public static InsuranceLeadResponse fromEntity(InsuranceLead l) {
        return new InsuranceLeadResponse(
                l.getId(),
                l.getStudentId(),
                l.getBrokerId(),
                l.getStatus(),
                l.getBountyAmount(),
                l.getQuotedAt(),
                l.getConvertedAt(),
                l.getCreatedAt()
        );
    }
}
