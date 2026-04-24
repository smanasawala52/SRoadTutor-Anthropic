package com.sroadtutor.auth.model;

/**
 * The 4 roles described in the blueprint. Spring Security uses the
 * {@code ROLE_} prefix convention automatically via {@code hasRole("OWNER")}.
 */
public enum Role {
    OWNER,
    INSTRUCTOR,
    STUDENT,
    PARENT
}
