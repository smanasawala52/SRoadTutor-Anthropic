-- =====================================================================
-- V1: core schema from blueprint (Phase 1 and later).  Tables are created
-- in an order that respects FK dependencies.  Integer enums are avoided —
-- we store strings so Hibernate's @Enumerated(STRING) stays trivially
-- debuggable with psql.
--
-- NOTE on ids: everything uses UUID v4 via pgcrypto's gen_random_uuid().
-- Enable the extension once.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------
-- schools
-- ---------------------------------------------------------------------
CREATE TABLE schools (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    owner_id            UUID,                           -- FK added after users exists
    plan_tier           VARCHAR(32) NOT NULL DEFAULT 'FREE',
    stripe_customer_id  VARCHAR(128),
    province            VARCHAR(8),                     -- SK, AB, BC, ON, etc.
    jurisdiction        VARCHAR(16) NOT NULL DEFAULT 'SGI', -- SGI | ICBC | MTO | DMV
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_schools_owner ON schools(owner_id);
CREATE INDEX idx_schools_jurisdiction ON schools(jurisdiction);

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID REFERENCES schools(id) ON DELETE SET NULL,
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(120),
    full_name           VARCHAR(200),
    phone               VARCHAR(32),
    role                VARCHAR(32) NOT NULL,
    auth_provider       VARCHAR(32) NOT NULL,
    provider_user_id    VARCHAR(128),
    language_pref       VARCHAR(8) NOT NULL DEFAULT 'en',
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_school ON users(school_id);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_provider ON users(auth_provider, provider_user_id);

ALTER TABLE schools
    ADD CONSTRAINT fk_schools_owner FOREIGN KEY (owner_id)
    REFERENCES users(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

-- ---------------------------------------------------------------------
-- instructors
-- ---------------------------------------------------------------------
CREATE TABLE instructors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    license_no          VARCHAR(64),
    sgi_cert            VARCHAR(64),
    vehicle_make        VARCHAR(64),
    vehicle_model       VARCHAR(64),
    vehicle_year        INT,
    working_hours_json  JSONB,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_instructors_school ON instructors(school_id);

-- ---------------------------------------------------------------------
-- students
-- ---------------------------------------------------------------------
CREATE TABLE students (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    school_id                UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    instructor_id            UUID REFERENCES instructors(id) ON DELETE SET NULL,
    package_total_lessons    INT NOT NULL DEFAULT 0,
    lessons_remaining        INT NOT NULL DEFAULT 0,
    status                   VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | PASSED | DROPPED
    road_test_date           DATE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_students_school ON students(school_id);
CREATE INDEX idx_students_instructor ON students(instructor_id);
CREATE INDEX idx_students_status ON students(status);

-- ---------------------------------------------------------------------
-- parent_student — many parents can be linked to one student
-- ---------------------------------------------------------------------
CREATE TABLE parent_student (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_id          UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    relationship        VARCHAR(32) DEFAULT 'PARENT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parent_user_id, student_id)
);
CREATE INDEX idx_parent_student_student ON parent_student(student_id);

-- ---------------------------------------------------------------------
-- sessions
-- ---------------------------------------------------------------------
CREATE TABLE lesson_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    instructor_id       UUID NOT NULL REFERENCES instructors(id) ON DELETE RESTRICT,
    student_id          UUID NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    scheduled_at        TIMESTAMPTZ NOT NULL,
    duration_mins       INT NOT NULL DEFAULT 60,
    status              VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    location            VARCHAR(500),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_school_time ON lesson_sessions(school_id, scheduled_at);
CREATE INDEX idx_sessions_instructor_time ON lesson_sessions(instructor_id, scheduled_at);
CREATE INDEX idx_sessions_student_time ON lesson_sessions(student_id, scheduled_at);

-- ---------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    student_id          UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    session_id          UUID REFERENCES lesson_sessions(id) ON DELETE SET NULL,
    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'CAD',
    method              VARCHAR(32) NOT NULL,           -- CASH | ETRANSFER | STRIPE
    status              VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    paid_at             TIMESTAMPTZ,
    stripe_payment_id   VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_student ON payments(student_id);
CREATE INDEX idx_payments_school_status ON payments(school_id, status);

-- ---------------------------------------------------------------------
-- mistake_categories (seeded in V3)
-- ---------------------------------------------------------------------
CREATE TABLE mistake_categories (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction        VARCHAR(16) NOT NULL,           -- SGI | ICBC | MTO | DMV
    category_name       VARCHAR(120) NOT NULL,
    severity            VARCHAR(16) NOT NULL,           -- MINOR | MAJOR | FAIL
    display_order       INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (jurisdiction, category_name)
);
CREATE INDEX idx_mistake_categories_juris ON mistake_categories(jurisdiction, display_order);

-- ---------------------------------------------------------------------
-- session_mistakes
-- ---------------------------------------------------------------------
CREATE TABLE session_mistakes (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID NOT NULL REFERENCES lesson_sessions(id) ON DELETE CASCADE,
    student_id              UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    mistake_category_id     UUID NOT NULL REFERENCES mistake_categories(id) ON DELETE RESTRICT,
    count                   INT NOT NULL DEFAULT 1,
    instructor_notes        TEXT,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_mistakes_session ON session_mistakes(session_id);
CREATE INDEX idx_session_mistakes_student ON session_mistakes(student_id);

-- ---------------------------------------------------------------------
-- road_test_results
-- ---------------------------------------------------------------------
CREATE TABLE road_test_results (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id                  UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    test_date                   DATE NOT NULL,
    result                      VARCHAR(16) NOT NULL,  -- PASS | FAIL
    examiner_notes              TEXT,
    attempt_number              INT NOT NULL DEFAULT 1,
    readiness_score_at_test     NUMERIC(5,2),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_road_test_student ON road_test_results(student_id);

-- ---------------------------------------------------------------------
-- reminders
-- ---------------------------------------------------------------------
CREATE TABLE reminders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES lesson_sessions(id) ON DELETE CASCADE,
    recipient_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel             VARCHAR(32) NOT NULL,          -- WHATSAPP | SMS | PUSH | EMAIL
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    payload_json        JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reminders_session ON reminders(session_id);

-- ---------------------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id               UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    plan                    VARCHAR(32) NOT NULL,      -- FREE | PRO | GROWTH | ENTERPRISE
    stripe_sub_id           VARCHAR(128),
    current_period_end      TIMESTAMPTZ,
    instructor_limit        INT,
    student_limit           INT,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_school ON subscriptions(school_id);

-- ---------------------------------------------------------------------
-- dealership_leads (Phase 2)
-- ---------------------------------------------------------------------
CREATE TABLE dealership_leads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID REFERENCES students(id) ON DELETE SET NULL,
    parent_user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    vehicle_pref_json   JSONB,
    budget              NUMERIC(12,2),
    financing_ready     BOOLEAN,
    dealership_id       UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'NEW',
    bounty_amount       NUMERIC(12,2),
    converted_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- dealerships (Phase 2)
-- ---------------------------------------------------------------------
CREATE TABLE dealerships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    city                VARCHAR(100),
    province            VARCHAR(8),
    crm_type            VARCHAR(64),
    crm_api_key_encrypted VARCHAR(500),
    bounty_per_lead     NUMERIC(12,2),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE dealership_leads
    ADD CONSTRAINT fk_dealership_leads_dealership FOREIGN KEY (dealership_id)
    REFERENCES dealerships(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- instructor_payouts (Phase 2)
-- ---------------------------------------------------------------------
CREATE TABLE instructor_payouts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id       UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    lead_id             UUID REFERENCES dealership_leads(id) ON DELETE SET NULL,
    payout_amount       NUMERIC(12,2) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    e_transfer_ref      VARCHAR(128),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- risk_scores (Phase 3)
-- ---------------------------------------------------------------------
CREATE TABLE risk_scores (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_anonymized_hash VARCHAR(128) NOT NULL,
    mistake_profile_json    JSONB NOT NULL,
    risk_tier               VARCHAR(32) NOT NULL,
    generated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    licensed_to_insurer     VARCHAR(200)
);
CREATE INDEX idx_risk_scores_hash ON risk_scores(student_anonymized_hash);
