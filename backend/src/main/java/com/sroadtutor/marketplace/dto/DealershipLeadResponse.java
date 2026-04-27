package com.sroadtutor.marketplace.dto;

import com.sroadtutor.marketplace.model.DealershipLead;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DealershipLeadResponse(
        UUID id,
        UUID studentId,
        UUID parentUserId,
        String vehiclePrefJson,
        BigDecimal budget,
        Boolean financingReady,
        UUID dealershipId,
        String status,
        BigDecimal bountyAmount,
        Instant convertedAt,
        Instant createdAt
) {

    public static DealershipLeadResponse fromEntity(DealershipLead l) {
        return new DealershipLeadResponse(
                l.getId(),
                l.getStudentId(),
                l.getParentUserId(),
                l.getVehiclePrefJson(),
                l.getBudget(),
                l.getFinancingReady(),
                l.getDealershipId(),
                l.getStatus(),
                l.getBountyAmount(),
                l.getConvertedAt(),
                l.getCreatedAt()
        );
    }
}
