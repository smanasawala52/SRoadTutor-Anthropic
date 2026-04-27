package com.sroadtutor.marketplace.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.marketplace.dto.DealershipLeadResponse;
import com.sroadtutor.marketplace.dto.DealershipResponse;
import com.sroadtutor.marketplace.dto.InstructorPayoutResponse;
import com.sroadtutor.marketplace.dto.MarkPayoutPaidRequest;
import com.sroadtutor.marketplace.repository.DealershipRepository;
import com.sroadtutor.marketplace.service.LeadRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lead routing + dealership reads + payouts. Owner-facing.
 */
@RestController
@RequestMapping("/api/marketplace")
@Tag(name = "Lead Routing", description = "Dealership routing + conversion + instructor payouts")
public class LeadRoutingController {

    private final LeadRoutingService service;
    private final DealershipRepository dealershipRepo;

    public LeadRoutingController(LeadRoutingService service, DealershipRepository dealershipRepo) {
        this.service = service;
        this.dealershipRepo = dealershipRepo;
    }

    @GetMapping("/dealerships")
    @Operation(summary = "List active dealerships. Any authenticated role.")
    public ResponseEntity<ApiResponse<List<DealershipResponse>>> activeDealerships() {
        var list = dealershipRepo.findByActiveTrue().stream()
                .map(DealershipResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @GetMapping("/schools/{schoolId}/leads")
    @Operation(summary = "List all leads for a school. OWNER of school only.")
    public ResponseEntity<ApiResponse<List<DealershipLeadResponse>>> schoolLeads(@PathVariable UUID schoolId) {
        var list = service.listLeadsForOwnerSchool(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), schoolId);
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @PostMapping("/leads/{id}/convert")
    @Operation(summary = "Mark a ROUTED lead as CONVERTED. Auto-creates instructor payout. OWNER only.")
    public ResponseEntity<ApiResponse<DealershipLeadResponse>> convert(@PathVariable UUID id) {
        var lead = service.markConverted(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(DealershipLeadResponse.fromEntity(lead)));
    }

    @GetMapping("/instructors/{instructorId}/payouts")
    @Operation(summary = "List payouts for an instructor. INSTRUCTOR (self) or OWNER (their school).")
    public ResponseEntity<ApiResponse<List<InstructorPayoutResponse>>> payoutsForInstructor(
            @PathVariable UUID instructorId
    ) {
        var list = service.myPayouts(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), instructorId).stream()
                .map(InstructorPayoutResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @PostMapping("/payouts/{id}/mark-paid")
    @Operation(summary = "Mark a PENDING payout as PAID. Records e-transfer ref. OWNER only.")
    public ResponseEntity<ApiResponse<InstructorPayoutResponse>> markPayoutPaid(
            @PathVariable UUID id,
            @Valid @RequestBody MarkPayoutPaidRequest request
    ) {
        var p = service.markPayoutPaid(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id, request.eTransferRef());
        return ResponseEntity.ok(ApiResponse.of(InstructorPayoutResponse.fromEntity(p)));
    }
}
