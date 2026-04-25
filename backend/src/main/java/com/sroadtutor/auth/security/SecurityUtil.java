package com.sroadtutor.auth.security;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Tiny helpers around the Spring {@code SecurityContext} so controllers and
 * services don't have to know that the principal is a UUID and the authority
 * is {@code ROLE_<role>}. JwtAuthenticationFilter populates both — see
 * {@code com.sroadtutor.auth.filter.JwtAuthenticationFilter}.
 *
 * <p>Throws {@link UnauthorizedException} (401) — never null — when no
 * authenticated context exists, so callers don't have to null-check.</p>
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /** UUID of the authenticated caller. */
    public static UUID currentUserId() {
        Authentication auth = requireAuth();
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID id) {
            return id;
        }
        // Defensive — JwtAuthenticationFilter always sets a UUID, but a
        // misconfigured test or future filter shouldn't crash with ClassCast.
        try {
            return UUID.fromString(principal.toString());
        } catch (RuntimeException ex) {
            throw new UnauthorizedException(
                    "INVALID_PRINCIPAL",
                    "Authenticated principal is not a UUID");
        }
    }

    /** Role of the authenticated caller. */
    public static Role currentRole() {
        Authentication auth = requireAuth();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authority = ga.getAuthority();
            if (authority != null && authority.startsWith("ROLE_")) {
                try {
                    return Role.valueOf(authority.substring("ROLE_".length()));
                } catch (IllegalArgumentException ignored) {
                    // unknown role string — treat as missing
                }
            }
        }
        throw new UnauthorizedException(
                "MISSING_ROLE",
                "Authenticated context has no ROLE_* authority");
    }

    private static Authentication requireAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new UnauthorizedException(
                    "NOT_AUTHENTICATED",
                    "No authenticated user in security context");
        }
        return auth;
    }
}
