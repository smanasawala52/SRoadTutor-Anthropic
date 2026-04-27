package com.sroadtutor.invitation.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.invitation.dto.AcceptInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInstructorInvitationRequest;
import com.sroadtutor.invitation.dto.CreateInvitationResponse;
import com.sroadtutor.invitation.dto.CreateParentInvitationRequest;
import com.sroadtutor.invitation.dto.CreateStudentInvitationRequest;
import com.sroadtutor.invitation.dto.InvitationLookupResponse;
import com.sroadtutor.invitation.dto.InvitationResponse;
import com.sroadtutor.invitation.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Owner-issued invitations.
 *
 * <p>Public endpoints (no JWT — token bearer is the auth):
 * <ul>
 *   <li>{@code GET /api/invitations/lookup/{token}} — pre-accept landing page.</li>
 *   <li>{@code POST /api/invitations/{token}/accept} — invitee sets password.</li>
 * </ul>
 * Both are whitelisted in {@link com.sroadtutor.config.SecurityConfig}.</p>
 */
@RestController
@Tag(name = "Invitations", description = "Owner-issued invitations for instructors / students / parents")
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    // ----- create -----

    @PostMapping("/api/schools/{schoolId}/invitations/instructor")
    @Operation(summary = "Invite an instructor to this school. OWNER of school only.")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> inviteInstructor(
            @PathVariable UUID schoolId,
            @Valid @RequestBody CreateInstructorInvitationRequest request
    ) {
        var resp = service.createInstructorInvitation(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @PostMapping("/api/schools/{schoolId}/invitations/student")
    @Operation(summary = "Invite a student to this school. OWNER or attached INSTRUCTOR.")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> inviteStudent(
            @PathVariable UUID schoolId,
            @Valid @RequestBody CreateStudentInvitationRequest request
    ) {
        var resp = service.createStudentInvitation(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @PostMapping("/api/schools/{schoolId}/invitations/parent")
    @Operation(summary = "Invite a parent to this school (optionally pre-link to students). OWNER only.")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> inviteParent(
            @PathVariable UUID schoolId,
            @Valid @RequestBody CreateParentInvitationRequest request
    ) {
        var resp = service.createParentInvitation(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    // ----- list / revoke / reissue -----

    @GetMapping("/api/schools/{schoolId}/invitations")
    @Operation(summary = "List invitations for a school. OWNER only. Filter by status optional.")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listForSchool(
            @PathVariable UUID schoolId,
            @RequestParam(name = "status", required = false) String status
    ) {
        var list = service.listForSchool(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                status);
        return ResponseEntity.ok(ApiResponse.of(
                list.stream().map(InvitationResponse::fromEntity).toList()));
    }

    @PostMapping("/api/invitations/{id}/revoke")
    @Operation(summary = "Revoke a pending invitation. OWNER who issued only.")
    public ResponseEntity<ApiResponse<InvitationResponse>> revoke(@PathVariable UUID id) {
        var inv = service.revoke(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(InvitationResponse.fromEntity(inv)));
    }

    @PostMapping("/api/invitations/{id}/reissue")
    @Operation(summary = "Generate a new token + refresh expiry. OWNER who issued only. TOKEN-mode only.")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> reissue(@PathVariable UUID id) {
        var resp = service.reissue(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    // ----- public (no JWT) -----

    @GetMapping("/api/invitations/lookup/{token}")
    @Operation(summary = "PUBLIC. Pre-accept landing — fetch basic invite info by token.")
    public ResponseEntity<ApiResponse<InvitationLookupResponse>> lookup(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.of(service.lookupByToken(token)));
    }

    @PostMapping("/api/invitations/{token}/accept")
    @Operation(summary = "PUBLIC. Accept a TOKEN-mode invitation by setting a password.")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(service.acceptByToken(token, request)));
    }
}
