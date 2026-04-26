package com.sroadtutor.instructor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed wrapper around the {@code instructors.working_hours_json} JSONB
 * column. Modelled as one or more {@link TimeRange}s per {@link DayOfWeek},
 * which lets the SPA represent split shifts (e.g. "Mon 09:00–12:00 + 14:00–18:00")
 * without sentinel values.
 *
 * <p>The DB column stays JSONB so we never have to migrate the schema when
 * the model evolves; the Java side provides {@link #toJson()} / {@link #fromJson(String)}
 * helpers so {@code InstructorService} can stay free of Jackson plumbing.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkingHours(Map<DayOfWeek, List<TimeRange>> schedule) {

    /** Empty schedule — useful seed when an instructor row is created before hours are set. */
    public static WorkingHours empty() {
        return new WorkingHours(new LinkedHashMap<>());
    }

    public record TimeRange(LocalTime start, LocalTime end) {
        public TimeRange {
            if (start == null || end == null) {
                throw new IllegalArgumentException("TimeRange start and end must both be supplied");
            }
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("TimeRange end (" + end + ") must be after start (" + start + ")");
            }
        }
    }

    // ------------------------------------------------------------
    // JSON marshalling
    // ------------------------------------------------------------
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WorkingHours", e);
        }
    }

    public static WorkingHours fromJson(String json) {
        if (json == null || json.isBlank()) return empty();
        try {
            return MAPPER.readValue(json, WorkingHours.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid working_hours_json payload: " + e.getMessage(), e);
        }
    }
}
