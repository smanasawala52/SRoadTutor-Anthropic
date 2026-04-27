package com.sroadtutor.evaluation.dto;

import com.sroadtutor.evaluation.model.SessionMistake;

import java.time.Instant;
import java.util.UUID;

public record SessionMistakeResponse(
        UUID id,
        UUID sessionId,
        UUID studentId,
        UUID mistakeCategoryId,
        String categoryName,
        String severity,
        int points,
        int count,
        String instructorNotes,
        Instant loggedAt
) {

    public static SessionMistakeResponse from(SessionMistake m, String categoryName, String severity, int points) {
        return new SessionMistakeResponse(
                m.getId(),
                m.getSessionId(),
                m.getStudentId(),
                m.getMistakeCategoryId(),
                categoryName,
                severity,
                points,
                m.getCount(),
                m.getInstructorNotes(),
                m.getLoggedAt()
        );
    }
}
