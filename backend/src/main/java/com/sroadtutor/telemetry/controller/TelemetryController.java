package com.sroadtutor.telemetry.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.telemetry.dto.AttachTelemetryRequest;
import com.sroadtutor.telemetry.dto.TelemetryDatasetSummary;
import com.sroadtutor.telemetry.dto.TelemetryEventResponse;
import com.sroadtutor.telemetry.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry (Phase 3)", description = "Vehicle telemetry attached to logged mistakes")
public class TelemetryController {

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @PostMapping("/mistakes/{sessionMistakeId}/events")
    @Operation(summary = "Attach a telemetry snapshot to a logged mistake. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<TelemetryEventResponse>> attach(
            @PathVariable UUID sessionMistakeId,
            @Valid @RequestBody AttachTelemetryRequest request
    ) {
        var resp = service.attach(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(),
                sessionMistakeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @GetMapping("/mistakes/{sessionMistakeId}/events")
    @Operation(summary = "List telemetry events for a mistake. Same scope as attach.")
    public ResponseEntity<ApiResponse<List<TelemetryEventResponse>>> listForMistake(
            @PathVariable UUID sessionMistakeId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                service.listForMistake(SecurityUtil.currentRole(), SecurityUtil.currentUserId(),
                        sessionMistakeId)));
    }

    @GetMapping("/dataset/summary")
    @Operation(summary = "AV-research dataset summary (counts only). OWNER only in V1; B2B API key gate is TD.")
    public ResponseEntity<ApiResponse<TelemetryDatasetSummary>> datasetSummary() {
        return ResponseEntity.ok(ApiResponse.of(
                service.datasetSummary(SecurityUtil.currentRole())));
    }
}
