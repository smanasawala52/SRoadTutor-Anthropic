package com.sroadtutor.school.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.school.dto.SchoolCreateRequest;
import com.sroadtutor.school.dto.SchoolMeResponse;
import com.sroadtutor.school.dto.SchoolResponse;
import com.sroadtutor.school.dto.SchoolUpdateRequest;
import com.sroadtutor.school.service.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Schools resource. The dominant SPA call is {@code GET /me} — every
 * authenticated user fetches their tenant's basic info on app boot. The
 * fuller detail endpoints are OWNER-only.
 */
@RestController
@RequestMapping("/api/schools")
@Tag(name = "Schools", description = "Driving-school tenants — create / read / update / deactivate")
public class SchoolController {

    private final SchoolService service;

    public SchoolController(SchoolService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create the OWNER's school. One school per owner.")
    public ResponseEntity<ApiResponse<SchoolResponse>> create(
            @Valid @RequestBody SchoolCreateRequest request
    ) {
        var school = service.createForCurrentOwner(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(SchoolResponse.fromEntity(school)));
    }

    @GetMapping("/me")
    @Operation(summary = "Lean projection of the caller's school. Any authenticated role.")
    public ResponseEntity<ApiResponse<SchoolMeResponse>> getMine() {
        var school = service.getMine(SecurityUtil.currentUserId());
        return ResponseEntity.ok(ApiResponse.of(SchoolMeResponse.fromEntity(school)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full school detail. OWNER of this school only.")
    public ResponseEntity<ApiResponse<SchoolResponse>> get(@PathVariable UUID id) {
        var school = service.getById(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(SchoolResponse.fromEntity(school)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Patch school fields. OWNER of this school only.")
    public ResponseEntity<ApiResponse<SchoolResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolUpdateRequest request
    ) {
        var school = service.update(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id,
                request);
        return ResponseEntity.ok(ApiResponse.of(SchoolResponse.fromEntity(school)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Soft-delete the school (sets is_active = false). Idempotent.")
    public ResponseEntity<ApiResponse<SchoolResponse>> deactivate(@PathVariable UUID id) {
        var school = service.deactivate(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(SchoolResponse.fromEntity(school)));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reverse a deactivate. Idempotent.")
    public ResponseEntity<ApiResponse<SchoolResponse>> reactivate(@PathVariable UUID id) {
        var school = service.reactivate(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(SchoolResponse.fromEntity(school)));
    }
}
