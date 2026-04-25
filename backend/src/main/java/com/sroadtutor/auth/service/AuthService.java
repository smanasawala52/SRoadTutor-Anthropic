package com.sroadtutor.auth.service;

import com.sroadtutor.auth.dto.AuthResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the 4 entry points of auth: signup, login, OAuth (Google),
 * refresh.  All heavy work (BCrypt, JWT, DB) is delegated to the dedicated services;
 * this class is the place to look for the business rules.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleOAuthService googleOAuthService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       GoogleOAuthService googleOAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.googleOAuthService = googleOAuthService;
    }

    // ===========================================================
    // Email + password
    // ===========================================================

    @Transactional
    public AuthResponse signup(SignupRequest request, HttpServletRequest http) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("EMAIL_ALREADY_EXISTS", "An account already exists for this email");
        }

        // username is auto-generated server-side until D7 surfaces a chooser; keep it
        // deterministic-but-unique by combining the email's local-part with a short
        // UUID suffix. Service layer always lowercases (uniqueness is on LOWER(username)).
        String username = generateInitialUsername(email);
        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .build();
        user = userRepository.save(user);
        log.info("New local-account user signup: {} as {}", user.getEmail(), user.getRole());
        return issueTokens(user, http);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest http) {
        String email = normaliseEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect"));

        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new UnauthorizedException("USE_OAUTH_LOGIN",
                    "This account was created via " + user.getAuthProvider() + ". Log in with that provider instead.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_DISABLED", "This account has been disabled");
        }

        return issueTokens(user, http);
    }

    // ===========================================================
    // OAuth
    // ===========================================================

    @Transactional
    public AuthResponse loginWithGoogle(OAuthLoginRequest request, HttpServletRequest http) {
        OAuthVerifier verified = googleOAuthService.verify(request.token());
        User user = upsertOAuthUser(AuthProvider.GOOGLE, verified, request.role());
        return issueTokens(user, http);
    }

    private User upsertOAuthUser(AuthProvider provider, OAuthVerifier verified, Role signupRole) {
        // Priority 1: we already know this providerUserId → always return that user.
        var byProvider = userRepository.findByAuthProviderAndProviderUserId(provider, verified.providerUserId());
        if (byProvider.isPresent()) {
            User existing = byProvider.get();
            if (!existing.isActive()) {
                throw new UnauthorizedException("ACCOUNT_DISABLED", "This account has been disabled");
            }
            // Keep the name fresh if the user updated it at the provider.
            if (verified.fullName() != null && !verified.fullName().equals(existing.getFullName())) {
                existing.setFullName(verified.fullName());
                existing = userRepository.save(existing);
            }
            return existing;
        }

        // Priority 2: same email exists but was created another way.
        var byEmail = userRepository.findByEmailIgnoreCase(normaliseEmail(verified.email()));
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            if (existing.getAuthProvider() != provider) {
                throw new BadRequestException("EMAIL_USED_WITH_DIFFERENT_PROVIDER",
                        "This email is registered with " + existing.getAuthProvider()
                                + ". Log in with that provider instead.");
            }
            existing.setProviderUserId(verified.providerUserId());
            return userRepository.save(existing);
        }

        // Priority 3: brand-new user.
        if (signupRole == null) {
            throw new BadRequestException("ROLE_REQUIRED",
                    "First-time OAuth signup requires a role (OWNER / INSTRUCTOR / STUDENT / PARENT)");
        }
        String email = normaliseEmail(verified.email());
        User created = User.builder()
                .email(email)
                .username(generateInitialUsername(email))
                .fullName(verified.fullName())
                .role(signupRole)
                .authProvider(provider)
                .providerUserId(verified.providerUserId())
                .emailVerifiedAt(Instant.now()) // trusted from provider
                .active(true)
                .build();
        created = userRepository.save(created);
        log.info("New OAuth user via {}: {} as {}", provider, created.getEmail(), created.getRole());
        return created;
    }

    // ===========================================================
    // Refresh / Logout
    // ===========================================================

    @Transactional
    public AuthResponse refresh(String refreshTokenRaw, HttpServletRequest http) {
        UUID userId = refreshTokenService.consumeAndRotate(refreshTokenRaw);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User no longer exists"));
        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_DISABLED", "This account has been disabled");
        }
        return issueTokens(user, http);
    }

    @Transactional
    public void logout(String refreshTokenRaw) {
        refreshTokenService.revoke(refreshTokenRaw);
    }

    // ===========================================================
    // Helpers
    // ===========================================================

    private AuthResponse issueTokens(User user, HttpServletRequest http) {
        String access = jwtService.generateAccessToken(user);
        String refresh = refreshTokenService.issueFor(user, http);
        return new AuthResponse(
                access,
                refresh,
                jwtService.accessTokenTtlSeconds(),
                new AuthResponse.UserDto(
                        user.getId(),
                        user.getSchoolId(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getRole(),
                        user.getAuthProvider(),
                        user.getLanguagePref(),
                        user.isEmailVerified(),
                        user.isPhoneVerified(),
                        user.isMustChangePassword()
                )
        );
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Generate the user's initial username by combining the email's local-part
     * (truncated to 50 chars to leave room for the suffix) with a short hex slice
     * of a fresh UUID. Always lowercased so the unique index on
     * {@code LOWER(username)} sees a normalised value.
     *
     * <p>This is a stop-gap until D7's username chooser ships in PR3+. Collisions
     * are vanishingly unlikely (8 hex chars = 32 bits of entropy per email
     * local-part).</p>
     */
    private static String generateInitialUsername(String email) {
        String localPart = email == null ? "user" : email.split("@", 2)[0];
        if (localPart.length() > 50) {
            localPart = localPart.substring(0, 50);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return (localPart + "_" + suffix).toLowerCase();
    }
}
