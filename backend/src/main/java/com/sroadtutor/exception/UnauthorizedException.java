package com.sroadtutor.exception;

/** 401 — caller is not authenticated, or their token is invalid/expired. */
public class UnauthorizedException extends RuntimeException {
    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
