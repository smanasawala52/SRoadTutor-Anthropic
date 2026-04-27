package com.sroadtutor.session.repository;

import com.sroadtutor.session.model.LessonSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lookups + collision detection for the scheduling engine.
 *
 * <p>Active = {@code SCHEDULED} or {@code COMPLETED}. Cancelled and no-show
 * rows are NOT considered active for collision detection.</p>
 *
 * <p>Overlap calculation lives in {@code SessionService} (Java) rather than
 * SQL — we narrow the candidate set with a {@code scheduledAt} range query,
 * then filter overlaps with the {@link LessonSession#getEndAt()} helper.
 * That keeps the JPQL portable across dialects and avoids registering a
 * custom interval-add function.</p>
 */
@Repository
public interface LessonSessionRepository extends JpaRepository<LessonSession, UUID> {

    /**
     * Active sessions for an instructor whose {@code scheduledAt} lies in
     * the (potentially over-broad) candidate window. Service layer narrows
     * to actual overlap by checking each row's end-time.
     */
    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.instructorId = :instructorId
              AND s.status IN ('SCHEDULED','COMPLETED')
              AND s.scheduledAt >= :rangeFrom
              AND s.scheduledAt <  :rangeTo
            """)
    List<LessonSession> findInstructorActiveInRange(@Param("instructorId") UUID instructorId,
                                                     @Param("rangeFrom") Instant rangeFrom,
                                                     @Param("rangeTo") Instant rangeTo);

    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.studentId = :studentId
              AND s.status IN ('SCHEDULED','COMPLETED')
              AND s.scheduledAt >= :rangeFrom
              AND s.scheduledAt <  :rangeTo
            """)
    List<LessonSession> findStudentActiveInRange(@Param("studentId") UUID studentId,
                                                  @Param("rangeFrom") Instant rangeFrom,
                                                  @Param("rangeTo") Instant rangeTo);

    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.schoolId = :schoolId
              AND s.scheduledAt >= :from
              AND s.scheduledAt <  :to
            ORDER BY s.scheduledAt ASC
            """)
    List<LessonSession> findForSchoolInRange(@Param("schoolId") UUID schoolId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);

    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.instructorId = :instructorId
              AND s.scheduledAt >= :from
              AND s.scheduledAt <  :to
            ORDER BY s.scheduledAt ASC
            """)
    List<LessonSession> findForInstructorInRange(@Param("instructorId") UUID instructorId,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to);

    @Query("""
            SELECT s FROM LessonSession s
            WHERE s.studentId = :studentId
              AND s.scheduledAt >= :from
              AND s.scheduledAt <  :to
            ORDER BY s.scheduledAt ASC
            """)
    List<LessonSession> findForStudentInRange(@Param("studentId") UUID studentId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);
}
