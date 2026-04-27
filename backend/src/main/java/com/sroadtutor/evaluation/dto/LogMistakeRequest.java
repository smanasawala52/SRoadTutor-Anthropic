package com.sroadtutor.evaluation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body for {@code POST /api/sessions/{sessionId}/mistakes} — instructor
 * logs a mistake during the lesson. The student id is derived from the
 * session — caller doesn't supply it.
 */
public record LogMistakeRequest(
        @NotNull UUID mistakeCategoryId,

        @Min(1) Integer count,

        @Size(max = 4000) String instructorNotes
) {}
