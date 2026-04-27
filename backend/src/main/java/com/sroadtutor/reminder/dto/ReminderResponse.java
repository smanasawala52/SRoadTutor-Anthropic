package com.sroadtutor.reminder.dto;

import com.sroadtutor.reminder.model.Reminder;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID sessionId,
        UUID recipientUserId,
        String channel,
        String reminderKind,
        String status,
        Instant scheduledFor,
        Instant sentAt,
        String failedReason,

        /** Pre-generated wa.me URL — non-null for PENDING/SENT WhatsApp reminders. */
        String waMeUrl,

        /** The body that has been (or will be) shared. */
        String renderedBody,

        UUID recipientPhoneId,
        UUID waMeLogId,

        Instant createdAt
) {

    public static ReminderResponse from(Reminder r, String waMeUrl, String renderedBody, UUID recipientPhoneId) {
        return new ReminderResponse(
                r.getId(),
                r.getSessionId(),
                r.getRecipientUserId(),
                r.getChannel(),
                r.getReminderKind(),
                r.getStatus(),
                r.getScheduledFor(),
                r.getSentAt(),
                r.getFailedReason(),
                waMeUrl,
                renderedBody,
                recipientPhoneId,
                r.getWaMeLogId(),
                r.getCreatedAt()
        );
    }
}
