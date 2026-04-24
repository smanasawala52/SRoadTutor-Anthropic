package com.sroadtutor.exception;

/** 404 — a lookup by id/slug returned no row. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
