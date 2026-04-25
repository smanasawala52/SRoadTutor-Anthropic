package com.sroadtutor.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One-shot token issued at signup so the user can prove they own their email.
 *
 * <p>We store the SHA-256 hex of the raw token (mirrors the
 * {@link RefreshToken} pattern) — a DB leak doesn't immediately hand over
 * valid verification links. {@code consumedAt} is set on first redemption;
 * any subsequent attempt to reuse the same token is rejected.</p>
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    /** SHA-256 hex of the raw token. */
    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @PrePersist
    void onCreate() {
        if (this.issuedAt == null) this.issuedAt = Instant.now();
    }

    /** True iff the token can still be redeemed at the given instant. */
    @Transient
    public boolean isActive(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
