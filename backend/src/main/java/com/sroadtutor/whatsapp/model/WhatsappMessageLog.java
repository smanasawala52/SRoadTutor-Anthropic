package com.sroadtutor.whatsapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit row for every wa.me link the platform generates (D17). Whatsapp is
 * the primary outbound channel; this log is how we answer "did this owner
 * actually nudge the student?" or "how many reminders did we send last week?"
 *
 * <p>Two timestamps:
 * <ul>
 *   <li>{@code linkGeneratedAt} — set on insert, when the SPA renders the
 *       link.</li>
 *   <li>{@code linkClickedAt} — set when the SPA fires the
 *       {@code /whatsapp/log/{id}/clicked} beacon. Most rows stay null
 *       (links are opened in WhatsApp, not WhatsApp Web).</li>
 * </ul>
 */
@Entity
@Table(name = "whatsapp_message_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappMessageLog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sender_user_id", columnDefinition = "uuid", nullable = false)
    private UUID senderUserId;

    @Column(name = "recipient_phone_id", columnDefinition = "uuid", nullable = false)
    private UUID recipientPhoneId;

    /** Null when the message was free-text (no template). */
    @Column(name = "template_id", columnDefinition = "uuid")
    private UUID templateId;

    /** Final body after placeholder substitution — stored verbatim for audit. */
    @Column(name = "rendered_body", nullable = false, columnDefinition = "text")
    private String renderedBody;

    /** Tenant context. Null only for cross-tenant platform messages. */
    @Column(name = "school_id", columnDefinition = "uuid")
    private UUID schoolId;

    /** Caller-supplied tag — e.g. "lesson:abc123" for grouping reminders. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "link_generated_at", nullable = false)
    private Instant linkGeneratedAt;

    @Column(name = "link_clicked_at")
    private Instant linkClickedAt;

    @PrePersist
    void onCreate() {
        if (this.linkGeneratedAt == null) this.linkGeneratedAt = Instant.now();
    }
}
