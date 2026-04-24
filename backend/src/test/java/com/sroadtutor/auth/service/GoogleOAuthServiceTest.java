package com.sroadtutor.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.sroadtutor.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOAuthServiceTest {

    @Test
    void verify_returnsVerifierOnSuccess() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-123");
        payload.setEmail("u@gmail.com");
        payload.set("name", "Test User");
        when(idToken.getPayload()).thenReturn(payload);
        when(verifier.verify(anyString())).thenReturn(idToken);

        GoogleOAuthService svc = new GoogleOAuthService(verifier);
        OAuthVerifier out = svc.verify("some-google-id-token");

        assertThat(out.providerUserId()).isEqualTo("sub-123");
        assertThat(out.email()).isEqualTo("u@gmail.com");
        assertThat(out.fullName()).isEqualTo("Test User");
    }

    @Test
    void verify_rejectsWhenTokenInvalid() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(anyString())).thenReturn(null);

        GoogleOAuthService svc = new GoogleOAuthService(verifier);
        assertThatThrownBy(() -> svc.verify("bad"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("verification failed");
    }

    @Test
    void verify_wrapsSecurityExceptions() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(anyString())).thenThrow(new java.io.IOException("network down"));

        GoogleOAuthService svc = new GoogleOAuthService(verifier);
        assertThatThrownBy(() -> svc.verify("bad"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
