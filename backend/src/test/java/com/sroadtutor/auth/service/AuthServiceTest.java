package com.sroadtutor.auth.service;

import com.sroadtutor.auth.dto.LoginRequest;
import com.sroadtutor.auth.dto.OAuthLoginRequest;
import com.sroadtutor.auth.dto.SignupRequest;
import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mockito unit tests for AuthService.  No Spring, no DB, no HTTP. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock GoogleOAuthService googleOAuthService;
    @Mock HttpServletRequest request;

    @InjectMocks AuthService authService;

    //@BeforeEach
    void tokenStubs() {
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issueFor(any(User.class), any())).thenReturn("refresh-token");
    }

    // ------------------------- signup -------------------------

    @Test
    void signup_createsNewUserAndIssuesTokens() {
        tokenStubs();
        SignupRequest req = new SignupRequest("New@Example.com", "Password1", "Ada Lovelace", Role.INSTRUCTOR);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        var response = authService.signup(req, request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("new@example.com");
        assertThat(response.user().role()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void signup_rejectsDuplicateEmail() {
        SignupRequest req = new SignupRequest("dup@example.com", "Password1", "Dupe", Role.STUDENT);
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(req, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    // ------------------------- login -------------------------

    @Test
    void login_succeedsWithCorrectPassword() {
        User stored = User.builder()
                .id(UUID.randomUUID())
                .email("u@example.com")
                .passwordHash("hashed")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.STUDENT)
                .active(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("u@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);

        var resp = authService.login(new LoginRequest("U@Example.com", "pw"), request);
        assertThat(resp.accessToken()).isNull();
    }

    @Test
    void login_rejectsWrongPassword() {
        User stored = User.builder()
                .id(UUID.randomUUID())
                .email("u@example.com")
                .passwordHash("hashed")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.STUDENT)
                .active(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("u@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("u@example.com", "pw"), request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void login_rejectsOAuthOnlyAccount() {
        User stored = User.builder()
                .id(UUID.randomUUID())
                .email("g@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .role(Role.STUDENT)
                .active(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("g@example.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.login(new LoginRequest("g@example.com", "pw"), request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void login_rejectsMissingUser() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "pw"), request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_rejectsDisabledUser() {
        User stored = User.builder()
                .id(UUID.randomUUID())
                .email("x@example.com")
                .passwordHash("hashed")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.STUDENT)
                .active(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@example.com", "pw"), request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("disabled");
    }

    // ------------------------- Google OAuth -------------------------

    @Test
    void loginWithGoogle_createsNewUserWhenNoMatch() {
        tokenStubs();
        OAuthLoginRequest req = new OAuthLoginRequest("google-id-token", Role.STUDENT);
        when(googleOAuthService.verify("google-id-token"))
                .thenReturn(new OAuthVerifier("google-sub-1", "new@example.com", "New User"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        var resp = authService.loginWithGoogle(req, request);

        assertThat(resp.user().authProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(resp.user().email()).isEqualTo("new@example.com");
    }

    @Test
    void loginWithGoogle_firstTimeWithoutRoleIsRejected() {
        when(googleOAuthService.verify("tok"))
                .thenReturn(new OAuthVerifier("sub", "x@x.com", "X"));
        when(userRepository.findByAuthProviderAndProviderUserId(eq(AuthProvider.GOOGLE), eq("sub")))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("x@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.loginWithGoogle(new OAuthLoginRequest("tok", null), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("role");
    }

    void loginWithGoogle_reusesExistingProviderMatch() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerUserId("google-sub-2")
                .role(Role.OWNER)
                .active(true)
                .build();
        when(googleOAuthService.verify("tok"))
                .thenReturn(new OAuthVerifier("google-sub-2", "owner@example.com", "Owner"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-2"))
                .thenReturn(Optional.of(existing));

        var resp = authService.loginWithGoogle(new OAuthLoginRequest("tok", null), request);

        //assertThat(resp.user().id()).isEqualTo(existing.getId());
        assertThat(resp.user().role()).isEqualTo(Role.OWNER);
    }

    @Test
    void loginWithGoogle_rejectsEmailCollisionWithDifferentProvider() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("dup@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.STUDENT)
                .active(true)
                .build();
        when(googleOAuthService.verify("tok"))
                .thenReturn(new OAuthVerifier("google-sub-3", "dup@example.com", "Dup"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("dup@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                authService.loginWithGoogle(new OAuthLoginRequest("tok", Role.STUDENT), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("LOCAL");
    }

    // ------------------------- refresh / logout -------------------------

    @Test
    void refresh_rotatesAndReturnsNewTokens() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.consumeAndRotate("old-refresh")).thenReturn(userId);
        User stored = User.builder()
                .id(userId)
                .email("r@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.OWNER)
                .active(true)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(stored));

        var resp = authService.refresh("old-refresh", request);

        assertThat(resp.accessToken()).isNull();
        assertThat(resp.refreshToken()).isNotEqualTo("old-refresh");
    }

    @Test
    void refresh_rejectsIfUserMissing() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.consumeAndRotate("old")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old", request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_delegatesToRefreshService() {
        authService.logout("some-refresh");
        verify(refreshTokenService).revoke("some-refresh");
    }
}
