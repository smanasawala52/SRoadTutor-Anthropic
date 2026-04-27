package com.sroadtutor.auth.service;

import com.sroadtutor.auth.model.RefreshToken;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.RefreshTokenRepository;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 15, 30, "iss"),
                new AppProperties.OAuth(
                        new AppProperties.OAuth.Google("cid", "csec")),
                new AppProperties.Cors(List.of("*"), "GET", "Authorization", true, 3600L),null);
        service = new RefreshTokenService(repository, props);
    }

    @Test
    void issueFor_persistsHashedTokenAndReturnsRaw() {
        User user = User.builder().id(UUID.randomUUID()).email("u@e.com").build();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("User-Agent")).thenReturn("JUnit");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        String raw = service.issueFor(user, req);

        assertThat(raw).isNotBlank();
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void consumeAndRotate_happyPath() {
        String raw = "unit-test-raw-token";
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash(RefreshTokenService.hash(raw))
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(repository.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(stored));

        UUID returned = service.consumeAndRotate(raw);

        assertThat(returned).isEqualTo(stored.getUserId());
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    void consumeAndRotate_rejectsUnknown() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeAndRotate("anything"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not recognised");
    }

    @Test
    void consumeAndRotate_reuseDetectionRevokesAllForUser() {
        String raw = "expired-or-reused";
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(RefreshTokenService.hash(raw))
                .issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(repository.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.consumeAndRotate(raw))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllForUser(eq(userId), any());
    }

    @Test
    void revoke_marksActiveTokenRevoked() {
        String raw = "revokeme";
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash(RefreshTokenService.hash(raw))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1000))
                .build();
        when(repository.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(stored));

        service.revoke(raw);

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(repository).save(stored);
    }

    @Test
    void revoke_silentOnUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("nothing");  // no throw
    }

    @Test
    void hash_isStable() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo(RefreshTokenService.hash("abc"))
                .hasSize(64);
    }
}
