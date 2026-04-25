package com.sroadtutor.auth.repository;

import com.sroadtutor.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lookups PR5 needs:
 * <ul>
 *   <li>Redeem by token hash — the only way an unauthenticated request can
 *       prove they hold the link from the email.</li>
 *   <li>List active (unconsumed) tokens for a user — so re-issuing during
 *       resend doesn't pile up orphan rows; we revoke prior actives.</li>
 * </ul>
 */
@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findByUserIdAndConsumedAtIsNull(UUID userId);
}
