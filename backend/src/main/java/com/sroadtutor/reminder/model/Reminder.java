package com.sroadtutor.reminder.model;

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
 * Pre-generated lesson reminder. Backed by the {@code reminders} table
 * (V1 + V11 columns).
 *
 * <p>Lifecycle:
 * <pre>
 *   PENDING ──> SENT       (instructor tapped "send" → wa.me link opened, audit row written)
 *           ├──> CANCELLED  (parent session cancelled or rescheduled before the reminder fired)
 *           └──> FAILED     (no WhatsApp opt-in / no primary phone / template missing — failed_reason set)
 * </pre>
 *
 * <p>Idempotency: V11 carries a partial UNIQUE index on
 * {@code (session_id, recipient_user_id, reminder_kind)} where status is
 * PENDING or SENT, so the cron can re-run safely.</p>
 */
@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_SENT      = "SENT";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FAILED    = "FAILED";

    public static final String CHANNEL_WHATSAPP = "WHATSAPP";

    public static final String KIND_LESSON_24H  = "LESSON_24H";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false)
    private UUID sessionId;

    @Column(name = "recipient_user_id", columnDefinition = "uuid", nullable = false)
    private UUID recipientUserId;

    @Column(name = "channel", nullable = false, length = 32)
    @Builder.Default
    private String channel = CHANNEL_WHATSAPP;

    @Column(name = "reminder_kind", nullable = false, length = 32)
    @Builder.Default
    private String reminderKind = KIND_LESSON_24H;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    /**
     * Pre-generated wa.me URL + rendered body + recipient phone id are kept
     * here so the SPA can render the reminder before the instructor taps
     * "send". JSONB; opaque from the Java side outside helpers.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    /** Once SENT, the audit log row created at fire time. */
    @Column(name = "wa_me_log_id", columnDefinition = "uuid")
    private UUID waMeLogId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
