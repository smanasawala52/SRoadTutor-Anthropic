package com.sroadtutor.marketplace.dto;

import com.sroadtutor.marketplace.model.Dealership;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of {@link Dealership}. The encrypted CRM API key is
 * deliberately omitted — it never leaves the backend.
 */
public record DealershipResponse(
        UUID id,
        String name,
        String city,
        String province,
        String crmType,
        BigDecimal bountyPerLead,
        boolean active,
        Instant createdAt
) {

    public static DealershipResponse fromEntity(Dealership d) {
        return new DealershipResponse(
                d.getId(),
                d.getName(),
                d.getCity(),
                d.getProvince(),
                d.getCrmType(),
                d.getBountyPerLead(),
                d.isActive(),
                d.getCreatedAt()
        );
    }
}
