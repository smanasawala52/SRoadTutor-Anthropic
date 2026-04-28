package com.sroadtutor.risk.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.risk.dto.RiskAggregateResponse;
import com.sroadtutor.risk.dto.RiskScoreResponse;
import com.sroadtutor.risk.service.RiskScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 3 risk-score endpoints. V1 ships OWNER-only access; the B2B API
 * key gate for insurer-licensed reads is tracked as TD.
 */
@RestController
@RequestMapping("/api/risk")
@Tag(name = "Risk Score (Phase 3)", description = "Anonymized new-driver risk scores + aggregate")
public class RiskScoreController {

    private final RiskScoreService service;

    public RiskScoreController(RiskScoreService service) {
        this.service = service;
    }

    @PostMapping("/scores/students/{studentId}/generate")
    @Operation(summary = "Generate (or re-generate) the anonymized risk score for a student. OWNER only.")
    public ResponseEntity<ApiResponse<RiskScoreResponse>> generate(@PathVariable UUID studentId) {
        var resp = service.generateForStudent(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), studentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @GetMapping("/scores/{hash}")
    @Operation(summary = "Read a risk score by anonymized hash. OWNER only in V1; B2B API key gate is TD.")
    public ResponseEntity<ApiResponse<RiskScoreResponse>> get(@PathVariable String hash) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getByHash(SecurityUtil.currentRole(), hash)));
    }

    @GetMapping("/aggregate")
    @Operation(summary = "Tier-distribution aggregate across the platform. No student-level detail.")
    public ResponseEntity<ApiResponse<RiskAggregateResponse>> aggregate() {
        return ResponseEntity.ok(ApiResponse.of(
                service.aggregate(SecurityUtil.currentRole())));
    }
}
