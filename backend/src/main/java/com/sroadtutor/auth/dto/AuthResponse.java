package com.sroadtutor.auth.dto;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;

import java.util.UUID;

/**
 * Standard response for any auth endpoint that logs a user in.
 *
 * <p>Phone numbers live on a separate resource ({@code /api/phone-numbers}, PR4)
 * — the auth response intentionally does not embed them so we don't fan a
 * 1..N relationship into a login envelope. Clients that need the user's primary
 * phone fetch it after login.</p>
 *
 * <p>{@code mustChangePassword} signals the SPA to redirect into the
 * password-rotation flow before doing anything else (D6, owner-pre-created
 * accounts using the {@code test123} dummy password).</p>
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        UserDto user
) {

    public record UserDto(
            UUID id,
            UUID schoolId,
            String email,
            String username,
            String fullName,
            Role role,
            AuthProvider authProvider,
            String languagePref,
            boolean emailVerified,
            boolean phoneVerified,
            boolean mustChangePassword
    ) {}
}
