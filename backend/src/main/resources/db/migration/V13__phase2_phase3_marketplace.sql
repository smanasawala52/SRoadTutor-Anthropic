-- =====================================================================
--  V13 — Phase 2 insurance + Phase 3 telemetry tables
-- =====================================================================
--  Three new tables for the next-wave revenue layers:
--
--    * insurance_brokers / insurance_leads — Phase 2 insurance lead
--      routing. Mirrors the dealership pattern from V1: brokers are
--      seeded by platform admin, leads are auto-routed when a student
--      transitions to status=PASSED, and the broker's bounty_per_quote
--      is copied onto the lead at routing time.
--
--    * telemetry_events — Phase 3 AV edge-case dataset. Each row is a
--      vehicle-telemetry snapshot keyed off a single session_mistake_id,
--      so the resulting export carries (mistake category, severity,
--      labelled telemetry payload). The {@code labelled_at} timestamp +
--      offset (relative to mistake) lets researchers reconstruct the
--      lead-up window.
--
--  The Phase 3 risk_scores table from V1 is re-used as-is — no schema
--  change needed.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. insurance_brokers — partnered insurance providers
-- ---------------------------------------------------------------------
CREATE TABLE insurance_brokers (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(200) NOT NULL,
    contact_email            VARCHAR(254),
    province                 VARCHAR(8),                   -- 2-letter province; null = nationwide
    crm_api_key_encrypted    VARCHAR(500),                 -- encrypted at rest in PR-marketplace-2 (TD)
    bounty_per_quote         NUMERIC(12, 2),
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_insurance_brokers_province ON insurance_brokers(province) WHERE active;

-- ---------------------------------------------------------------------
-- 2. insurance_leads — graduation-driven lead pipeline
-- ---------------------------------------------------------------------
CREATE TABLE insurance_leads (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID REFERENCES students(id) ON DELETE SET NULL,
    broker_id       UUID REFERENCES insurance_brokers(id) ON DELETE SET NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'NEW',     -- NEW | ROUTED | QUOTED | CONVERTED | DEAD
    bounty_amount   NUMERIC(12, 2),
    quoted_at       TIMESTAMPTZ,
    converted_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_insurance_leads_student   ON insurance_leads(student_id);
CREATE INDEX idx_insurance_leads_broker    ON insurance_leads(broker_id);
CREATE INDEX idx_insurance_leads_status    ON insurance_leads(status);

-- ---------------------------------------------------------------------
-- 3. telemetry_events — labelled vehicle-telemetry datapoints
-- ---------------------------------------------------------------------
CREATE TABLE telemetry_events (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_mistake_id       UUID         NOT NULL REFERENCES session_mistakes(id) ON DELETE CASCADE,
    vehicle_make             VARCHAR(64),
    vehicle_model            VARCHAR(64),
    vehicle_year             INT,
    telemetry_json           JSONB        NOT NULL,
    /* Offset of the snapshot relative to logged_at on the parent mistake.
       Negative = before the mistake (lead-up). Useful for AV training data. */
    offset_ms                BIGINT,
    synced_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_telemetry_session_mistake ON telemetry_events(session_mistake_id);
CREATE INDEX idx_telemetry_vehicle         ON telemetry_events(vehicle_make, vehicle_model);
