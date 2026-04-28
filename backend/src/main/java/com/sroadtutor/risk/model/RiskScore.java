package com.sroadtutor.risk.model;

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
 * Anonymized risk score for a graduated driver. Backed by the
 * {@code risk_scores} table from V1.
 *
 * <p>PIPEDA compliance: this row never carries a raw {@code studentId}.
 * The {@code studentAnonymizedHash} is a SHA-256 of
 * {@code studentId + platform-salt}; the platform retains the salt but
 * the licensed insurer never sees it. Aggregations (jurisdiction-level
 * risk distributions) are exported without student-level detail.</p>
 */
@Entity
@Table(name = "risk_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScore {

    public static final String TIER_LOW      = "LOW";
    public static final String TIER_MEDIUM   = "MEDIUM";
    public static final String TIER_HIGH     = "HIGH";
    public static final String TIER_CRITICAL = "CRITICAL";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** SHA-256 hex of (studentId + salt). Never the raw student id. */
    @Column(name = "student_anonymized_hash", nullable = false, length = 128)
    private String studentAnonymizedHash;

    /**
     * Aggregated mistake profile — tier counts, total demerits, readiness
     * snapshot. Stored as JSONB; opaque on the Java side so we can evolve
     * the shape without schema changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mistake_profile_json", nullable = false, columnDefinition = "jsonb")
    private String mistakeProfileJson;

    @Column(name = "risk_tier", nullable = false, length = 32)
    private String riskTier;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** Insurer that licensed this row, if any (free-text). null = unlicensed. */
    @Column(name = "licensed_to_insurer", length = 200)
    private String licensedToInsurer;

    @PrePersist
    void onCreate() {
        if (this.generatedAt == null) this.generatedAt = Instant.now();
    }
}
