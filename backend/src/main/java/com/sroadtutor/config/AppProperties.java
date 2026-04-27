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
        Cors cors,
        Stripe stripe
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

    /**
     * Stripe billing config (PR12.5). All fields are nullable / optional so the
     * app boots even when Stripe is not yet provisioned — services fall back
     * to an admin-mode no-op path when {@code secretKey} is blank.
     *
     * <p>{@code prices} maps a {@link com.sroadtutor.subscription.model.PlanTier}
     * name to a Stripe Price id (e.g. {@code price_1AbCDe...}). Missing entries
     * mean "no Stripe Checkout for this tier" and the upgrade endpoint will
     * fall back to admin-mode for those plans.</p>
     */
    public record Stripe(
            String secretKey,
            String webhookSecret,
            String successUrl,
            String cancelUrl,
            Prices prices
    ) {

        public record Prices(
                String pro,
                String growth,
                String enterprise
        ) {}
    }
}
