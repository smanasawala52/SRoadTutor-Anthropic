package com.sroadtutor.telemetry.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body for {@code POST /api/telemetry/mistakes/{sessionMistakeId}/events}.
 * Owner-supplied vehicle telemetry attached to a logged mistake.
 */
public record AttachTelemetryRequest(
        @Size(max = 64) String vehicleMake,
        @Size(max = 64) String vehicleModel,
        @Min(1900)      Integer vehicleYear,

        @NotNull Map<String, Object> telemetry,

        Long offsetMs
) {}
