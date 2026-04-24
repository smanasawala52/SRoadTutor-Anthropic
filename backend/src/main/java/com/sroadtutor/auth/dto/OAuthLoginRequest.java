package com.sroadtutor.auth.dto;

import com.sroadtutor.auth.model.Role;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /auth/google and /auth/facebook body.
 *
 * <p>Client sends the provider-issued {@code token} after completing the
 * native OAuth flow.  Google → ID token (JWT).  Facebook → access token.
 * Backend verifies it upstream before issuing our own JWT.</p>
 *
 * <p>{@code role} is only honoured on first-time signup via OAuth.  For an
 * existing user, their stored role wins regardless of what the client sends.</p>
 */
public record OAuthLoginRequest(
        @NotBlank String token,
        Role role
) {}
