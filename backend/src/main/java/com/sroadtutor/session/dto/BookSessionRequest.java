package com.sroadtutor.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Body for {@code POST /api/sessions} — book a new lesson.
 *
 * <p>{@code forceOutsideHours} is OWNER-only and lets the booking go through
 * even if {@code scheduledAt} falls outside the instructor's
 * {@code working_hours_json} window. Instructor / student bookings always
 * fail-closed on hours.</p>
 */
public record BookSessionRequest(
        @NotNull UUID instructorId,
        @NotNull UUID studentId,
        @NotNull Instant scheduledAt,

        @Min(15) @Max(360) Integer durationMins,

        @Size(max = 500) String location,
        @Size(max = 4000) String notes,

        Boolean forceOutsideHours
) {}
