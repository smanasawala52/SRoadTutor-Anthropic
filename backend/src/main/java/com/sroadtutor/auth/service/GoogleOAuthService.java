package com.sroadtutor.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies a Google ID token by calling Google's public-keys endpoint
 * and checking signature + audience + expiry.  Returns the user's
 * {@code sub} (stable id) and email.
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(AppProperties props) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(props.oauth().google().clientId()))
                .build();
    }

    /** Package-private ctor for unit tests — inject a mock verifier. */
    GoogleOAuthService(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    public OAuthVerifier verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID token verification failed");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return OAuthVerifier.of(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name")
            );
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("Google ID token verify error: {}", ex.getMessage());
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Could not verify Google ID token");
        }
    }
}
