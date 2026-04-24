package com.sroadtutor.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/refresh body. */
public record RefreshRequest(@NotBlank String refreshToken) {}
