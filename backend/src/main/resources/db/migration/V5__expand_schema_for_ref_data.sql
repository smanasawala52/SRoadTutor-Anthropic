-- =====================================================================
-- V5 — Schema additions for Excel reference data
-- =====================================================================
--  Adds:
--    * mistake_categories.points       (int, default 2)   — demerit points
--    * mistake_categories.source_code  (varchar, nullable) — e.g. 'M001'
--    * training_topics                 (new table)
--    * driver_payment_settings         (new table)
--
--  These support the richer data we ingest in V6 + V7 (from the
--  Drivers Student Manager.xlsx reference workbook).
-- =====================================================================

-- mistake_categories: add points + external source code
ALTER TABLE mistake_categories
    ADD COLUMN IF NOT EXISTS points      INT NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS source_code VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_mistake_categories_source_code
    ON mistake_categories(source_code);

-- -------------------------------------------------------------------
-- training_topics — SGI handbook index. Jurisdiction-scoped so we can
-- add ICBC / MTO / DMV equivalents later without schema changes.
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS training_topics (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction         VARCHAR(16) NOT NULL DEFAULT 'SGI',
    source_code          VARCHAR(32),                     -- e.g. 'T0001'
    category             VARCHAR(100) NOT NULL,
    topic_name           VARCHAR(200) NOT NULL,
    sgi_description      TEXT,
    youtube_link         VARCHAR(500),
    handbook_link        VARCHAR(500),
    display_order        INT NOT NULL DEFAULT 0,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (jurisdiction, source_code)
);
CREATE INDEX IF NOT EXISTS idx_training_topics_juris_order
    ON training_topics(jurisdiction, display_order);

-- -------------------------------------------------------------------
-- driver_payment_settings — per-instructor rate cards.
-- Multiple rate rows per instructor (Standard, Discounted, etc.).
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS driver_payment_settings (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id        UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    source_code          VARCHAR(32),                     -- e.g. 'DP1775860953053'
    rate_type            VARCHAR(100) NOT NULL,           -- 'Standard Rate', 'Discounted Rate (Student Car)'
    rate                 NUMERIC(10,2) NOT NULL,          -- dollars/hour
    sessions_covered     INT NOT NULL DEFAULT 1,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_driver_payment_settings_instructor
    ON driver_payment_settings(instructor_id);
