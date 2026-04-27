package com.sroadtutor.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One financial transaction tied to a student. Backed by the
 * {@code payments} table from V1.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li><b>Auto-created</b> at session COMPLETE — status=UNPAID, amount =
 *       {@code instructor.hourly_rate × duration/60} (or 0 if no rate set),
 *       method=null. Owner / instructor later marks paid.</li>
 *   <li><b>Manually recorded</b> at any time — owner records cash /
 *       e-transfer / write-off (OTHER) payments not tied to a single
 *       session, e.g. a $300 package up front.</li>
 *   <li><b>Stripe-created</b> rows arrive via Stripe webhook (PR12.5).
 *       Status starts PAID and {@code stripe_payment_id} is set.</li>
 * </ul>
 *
 * <p>V1 statuses: UNPAID | PAID. PARTIAL is reserved for a future split-
 * payment feature (P7).</p>
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PAID   = "PAID";

    public static final String METHOD_CASH      = "CASH";
    public static final String METHOD_ETRANSFER = "ETRANSFER";
    public static final String METHOD_STRIPE    = "STRIPE";
    public static final String METHOD_OTHER     = "OTHER";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", columnDefinition = "uuid", nullable = false)
    private UUID studentId;

    /** Optional — payments not tied to a specific session (e.g. a package prepay) leave this null. */
    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "CAD";

    /** CASH | ETRANSFER | STRIPE | OTHER — null on auto-created UNPAID rows. */
    @Column(name = "method", length = 32)
    private String method;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "stripe_payment_id", length = 128)
    private String stripePaymentId;

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
