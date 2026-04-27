package com.sroadtutor.dashboard.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.dashboard.dto.DashboardResponse;
import com.sroadtutor.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Owner home-screen rollup")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/owner")
    @Operation(summary = "Single-shot rollup for the OWNER's home screen.")
    public ResponseEntity<ApiResponse<DashboardResponse>> ownerDashboard() {
        return ResponseEntity.ok(ApiResponse.of(
                service.getForCurrentOwner(SecurityUtil.currentRole(), SecurityUtil.currentUserId())));
    }
}
