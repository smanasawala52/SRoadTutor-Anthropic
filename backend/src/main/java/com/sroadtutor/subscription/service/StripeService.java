package com.sroadtutor.subscription.service;

import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.subscription.model.PlanTier;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thin facade over the Stripe Java SDK. Locked at PR12.5:
 *
 * <ul>
 *   <li><b>Optional at boot</b> — {@code app.stripe.secret-key} blank → all
 *       methods turn into no-ops or throw {@code STRIPE_NOT_CONFIGURED}. The
 *       app still runs (admin-mode upgrade keeps the SubscriptionService
 *       contract working) so dev environments without Stripe credentials
 *       boot cleanly.</li>
 *   <li><b>Customer per school</b> — {@link #createCustomer} is called from
 *       {@code SchoolService.createForCurrentOwner} after the school row is
 *       persisted; the returned id lands in {@code schools.stripe_customer_id}.</li>
 *   <li><b>Hosted Checkout for upgrade</b> — {@link #createUpgradeCheckoutSession}
 *       returns a Stripe-hosted Checkout URL the SPA redirects to. The
 *       webhook flips the plan when {@code checkout.session.completed} fires.</li>
 *   <li><b>Webhook signature-verified</b> — {@link #parseAndVerifyEvent} fails
 *       closed; the controller calls this before dispatching by event type.</li>
 * </ul>
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final AppProperties.Stripe config;

    public StripeService(AppProperties props) {
        this.config = props.stripe();
        if (isConfigured()) {
            // Set the API key on the singleton — Stripe's SDK reads it statically.
            Stripe.apiKey = config.secretKey();
            log.info("Stripe configured (live wire-up active)");
        } else {
            log.info("Stripe NOT configured — billing endpoints fall back to admin-mode");
        }
    }

    // ============================================================
    // Configuration helpers
    // ============================================================

    public boolean isConfigured() {
        return config != null
                && config.secretKey() != null
                && !config.secretKey().isBlank();
    }

    /**
     * Returns the Stripe Price id for a given plan tier, or null if not
     * configured for this tier (FREE always returns null).
     */
    public String priceFor(PlanTier tier) {
        if (config == null || config.prices() == null) return null;
        return switch (tier) {
            case PRO        -> blankToNull(config.prices().pro());
            case GROWTH     -> blankToNull(config.prices().growth());
            case ENTERPRISE -> blankToNull(config.prices().enterprise());
            case FREE       -> null;
        };
    }

    // ============================================================
    // Customer
    // ============================================================

    /**
     * Creates a Stripe Customer for the school. Returns the new customer id.
     * No-op (returns null) when Stripe is not configured — the school row's
     * {@code stripe_customer_id} stays null.
     */
    public String createCustomer(UUID schoolId, String schoolName, String ownerEmail) {
        if (!isConfigured()) return null;
        try {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("schoolId", schoolId.toString());

            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(schoolName)
                    .setEmail(ownerEmail)
                    .setDescription("SRoadTutor school " + schoolId)
                    .putAllMetadata(metadata)
                    .build();
            Customer customer = Customer.create(params);
            log.info("Stripe Customer {} created for school={}", customer.getId(), schoolId);
            return customer.getId();
        } catch (StripeException ex) {
            log.error("Stripe createCustomer failed for school={}: {}", schoolId, ex.getMessage());
            throw new BadRequestException(
                    "STRIPE_CUSTOMER_FAILED",
                    "Stripe rejected the customer create call: " + ex.getMessage());
        }
    }

    // ============================================================
    // Checkout session for upgrade
    // ============================================================

    /**
     * Creates a Stripe-hosted Checkout Session for an upgrade. The school's
     * existing {@code stripeCustomerId} is reused if present so the upgrade
     * is attached to the right customer. Returns the Checkout URL.
     */
    public String createUpgradeCheckoutSession(UUID schoolId, String stripeCustomerId, PlanTier targetPlan) {
        requireConfigured();
        String priceId = priceFor(targetPlan);
        if (priceId == null) {
            throw new BadRequestException(
                    "STRIPE_PRICE_NOT_CONFIGURED",
                    "No Stripe price id configured for plan " + targetPlan
                            + " — set STRIPE_PRICE_" + targetPlan + " or use admin-mode upgrade.");
        }

        try {
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(config.successUrl())
                    .setCancelUrl(config.cancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId)
                            .build())
                    .putMetadata("schoolId", schoolId.toString())
                    .putMetadata("targetPlan", targetPlan.name());

            // Reuse customer if already created (avoids creating a duplicate
            // record in Stripe each upgrade attempt).
            if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
                builder.setCustomer(stripeCustomerId);
            }

            Session session = Session.create(builder.build());
            log.info("Stripe Checkout session {} created for school={} plan={}",
                    session.getId(), schoolId, targetPlan);
            return session.getUrl();
        } catch (StripeException ex) {
            log.error("Stripe createCheckoutSession failed for school={}: {}", schoolId, ex.getMessage());
            throw new BadRequestException(
                    "STRIPE_CHECKOUT_FAILED",
                    "Stripe rejected the checkout-session call: " + ex.getMessage());
        }
    }

    // ============================================================
    // Webhook verification + parsing
    // ============================================================

    /**
     * Verifies the webhook signature and returns the parsed Event. Throws
     * {@code INVALID_WEBHOOK_SIGNATURE} on any failure — the controller maps
     * this to a 400 (Stripe will retry the delivery).
     */
    public Event parseAndVerifyEvent(String payload, String signatureHeader) {
        requireConfigured();
        if (config.webhookSecret() == null || config.webhookSecret().isBlank()) {
            throw new BadRequestException(
                    "STRIPE_NOT_CONFIGURED",
                    "Stripe webhook secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException(
                    "MISSING_WEBHOOK_SIGNATURE",
                    "Stripe-Signature header is required");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, config.webhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            throw new BadRequestException(
                    "INVALID_WEBHOOK_SIGNATURE",
                    "Stripe webhook signature did not verify");
        }
    }

    /**
     * Convenience for the controller — pulls the Subscription object from a
     * webhook event whose data.object is a Subscription. Falls back to the
     * raw deserialiser when the SDK's typed wrapper isn't populated.
     */
    public Subscription extractSubscription(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(o -> o instanceof Subscription)
                .map(o -> (Subscription) o)
                .orElse(null);
    }

    /** Same as above but for checkout.session.completed events. */
    public Session extractCheckoutSession(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(o -> o instanceof Session)
                .map(o -> (Session) o)
                .orElse(null);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "STRIPE_NOT_CONFIGURED",
                    "Stripe is not configured on this environment. Set STRIPE_SECRET_KEY or use admin-mode upgrade.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
