package com.sroadtutor.phone.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 1..N phone number for any owner (D13/D14/D15/D16). The CHECK constraint
 * {@code phone_owner_exactly_one} on the table guarantees exactly one of
 * {@link #userId}, {@link #schoolId}, {@link #instructorId},
 * {@link #studentId} is non-null.
 *
 * <p>Q1a — There is no {@code parentId} column. PARENT is a role on
 * {@code users}, so a parent's phones use {@link #userId}.</p>
 *
 * <p>E.164 storage: {@code countryCode} + {@code nationalNumber} + the
 * concatenated {@code e164} string. The split form is what wa.me likes
 * (digits only, no "+"); the joined form is what humans paste back.</p>
 *
 * <p>Uniqueness rules (enforced via partial unique indexes in V8):
 * <ul>
 *   <li>Only one row per owner can have {@code is_primary = true}.</li>
 *   <li>A given e164 cannot duplicate within the SAME owner. D14 still allows
 *       the same number across DIFFERENT owners (parent + student sharing).</li>
 * </ul>
 */
@Entity
@Table(name = "phone_numbers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneNumber {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "school_id", columnDefinition = "uuid")
    private UUID schoolId;

    @Column(name = "instructor_id", columnDefinition = "uuid")
    private UUID instructorId;

    @Column(name = "student_id", columnDefinition = "uuid")
    private UUID studentId;

    /** Country dialing code, digits only, no leading "+". E.g. "1", "880", "44". */
    @Column(name = "country_code", nullable = false, length = 4)
    private String countryCode;

    /** National significant number, digits only. */
    @Column(name = "national_number", nullable = false, length = 20)
    private String nationalNumber;

    /** Full E.164 (e.g. "+13065551234"). The single canonical lookup key. */
    @Column(name = "e164", nullable = false, length = 20)
    private String e164;

    /** "Mobile", "Office", "Home" — free-text, optional. */
    @Column(name = "label", length = 40)
    private String label;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Column(name = "is_whatsapp", nullable = false)
    @Builder.Default
    private boolean whatsapp = true;

    @Column(name = "whatsapp_opt_in", nullable = false)
    @Builder.Default
    private boolean whatsappOptIn = true;

    /** Null = unverified. Set = verified at this instant. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

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

    /** True iff the phone has been verified at any point (D15). */
    @Transient
    public boolean isVerified() {
        return verifiedAt != null;
    }
}
