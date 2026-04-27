package com.sroadtutor.auth.controller;

import com.sroadtutor.auth.dto.EmailVerifyConfirmResponse;
import com.sroadtutor.auth.dto.EmailVerifyResponse;
import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.auth.service.EmailVerificationService;
import com.sroadtutor.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email verification endpoints.
 *
 * <p>{@code /send} requires authentication (JWT) — only the user themselves
 * can request a verify link. {@code /{token}/confirm} is intentionally PUBLIC
 * (whitelisted in {@link com.sroadtutor.config.SecurityConfig}) so the link
 * works even from a logged-out browser.</p>
 */
@RestController
@RequestMapping("/auth/email-verify")
@Tag(name = "Email Verification", description = "Issue + redeem one-shot email verification tokens")
public class EmailVerificationController {

    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    @Operation(summary = "Issue (or re-issue) a verification token for the caller's email")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> send() {
        return ResponseEntity.ok(ApiResponse.of(
                service.issueForCurrentUser(SecurityUtil.currentUserId())));
    }

    @PostMapping("/{token}/confirm")
    @Operation(summary = "Redeem a verification token (public — no JWT required)")
    public ResponseEntity<ApiResponse<EmailVerifyConfirmResponse>> confirm(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(ApiResponse.of(service.confirm(token)));
    }
}
