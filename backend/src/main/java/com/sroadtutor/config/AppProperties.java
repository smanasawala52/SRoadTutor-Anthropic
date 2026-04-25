package com.sroadtutor.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strongly-typed bindings for everything under {@code app.*} in
 * application.yml.  Failing to set any {@code @NotBlank} field makes the
 * app refuse to start — giving you a loud, early failure instead of a
 * mysterious NPE at request time.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        OAuth oauth,
        Cors cors
) {

    public record Jwt(
            @NotBlank(message = "app.jwt.secret must be set (env: JWT_SECRET)") String secret,
            @Positive long accessTokenExpirationMinutes,
            @Positive long refreshTokenExpirationDays,
            @NotBlank String issuer
    ) {}

    public record OAuth(Google google) {
        public record Google(
                @NotBlank(message = "app.oauth.google.client-id must be set (env: GOOGLE_OAUTH_CLIENT_ID)") String clientId,
                @NotBlank(message = "app.oauth.google.client-secret must be set (env: GOOGLE_OAUTH_CLIENT_SECRET)") String clientSecret
        ) {}
    }

    public record Cors(
            List<String> allowedOrigins,
            String allowedMethods,
            String allowedHeaders,
            boolean allowCredentials,
            long maxAge
    ) {}
}
