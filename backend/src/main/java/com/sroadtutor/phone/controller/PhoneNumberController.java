package com.sroadtutor.phone.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.phone.dto.PhoneNumberRequest;
import com.sroadtutor.phone.dto.PhoneNumberResponse;
import com.sroadtutor.phone.dto.PhoneNumberUpdateRequest;
import com.sroadtutor.phone.dto.WhatsappOptInRequest;
import com.sroadtutor.phone.model.PhoneOwnerType;
import com.sroadtutor.phone.service.PhoneNumberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Phone number CRUD. Every endpoint authenticates via JWT (see
 * SecurityConfig — {@code /api/**} requires a token), and every action is
 * gated by {@code PhoneScopeChecker} inside the service layer.
 *
 * <p>Owner FK is set on create and is immutable afterwards. The "primary"
 * toggle has its own endpoint because flipping it carries the demote-the-old
 * one semantics that don't belong on a generic PUT.</p>
 */
@RestController
@RequestMapping("/api/phone-numbers")
@Tag(name = "Phone Numbers", description = "1..N phone numbers per owner; WhatsApp opt-in + verification")
public class PhoneNumberController {

    private final PhoneNumberService service;

    public PhoneNumberController(PhoneNumberService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a phone number for an owner (USER | SCHOOL | INSTRUCTOR | STUDENT)")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> create(
            @Valid @RequestBody PhoneNumberRequest request
    ) {
        var phone = service.create(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(PhoneNumberResponse.fromEntity(phone)));
    }

    @GetMapping
    @Operation(summary = "List phone numbers for an owner. Defaults to the caller's own user_id phones.")
    public ResponseEntity<ApiResponse<List<PhoneNumberResponse>>> list(
            @RequestParam(name = "ownerType", required = false) PhoneOwnerType ownerType,
            @RequestParam(name = "ownerId", required = false) UUID ownerId
    ) {
        UUID currentUserId = SecurityUtil.currentUserId();
        // Defaults: caller's own user_id phones — the most common SPA call.
        PhoneOwnerType effectiveType = ownerType == null ? PhoneOwnerType.USER : ownerType;
        UUID effectiveId = ownerId == null ? currentUserId : ownerId;

        var phones = service.listForOwner(SecurityUtil.currentRole(), currentUserId,
                effectiveType, effectiveId);
        var resp = phones.stream().map(PhoneNumberResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one phone number by id")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> get(@PathVariable UUID id) {
        var phone = service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(PhoneNumberResponse.fromEntity(phone)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update label, country/national number, WhatsApp flags. Owner FK is immutable.")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PhoneNumberUpdateRequest request
    ) {
        var phone = service.update(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of(PhoneNumberResponse.fromEntity(phone)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a phone number")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/primary")
    @Operation(summary = "Promote this phone to primary (demotes any existing primary on the same owner)")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> makePrimary(@PathVariable UUID id) {
        var phone = service.promoteToPrimary(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(PhoneNumberResponse.fromEntity(phone)));
    }

    @PostMapping("/{id}/whatsapp-optin")
    @Operation(summary = "Toggle WhatsApp opt-in for this phone")
    public ResponseEntity<ApiResponse<PhoneNumberResponse>> setWhatsappOptIn(
            @PathVariable UUID id,
            @Valid @RequestBody WhatsappOptInRequest request
    ) {
        var phone = service.setWhatsappOptIn(SecurityUtil.currentRole(), SecurityUtil.currentUserId(),
                id, request.optIn());
        return ResponseEntity.ok(ApiResponse.of(PhoneNumberResponse.fromEntity(phone)));
    }
}
