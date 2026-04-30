package com.sroadtutor.auth.service;

import com.sroadtutor.auth.dto.EmailVerifyConfirmResponse;
import com.sroadtutor.auth.dto.EmailVerifyResponse;
import com.sroadtutor.auth.model.EmailVerificationToken;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.EmailVerificationTokenRepository;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Issues + redeems email-verification tokens.
 *
 * <p>Locked at PR8 kickoff:
 * <ul>
 *   <li><b>One active token per user</b> — calling {@link #issueForCurrentUser}
 *       revokes any unconsumed prior tokens before issuing a fresh one. The
 *       <code>reissued</code> flag in the response tells the caller whether
 *       this overwrote a prior unused token.</li>
 *   <li><b>24-hour expiry</b> — tokens that age out can never be redeemed,
 *       even if the user forgot to click in time.</li>
 *   <li><b>Idempotent confirm</b> — confirming a token after the user is
 *       already verified is a 400 {@code ALREADY_VERIFIED} (we want the SPA
 *       to know about the no-op explicitly rather than silently treat it as
 *       success and risk a double-redeem race).</li>
 *   <li><b>Token-in-response for V1</b> — until SMTP is wired up, the raw
 *       token + a dev verify URL are included in {@link EmailVerifyResponse}.
 *       When email infra arrives, the response strips these fields.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Per E2: 24h. Long enough for the user to click during the day they signed up. */
    static final Duration TOKEN_TTL = Duration.ofHours(24);

    /** Dev base URL for the verify link. SPA replaces with its own when wiring real email. */
    private static final String DEV_VERIFY_URL_BASE = "http://localhost:5173/verify-email/";

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepo,
                                    UserRepository userRepo) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
    }

    // ============================================================
    // Issue
    // ============================================================

    @Transactional
    public EmailVerifyResponse issueForCurrentUser(UUID currentUserId) {
        User user = userRepo.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Current user no longer exists"));

        if (user.isEmailVerified()) {
            throw new BadRequestException(
                    "ALREADY_VERIFIED",
                    "Email is already verified");
        }

        // Per E3: revoke any prior unconsumed tokens before issuing a fresh
        // one. Mark them consumed=now so the unique-active invariant holds.
        List<EmailVerificationToken> active = tokenRepo.findByUserIdAndConsumedAtIsNull(currentUserId);
        Instant now = Instant.now();
        boolean reissued = false;
        for (EmailVerificationToken old : active) {
            old.setConsumedAt(now);
            tokenRepo.save(old);
            reissued = true;
        }

        String rawToken = random6DigitToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(currentUserId)
                .tokenHash(sha256Hex(rawToken))
                .rawToken(rawToken)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .build();
        token = tokenRepo.save(token);

        String devUrl = DEV_VERIFY_URL_BASE + rawToken;
        log.info("Email-verify token issued for user={} (reissued={}). DEV URL: {}",
                currentUserId, reissued, devUrl);

        return new EmailVerifyResponse(
                token.getId(),
                rawToken,
                devUrl,
                token.getExpiresAt(),
                reissued);
    }

    // ============================================================
    // Confirm
    // ============================================================

    @Transactional
    public EmailVerifyConfirmResponse confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException(
                    "INVALID_VERIFY_TOKEN",
                    "Verification token is required");
        }
        EmailVerificationToken token = tokenRepo.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_VERIFY_TOKEN",
                        "Verification token not recognised"));

        Instant now = Instant.now();
        if (!token.isActive(now)) {
            throw new BadRequestException(
                    "VERIFY_TOKEN_EXPIRED_OR_USED",
                    "Verification token is expired or already used");
        }

        User user = userRepo.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User no longer exists: " + token.getUserId()));

        if (user.isEmailVerified()) {
            // Edge case — token is still active but the user got verified
            // through another path. Mark the token consumed so it can't be
            // re-used and tell the caller about it.
            token.setConsumedAt(now);
            tokenRepo.save(token);
            throw new BadRequestException(
                    "ALREADY_VERIFIED",
                    "Email is already verified");
        }

        token.setConsumedAt(now);
        tokenRepo.save(token);

        user.setEmailVerifiedAt(now);
        userRepo.save(user);

        log.info("Email verified for user={} via token={}", user.getId(), token.getId());

        return new EmailVerifyConfirmResponse(
                user.getId(),
                user.getEmail(),
                user.getEmailVerifiedAt());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String random6DigitToken() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    static String sha256Hex(String raw) {
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
}
