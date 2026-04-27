-- =====================================================================
--  V10 — Phase 1 scheduling engine support
-- =====================================================================
--  Context
--    - PR9 ships LessonSession JPA + scheduling endpoints.
--      The lesson_sessions table itself was created back in V1, so this
--      migration just adds the bits the scheduling rules need.
--
--    - schools.timezone — required to interpret instructor working_hours_json
--      windows (which are LocalTime, no timezone). All scheduled_at columns
--      stay TIMESTAMPTZ; we only ever compare against the school's local
--      wall-clock when validating "is this session inside the instructor's
--      working hours?". Defaults existing rows to America/Regina (the
--      Saskatchewan tenant we're starting with). Owners can change later
--      via PUT /api/schools/{id}.
--
--    - lesson_sessions.created_by_user_id — captures who booked the row.
--      Useful for audit + the "owner / instructor / student initiated this
--      booking" diagnostic.
--
--    - lesson_sessions.cancelled_at / cancelled_by_user_id — soft-delete
--      audit pair. Nullable; populated when status = CANCELLED.
--
--  Indexes
--    - The combo (instructor_id, scheduled_at) and (student_id, scheduled_at)
--      already exist from V1. Scheduling collision queries lean on those.
--    - One new partial index (instructor_id, scheduled_at) WHERE status
--      IN ('SCHEDULED','COMPLETED') — collision detection's hot path.
-- =====================================================================

ALTER TABLE schools
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Regina';

ALTER TABLE lesson_sessions
    ADD COLUMN created_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN cancelled_at        TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

-- Partial index for collision detection — narrows the scan to active rows.
CREATE INDEX idx_sessions_instructor_active
    ON lesson_sessions(instructor_id, scheduled_at)
    WHERE status IN ('SCHEDULED', 'COMPLETED');

CREATE INDEX idx_sessions_student_active
    ON lesson_sessions(student_id, scheduled_at)
    WHERE status IN ('SCHEDULED', 'COMPLETED');
