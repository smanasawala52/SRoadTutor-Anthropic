-- =====================================================================
--  V8 — Phase 1 foundation
-- =====================================================================
--  Adds everything the locked design (docs/architecture/01-foundation-design.md)
--  needs to land for Phase 1: usernames, email/phone verification, schools
--  metadata, instructor↔schools M:N, phone numbers, invitations,
--  WhatsApp templates + audit log, email verification tokens.
--
--  This migration locks the three forks decided at PR2 kickoff:
--
--    Q1a  parent_id REMOVED from phone_numbers. Parents are users (role=PARENT),
--         so a parent's phones use the user_id FK like every other login-capable
--         actor. Schools/instructors/students get their own FK columns because
--         they ARE separate entities.
--    Q2a  users.email_verified BOOLEAN dropped. Replaced by users.email_verified_at
--         TIMESTAMPTZ — null = unverified, set = verified-at-this-moment.
--         Existing rows with email_verified=true get email_verified_at backfilled
--         from updated_at (or created_at if updated_at is null).
--    Q3a  users.phone column dropped. Legacy values backfilled into phone_numbers
--         as the user's primary number. Only canonical NA formats parse cleanly;
--         non-matching rows are dropped on the floor and re-entered via PR4 CRUD.
--
--  Step 13 from §9 of the foundation design ("ALTER TYPE auth_provider DROP VALUE
--  'FACEBOOK'") is N/A — V1 stored auth_provider as VARCHAR(32), not a Postgres
--  native enum, so there is no enum value to drop. The defensive DELETE in
--  step 11 is the only Facebook DB cleanup needed.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users — new columns + backfills + legacy column drops
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN username             VARCHAR(64),
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verified_at    TIMESTAMPTZ,
    ADD COLUMN phone_verified_at    TIMESTAMPTZ;

-- (Q2a) Backfill email_verified_at from the legacy boolean before we drop it.
UPDATE users
SET    email_verified_at = COALESCE(updated_at, created_at)
WHERE  email_verified = TRUE
  AND  email_verified_at IS NULL;

-- (D7) Backfill username deterministically: lower(email-prefix)_<first 8 of id>.
-- Truncated to 50 chars on the prefix so the suffix always fits within VARCHAR(64).
UPDATE users
SET    username = LOWER(
                    SUBSTRING(SPLIT_PART(email, '@', 1) FROM 1 FOR 50)
                    || '_'
                    || SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 1 FOR 8)
                )
WHERE  username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;

-- Case-insensitive uniqueness on username, mirroring the email pattern.
CREATE UNIQUE INDEX ux_users_username_lower ON users (LOWER(username));

-- (Q3a) Backfill users.phone → phone_numbers as the user's primary number.
-- Only canonical North-American formats parse cleanly here. Anything else is
-- left to PR4's phone CRUD to capture explicitly. (phone_numbers itself is
-- created in §5 below; this INSERT lives there to respect FK ordering.)

-- (Q2a) Drop the legacy boolean. Java derives isEmailVerified() from
-- emailVerifiedAt != null going forward.
ALTER TABLE users DROP COLUMN email_verified;

-- ---------------------------------------------------------------------
-- 2. schools — tax IDs, registration #, metadata, synthetic flag (D12)
-- ---------------------------------------------------------------------
ALTER TABLE schools
    ADD COLUMN gst_number                   VARCHAR(40),
    ADD COLUMN pst_number                   VARCHAR(40),
    ADD COLUMN hst_number                   VARCHAR(40),
    ADD COLUMN business_registration_number VARCHAR(80),
    ADD COLUMN metadata                     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN is_synthetic                 BOOLEAN      NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- 3. instructors — school_id becomes nullable (D5), small profile adds
-- ---------------------------------------------------------------------
ALTER TABLE instructors
    ALTER COLUMN school_id DROP NOT NULL;

ALTER TABLE instructors
    ADD COLUMN vehicle_plate VARCHAR(20),
    ADD COLUMN bio           TEXT;

-- ---------------------------------------------------------------------
-- 4. instructor_schools — M:N join (D5)
-- ---------------------------------------------------------------------
CREATE TABLE instructor_schools (
    instructor_id  UUID         NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    school_id      UUID         NOT NULL REFERENCES schools(id)     ON DELETE CASCADE,
    role_at_school VARCHAR(32)  NOT NULL DEFAULT 'REGULAR',  -- OWNER | HEAD | REGULAR
    joined_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    left_at        TIMESTAMPTZ,
    PRIMARY KEY (instructor_id, school_id)
);
CREATE INDEX ix_instructor_schools_school ON instructor_schools(school_id);

