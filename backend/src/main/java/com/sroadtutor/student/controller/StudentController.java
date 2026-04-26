package com.sroadtutor.student.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.student.dto.AddStudentRequest;
import com.sroadtutor.student.dto.LinkParentRequest;
import com.sroadtutor.student.dto.StudentResponse;
import com.sroadtutor.student.dto.StudentUpdateRequest;
import com.sroadtutor.student.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Students CRUD + parent linkage. Add-by-owner is the marquee endpoint —
 * a single POST that lands a User row, a Student row, and (optionally) a
 * Parent user + link in one transaction.
 */
@RestController
@Tag(name = "Students", description = "Student profiles, package tracking, and parent linkage")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    // ----- create -----

    @PostMapping("/api/schools/{schoolId}/students")
    @Operation(summary = "Add a student to the school. Creates the student User + Student row + optional Parent link.")
    public ResponseEntity<ApiResponse<StudentResponse>> addToSchool(
            @PathVariable UUID schoolId,
            @Valid @RequestBody AddStudentRequest request
    ) {
        var resp = service.addByOwner(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    // ----- reads -----

    @GetMapping("/api/schools/{schoolId}/students")
    @Operation(summary = "List students at a school. OWNER sees all; INSTRUCTOR sees only their assigned students.")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> listForSchool(@PathVariable UUID schoolId) {
        var list = service.listForSchool(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                schoolId);
        return ResponseEntity.ok(ApiResponse.of(list));
    }

    @GetMapping("/api/students/me")
    @Operation(summary = "Get the caller's own student profile (role=STUDENT).")
    public ResponseEntity<ApiResponse<StudentResponse>> getMine() {
        return ResponseEntity.ok(ApiResponse.of(
                service.getMine(SecurityUtil.currentUserId())));
    }

    @GetMapping("/api/students/{id}")
    @Operation(summary = "Get a student by id. Scope: OWNER, assigned INSTRUCTOR, the student themselves, or a linked PARENT.")
    public ResponseEntity<ApiResponse<StudentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(
                service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id)));
    }

    // ----- update -----

    @PutMapping("/api/students/{id}")
    @Operation(summary = "Patch student fields. OWNER of school or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<StudentResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody StudentUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                service.update(
                        SecurityUtil.currentRole(),
                        SecurityUtil.currentUserId(),
                        id,
                        request)));
    }

    // ----- parent linkage -----

    @PostMapping("/api/students/{id}/parents")
    @Operation(summary = "Link a parent to a student (find-or-create by email).")
    public ResponseEntity<ApiResponse<StudentResponse>> linkParent(
            @PathVariable UUID id,
            @Valid @RequestBody LinkParentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                service.linkParent(
                        SecurityUtil.currentRole(),
                        SecurityUtil.currentUserId(),
                        id,
                        request)));
    }

    @DeleteMapping("/api/students/{id}/parents/{parentUserId}")
    @Operation(summary = "Remove a parent link (does not delete the parent user).")
    public ResponseEntity<Void> unlinkParent(
            @PathVariable UUID id,
            @PathVariable UUID parentUserId
    ) {
        service.unlinkParent(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id,
                parentUserId);
        return ResponseEntity.noContent().build();
    }
}
