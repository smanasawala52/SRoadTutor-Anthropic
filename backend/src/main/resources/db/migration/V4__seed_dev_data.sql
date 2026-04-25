-- =============================================================================
--  V4 — TOMBSTONE (deprecated dev seed)
-- =============================================================================
--  This file used to seed fake users / schools / lessons during early
--  development.  As of 2026-04-24 we replaced it with real Excel-based
--  reference + customer data spread across V5 / V6 / V7:
--
--      V5  expand schema (mistake_categories.points/source_code,
--          training_topics, driver_payment_settings)
--      V6  56 SGI mistake categories + 99 training topics
--      V7  1 school + 2 instructors + 11 students + sessions + payments
--
--  We can't delete this file — Flyway requires the migration sequence to
--  stay contiguous.  Removing it would change the schema version map and
--  re-trigger the whole pipeline on every existing environment.  So we
--  leave the file in place as a NO-OP and document why.
--
--  Next migration version to add: V8.
-- =============================================================================

DO $body$
BEGIN
    RAISE NOTICE 'V4 is now a tombstone — see V5/V6/V7 for the real seed data.';
END
$body$ LANGUAGE plpgsql;
