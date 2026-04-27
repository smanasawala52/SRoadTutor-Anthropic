package com.sroadtutor.marketplace.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One marketplace lead. Backed by {@code dealership_leads} from V1.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li><b>NEW</b> — parent submitted via the First Car Matchmaker (PR16).
 *       Bare row with vehicle prefs, budget, financing readiness flag.
 *       {@code dealershipId} + {@code bountyAmount} unset.</li>
 *   <li><b>ROUTED</b> — graduation trigger fired (student.status flipped to
 *       PASSED). PR17 picks an active dealership in the same province and
 *       populates {@code dealershipId} + {@code bountyAmount}.</li>
 *   <li><b>CONVERTED</b> — dealership confirms the sale. {@code convertedAt}
 *       set; PR17 auto-creates an {@link InstructorPayout} for the
 *       student's instructor.</li>
 *   <li><b>DEAD</b> — lead expired or rejected. Not used in V1 — tracked
 *       as TD when we want to surface lost-lead analytics.</li>
 * </ul>
 */
@Entity
@Table(name = "dealership_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealershipLead {

    public static final String STATUS_NEW       = "NEW";
    public static final String STATUS_ROUTED    = "ROUTED";
    public static final String STATUS_CONVERTED = "CONVERTED";
    public static final String STATUS_DEAD      = "DEAD";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", columnDefinition = "uuid")
    private UUID studentId;

    @Column(name = "parent_user_id", columnDefinition = "uuid")
    private UUID parentUserId;

    /**
     * Free-form vehicle preferences captured by the SPA (make, model,
     * features, etc.). JSONB on the DB; opaque from Java.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vehicle_pref_json", columnDefinition = "jsonb")
    private String vehiclePrefJson;

    @Column(name = "budget", precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(name = "financing_ready")
    private Boolean financingReady;

    @Column(name = "dealership_id", columnDefinition = "uuid")
    private UUID dealershipId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_NEW;

    @Column(name = "bounty_amount", precision = 12, scale = 2)
    private BigDecimal bountyAmount;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
