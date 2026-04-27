package com.sroadtutor.subscription.controller;

import com.sroadtutor.subscription.model.PlanTier;
import com.sroadtutor.subscription.service.StripeService;
import com.sroadtutor.subscription.service.SubscriptionService;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stripe webhook receiver. Public endpoint (whitelisted in
 * {@link com.sroadtutor.config.SecurityConfig}); the Stripe-Signature
 * header is the auth proof — verified inside
 * {@link StripeService#parseAndVerifyEvent}.
 *
 * <p>Event handling — locked at PR12.5:
 * <ul>
 *   <li>{@code checkout.session.completed} — initial purchase. Reads
 *       {@code metadata.schoolId} + {@code metadata.targetPlan} the
 *       Checkout was created with, calls
 *       {@link SubscriptionService#applyStripeUpdate} with the resulting
 *       Stripe subscription id.</li>
 *   <li>{@code customer.subscription.updated} — plan change initiated
 *       inside Stripe (e.g. via Customer Portal). Re-syncs.</li>
 *   <li>{@code customer.subscription.deleted} — cancellation.
 *       {@link SubscriptionService#applyStripeCancellation} flips back to FREE.</li>
 *   <li>Anything else — logged at INFO and acked, since Stripe wants a
 *       2xx for any event we don't care about.</li>
 * </ul>
 *
 * <p>Always returns {@code 200 OK} once the signature verifies. Errors
 * are logged and the response stays 2xx so Stripe doesn't retry on a
 * mistake we can't fix automatically. The signature path itself returns
 * {@code 400} on verification failure — that's the only retry signal we
 * want Stripe to respect.</p>
 */
@RestController
@RequestMapping("/api/stripe")
@Tag(name = "Stripe Webhook", description = "Live webhook receiver — signature-verified, dispatches by event type")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;

    public StripeWebhookController(StripeService stripeService,
                                     SubscriptionService subscriptionService) {
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/webhook")
    @Operation(summary = "PUBLIC. Stripe webhook receiver. Signature-verified.")
    public ResponseEntity<String> webhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody(required = false) String payload
    ) {
        if (!stripeService.isConfigured()) {
            // Stripe isn't wired on this environment. Ack and ignore.
            log.info("Stripe webhook received but Stripe is not configured — ignoring");
            return ResponseEntity.ok("ignored");
        }

        Event event = stripeService.parseAndVerifyEvent(payload, signature);
        try {
            dispatch(event);
        } catch (RuntimeException ex) {
            // Don't let an internal handler error trigger a Stripe retry —
            // log it and ack so we can fix forward.
            log.error("Stripe webhook handler failed for event id={} type={}",
                    event.getId(), event.getType(), ex);
        }
        return ResponseEntity.ok("ok");
    }

    // ============================================================
    // Dispatch
    // ============================================================

    private void dispatch(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted(event);
            case "customer.subscription.created", "customer.subscription.updated" -> onSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(event);
            default -> log.info("Stripe webhook event type={} — no handler, acked", event.getType());
        }
    }

    private void onCheckoutCompleted(Event event) {
        Session session = stripeService.extractCheckoutSession(event);
        if (session == null) {
            log.warn("checkout.session.completed event {} had no Session payload", event.getId());
            return;
        }
        Map<String, String> metadata = session.getMetadata();
        UUID schoolId = uuidOrNull(metadata, "schoolId");
        PlanTier target = planOrNull(metadata, "targetPlan");
        if (schoolId == null || target == null) {
            log.warn("checkout.session.completed event {} missing required metadata (schoolId / targetPlan)",
                    event.getId());
            return;
        }
        subscriptionService.applyStripeUpdate(
                schoolId, target, session.getSubscription(), null);
    }

    private void onSubscriptionUpdated(Event event) {
        Subscription sub = stripeService.extractSubscription(event);
        if (sub == null) {
            log.warn("customer.subscription.* event {} had no Subscription payload", event.getId());
            return;
        }
        Map<String, String> metadata = sub.getMetadata();
        UUID schoolId = uuidOrNull(metadata, "schoolId");
        PlanTier target = planOrNull(metadata, "targetPlan");
        if (schoolId == null) {
            log.warn("customer.subscription.* event {} missing schoolId metadata — cannot route",
                    event.getId());
            return;
        }
        if (target == null) {
            // Stripe can update a sub without target metadata (e.g. price-id
            // change via Portal). Refuse silently — admin-mode upgrade is
            // still available. Tracked as TD: derive plan from the sub's
            // line items by looking up the Price -> PlanTier mapping.
            log.info("customer.subscription.* for school={} has no targetPlan metadata; skipping flip",
                    schoolId);
            return;
        }
        Instant periodEnd = sub.getCurrentPeriodEnd() == null
                ? null
                : Instant.ofEpochSecond(sub.getCurrentPeriodEnd());
        subscriptionService.applyStripeUpdate(schoolId, target, sub.getId(), periodEnd);
    }

    private void onSubscriptionDeleted(Event event) {
        Subscription sub = stripeService.extractSubscription(event);
        if (sub == null) {
            log.warn("customer.subscription.deleted event {} had no Subscription payload", event.getId());
            return;
        }
        Map<String, String> metadata = sub.getMetadata();
        UUID schoolId = uuidOrNull(metadata, "schoolId");
        if (schoolId == null) {
            log.warn("customer.subscription.deleted event {} missing schoolId metadata", event.getId());
            return;
        }
        subscriptionService.applyStripeCancellation(schoolId);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static UUID uuidOrNull(Map<String, String> m, String key) {
        if (m == null) return null;
        String v = m.get(key);
        if (v == null || v.isBlank()) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static PlanTier planOrNull(Map<String, String> m, String key) {
        if (m == null) return null;
        String v = m.get(key);
        if (v == null || v.isBlank()) return null;
        try {
            return PlanTier.valueOf(v.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
