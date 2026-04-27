package com.sroadtutor.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Body for {@code PUT /api/sessions/{id}} — reschedule. All fields nullable;
 * service applies a field if non-null. Conflict + working-hours checks re-run
 * against the new (instructor, scheduledAt, duration) tuple.
 */
public record RescheduleSessionRequest(
        UUID instructorId,
        Instant scheduledAt,

        @Min(15) @Max(360) Integer durationMins,

        @Size(max = 500) String location,
        @Size(max = 4000) String notes,

        Boolean forceOutsideHours
) {}
