package com.sroadtutor.student.dto;

import com.sroadtutor.auth.model.User;
import com.sroadtutor.student.model.Student;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read projection of {@link Student}. The {@code parents} list is populated by
 * {@code StudentService} from {@code parent_student} rows when present;
 * callers fetching a student detail page get their parent contacts in the
 * same response.
 *
 * <p>{@code fullName} / {@code email} / {@code active} are pulled from the
 * owning {@link User} so SPA clients have user-meaningful identity without an
 * extra round-trip.</p>
 */
public record StudentResponse(
        UUID id,
        UUID userId,
        UUID schoolId,
        UUID instructorId,
        String fullName,
        String email,
        boolean active,
        int packageTotalLessons,
        int lessonsRemaining,
        String status,
        LocalDate roadTestDate,
        Instant createdAt,
        Instant updatedAt,
        List<ParentLink> parents
) {

    public record ParentLink(
            UUID parentUserId,
            String parentEmail,
            String parentFullName,
            String relationship
    ) {}

    public static StudentResponse from(Student s, User u, List<ParentLink> parents) {
        return new StudentResponse(
                s.getId(),
                s.getUserId(),
                s.getSchoolId(),
                s.getInstructorId(),
                u == null ? null : u.getFullName(),
                u == null ? null : u.getEmail(),
                u == null || u.isActive(),
                s.getPackageTotalLessons(),
                s.getLessonsRemaining(),
                s.getStatus(),
                s.getRoadTestDate(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                parents == null ? List.of() : parents
        );
    }
}