-- Backfill: any instructor that already has a school_id gets a join row.
INSERT INTO instructor_schools (instructor_id, school_id, role_at_school, joined_at)
SELECT id, school_id, 'REGULAR', COALESCE(created_at, NOW())
FROM   instructors
WHERE  school_id IS NOT NULL
ON CONFLICT (instructor_id, school_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5. phone_numbers — 1..N phones for any owner type (D13/D14/D15/D16)
--    Q1a: parent_id is intentionally NOT a column. Parents reuse user_id.
-- ---------------------------------------------------------------------
CREATE TABLE phone_numbers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- exactly ONE of these owner FKs must be non-null (CHECK below)
    user_id         UUID REFERENCES users(id)       ON DELETE CASCADE,
    school_id       UUID REFERENCES schools(id)     ON DELETE CASCADE,
    instructor_id   UUID REFERENCES instructors(id) ON DELETE CASCADE,
    student_id      UUID REFERENCES students(id)    ON DELETE CASCADE,

    -- E.164 storage (D16)
    country_code    VARCHAR(4)  NOT NULL,           -- digits only, no leading "+"
    national_number VARCHAR(20) NOT NULL,           -- digits only
    e164            VARCHAR(20) NOT NULL,           -- "+13065551234"

    label           VARCHAR(40),                    -- "Mobile", "Office", "Home"
    is_primary      BOOLEAN     NOT NULL DEFAULT FALSE,
    is_whatsapp     BOOLEAN     NOT NULL DEFAULT TRUE,
    whatsapp_opt_in BOOLEAN     NOT NULL DEFAULT TRUE,
    verified_at     TIMESTAMPTZ,                    -- null = unverified

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Exactly-one-owner: the cheapest portable enforcement of polymorphic
    -- ownership. Polymorphic (owner_type, owner_id) FKs would need triggers.
    CONSTRAINT phone_owner_exactly_one CHECK (
        (CASE WHEN user_id       IS NULL THEN 0 ELSE 1 END)
      + (CASE WHEN school_id     IS NULL THEN 0 ELSE 1 END)
      + (CASE WHEN instructor_id IS NULL THEN 0 ELSE 1 END)
      + (CASE WHEN student_id    IS NULL THEN 0 ELSE 1 END) = 1
    )
);

-- One primary number per owner — partial unique indexes per owner column.
CREATE UNIQUE INDEX ux_phone_primary_user
    ON phone_numbers(user_id)       WHERE is_primary AND user_id       IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_school
    ON phone_numbers(school_id)     WHERE is_primary AND school_id     IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_instructor
    ON phone_numbers(instructor_id) WHERE is_primary AND instructor_id IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_student
    ON phone_numbers(student_id)    WHERE is_primary AND student_id    IS NOT NULL;

-- A given e164 cannot appear twice for the SAME owner. D14 still allows
-- the SAME number across DIFFERENT owners (parent + student sharing a line).
CREATE UNIQUE INDEX ux_phone_user_e164
    ON phone_numbers(user_id, e164)       WHERE user_id       IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_school_e164
    ON phone_numbers(school_id, e164)     WHERE school_id     IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_instructor_e164
    ON phone_numbers(instructor_id, e164) WHERE instructor_id IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_student_e164
    ON phone_numbers(student_id, e164)    WHERE student_id    IS NOT NULL;

CREATE INDEX ix_phone_e164             ON phone_numbers(e164);
CREATE INDEX ix_phone_whatsapp_lookup  ON phone_numbers(e164)
    WHERE is_whatsapp AND whatsapp_opt_in;

-- (Q3a) Legacy users.phone backfill. Two narrow patterns; anything else is
-- skipped intentionally.
--
--   Case A: already E.164 NA      "+1XXXXXXXXXX"   → split as country=1
--   Case B: 10 plain NA digits    "XXXXXXXXXX"     → prefix +1
--
-- We dodge full free-form parsing on purpose. The dev seed (V7) is Canadian,
-- and PR4's phone CRUD will be the canonical entry point going forward.
INSERT INTO phone_numbers (
    user_id, country_code, national_number, e164,
    label, is_primary, is_whatsapp, whatsapp_opt_in, created_at, updated_at
)
SELECT u.id, '1', SUBSTRING(u.phone FROM 3 FOR 10), u.phone,
       'Mobile', TRUE, TRUE, TRUE, NOW(), NOW()
FROM   users u
WHERE  u.phone ~ '^\+1[0-9]{10}$';

INSERT INTO phone_numbers (
    user_id, country_code, national_number, e164,
    label, is_primary, is_whatsapp, whatsapp_opt_in, created_at, updated_at
)
SELECT u.id, '1', u.phone, '+1' || u.phone,
       'Mobile', TRUE, TRUE, TRUE, NOW(), NOW()
