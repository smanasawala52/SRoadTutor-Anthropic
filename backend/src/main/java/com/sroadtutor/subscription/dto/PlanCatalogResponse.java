package com.sroadtutor.subscription.dto;

import com.sroadtutor.subscription.model.PlanTier;

import java.util.Arrays;
import java.util.List;

/**
 * Public response of {@code GET /api/subscriptions/plans} — drives the
 * SPA's pricing page.
 */
public record PlanCatalogResponse(List<PlanRow> plans) {

    public record PlanRow(
            String tier,
            String monthlyPriceCad,
            int instructorLimit,
            int studentLimit,
            int phonesPerOwnerLimit,
            int waMeMonthlyLimit
    ) {}

    public static PlanCatalogResponse build() {
        return new PlanCatalogResponse(
                Arrays.stream(PlanTier.values())
                        .map(p -> new PlanRow(
                                p.name(),
                                p.monthlyPriceCad(),
                                p.instructorLimit(),
                                p.studentLimit(),
                                p.phonesPerOwnerLimit(),
                                p.waMeMonthlyLimit()))
                        .toList());
    }
}
