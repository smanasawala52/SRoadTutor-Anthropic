package com.sroadtutor.marketplace.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.marketplace.dto.DealershipLeadResponse;
import com.sroadtutor.marketplace.dto.SubmitMatchmakerRequest;
import com.sroadtutor.marketplace.service.MatchmakerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marketplace")
@Tag(name = "Matchmaker", description = "First Car Matchmaker — parent-side intake")
public class MatchmakerController {

    private final MatchmakerService service;

    public MatchmakerController(MatchmakerService service) {
        this.service = service;
    }

    @PostMapping("/matchmaker")
    @Operation(summary = "PARENT submits the First Car Matchmaker form. One NEW lead per student (re-submit overwrites).")
    public ResponseEntity<ApiResponse<DealershipLeadResponse>> submit(
            @Valid @RequestBody SubmitMatchmakerRequest request
    ) {
        var resp = service.submit(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @GetMapping("/matchmaker/me")
    @Operation(summary = "PARENT lists their own submitted leads.")
    public ResponseEntity<ApiResponse<List<DealershipLeadResponse>>> myLeads() {
        return ResponseEntity.ok(ApiResponse.of(
                service.myLeads(SecurityUtil.currentRole(), SecurityUtil.currentUserId())));
    }
}
