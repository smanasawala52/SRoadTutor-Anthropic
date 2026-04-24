package com.sroadtutor.auth.dto;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;

import java.util.UUID;

/** Standard response for any auth endpoint that logs a user in. */
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
            String fullName,
            String phone,
            Role role,
            AuthProvider authProvider,
            String languagePref,
            boolean emailVerified
    ) {}
}
