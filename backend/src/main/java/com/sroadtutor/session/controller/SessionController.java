package com.sroadtutor.session.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.session.dto.BookSessionRequest;
import com.sroadtutor.session.dto.RescheduleSessionRequest;
import com.sroadtutor.session.dto.SessionResponse;
import com.sroadtutor.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lesson scheduling endpoints. Calendar reads at {@code /api/sessions} (with
 * date-range filters) cover OWNER / INSTRUCTOR / STUDENT / PARENT views;
 * action endpoints (book / reschedule / cancel / complete / no-show) are
 * scope-aware in the service.
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Lesson scheduling — book, reschedule, cancel, complete, no-show")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    // ----- write -----

    @PostMapping
    @Operation(summary = "Book a new lesson. Conflict + working-hours validated. OWNER/INSTRUCTOR/STUDENT scope-aware.")
    public ResponseEntity<ApiResponse<SessionResponse>> book(
            @Valid @RequestBody BookSessionRequest request
    ) {
        var s = service.book(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reschedule a SCHEDULED session. Re-validates conflicts + working hours.")
    public ResponseEntity<ApiResponse<SessionResponse>> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleSessionRequest request
    ) {
        var s = service.reschedule(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a SCHEDULED session. Idempotent. Any participant.")
    public ResponseEntity<ApiResponse<SessionResponse>> cancel(@PathVariable UUID id) {
        var s = service.cancel(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a SCHEDULED session COMPLETED. Decrements student.lessons_remaining. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<SessionResponse>> complete(@PathVariable UUID id) {
        var s = service.complete(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    @PostMapping("/{id}/no-show")
    @Operation(summary = "Mark a SCHEDULED session NO_SHOW. Decrements student.lessons_remaining. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<SessionResponse>> markNoShow(@PathVariable UUID id) {
        var s = service.markNoShow(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    // ----- read -----

    @GetMapping("/{id}")
    @Operation(summary = "Get a session by id. Any participant.")
    public ResponseEntity<ApiResponse<SessionResponse>> get(@PathVariable UUID id) {
        var s = service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(SessionResponse.fromEntity(s)));
    }

    @GetMapping
    @Operation(summary = "Calendar list. Required: from + to (ISO-8601 instants). OWNER must supply schoolId; PARENT must supply studentId.")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> calendar(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "schoolId",     required = false) UUID schoolId,
            @RequestParam(name = "instructorId", required = false) UUID instructorId,
            @RequestParam(name = "studentId",    required = false) UUID studentId
    ) {
        var list = service.calendar(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId, instructorId, studentId,
                from, to);
        return ResponseEntity.ok(ApiResponse.of(
                list.stream().map(SessionResponse::fromEntity).toList()));
    }
}
