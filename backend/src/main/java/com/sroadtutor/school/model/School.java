package com.sroadtutor.school.model;

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
 * Driving-school tenant. Every row in the system is scoped to a school
 * (directly via a {@code school_id} FK or transitively through the user/
 * instructor/student that owns it).
 *
 * <p>Lifecycle invariants (locked at PR5 kickoff):
 * <ul>
 *   <li><b>One school per owner</b> — enforced by {@code SchoolService} at
 *       create time. The DB does NOT carry a unique index on {@code owner_id}
 *       because we may relax this later (a single owner running two schools
 *       under one account); it's cheaper to enforce in code now and add the
 *       index when we trust the policy.</li>
 *   <li><b>Soft-delete only</b> — {@code isActive=false} retires a school
 *       without cascading. Hard delete is forbidden at the service layer
 *       because instructor / student / session / audit rows still reference
 *       it.</li>
 *   <li><b>{@code planTier}</b> is the source of truth for plan limits in V1.
 *       The {@code subscriptions} table will become authoritative when PR9
 *       wires Stripe billing; until then this column is editable by the OWNER
 *       but locked to {@code FREE} (no upgrade path yet).</li>
 * </ul>
 *
 * <p>Tax-ID columns ({@code gstNumber}, {@code pstNumber}, {@code hstNumber},
 * {@code businessRegistrationNumber}) are stored as opaque strings — format
 * varies wildly by Canadian province + edge cases like sole proprietors, and
 * V1 trusts the SPA to surface validation hints. Length caps mirror V8.</p>
 */
@Entity
@Table(name = "schools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class School {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * The {@code users.id} of the OWNER. The DB FK is
     * {@code ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED} (V1) so the
     * column may be null after the owner row is removed; in practice we
     * never delete users so the constraint is academic.
     */
    @Column(name = "owner_id", columnDefinition = "uuid")
    private UUID ownerId;

    /** FREE | PRO | GROWTH | ENTERPRISE. V1 only ever stores FREE; PR9 unlocks the rest. */
    @Column(name = "plan_tier", nullable = false, length = 32)
    @Builder.Default
    private String planTier = "FREE";

    /** Populated by Stripe when billing module lands (PR9). Null until then. */
    @Column(name = "stripe_customer_id", length = 128)
    private String stripeCustomerId;

    /** Two-letter province code: SK, AB, BC, ON, QC, … Optional. */
    @Column(name = "province", length = 8)
    private String province;

    /** SGI | ICBC | MTO | DMV — drives the mistake-category catalogue. */
    @Column(name = "jurisdiction", nullable = false, length = 16)
    @Builder.Default
    private String jurisdiction = "SGI";

    @Column(name = "gst_number", length = 40)
    private String gstNumber;

    @Column(name = "pst_number", length = 40)
    private String pstNumber;

    @Column(name = "hst_number", length = 40)
    private String hstNumber;

    @Column(name = "business_registration_number", length = 80)
    private String businessRegistrationNumber;

    /**
     * IANA timezone (e.g. {@code America/Regina}) — used by the scheduling
     * engine to interpret instructor {@code working_hours_json} windows
     * (which are {@link java.time.LocalTime}, no offset). Defaults to
     * {@code America/Regina} per V10 migration; owners can change via
     * the school update endpoint.
     */
    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = "America/Regina";

    /**
     * Free-form metadata bag — used for SPA-side feature flags, brand colours,
     * etc. Stored as JSONB; modeled as raw JSON string on the Java side because
     * V1 doesn't read structured fields out of it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    /**
     * D12 — when an owner pre-creates a synthetic school for their own books
     * (no real students yet), we mark it so dashboards / billing can ignore it.
     */
    @Column(name = "is_synthetic", nullable = false)
    @Builder.Default
    private boolean synthetic = false;

    /**
     * Soft-delete flag. {@code true} = active tenant; {@code false} = retired.
     * Retired schools are read-only — no new instructors / students / sessions
     * may be created under them, and existing rows remain queryable for audit.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
