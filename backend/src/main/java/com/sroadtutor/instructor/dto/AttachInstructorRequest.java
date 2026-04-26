package com.sroadtutor.instructor.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code POST /api/schools/{schoolId}/instructors/{instructorId}/attach}.
 * The role at the school is enum-like; we accept the three values an owner can set.
 */
public record AttachInstructorRequest(
        @Pattern(regexp = "^(OWNER|HEAD|REGULAR)$",
                message = "roleAtSchool must be one of OWNER, HEAD, REGULAR")
        String roleAtSchool
) {}
