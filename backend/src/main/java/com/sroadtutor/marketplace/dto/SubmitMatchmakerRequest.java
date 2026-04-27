package com.sroadtutor.marketplace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Body for {@code POST /api/marketplace/matchmaker} — parent submits the
 * First Car Matchmaker form.
 *
 * <p>{@code vehiclePreferences} is free-form JSON the SPA captures (make,
 * model, body type, must-haves, etc.). The service serialises it into the
 * {@code vehicle_pref_json} JSONB column and never reads structured
 * fields out of it — that's the dealership's job at conversion time.</p>
 */
public record SubmitMatchmakerRequest(
        @NotNull UUID studentId,

        Map<String, Object> vehiclePreferences,

        @Digits(integer = 10, fraction = 2)
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal budget,

        Boolean financingReady
) {}
