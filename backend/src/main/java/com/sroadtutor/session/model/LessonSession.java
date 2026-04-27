package com.sroadtutor.session.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One booked / completed / cancelled / no-show driving lesson, backed by the
 * {@code lesson_sessions} table from V1 + V10 audit columns.
 *
 * <p>{@code scheduledAt} is {@code TIMESTAMPTZ} on the wire — the SPA sends
 * absolute instants; the scheduling engine compares against the school's
 * configured timezone when validating against instructor working hours.</p>
 *
 * <p>Status transitions enforced by {@code SessionService}:
 * <pre>
 *   SCHEDULED → COMPLETED   (decrement student.lessons_remaining)
 *   SCHEDULED → CANCELLED   (no decrement; sets cancelled_at + cancelled_by)
 *   SCHEDULED → NO_SHOW     (decrement student.lessons_remaining)
 *   any       ↛ SCHEDULED   (terminal; reschedule = update scheduledAt in place
 *                            while still SCHEDULED)
 * </pre>
 */
@Entity
@Table(name = "lesson_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonSession {

    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_NO_SHOW   = "NO_SHOW";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    @Column(name = "instructor_id", columnDefinition = "uuid", nullable = false)
    private UUID instructorId;

    @Column(name = "student_id", columnDefinition = "uuid", nullable = false)
    private UUID studentId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_mins", nullable = false)
    @Builder.Default
    private int durationMins = 60;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_SCHEDULED;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** V10 audit column. Caller who booked the row. Nullable on legacy rows. */
    @Column(name = "created_by_user_id", columnDefinition = "uuid")
    private UUID createdByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id", columnDefinition = "uuid")
    private UUID cancelledByUserId;

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

    /** End time of the booked window. */
    @Transient
    public Instant getEndAt() {
        return scheduledAt.plusSeconds(60L * durationMins);
    }
}
