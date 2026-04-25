package com.sroadtutor.instructor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link InstructorSchool}. JPA requires the PK
 * class to implement Serializable, override equals/hashCode, and have a
 * no-arg constructor — Lombok's {@code @EqualsAndHashCode} handles the
 * value-equality contract correctly here.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InstructorSchoolId implements Serializable {

    @Column(name = "instructor_id", columnDefinition = "uuid", nullable = false)
    private UUID instructorId;

    @Column(name = "school_id", columnDefinition = "uuid", nullable = false)
    private UUID schoolId;
}
