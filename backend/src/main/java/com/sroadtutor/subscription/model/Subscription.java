package com.sroadtutor.subscription.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-school subscription pointer. Backed by the {@code subscriptions}
 * table from V1.
 *
 * <p>V1 ships with a stub upgrade flow (admin-mode flips {@code plan});
 * full Stripe integration arrives in PR12.5. Until then,
 * {@code stripeSubId} is null on locally-flipped rows.</p>
 *
 * <p>Source-of-truth resolution (per S6):
 * <ol>
 *   <li>If a subscription row exists with {@code cancelledAt = null}, use
 *       {@code subscriptions.plan}.</li>
 *   <li>Otherwise fall back to {@code schools.plan_tier}.</li>
 * </ol>
 * The {@link com.sroadtutor.subscription.service.PlanLimitsService} hides
 * this lookup from callers.</p>
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    /** FREE | PRO | GROWTH | ENTERPRISE — see {@link PlanTier}. */
    @Column(name = "plan", nullable = false, length = 32)
    private String plan;

    @Column(name = "stripe_sub_id", length = 128)
    private String stripeSubId;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "instructor_limit")
    private Integer instructorLimit;

    @Column(name = "student_limit")
    private Integer studentLimit;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

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
