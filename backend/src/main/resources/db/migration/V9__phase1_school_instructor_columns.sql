-- =====================================================================
--  V9 — Phase 1 column adds for school + instructor modules (PR5/PR6)
-- =====================================================================
--  Context
--    - PR5 ships the School JPA entity + CRUD endpoints. We need a
--      tenant-level "soft-delete" flag so an owner can shutter their
--      school without cascading the destruction down to instructors,
--      students, sessions, and the audit log. Hard delete is forbidden
--      at the service layer.
--
--    - PR6 ships the Instructor JPA entity + endpoints. The investor
--      blueprint calls out a single hourly rate per instructor at V1
--      ("hourly rate"). The richer multi-rate model already exists in
--      V5's driver_payment_settings, but PR6 only needs the headline
--      number. driver_payment_settings remains untouched and will be
--      surfaced in PR8 (mistake logger / cost calc).
--
--  Design notes
--    - is_active on schools defaults TRUE so every existing row stays
--      live without a backfill statement.
--    - hourly_rate on instructors is NULLABLE — invited instructors who
--      haven't agreed a rate yet should not be force-defaulted to $0.
--    - No new tables. students + parent_student already exist from V1
--      and need no schema changes for PR7's add-by-owner flow.
-- =====================================================================

ALTER TABLE schools
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_schools_is_active ON schools(is_active);

ALTER TABLE instructors
    ADD COLUMN hourly_rate NUMERIC(10, 2);
