package com.sroadtutor.auth.service;

import com.sroadtutor.auth.model.RefreshToken;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.RefreshTokenRepository;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues refresh tokens (opaque random strings), stores their hash, and
 * rotates them on every use.  Rotation means: every time a refresh token
 * is exchanged for a new access token, a NEW refresh token is issued and
 * the old one is marked revoked.  That limits the damage of a stolen
 * token to the window between the theft and the victim's next refresh.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties props) {
        this.repository = repository;
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTokenExpirationDays());
    }

    /**
     * Creates a new refresh token for the user.  Returns the plaintext
     * token string that the client should store — the DB only holds a hash.
     */
    @Transactional
    public String issueFor(User user, HttpServletRequest request) {
        String rawToken = randomToken();
        RefreshToken entity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshTtl))
                .userAgent(truncate(headerOrNull(request, "User-Agent"), 512))
                .ipAddress(truncate(clientIp(request), 64))
                .build();
        repository.save(entity);
        return rawToken;
    }

    /**
     * Validates the supplied refresh token, revokes it (rotation), and
     * returns the user id it was issued for.  Throws if invalid or reused.
     */
    @Transactional
    public UUID consumeAndRotate(String rawToken) {
        RefreshToken stored = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token not recognised"));

        if (!stored.isActive(Instant.now())) {
            // Reuse detection: revoke all tokens for this user as a safety net.
            repository.revokeAllForUser(stored.getUserId(), Instant.now());
            throw new UnauthorizedException("REFRESH_TOKEN_REVOKED", "Refresh token is expired or revoked");
        }

        stored.setRevokedAt(Instant.now());
        repository.save(stored);
        return stored.getUserId();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                repository.save(rt);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String headerOrNull(HttpServletRequest req, String name) {
        return req == null ? null : req.getHeader(name);
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First entry is the original client.
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
