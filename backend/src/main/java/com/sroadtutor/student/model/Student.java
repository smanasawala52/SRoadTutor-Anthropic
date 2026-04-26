package com.sroadtutor.student.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Student profile. Backed by the {@code students} table from V1.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@code userId} — UNIQUE FK to the owning {@link com.sroadtutor.auth.model.User}.
 *       Created in the same transaction as the student row by
 *       {@link com.sroadtutor.student.service.StudentService#addByOwner}.</li>
 *   <li>{@code instructorId} — optional pointer to the assigned instructor;
 *       students may be unassigned at creation and routed later.</li>
 *   <li>{@code status} — ACTIVE | PASSED | DROPPED. Hard delete is forbidden;
 *       set {@code status=DROPPED} for the soft equivalent.</li>
 *   <li>{@code packageTotalLessons} / {@code lessonsRemaining} — manually
 *       managed in V1. PR6's session module will auto-decrement on completion.</li>
 * </ul>
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    public static final String STATUS_ACTIVE  = "ACTIVE";
    public static final String STATUS_PASSED  = "PASSED";
    public static final String STATUS_DROPPED = "DROPPED";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    @Column(name = "instructor_id", columnDefinition = "uuid")
    private UUID instructorId;

    @Column(name = "package_total_lessons", nullable = false)
    @Builder.Default
    private int packageTotalLessons = 0;

    @Column(name = "lessons_remaining", nullable = false)
    @Builder.Default
    private int lessonsRemaining = 0;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @Column(name = "road_test_date")
    private LocalDate roadTestDate;

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
