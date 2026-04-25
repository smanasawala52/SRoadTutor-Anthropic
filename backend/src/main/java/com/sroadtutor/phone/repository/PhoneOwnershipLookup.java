package com.sroadtutor.phone.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only entity-ownership lookups needed by the phone-number scope checker.
 * We deliberately do NOT introduce {@code Student}/{@code Instructor} JPA
 * entities here — those land in PR7 / PR8 with their full controllers and
 * services. PR4 only needs to answer narrow yes/no scope questions, and
 * {@code NamedParameterJdbcTemplate} keeps the queries tight and obvious.
 *
 * <p>All queries are read-only; we use {@code queryForList} (not
 * {@code queryForObject}) so a missing row is an empty list — not an
 * {@code EmptyResultDataAccessException}.</p>
 */
@Repository
public class PhoneOwnershipLookup {

    private final NamedParameterJdbcTemplate jdbc;

    public PhoneOwnershipLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------
    // School lookup
    // ------------------------------------------------------------

    /** True iff the {@code schoolId} row exists. */
    public boolean schoolExists(UUID schoolId) {
        return existsBy(
                "SELECT 1 FROM schools WHERE id = :id",
                new MapSqlParameterSource("id", schoolId)
        );
    }

    /** {@code users.school_id} for the given user. Empty when the user is school-less or absent. */
    public Optional<UUID> userSchoolId(UUID userId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT school_id FROM users WHERE id = :id",
                new MapSqlParameterSource("id", userId),
                UUID.class
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0));
    }

    // ------------------------------------------------------------
    // Instructor lookup
    // ------------------------------------------------------------

    /** True iff the given instructor row's {@code user_id} equals {@code userId}. */
    public boolean instructorBelongsToUser(UUID instructorId, UUID userId) {
        return existsBy(
                """
                SELECT 1
                FROM   instructors
                WHERE  id      = :instructorId
                  AND  user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("instructorId", instructorId)
                        .addValue("userId", userId)
        );
    }

    /**
     * True iff the instructor is currently teaching at the given school via the
     * {@code instructor_schools} M:N join (no {@code left_at}). The legacy
     * {@code instructors.school_id} is also accepted as a fallback for rows
     * created before the M:N join was used.
     */
    public boolean instructorAtSchool(UUID instructorId, UUID schoolId) {
        // Two SELECTs UNIONed so each EXISTS can short-circuit independently.
        return existsBy(
                """
                SELECT 1
                FROM   instructor_schools
                WHERE  instructor_id = :instructorId
                  AND  school_id     = :schoolId
                  AND  left_at IS NULL
                UNION ALL
                SELECT 1
                FROM   instructors
                WHERE  id        = :instructorId
                  AND  school_id = :schoolId
                """,
                new MapSqlParameterSource()
                        .addValue("instructorId", instructorId)
                        .addValue("schoolId", schoolId)
        );
    }

    // ------------------------------------------------------------
    // Student lookup
    // ------------------------------------------------------------

    /** True iff the given student row exists in the given school. */
    public boolean studentAtSchool(UUID studentId, UUID schoolId) {
        return existsBy(
                """
                SELECT 1
                FROM   students
                WHERE  id        = :studentId
                  AND  school_id = :schoolId
                """,
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("schoolId", schoolId)
        );
    }

    /** True iff the parent user is linked to the student via {@code parent_student}. */
    public boolean parentLinkedToStudent(UUID parentUserId, UUID studentId) {
        return existsBy(
                """
                SELECT 1
                FROM   parent_student
                WHERE  parent_user_id = :parentId
                  AND  student_id     = :studentId
                """,
                new MapSqlParameterSource()
                        .addValue("parentId", parentUserId)
                        .addValue("studentId", studentId)
        );
    }

    /** True iff the student row's {@code user_id} equals {@code userId}. */
    public boolean studentBelongsToUser(UUID studentId, UUID userId) {
        return existsBy(
                """
                SELECT 1
                FROM   students
                WHERE  id      = :studentId
                  AND  user_id = :userId
                """,
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("userId", userId)
        );
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private boolean existsBy(String sql, MapSqlParameterSource params) {
        // queryForList(..., Integer.class) returns an empty list (not an
        // exception) when no rows matched — exactly what we want for an
        // existence probe.
        List<Integer> rows = jdbc.queryForList(sql, params, Integer.class);
        return !rows.isEmpty();
    }

    /** Variant when callers prefer {@link Map} over {@link MapSqlParameterSource}. */
    @SuppressWarnings("unused")
    private boolean existsBy(String sql, Map<String, Object> params) {
        List<Integer> rows = jdbc.queryForList(sql, params, Integer.class);
        return !rows.isEmpty();
    }
}
