package com.sroadtutor.evaluation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /api/students/{id}/readiness-score} — driver of
 * the SPA's readiness gauge.
 *
 * <p>The score is computed by {@code MistakeLogService}: a per-session
 * score = max(0, 100 - sum(category.points × count)). The cumulative
 * "readiness" is the average of the last N (default 5) sessions, with a
 * hard floor of zero. {@code anyFailMistakeRecently} flags whether any
 * FAIL-severity mistake has been logged in the last N sessions — if true,
 * the SPA can render a "needs another lesson" prompt regardless of the
 * numerical score.</p>
 */
public record ReadinessScoreResponse(
        UUID studentId,
        int sessionsConsidered,
        double averageScore,
        boolean anyFailMistakeRecently,
        List<PerSessionScore> perSession
) {

    public record PerSessionScore(
            UUID sessionId,
            int score,
            int totalDemerits,
            boolean hadFail
    ) {}
}
