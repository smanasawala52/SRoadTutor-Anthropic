package com.sroadtutor.subscription.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.subscription.dto.PlanCatalogResponse;
import com.sroadtutor.subscription.dto.SubscriptionMeResponse;
import com.sroadtutor.subscription.dto.UpgradeRequest;
import com.sroadtutor.subscription.dto.UpgradeResponse;
import com.sroadtutor.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscriptions", description = "Plan tier + limits + admin-mode upgrade (Stripe pending PR12.5)")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @Operation(summary = "Caller's school's plan + limits + current monthly usage.")
    public ResponseEntity<ApiResponse<SubscriptionMeResponse>> me() {
        return ResponseEntity.ok(ApiResponse.of(
                service.getMine(SecurityUtil.currentUserId())));
    }

    @PostMapping("/upgrade")
    @Operation(summary = "Upgrade plan. Returns Stripe Checkout URL when configured; otherwise admin-mode flips inline.")
    public ResponseEntity<ApiResponse<UpgradeResponse>> upgrade(
            @Valid @RequestBody UpgradeRequest request
    ) {
        UpgradeResponse resp = service.upgrade(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), request);
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    @GetMapping("/plans")
    @Operation(summary = "PUBLIC. Plan catalog for the SPA's pricing page.")
    public ResponseEntity<ApiResponse<PlanCatalogResponse>> plans() {
        return ResponseEntity.ok(ApiResponse.of(PlanCatalogResponse.build()));
    }
}
