package com.sroadtutor.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope. {@code code} is a stable machine-readable key
 * (e.g. {@code EMAIL_ALREADY_EXISTS}); {@code message} is human-readable.
 * {@code fieldErrors} is populated for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Integer status,
        List<FieldError> fieldErrors,
        Instant timestamp
) {

    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(code, message, status, null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, int status, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, status, fieldErrors, Instant.now());
    }

    public record FieldError(String field, String message) {}
}
