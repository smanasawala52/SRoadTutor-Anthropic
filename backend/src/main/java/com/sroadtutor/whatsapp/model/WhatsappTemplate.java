package com.sroadtutor.whatsapp.model;

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
 * Reusable WhatsApp message body with {@code {{placeholder}}} tokens.
 * Templates can be platform-level ({@link #schoolId} null — Anthropic-style
 * defaults) or school-overridden (an owner's custom phrasing).
 *
 * <p>Uniqueness is enforced via two partial indexes in V8 — {@code (schoolId,
 * code, language)} when schoolId is non-null, {@code (code, language)} when
 * schoolId is null — to give us the same effect as a unique
 * {@code COALESCE(schoolId, sentinel)} without expression-index gymnastics.</p>
 */
@Entity
@Table(name = "whatsapp_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappTemplate {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Null = platform-level default, non-null = school-specific override. */
    @Column(name = "school_id", columnDefinition = "uuid")
    private UUID schoolId;

    /** Stable identifier — "lesson_reminder", "payment_due", etc. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** Human-readable name shown in the owner's template-picker UI. */
    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "language", nullable = false, length = 8)
    @Builder.Default
    private String language = "en";

    /** Body text with literal {@code {{name}}} placeholders. */
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /**
     * JSONB array of expected placeholder names — used to validate the body
     * at save-time and to drive the owner's template-editor. Stored as a
     * raw JSON string on the Java side.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "placeholders", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String placeholders = "[]";

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
