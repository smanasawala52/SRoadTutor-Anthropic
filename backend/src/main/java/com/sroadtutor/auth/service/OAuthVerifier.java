package com.sroadtutor.auth.service;

/**
 * Result of verifying a provider token.  Always contains a stable provider
 * user id; email and name are best-effort (depending on the provider's
 * granted scopes).
 */
public record OAuthVerifier(String providerUserId, String email, String fullName) {

    public static OAuthVerifier of(String providerUserId, String email, String fullName) {
        return new OAuthVerifier(providerUserId, email, fullName);
    }
}
