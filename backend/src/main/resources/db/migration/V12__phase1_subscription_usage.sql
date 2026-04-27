-- =====================================================================
--  V12 — Phase 1 subscription usage (PR12)
-- =====================================================================
--  The subscriptions table from V1 captures the per-school plan + Stripe
--  pointer. PR12 adds a usage counter table so we can enforce monthly
--  caps (e.g. wa.me messages/month) without scanning the full audit log
--  on every send.
--
--  One row per (school, period_start) tuple. period_start is always the
--  UTC midnight of the first day of the calendar month. The application
--  upserts on each metered event:
--
--      INSERT INTO subscription_usage (school_id, period_start, ...)
--          VALUES (..., 1) ON CONFLICT DO UPDATE SET wa_me_count = ... + 1;
--
--  PostgreSQL's ON CONFLICT (...) DO UPDATE handles the increment race-
--  free; multi-tenant traffic on the same row is the only contention
--  point and is short-lived.
-- =====================================================================

CREATE TABLE subscription_usage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID         NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    period_start    DATE         NOT NULL,
    wa_me_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_subscription_usage_school_period UNIQUE (school_id, period_start)
);

CREATE INDEX idx_subscription_usage_school ON subscription_usage(school_id);
