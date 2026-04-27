package com.sroadtutor.subscription.model;

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
 * Per-school monthly usage counter. One row per (schoolId, periodStart).
 *
 * <p>{@code periodStart} is the first day of the calendar month at UTC.
 * The application upserts via {@code findOrCreate + increment}.</p>
 */
@Entity
@Table(name = "subscription_usage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subscription_usage_school_period",
                        columnNames = {"school_id", "period_start"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionUsage {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "wa_me_count", nullable = false)
    @Builder.Default
    private int waMeCount = 0;

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
