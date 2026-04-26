package com.sroadtutor.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code POST /api/students} — owner or instructor creates a student
 * in their school. Atomic: User row + Student row + (optional) Parent linkage
 * all land in one transaction.
 *
 * <p>{@code parentEmail} is the magic field — when supplied, the service
 * either links to an existing PARENT user with that email or creates a new
 * one (dummy password, {@code mustChangePassword=true}).</p>
 *
 * <p>The student's {@code instructorId} is optional. When omitted, the
 * student is unassigned and an OWNER can route them later.</p>
 */
public record AddStudentRequest(
        @NotBlank @Email @Size(max = 254) String studentEmail,
        @NotBlank @Size(max = 200)        String studentFullName,

        @Size(max = 8)                    String languagePref,

        UUID                              instructorId,

        @Min(0)                           Integer packageTotalLessons,
        @Min(0)                           Integer lessonsRemaining,

        LocalDate                         roadTestDate,

        // ---- optional parent linkage on the same form ----
        @Email @Size(max = 254)           String parentEmail,
        @Size(max = 200)                  String parentFullName,
        @Size(max = 32)                   String parentRelationship
) {}
