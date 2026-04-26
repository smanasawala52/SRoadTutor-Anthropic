package com.sroadtutor.student.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Many-to-many link between a parent {@link com.sroadtutor.auth.model.User}
 * (with {@code role=PARENT}) and a {@link Student}. Backed by the
 * {@code parent_student} table from V1, with its own surrogate id and a
 * UNIQUE on the (parentUserId, studentId) pair so dedupe is DB-enforced.
 */
@Entity
@Table(name = "parent_student",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_parent_student_pair",
                        columnNames = {"parent_user_id", "student_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentStudent {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parent_user_id", columnDefinition = "uuid", nullable = false)
    private UUID parentUserId;

    @Column(name = "student_id", columnDefinition = "uuid", nullable = false)
    private UUID studentId;

    /** PARENT | GUARDIAN | OTHER — free text in V1, may become an enum. */
    @Column(name = "relationship", length = 32)
    @Builder.Default
    private String relationship = "PARENT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
