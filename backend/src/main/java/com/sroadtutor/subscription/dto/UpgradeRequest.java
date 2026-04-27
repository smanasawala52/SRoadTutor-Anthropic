package com.sroadtutor.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code POST /api/subscriptions/upgrade} — admin-mode plan flip
 * (PR12 stub). Stripe-driven upgrades arrive via webhook in PR12.5.
 */
public record UpgradeRequest(
        @NotBlank
        @Pattern(regexp = "^(FREE|PRO|GROWTH|ENTERPRISE)$",
                message = "targetPlan must be one of FREE, PRO, GROWTH, ENTERPRISE")
        String targetPlan
) {}
