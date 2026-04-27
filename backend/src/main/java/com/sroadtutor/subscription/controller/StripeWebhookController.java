package com.sroadtutor.subscription.controller;

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

/**
 * Stripe webhook scaffold (PR12 stub). Just logs the inbound payload for
 * now; signature verification + event handling lands in PR12.5 once we
 * have a Stripe account configured. The endpoint is publicly reachable
 * (whitelisted in {@link com.sroadtutor.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/stripe")
@Tag(name = "Stripe Webhook", description = "Scaffolded; live in PR12.5")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @PostMapping("/webhook")
    @Operation(summary = "PUBLIC. Stripe webhook receiver (scaffold; PR12.5 wires real handling).")
    public ResponseEntity<String> webhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody(required = false) String payload
    ) {
        log.info("Stripe webhook received (scaffold): signaturePresent={} bodyBytes={}",
                signature != null,
                payload == null ? 0 : payload.length());
        // V1: ack everything. PR12.5 will verify the signature and dispatch
        // by event type (customer.subscription.created/updated/deleted, etc.).
        return ResponseEntity.ok("ok");
    }
}
