package com.sroadtutor.instructor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * M:N join row between {@code instructors} and {@code schools} (D5).
 * An instructor can teach at multiple schools, a school has many instructors,
 * and the relationship has its own metadata: {@code roleAtSchool},
 * {@code joinedAt}, {@code leftAt}.
 *
 * <p>Soft delete: {@code leftAt} is non-null when the instructor has stopped
 * teaching at that school. We do NOT delete the row — historical lessons
 * still reference it.</p>
 */
@Entity
@Table(name = "instructor_schools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstructorSchool {

    @EmbeddedId
    private InstructorSchoolId id;

    /** OWNER | HEAD | REGULAR — VARCHAR for now, may become an enum once roles solidify. */
    @Column(name = "role_at_school", nullable = false, length = 32)
    @Builder.Default
    private String roleAtSchool = "REGULAR";

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @PrePersist
    void onCreate() {
        if (this.joinedAt == null) this.joinedAt = Instant.now();
    }

    /** True iff the instructor is currently teaching at this school. */
    @Transient
    public boolean isActive() {
        return leftAt == null;
    }
}
