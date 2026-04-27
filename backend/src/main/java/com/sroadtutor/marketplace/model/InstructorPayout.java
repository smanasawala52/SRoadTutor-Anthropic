package com.sroadtutor.marketplace.model;

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
 * Auto-created when a {@link DealershipLead} converts. The instructor
 * earns a $100 kickback per investor blueprint.
 */
@Entity
@Table(name = "instructor_payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstructorPayout {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID    = "PAID";

    /** Default payout per converted lead — locked at PR17 per investor blueprint. */
    public static final BigDecimal DEFAULT_PAYOUT_AMOUNT = new BigDecimal("100.00");

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instructor_id", columnDefinition = "uuid", nullable = false)
    private UUID instructorId;

    @Column(name = "lead_id", columnDefinition = "uuid")
    private UUID leadId;

    @Column(name = "payout_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "e_transfer_ref", length = 128)
    private String eTransferRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
