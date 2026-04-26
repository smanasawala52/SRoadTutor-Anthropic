package com.sroadtutor.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/students/{id}/parents} — link a parent to an
 * existing student. Same email lookup / auto-create semantics as
 * {@code AddStudentRequest}'s parent fields.
 */
public record LinkParentRequest(
        @NotBlank @Email @Size(max = 254) String parentEmail,
        @Size(max = 200)                  String parentFullName,
        @Size(max = 32)                   String relationship
) {}
