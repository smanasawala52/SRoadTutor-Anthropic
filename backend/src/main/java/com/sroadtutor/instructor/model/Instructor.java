package com.sroadtutor.instructor.model;

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
 * Instructor profile. Backed by the {@code instructors} table from V1
 * (extended in V8 with {@code vehicle_plate} + {@code bio} and in V9 with
 * {@code hourly_rate}).
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@code userId} is the owning {@link com.sroadtutor.auth.model.User}
 *       — UNIQUE, so a user is at most ONE instructor row.</li>
 *   <li>{@code schoolId} is the legacy "primary school" pointer (V1) and is
 *       nullable since V8 — multi-school instructors live in
 *       {@link InstructorSchool}. Service layer treats {@code schoolId} as
 *       informational; tenant-scope decisions go through {@code instructor_schools}.</li>
 *   <li>{@code workingHoursJson} is opaque JSONB; {@link WorkingHours}
 *       provides typed access.</li>
 *   <li>{@code active=false} retires the instructor at all schools (read-only
 *       afterwards).</li>
 * </ul>
 */
@Entity
@Table(name = "instructors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instructor {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID userId;

    /** Nullable since V8. Use {@code instructor_schools} for the source of truth. */
    @Column(name = "school_id", columnDefinition = "uuid")
    private UUID schoolId;

    @Column(name = "license_no", length = 64)
    private String licenseNo;

    @Column(name = "sgi_cert", length = 64)
    private String sgiCert;

    @Column(name = "vehicle_make", length = 64)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 64)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    /** Stored as JSONB. Use {@link WorkingHours#fromJson(String)} for typed access. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_hours_json", columnDefinition = "jsonb")
    private String workingHoursJson;

    /** V9 column. Nullable — instructors who haven't agreed a rate yet stay unset. */
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
