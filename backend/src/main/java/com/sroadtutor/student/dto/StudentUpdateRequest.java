package com.sroadtutor.student.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code PUT /api/students/{id}}. All fields nullable; service applies
 * a field if non-null. The owning {@code userId} and {@code schoolId} are
 * immutable (re-create to move).
 */
public record StudentUpdateRequest(
        UUID instructorId,

        @Min(0) Integer packageTotalLessons,
        @Min(0) Integer lessonsRemaining,

        @Pattern(regexp = "^(ACTIVE|PASSED|DROPPED)$",
                message = "status must be one of ACTIVE, PASSED, DROPPED")
        String status,

        LocalDate roadTestDate
) {}
