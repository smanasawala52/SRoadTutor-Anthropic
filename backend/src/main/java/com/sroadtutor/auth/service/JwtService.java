package com.sroadtutor.auth.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Mints and verifies the short-lived access JWT.
 *
 * <p>Refresh tokens are NOT JWTs — see {@link RefreshTokenService}. That
 * separation lets us revoke refresh tokens without paying a DB lookup on
 * every authenticated request.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_SCHOOL_ID = "schoolId";
    public static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(AppProperties props) {
        byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (~64 chars) for HS256. Use `openssl rand -base64 64`.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = props.jwt().issuer();
        this.accessTokenTtl = Duration.ofMinutes(props.jwt().accessTokenExpirationMinutes());
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .claims(Map.of(
                        CLAIM_ROLE, user.getRole().name(),
                        CLAIM_SCHOOL_ID, user.getSchoolId() == null ? "" : user.getSchoolId().toString(),
                        CLAIM_EMAIL, user.getEmail()
                ))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /**
     * Parse + validate signature + expiry.  Throws {@link UnauthorizedException}
     * on any failure so the caller doesn't have to know jjwt internals.
     */
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            throw new UnauthorizedException("INVALID_TOKEN", "Invalid or expired access token");
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }
}
