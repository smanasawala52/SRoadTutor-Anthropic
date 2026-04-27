-- =====================================================================
--  V11 — Phase 1 reminders module (PR10)
-- =====================================================================
--  Context
--    The reminders table itself was created in V1. PR10 adds the bits the
--    cron-driven sweep + manual-fire flow need:
--
--      * reminder_kind     — distinguishes the 24h pre-lesson reminder
--                            from any future kinds (1h reminder, post-
--                            lesson follow-up, etc.). Locked at V1: only
--                            'LESSON_24H' for now.
--      * scheduled_for     — the Instant we expect to fire at. The cron
--                            looks for rows whose scheduled_for falls in
--                            the next sweep window AND whose status =
--                            PENDING.
--      * failed_reason     — populated when status flips to FAILED so
--                            owners / instructors can see why a reminder
--                            didn't go.
--      * wa_me_log_id      — once SENT, links to the whatsapp_message_log
--                            row created at fire time (audit consistency).
--
--    A unique constraint on (session_id, recipient_user_id, kind) is the
--    cron's idempotency key — repeated sweeps don't re-create rows for
--    the same triple.
--
--  Also seeds a platform-default whatsapp_templates row with code
--  'lesson_reminder'. school-specific overrides may shadow this in a
--  future PR; today's resolver picks the school override first then falls
--  back to this row.
-- =====================================================================

ALTER TABLE reminders
    ADD COLUMN reminder_kind VARCHAR(32),
    ADD COLUMN scheduled_for TIMESTAMPTZ,
    ADD COLUMN failed_reason VARCHAR(500),
    ADD COLUMN wa_me_log_id  UUID REFERENCES whatsapp_message_log(id) ON DELETE SET NULL;

-- Backfill any historical rows (none in V1 but defensive)
UPDATE reminders SET reminder_kind = 'LESSON_24H' WHERE reminder_kind IS NULL;
ALTER TABLE reminders ALTER COLUMN reminder_kind SET NOT NULL;

-- Idempotency key — one (session, recipient, kind) at a time.
-- Allows rebooking after a CANCELLED / FAILED row exists by including status
-- in the WHERE clause (we only block PENDING + SENT against duplication).
CREATE UNIQUE INDEX ux_reminders_session_recipient_kind_active
    ON reminders(session_id, recipient_user_id, reminder_kind)
    WHERE status IN ('PENDING', 'SENT');

CREATE INDEX idx_reminders_pending_due
    ON reminders(scheduled_for)
    WHERE status = 'PENDING';

CREATE INDEX idx_reminders_recipient_status
    ON reminders(recipient_user_id, status);

-- ---------------------------------------------------------------------
-- Default lesson_reminder template (platform-level, school_id = null)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates (school_id, code, label, language, body, placeholders, is_active)
VALUES (
    NULL,
    'lesson_reminder',
    'Default lesson-reminder template',
    'en',
    'Hi {{studentName}}, this is a reminder of your driving lesson with {{instructorName}} on {{lessonTimeLocal}} ({{lessonDuration}} min){{locationSuffix}}. See you then!',
    '["studentName","instructorName","lessonTimeLocal","lessonDuration","locationSuffix"]'::jsonb,
    TRUE
)
ON CONFLICT DO NOTHING;
