package com.sroadtutor.invitation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-issued invitation that turns into a user on acceptance (D6).
 *
 * <p>Two delivery modes — pick one per invite:
 * <ul>
 *   <li><b>TOKEN</b>: a sha-256 hex of a random URL-safe token lands in
 *       {@code tokenHash}; the plaintext goes only in the email.
 *       Acceptance = clicking the link, choosing a password.</li>
 *   <li><b>DUMMY_PWD</b>: no token; the owner pre-creates the user with
 *       the well-known {@code test123} password and {@code mustChangePassword
 *       = true}. Used when the invitee can't easily receive email.</li>
 * </ul>
 * The CHECK constraint {@code chk_invitation_token_or_dummy} keeps these in
 * lockstep at the database level.</p>
 */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    @Column(name = "invited_by_user_id", columnDefinition = "uuid", nullable = false)
    private UUID invitedByUserId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** INSTRUCTOR | STUDENT | PARENT — owners can't invite other owners. */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    /** TOKEN | DUMMY_PWD — see class doc. */
    @Column(name = "delivery_mode", nullable = false, length = 16)
    private String deliveryMode;

    /** SHA-256 hex of the raw token. Null when {@code deliveryMode = DUMMY_PWD}. */
    @Column(name = "token_hash", length = 128)
    private String tokenHash;

    /** PENDING | ACCEPTED | EXPIRED | REVOKED. */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_user_id", columnDefinition = "uuid")
    private UUID acceptedUserId;

    /**
     * Optional payload — e.g. {@code instructorDetails} pre-filled by the
     * inviting owner (hourly rate, intro bio). Stored as JSONB; modeled as
     * {@code String} on the Java side because PR2 doesn't read it yet.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
