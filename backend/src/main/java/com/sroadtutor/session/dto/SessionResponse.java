package com.sroadtutor.session.dto;

import com.sroadtutor.session.model.LessonSession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID schoolId,
        UUID instructorId,
        UUID studentId,
        Instant scheduledAt,
        Instant endAt,
        int durationMins,
        String status,
        String location,
        String notes,
        UUID createdByUserId,
        Instant cancelledAt,
        UUID cancelledByUserId,
        Instant createdAt,
        Instant updatedAt
) {

    public static SessionResponse fromEntity(LessonSession s) {
        return new SessionResponse(
                s.getId(),
                s.getSchoolId(),
                s.getInstructorId(),
                s.getStudentId(),
                s.getScheduledAt(),
                s.getEndAt(),
                s.getDurationMins(),
                s.getStatus(),
                s.getLocation(),
                s.getNotes(),
                s.getCreatedByUserId(),
                s.getCancelledAt(),
                s.getCancelledByUserId(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
