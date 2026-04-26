package com.sroadtutor.instructor.repository;

import com.sroadtutor.instructor.model.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lookups: by id, by owning user (one-instructor-per-user invariant),
 * and the school-scoped active list driven by the {@code instructor_schools}
 * join (no {@code left_at}).
 */
@Repository
public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    Optional<Instructor> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * Active instructors at the given school via {@code instructor_schools}.
     * Falls back to the legacy {@code instructors.school_id} so older rows
     * created before V8's M:N join was used still surface.
     */
    @Query("""
            SELECT i FROM Instructor i
            WHERE i.active = true
              AND (
                EXISTS (
                  SELECT 1 FROM InstructorSchool js
                  WHERE js.id.instructorId = i.id
                    AND js.id.schoolId     = :schoolId
                    AND js.leftAt IS NULL
                )
                OR i.schoolId = :schoolId
              )
            """)
    List<Instructor> findActiveBySchool(@Param("schoolId") UUID schoolId);
}
