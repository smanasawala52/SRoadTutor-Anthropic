package com.sroadtutor.insurance.dto;

import com.sroadtutor.insurance.model.InsuranceBroker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read projection. The encrypted CRM API key is never surfaced.
 */
public record InsuranceBrokerResponse(
        UUID id,
        String name,
        String contactEmail,
        String province,
        BigDecimal bountyPerQuote,
        boolean active,
        Instant createdAt
) {

    public static InsuranceBrokerResponse fromEntity(InsuranceBroker b) {
        return new InsuranceBrokerResponse(
                b.getId(),
                b.getName(),
                b.getContactEmail(),
                b.getProvince(),
                b.getBountyPerQuote(),
                b.isActive(),
                b.getCreatedAt()
        );
    }
}
