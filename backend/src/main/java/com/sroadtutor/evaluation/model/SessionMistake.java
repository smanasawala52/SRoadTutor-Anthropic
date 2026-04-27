package com.sroadtutor.evaluation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One logged mistake during a lesson. Multiple rows per session are
 * allowed — the instructor may log the same category twice if it happens
 * twice. The {@code count} column lets a single row carry multiplicity if
 * the SPA prefers (e.g. "+3" tap on a category increments count instead
 * of inserting a new row). Both styles are valid.
 */
@Entity
@Table(name = "session_mistakes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionMistake {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", columnDefinition = "uuid", nullable = false)
    private UUID studentId;

    @Column(name = "mistake_category_id", columnDefinition = "uuid", nullable = false)
    private UUID mistakeCategoryId;

    @Column(name = "count", nullable = false)
    @Builder.Default
    private int count = 1;

    @Column(name = "instructor_notes", columnDefinition = "text")
    private String instructorNotes;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @PrePersist
    void onCreate() {
        if (this.loggedAt == null) this.loggedAt = Instant.now();
    }
}
