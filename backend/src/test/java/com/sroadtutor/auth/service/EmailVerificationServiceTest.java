package com.sroadtutor.auth.service;

import com.sroadtutor.auth.dto.EmailVerifyConfirmResponse;
import com.sroadtutor.auth.dto.EmailVerifyResponse;
import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.EmailVerificationToken;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.EmailVerificationTokenRepository;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock EmailVerificationTokenRepository tokenRepo;
    @Mock UserRepository userRepo;

    @InjectMocks EmailVerificationService service;

    @Test
    void issue_succeedsForUnverifiedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@x.com")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).active(true).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findByUserIdAndConsumedAtIsNull(userId)).thenReturn(List.of());
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(inv -> {
            EmailVerificationToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        EmailVerifyResponse resp = service.issueForCurrentUser(userId);
        assertThat(resp.rawToken()).matches("^\\d{6}$");
        assertThat(resp.verifyUrlForDev()).contains(resp.rawToken());
        assertThat(resp.expiresAt()).isAfter(Instant.now());
        assertThat(resp.reissued()).isFalse();
    }

    @Test
    void issue_revokesPriorActiveTokensAndFlagsReissued() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@x.com")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).active(true).build();
        EmailVerificationToken old = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(userId).tokenHash("OLD")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findByUserIdAndConsumedAtIsNull(userId)).thenReturn(List.of(old));
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(inv -> {
            EmailVerificationToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        EmailVerifyResponse resp = service.issueForCurrentUser(userId);
        assertThat(resp.reissued()).isTrue();
        // 1 save for the old (consumed=now), 1 save for the new token = 2 total
        verify(tokenRepo, times(2)).save(any(EmailVerificationToken.class));
        assertThat(old.getConsumedAt()).isNotNull();
    }

    @Test
    void issue_rejectsAlreadyVerifiedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@x.com")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).active(true)
                .emailVerifiedAt(Instant.now())
                .build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.issueForCurrentUser(userId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("ALREADY_VERIFIED"));
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void confirm_succeedsForFreshToken() {
        UUID userId = UUID.randomUUID();
        String raw = "123456";
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(userId)
                .tokenHash(EmailVerificationService.sha256Hex(raw))
                .rawToken(raw)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        User user = User.builder().id(userId).email("u@x.com")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).active(true).build();
        when(tokenRepo.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailVerifyConfirmResponse resp = service.confirm(raw);
        assertThat(resp.userId()).isEqualTo(userId);
        assertThat(resp.emailVerifiedAt()).isNotNull();
        assertThat(token.getConsumedAt()).isNotNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void confirm_rejectsExpiredToken() {
        String raw = "987654";
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .tokenHash(EmailVerificationService.sha256Hex(raw))
                .rawToken(raw)
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(tokenRepo.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm(raw))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("VERIFY_TOKEN_EXPIRED_OR_USED"));
    }

    @Test
    void confirm_rejectsConsumedToken() {
        String raw = "111222";
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .tokenHash(EmailVerificationService.sha256Hex(raw))
                .rawToken(raw)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumedAt(Instant.now().minusSeconds(30))
                .build();
        when(tokenRepo.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm(raw))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("VERIFY_TOKEN_EXPIRED_OR_USED"));
    }

    @Test
    void confirm_rejectsUnknownToken() {
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm("unknown"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INVALID_VERIFY_TOKEN"));
    }

    @Test
    void confirm_handlesAlreadyVerifiedUserGracefully() {
        String raw = "333444";
        UUID userId = UUID.randomUUID();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(userId)
                .tokenHash(EmailVerificationService.sha256Hex(raw))
                .rawToken(raw)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        User user = User.builder().id(userId).email("u@x.com")
                .role(Role.STUDENT).authProvider(AuthProvider.LOCAL).active(true)
                .emailVerifiedAt(Instant.now().minusSeconds(10))
                .build();
        when(tokenRepo.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.confirm(raw))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("ALREADY_VERIFIED"));
        // Token should still be marked consumed so it can't be re-used.
        assertThat(token.getConsumedAt()).isNotNull();
    }
}
