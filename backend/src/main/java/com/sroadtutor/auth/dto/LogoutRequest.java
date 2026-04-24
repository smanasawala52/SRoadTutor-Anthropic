package com.sroadtutor.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/logout body — revokes the given refresh token server-side. */
public record LogoutRequest(@NotBlank String refreshToken) {}
