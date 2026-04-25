package com.sroadtutor.exception;

/**
 * 400 — the request itself is malformed or business rules reject it.
 *
 * <p>The exception carries a machine-readable {@code code} and a human-readable
 * {@code message}. Both are surfaced into the {@link com.sroadtutor.common.ErrorResponse}
 * envelope by {@link GlobalExceptionHandler} — never combined into the message
 * itself, since duplicating the code in two response fields would just create
 * noise. Tests should assert against {@link #getCode()}, not message substrings.
 */
public class BadRequestException extends RuntimeException {
    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