FROM   users u
WHERE  u.phone ~ '^[0-9]{10}$';

-- (Q3a) Drop the legacy column now that anything parseable has moved.
ALTER TABLE users DROP COLUMN phone;

-- ---------------------------------------------------------------------
-- 6. invitations — tokenized owner→user invites (D6)
-- ---------------------------------------------------------------------
CREATE TABLE invitations (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id          UUID         NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    invited_by_user_id UUID         NOT NULL REFERENCES users(id),
    email              VARCHAR(254) NOT NULL,
    username           VARCHAR(64)  NOT NULL,
    role               VARCHAR(32)  NOT NULL,   -- INSTRUCTOR | STUDENT | PARENT
    delivery_mode      VARCHAR(16)  NOT NULL,   -- TOKEN | DUMMY_PWD
    token_hash         VARCHAR(128),            -- sha-256 hex; null for DUMMY_PWD
    status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | EXPIRED | REVOKED
    expires_at         TIMESTAMPTZ,
    accepted_at        TIMESTAMPTZ,
    accepted_user_id   UUID REFERENCES users(id),
    metadata           JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- holds optional instructorDetails
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_invitation_token_or_dummy CHECK (
        (delivery_mode = 'TOKEN'     AND token_hash IS NOT NULL)
     OR (delivery_mode = 'DUMMY_PWD' AND token_hash IS NULL)
    )
);
CREATE UNIQUE INDEX ux_invitations_token_hash
    ON invitations(token_hash) WHERE token_hash IS NOT NULL;
CREATE INDEX ix_invitations_school     ON invitations(school_id);
CREATE INDEX ix_invitations_email_lower ON invitations(LOWER(email));
CREATE INDEX ix_invitations_status     ON invitations(status);

-- ---------------------------------------------------------------------
-- 7. whatsapp_templates — reusable bodies with {{placeholders}}
-- ---------------------------------------------------------------------
CREATE TABLE whatsapp_templates (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    UUID REFERENCES schools(id) ON DELETE CASCADE,  -- null = platform-level
    code         VARCHAR(64)  NOT NULL,        -- "lesson_reminder", "payment_due"
    label        VARCHAR(120) NOT NULL,
    language     VARCHAR(8)   NOT NULL DEFAULT 'en',
    body         TEXT         NOT NULL,        -- holds {{placeholder}} tokens
    placeholders JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- expected names
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- Two partial unique indexes give the same effect as a unique
-- COALESCE(school_id, sentinel) without the expression-index gymnastics.
CREATE UNIQUE INDEX ux_whatsapp_templates_school_code_lang
    ON whatsapp_templates(school_id, code, language) WHERE school_id IS NOT NULL;
CREATE UNIQUE INDEX ux_whatsapp_templates_platform_code_lang
    ON whatsapp_templates(code, language) WHERE school_id IS NULL;

-- ---------------------------------------------------------------------
-- 8. whatsapp_message_log — audit of every wa.me link generated
-- ---------------------------------------------------------------------
CREATE TABLE whatsapp_message_log (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id     UUID         NOT NULL REFERENCES users(id),
    recipient_phone_id UUID         NOT NULL REFERENCES phone_numbers(id) ON DELETE CASCADE,
    template_id        UUID REFERENCES whatsapp_templates(id),
    rendered_body      TEXT         NOT NULL,
    school_id          UUID REFERENCES schools(id),  -- tenant context
    correlation_id     VARCHAR(64),
    link_generated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    link_clicked_at    TIMESTAMPTZ                   -- set by /whatsapp/log/{id}/clicked beacon
);
CREATE INDEX ix_whatsapp_log_sender       ON whatsapp_message_log(sender_user_id);
CREATE INDEX ix_whatsapp_log_recipient    ON whatsapp_message_log(recipient_phone_id);
CREATE INDEX ix_whatsapp_log_school_time  ON whatsapp_message_log(school_id, link_generated_at);

-- ---------------------------------------------------------------------
-- 9. email_verification_tokens — one-shot signup verification
-- ---------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    raw_token  VARCHAR(6) NULL,  -- sha-256 hex
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX ix_email_verification_user   ON email_verification_tokens(user_id);
CREATE INDEX ix_email_verification_active ON email_verification_tokens(user_id) WHERE consumed_at IS NULL;

-- ---------------------------------------------------------------------
-- 10. defensive Facebook cleanup (D17)
--     Should be 0 rows in QA but executes anyway for any environment that
--     predates PR1.
-- ---------------------------------------------------------------------
DELETE FROM users WHERE auth_provider = 'FACEBOOK';
