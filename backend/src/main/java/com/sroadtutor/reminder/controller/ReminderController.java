package com.sroadtutor.reminder.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.reminder.dto.ReminderResponse;
import com.sroadtutor.reminder.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Reminder endpoints. Cron does the heavy lifting (PENDING row generation);
 * this controller exposes the manual-fire + read paths the SPA needs.
 */
@RestController
@RequestMapping("/api/reminders")
@Tag(name = "Reminders", description = "Pre-generated lesson reminders + manual fire")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    @GetMapping("/me/pending")
    @Operation(summary = "List PENDING reminders for the caller (a recipient). Driver of the SPA's 'reminders to send' panel.")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> listMyPending() {
        return ResponseEntity.ok(ApiResponse.of(
                service.listPendingForCurrentUser(SecurityUtil.currentUserId())));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "List all reminders for a session (any status). OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> listForSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.of(
                service.listForSession(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), sessionId)));
    }

    @PostMapping("/{id}/fire")
    @Operation(summary = "Mark a PENDING reminder as SENT. Creates a whatsapp_message_log row. Idempotent on already-SENT.")
    public ResponseEntity<ApiResponse<ReminderResponse>> fire(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(
                service.fire(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id)));
    }
}
