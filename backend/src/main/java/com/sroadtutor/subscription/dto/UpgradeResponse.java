package com.sroadtutor.subscription.dto;

/**
 * Response of {@code POST /api/subscriptions/upgrade}. Two shapes:
 *
 * <ul>
 *   <li><b>Stripe path</b> ({@code mode=STRIPE_CHECKOUT}): {@code checkoutUrl}
 *       is set; SPA redirects the user to Stripe-hosted Checkout. The plan
 *       does NOT flip until {@code checkout.session.completed} fires.</li>
 *   <li><b>Admin-mode path</b> ({@code mode=ADMIN}): plan flipped immediately;
 *       {@code checkoutUrl} is null. Used when Stripe isn't configured or no
 *       Stripe price id exists for the target tier.</li>
 * </ul>
 */
public record UpgradeResponse(
        String mode,
        String currentPlan,
        String checkoutUrl
) {
    public static final String MODE_STRIPE_CHECKOUT = "STRIPE_CHECKOUT";
    public static final String MODE_ADMIN          = "ADMIN";
}
