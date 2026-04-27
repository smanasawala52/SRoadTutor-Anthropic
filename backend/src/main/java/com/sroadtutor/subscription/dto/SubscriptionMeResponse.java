package com.sroadtutor.subscription.dto;

/**
 * Response of {@code GET /api/subscriptions/me} — caller's school's plan +
 * limits + current usage.
 */
public record SubscriptionMeResponse(
        String tier,
        String monthlyPriceCad,
        Limits limits,
        Usage usage,
        boolean stripeManaged
) {

    public record Limits(int instructorLimit, int studentLimit,
                         int phonesPerOwnerLimit, int waMeMonthlyLimit) {}

    public record Usage(int waMeThisMonth) {}
}
