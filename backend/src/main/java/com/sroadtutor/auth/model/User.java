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
 * Users table.  Maps rows one-to-one with people who can log in.
 *
 * <p>In Phase 1, {@code schoolId} is nullable because users can exist before
 * they belong to a school (e.g. an Instructor invited by email signs up first;
 * their school membership lands later via {@code instructor_schools}).
 *
 * <p>PR2 schema changes (locked in V8):
 * <ul>
 *   <li>+ {@code username} — globally unique, case-insensitive (D7).</li>
 *   <li>+ {@code mustChangePassword} — true when the owner pre-created
 *       this account with the dummy password ({@code test123}); forces
 *       a password rotation on first login (D6).</li>
 *   <li>+ {@code emailVerifiedAt} / {@code phoneVerifiedAt} — null means
 *       unverified. Replaces the legacy {@code email_verified} boolean.</li>
 *   <li>− {@code phone} — moved into {@code phone_numbers}, single source
 *       of truth for any 1..N phone number tracking (D13/Q3a).</li>
 * </ul>
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_users_school", columnList = "school_id"),
                @Index(name = "idx_users_role", columnList = "role")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid")
    private UUID schoolId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /**
     * Globally unique handle, case-insensitively (enforced by
     * {@code ux_users_username_lower}). Service layer always lowercases on
     * read/write to keep comparisons trivially correct.
     */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** Null for OAuth-only users.  Otherwise a BCrypt hash. */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * True when the user must rotate their password on next login. Set when an
     * owner pre-creates a row with the {@code test123} dummy password (D6).
     */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    private AuthProvider authProvider;

    /** Provider-specific id, e.g. Google sub.  Null for LOCAL. */
    @Column(name = "provider_user_id", length = 128)
    private String providerUserId;

    @Column(name = "language_pref", length = 8)
    @Builder.Default
    private String languagePref = "en";

    /** Null = email never verified. Set = verified at this instant. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Null = WhatsApp/phone never verified. Set = verified at this instant. */
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** True iff the email has been verified at any point. */
    @Transient
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** True iff the primary phone has been verified at any point. */
    @Transient
    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

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
