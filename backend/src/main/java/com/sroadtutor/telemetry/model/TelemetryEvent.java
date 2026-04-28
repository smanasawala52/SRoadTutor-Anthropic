package com.sroadtutor.telemetry.model;

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
 * Vehicle-telemetry datapoint linked to a logged {@code session_mistake}.
 * Backed by {@code telemetry_events} from V13.
 *
 * <p>The pairing of (mistake category + severity) with arbitrary
 * vehicle-side telemetry (steering angle, brake pressure, camera frames,
 * etc.) is the labelled-dataset product investors can sell to AV research
 * firms. {@code offsetMs} is signed: negative = before the mistake (lead-up),
 * positive = after (recovery).</p>
 */
@Entity
@Table(name = "telemetry_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryEvent {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_mistake_id", columnDefinition = "uuid", nullable = false)
    private UUID sessionMistakeId;

    @Column(name = "vehicle_make", length = 64)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 64)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    /** Free-form telemetry payload. JSONB; opaque from Java. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "telemetry_json", nullable = false, columnDefinition = "jsonb")
    private String telemetryJson;

    /** Signed; negative = pre-mistake lead-up, positive = post recovery. */
    @Column(name = "offset_ms")
    private Long offsetMs;

    @Column(name = "synced_at", nullable = false, updatable = false)
    private Instant syncedAt;

    @PrePersist
    void onCreate() {
        if (this.syncedAt == null) this.syncedAt = Instant.now();
    }
}
