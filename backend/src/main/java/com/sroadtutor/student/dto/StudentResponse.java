package com.sroadtutor.student.dto;

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
 */
public record StudentResponse(
        UUID id,
        UUID userId,
        UUID schoolId,
        UUID instructorId,
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

    public static StudentResponse from(Student s, List<ParentLink> parents) {
        return new StudentResponse(
                s.getId(),
                s.getUserId(),
                s.getSchoolId(),
                s.getInstructorId(),
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
