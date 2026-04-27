package com.sroadtutor.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single-shot dashboard rollup for an OWNER's home screen.
 *
 * <p>All money columns are in CAD (V1 hardcode — Stripe in PR12.5 will start
 * surfacing currency-of-charge separately). The {@code window} block tells
 * the SPA which time-bounded numbers cover what range.</p>
 */
public record DashboardResponse(
        UUID schoolId,
        String schoolName,
        String planTier,
        Window window,

        BigDecimal totalRevenuePaid,
        BigDecimal totalOutstanding,
        long activeStudentCount,
        long upcomingSessionsCount,
        long completedSessionsInWindow,

        BigDecimal monthlyRecurringRevenue,

        List<InstructorWorkload> instructorWorkloads
) {

    /** [from, to) — time bounds the time-sensitive metrics use. */
    public record Window(Instant from, Instant to, int days) {}

    public record InstructorWorkload(
            UUID instructorId,
            String instructorName,
            long scheduledSessionsInWindow,
            long completedSessionsInWindow,
            long activeStudentsAssigned
    ) {}
}
