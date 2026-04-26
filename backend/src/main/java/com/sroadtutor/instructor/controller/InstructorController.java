package com.sroadtutor.instructor.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.instructor.dto.AttachInstructorRequest;
import com.sroadtutor.instructor.dto.InstructorCreateRequest;
import com.sroadtutor.instructor.dto.InstructorResponse;
import com.sroadtutor.instructor.dto.InstructorUpdateRequest;
import com.sroadtutor.instructor.service.InstructorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Instructor profile + multi-school attach. Endpoints split between two
 * roots so the URL hierarchy mirrors ownership intent:
 * <ul>
 *   <li>{@code /api/instructors/*} — profile read/write (the instructor or
 *       a school OWNER they're attached to).</li>
 *   <li>{@code /api/schools/{schoolId}/instructors/*} — school-scoped
 *       attach / detach / roster (OWNER of that school).</li>
 * </ul>
 */
@RestController
@Tag(name = "Instructors", description = "Instructor profiles + multi-school attachment")
public class InstructorController {

    private final InstructorService service;

    public InstructorController(InstructorService service) {
        this.service = service;
    }

    // ----- profile (caller-centric) -----

    @PostMapping("/api/instructors/me")
    @Operation(summary = "Create the caller's instructor profile (self-register).")
    public ResponseEntity<ApiResponse<InstructorResponse>> createMe(
            @Valid @RequestBody InstructorCreateRequest request
    ) {
        var i = service.createForCurrentUser(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(InstructorResponse.fromEntity(i)));
    }

    @GetMapping("/api/instructors/me")
    @Operation(summary = "Get the caller's instructor profile.")
    public ResponseEntity<ApiResponse<InstructorResponse>> getMe() {
        var i = service.getMine(SecurityUtil.currentUserId());
        return ResponseEntity.ok(ApiResponse.of(InstructorResponse.fromEntity(i)));
    }

    @GetMapping("/api/instructors/{id}")
    @Operation(summary = "Get an instructor profile by id (self or attached-school OWNER).")
    public ResponseEntity<ApiResponse<InstructorResponse>> get(@PathVariable UUID id) {
        var i = service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(InstructorResponse.fromEntity(i)));
    }

    @PutMapping("/api/instructors/{id}")
    @Operation(summary = "Patch instructor profile (self or attached-school OWNER).")
    public ResponseEntity<ApiResponse<InstructorResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody InstructorUpdateRequest request
    ) {
        var i = service.update(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id,
                request);
        return ResponseEntity.ok(ApiResponse.of(InstructorResponse.fromEntity(i)));
    }

    @PostMapping("/api/instructors/{id}/deactivate")
    @Operation(summary = "Soft-delete an instructor profile (self or attached-school OWNER).")
    public ResponseEntity<ApiResponse<InstructorResponse>> deactivate(@PathVariable UUID id) {
        var i = service.deactivate(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        return ResponseEntity.ok(ApiResponse.of(InstructorResponse.fromEntity(i)));
    }

    // ----- school-scoped (attach / detach / list) -----

    @GetMapping("/api/schools/{schoolId}/instructors")
    @Operation(summary = "List active instructors at a school. OWNER of school or any instructor at school.")
    public ResponseEntity<ApiResponse<List<InstructorResponse>>> listForSchool(
            @PathVariable UUID schoolId
    ) {
        var list = service.listForSchool(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId);
        return ResponseEntity.ok(ApiResponse.of(
                list.stream().map(InstructorResponse::fromEntity).toList()));
    }

    @PostMapping("/api/schools/{schoolId}/instructors/{instructorId}/attach")
    @Operation(summary = "Attach an instructor to a school (M:N). OWNER of school only.")
    public ResponseEntity<Void> attach(
            @PathVariable UUID schoolId,
            @PathVariable UUID instructorId,
            @Valid @RequestBody(required = false) AttachInstructorRequest request
    ) {
        String roleAtSchool = request == null ? null : request.roleAtSchool();
        service.attachToSchool(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                instructorId,
                roleAtSchool);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/schools/{schoolId}/instructors/{instructorId}/detach")
    @Operation(summary = "Detach an instructor from a school (sets left_at). OWNER of school only.")
    public ResponseEntity<Void> detach(
            @PathVariable UUID schoolId,
            @PathVariable UUID instructorId
    ) {
        service.detachFromSchool(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                instructorId);
        return ResponseEntity.noContent().build();
    }
}
