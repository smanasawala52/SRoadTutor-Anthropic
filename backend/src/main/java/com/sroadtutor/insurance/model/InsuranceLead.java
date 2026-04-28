package com.sroadtutor.insurance.model;

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
 * Insurance lead. Backed by {@code insurance_leads} from V13.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li><b>NEW</b> — created at graduation when a routable broker isn't
 *       immediately resolved (or by direct opt-in). Bare row.</li>
 *   <li><b>ROUTED</b> — graduation trigger picked an active broker in the
 *       school's province; populates {@code brokerId} + {@code bountyAmount}.</li>
 *   <li><b>QUOTED</b> — broker confirmed a quote was started ({@code quotedAt}
 *       set). This is the step that triggers the bounty payable to the
 *       platform per investor blueprint ("bounty per quote started").</li>
 *   <li><b>CONVERTED</b> — broker confirmed a policy was bound. Future
 *       commission tier (out of V1 scope).</li>
 *   <li><b>DEAD</b> — never quoted within the broker's SLA window. Tracked
 *       for analytics; no automated transition in V1.</li>
 * </ul>
 *
 * <p>Note: insurance leads are NOT created from a parent intake form
 * (unlike the dealership matchmaker). They're fired off automatically at
 * graduation. New drivers need insurance the day they get a license — no
 * pre-graduation parent form is needed.</p>
 */
@Entity
@Table(name = "insurance_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceLead {

    public static final String STATUS_NEW       = "NEW";
    public static final String STATUS_ROUTED    = "ROUTED";
    public static final String STATUS_QUOTED    = "QUOTED";
    public static final String STATUS_CONVERTED = "CONVERTED";
    public static final String STATUS_DEAD      = "DEAD";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", columnDefinition = "uuid")
    private UUID studentId;

    @Column(name = "broker_id", columnDefinition = "uuid")
    private UUID brokerId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_NEW;

    @Column(name = "bounty_amount", precision = 12, scale = 2)
    private BigDecimal bountyAmount;

    @Column(name = "quoted_at")
    private Instant quotedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
