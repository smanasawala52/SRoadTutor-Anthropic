package com.sroadtutor.exception;

/** 400 — the request itself is malformed or business rules reject it. */
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
