package com.sroadtutor.insurance.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.insurance.dto.InsuranceBrokerResponse;
import com.sroadtutor.insurance.dto.InsuranceLeadResponse;
import com.sroadtutor.insurance.repository.InsuranceBrokerRepository;
import com.sroadtutor.insurance.service.InsuranceLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Insurance lead endpoints — owner-facing reads + status transitions.
 * Brokers are admin-seeded for V1; an admin-side broker-CRUD module is
 * tracked as TD.
 */
@RestController
@RequestMapping("/api/insurance")
@Tag(name = "Insurance", description = "Insurance broker leads — graduation-driven routing")
public class InsuranceLeadController {

    private final InsuranceLeadService service;
    private final InsuranceBrokerRepository brokerRepo;

    public InsuranceLeadController(InsuranceLeadService service,
                                     InsuranceBrokerRepository brokerRepo) {
        this.service = service;
        this.brokerRepo = brokerRepo;
    }

    @GetMapping("/brokers")
    @Operation(summary = "List active insurance brokers. Any authenticated role.")
    public ResponseEntity<ApiResponse<List<InsuranceBrokerResponse>>> activeBrokers() {
        var list = brokerRepo.findByActiveTrue().stream()
                .map(InsuranceBrokerResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @GetMapping("/schools/{schoolId}/leads")
    @Operation(summary = "List insurance leads for a school. OWNER of school only.")
    public ResponseEntity<ApiResponse<List<InsuranceLeadResponse>>> schoolLeads(@PathVariable UUID schoolId) {
        var list = service.listLeadsForOwnerSchool(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), schoolId);
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @PostMapping("/leads/{id}/quote")
    @Operation(summary = "Mark a ROUTED lead as QUOTED — bounty trigger. OWNER only.")
    public ResponseEntity<ApiResponse<InsuranceLeadResponse>> markQuoted(@PathVariable UUID id) {
        var l = service.markQuoted(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(InsuranceLeadResponse.fromEntity(l)));
    }

    @PostMapping("/leads/{id}/convert")
    @Operation(summary = "Mark a QUOTED lead as CONVERTED. OWNER only.")
    public ResponseEntity<ApiResponse<InsuranceLeadResponse>> markConverted(@PathVariable UUID id) {
        var l = service.markConverted(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(InsuranceLeadResponse.fromEntity(l)));
    }

    @PostMapping("/leads/{id}/dead")
    @Operation(summary = "Mark a non-CONVERTED lead as DEAD (lost). OWNER only.")
    public ResponseEntity<ApiResponse<InsuranceLeadResponse>> markDead(@PathVariable UUID id) {
        var l = service.markDead(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(InsuranceLeadResponse.fromEntity(l)));
    }
}
