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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
                .body(ApiResponse.of(service.toResponse(s)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reschedule a SCHEDULED session. Re-validates conflicts + working hours.")
    public ResponseEntity<ApiResponse<SessionResponse>> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleSessionRequest request
    ) {
        var s = service.reschedule(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of(service.toResponse(s)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a SCHEDULED session. Idempotent. Any participant.")
    public ResponseEntity<ApiResponse<SessionResponse>> cancel(@PathVariable UUID id) {
        var s = service.cancel(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(service.toResponse(s)));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a SCHEDULED session COMPLETED. Decrements student.lessons_remaining. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<SessionResponse>> complete(@PathVariable UUID id) {
        var s = service.complete(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(service.toResponse(s)));
    }

    @PostMapping("/{id}/no-show")
    @Operation(summary = "Mark a SCHEDULED session NO_SHOW. Decrements student.lessons_remaining. OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<SessionResponse>> markNoShow(@PathVariable UUID id) {
        var s = service.markNoShow(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(service.toResponse(s)));
    }

    // ----- read -----

    @GetMapping("/{id}")
    @Operation(summary = "Get a session by id. Any participant.")
    public ResponseEntity<ApiResponse<SessionResponse>> get(@PathVariable UUID id) {
        var s = service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(service.toResponse(s)));
    }

    @GetMapping
    @Operation(summary = "Calendar list. Required: from + to (ISO-8601 instants or dates). OWNER must supply schoolId; PARENT must supply studentId.")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> calendar(
            @RequestParam(name = "from") String fromStr,
            @RequestParam(name = "to")   String toStr,
            @RequestParam(name = "schoolId",     required = false) UUID schoolId,
            @RequestParam(name = "instructorId", required = false) UUID instructorId,
            @RequestParam(name = "studentId",    required = false) UUID studentId
    ) {
        Instant from = parseDateOrInstant(fromStr, true);
        Instant to = parseDateOrInstant(toStr, false);

        var list = service.calendar(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId, instructorId, studentId,
                from, to);
        return ResponseEntity.ok(ApiResponse.of(
                list.stream().map(service::toResponse).toList()));
    }

    private Instant parseDateOrInstant(String val, boolean startOfDay) {
        if (val == null) return null;
        try {
            return Instant.parse(val);
        } catch (Exception e) {
            try {
                LocalDate date = LocalDate.parse(val);
                if (startOfDay) {
                    return date.atStartOfDay(ZoneId.of("UTC")).toInstant();
                } else {
                    return date.plusDays(1).atStartOfDay(ZoneId.of("UTC")).minusNanos(1).toInstant();
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid date format: " + val);
            }
        }
    }
}