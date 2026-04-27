package com.sroadtutor.evaluation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Pre-seeded mistake catalog entry (e.g. "Failure to signal", "Improper
 * lane change"). Backed by {@code mistake_categories} from V1 + V5
 * additions ({@code points}, {@code source_code}).
 *
 * <p>Severity drives the default points if {@code points} is left at its
 * default of 2 and a finer-grained value isn't seeded — but the
 * source-of-truth for the demerit weighting in the readiness score is
 * always {@code points}.</p>
 */
@Entity
@Table(name = "mistake_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MistakeCategory {

    public static final String SEVERITY_MINOR = "MINOR";
    public static final String SEVERITY_MAJOR = "MAJOR";
    public static final String SEVERITY_FAIL  = "FAIL";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "jurisdiction", nullable = false, length = 16)
    private String jurisdiction;

    @Column(name = "category_name", nullable = false, length = 120)
    private String categoryName;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** V5 — demerit points used by the readiness scorer. Default 2. */
    @Column(name = "points", nullable = false)
    @Builder.Default
    private int points = 2;

    /** V5 — external code from the source workbook (e.g. 'M001'). */
    @Column(name = "source_code", length = 32)
    private String sourceCode;
}
