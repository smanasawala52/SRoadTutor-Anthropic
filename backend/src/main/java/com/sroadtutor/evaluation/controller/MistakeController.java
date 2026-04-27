package com.sroadtutor.evaluation.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.evaluation.dto.LogMistakeRequest;
import com.sroadtutor.evaluation.dto.MistakeCategoryResponse;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.dto.SessionMistakeResponse;
import com.sroadtutor.evaluation.service.MistakeLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Mistakes", description = "SGI mistake logger + readiness score")
public class MistakeController {

    private final MistakeLogService service;

    public MistakeController(MistakeLogService service) {
        this.service = service;
    }

    @GetMapping("/api/mistakes/categories/{jurisdiction}")
    @Operation(summary = "List active mistake categories for a jurisdiction (SGI/ICBC/MTO/DMV).")
    public ResponseEntity<ApiResponse<List<MistakeCategoryResponse>>> categories(
            @PathVariable String jurisdiction
    ) {
        return ResponseEntity.ok(ApiResponse.of(service.listCategories(jurisdiction)));
    }

    @PostMapping("/api/sessions/{sessionId}/mistakes")
    @Operation(summary = "Log a mistake during a SCHEDULED or COMPLETED lesson. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<SessionMistakeResponse>> log(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LogMistakeRequest request
    ) {
        var resp = service.log(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @GetMapping("/api/sessions/{sessionId}/mistakes")
    @Operation(summary = "List mistakes for a session. Any participant.")
    public ResponseEntity<ApiResponse<List<SessionMistakeResponse>>> listForSession(
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                service.listForSession(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), sessionId)));
    }

    @GetMapping("/api/students/{id}/mistakes")
    @Operation(summary = "Full mistake history for a student.")
    public ResponseEntity<ApiResponse<List<SessionMistakeResponse>>> listForStudent(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(
                service.listForStudent(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id)));
    }

    @GetMapping("/api/students/{id}/readiness-score")
    @Operation(summary = "Compute the cumulative readiness score for a student over the last 5 sessions.")
    public ResponseEntity<ApiResponse<ReadinessScoreResponse>> readiness(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(
                service.readinessForStudent(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id)));
    }
}
