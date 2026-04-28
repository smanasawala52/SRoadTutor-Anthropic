package com.sroadtutor.risk.dto;

import java.time.Instant;
import java.util.Map;

/**
 * B2B aggregate endpoint response — counts per tier across the entire
 * platform. No student-level detail leaks; this is the data product
 * insurers can subscribe to under PIPEDA.
 */
public record RiskAggregateResponse(
        long totalDrivers,
        Map<String, Long> countsByTier,
        Instant generatedAt
) {}
