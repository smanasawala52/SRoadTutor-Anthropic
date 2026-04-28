package com.sroadtutor.telemetry.dto;

import java.time.Instant;

/**
 * High-level dataset summary for the AV-research B2B endpoint. V1 returns
 * counts only — no row-level data. The full export channel is tracked as
 * TD when a buyer signs an MSA.
 */
public record TelemetryDatasetSummary(
        long totalEvents,
        Instant generatedAt
) {}
