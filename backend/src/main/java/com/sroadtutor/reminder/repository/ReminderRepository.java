package com.sroadtutor.reminder.repository;

import com.sroadtutor.reminder.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    /**
     * Used by the cron to find PENDING reminders that should be presented
     * to the instructor's app (or by an instructor's "due now" poll).
     */
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.status = 'PENDING'
              AND r.scheduledFor <= :upTo
            ORDER BY r.scheduledFor ASC
            """)
    List<Reminder> findPendingDueBy(@Param("upTo") Instant upTo);

    /** All reminders for a session (any status). Used by cancel-cascade. */
    List<Reminder> findBySessionId(UUID sessionId);

    /** Active (PENDING / SENT) reminders for a session — used to no-op the cron's "create if missing". */
    List<Reminder> findBySessionIdAndStatusIn(UUID sessionId, List<String> statuses);

    /** Recipient-centric pending list — drives the SPA's "your reminders to send" panel. */
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.recipientUserId = :userId
              AND r.status = 'PENDING'
              AND r.scheduledFor <= :upTo
            ORDER BY r.scheduledFor ASC
            """)
    List<Reminder> findPendingForRecipient(@Param("userId") UUID userId,
                                            @Param("upTo") Instant upTo);

    Optional<Reminder> findBySessionIdAndRecipientUserIdAndReminderKindAndStatusIn(
            UUID sessionId, UUID recipientUserId, String reminderKind, List<String> statuses);
}
