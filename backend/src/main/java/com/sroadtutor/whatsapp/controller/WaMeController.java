package com.sroadtutor.whatsapp.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.whatsapp.dto.ClickConfirmResponse;
import com.sroadtutor.whatsapp.dto.WaMeLinkRequest;
import com.sroadtutor.whatsapp.dto.WaMeLinkResponse;
import com.sroadtutor.whatsapp.service.WaMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * WhatsApp click-to-chat endpoints. The native WhatsApp Cloud API path is
 * tracked as TD-17 (V2+); V1 generates wa.me links that the SPA renders as
 * anchors. Click-confirm is the verification beacon (D14).
 */
@RestController
@RequestMapping("/api/whatsapp/links")
@Tag(name = "WhatsApp Links",
        description = "Generate wa.me click-to-chat links + click-confirm beacon (verifies recipient phone)")
public class WaMeController {

    private final WaMeService waMeService;

    public WaMeController(WaMeService waMeService) {
        this.waMeService = waMeService;
    }

    @PostMapping
    @Operation(summary = "Generate a wa.me link for a phone number; writes a whatsapp_message_log row")
    public ResponseEntity<ApiResponse<WaMeLinkResponse>> generate(
            @Valid @RequestBody WaMeLinkRequest request
    ) {
        WaMeLinkResponse resp = waMeService.generateLink(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @PostMapping("/{logId}/click-confirm")
    @Operation(summary = "Beacon fired when the SPA opens the wa.me anchor — flips phone.verified_at on first click")
    public ResponseEntity<ApiResponse<ClickConfirmResponse>> confirmClick(@PathVariable UUID logId) {
        ClickConfirmResponse resp = waMeService.confirmClick(
                SecurityUtil.currentUserId(),
                logId);
        return ResponseEntity.ok(ApiResponse.of(resp));
    }
}
