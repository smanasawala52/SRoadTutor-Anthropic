package com.sroadtutor.auth.service;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests — no Spring context, no DB. */
class JwtServiceTest {

    private static final String SECRET =
            "jwt-secret-for-unit-tests-must-be-at-least-64-bytes-long-abc-xyz-abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(SECRET, 15, 30, "sroadtutor-test"),
                new AppProperties.OAuth(
                        new AppProperties.OAuth.Google("cid", "csecret")),
                new AppProperties.Cors(java.util.List.of("*"), "GET,POST", "Authorization", true, 3600L)
        );
        jwtService = new JwtService(props);
    }

    @Test
    void generateAccessToken_encodesUserClaims() {
        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parseAndValidate(token);
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("INSTRUCTOR");
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo(user.getEmail());
        assertThat(claims.getIssuer()).isEqualTo("sroadtutor-test");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void extractors_returnCorrectTypes() {
        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseAndValidate(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(user.getId());
        assertThat(jwtService.extractRole(claims)).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void parseAndValidate_rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "different-secret-different-secret-different-secret-different-secret-xy".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("sroadtutor-test")
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(foreignToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void parseAndValidate_rejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("sroadtutor-test")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(expired))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void parseAndValidate_rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.parseAndValidate("not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void constructor_rejectsTooShortSecret() {
        AppProperties bad = new AppProperties(
                new AppProperties.Jwt("short", 15, 30, "iss"),
                new AppProperties.OAuth(
                        new AppProperties.OAuth.Google("cid", "csec")),
                new AppProperties.Cors(java.util.List.of("*"), "GET", "*", true, 3600)
        );
        assertThatThrownBy(() -> new JwtService(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private User sampleUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("instructor@test.com")
                .fullName("Tess Tutor")
                .role(Role.INSTRUCTOR)
                .authProvider(AuthProvider.LOCAL)
                .languagePref("en")
                .active(true)
                .build();
    }
}
