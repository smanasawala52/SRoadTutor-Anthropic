-- =============================================================================
--  V4 — Development-only seed data
-- =============================================================================
--  This migration inserts sample rows so you can hit the dev DB in Postman /
--  Swagger and see realistic data. It is GATED behind a Flyway placeholder so
--  it ONLY runs on the `dev` profile. QA and prod skip it entirely.
--
--  How the gate works:
--    * Base application.yml does NOT set any spring.flyway.placeholders.* key.
--    * application-dev.yml sets:
--          spring.flyway.placeholders.seed-dev: "true"
--    * application-qa.yml / application-prod.yml set:
--          spring.flyway.placeholders.seed-dev: "false"
--    * Flyway interpolates ${seed-dev} into this file at run-time.
--
--  The whole migration body is wrapped in an anonymous PL/pgSQL block that
--  checks the placeholder at the SQL level and no-ops when it's false.
--
--  DEFAULT LOGIN CREDENTIALS (dev only):
--     ---------------------------------------------------------
--     email                         | password   | role
--     owner@sroadtutor.dev          | Password1  | OWNER
--     instructor1@sroadtutor.dev    | Password1  | INSTRUCTOR
--     instructor2@sroadtutor.dev    | Password1  | INSTRUCTOR
--     student1@sroadtutor.dev       | Password1  | STUDENT
--     student2@sroadtutor.dev       | Password1  | STUDENT
--     parent1@sroadtutor.dev        | Password1  | PARENT
--     ---------------------------------------------------------
--
--  The password hash below is BCrypt(strength=12) of "Password1". It is safe
--  to hard-code here because these accounts NEVER go to production.
-- =============================================================================

DO $$
BEGIN
    IF '${seed-dev}' <> 'true' THEN
        RAISE NOTICE 'Skipping V4 dev seed — not on dev profile.';
        RETURN;
    END IF;

    -- Idempotency: if the seed owner already exists, this migration has
    -- already fired (Flyway checksums prevent re-runs anyway, but this is a
    -- second belt-and-suspenders check in case someone wipes the history).
    IF EXISTS (SELECT 1 FROM users WHERE email = 'owner@sroadtutor.dev') THEN
        RAISE NOTICE 'Dev seed already present — skipping.';
        RETURN;
    END IF;

    -- -------------------------------------------------------------------
    -- 1. Schools
    -- -------------------------------------------------------------------
    INSERT INTO schools (id, name, jurisdiction, owner_id, created_at, updated_at)
    VALUES
        ('11111111-0000-0000-0000-000000000001'::uuid, 'Regina Wheels Driving School',   'SGI', NULL, NOW(), NOW()),
        ('11111111-0000-0000-0000-000000000002'::uuid, 'Saskatoon Road Masters',          'SGI', NULL, NOW(), NOW());

    -- -------------------------------------------------------------------
    -- 2. Users (owner, 2 instructors, 2 students, 1 parent)
    --    password_hash = BCrypt("Password1", strength 12)
    -- -------------------------------------------------------------------
    INSERT INTO users (id, email, password_hash, full_name, phone_number, role, school_id, auth_provider, enabled, created_at, updated_at)
    VALUES
        ('22222222-0000-0000-0000-000000000001'::uuid, 'owner@sroadtutor.dev',       '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Olivia Owner',     '+13065550001', 'OWNER',      '11111111-0000-0000-0000-000000000001', 'LOCAL', TRUE, NOW(), NOW()),
        ('22222222-0000-0000-0000-000000000002'::uuid, 'instructor1@sroadtutor.dev', '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Ian Instructor',   '+13065550002', 'INSTRUCTOR', '11111111-0000-0000-0000-000000000001', 'LOCAL', TRUE, NOW(), NOW()),
        ('22222222-0000-0000-0000-000000000003'::uuid, 'instructor2@sroadtutor.dev', '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Isla Instructor',  '+13065550003', 'INSTRUCTOR', '11111111-0000-0000-0000-000000000002', 'LOCAL', TRUE, NOW(), NOW()),
        ('22222222-0000-0000-0000-000000000004'::uuid, 'student1@sroadtutor.dev',    '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Sam Student',      '+13065550004', 'STUDENT',    '11111111-0000-0000-0000-000000000001', 'LOCAL', TRUE, NOW(), NOW()),
        ('22222222-0000-0000-0000-000000000005'::uuid, 'student2@sroadtutor.dev',    '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Sophie Student',   '+13065550005', 'STUDENT',    '11111111-0000-0000-0000-000000000002', 'LOCAL', TRUE, NOW(), NOW()),
        ('22222222-0000-0000-0000-000000000006'::uuid, 'parent1@sroadtutor.dev',     '$2a$12$iXrA8RxN5t7lK3WqYgC6fuE7MBn/T.eC9tA1S0.fNwZ3Mq4KXgT6e', 'Parent Person',    '+13065550006', 'PARENT',     NULL,                                   'LOCAL', TRUE, NOW(), NOW());

    -- Back-fill school owners now that users exist.
    UPDATE schools SET owner_id = '22222222-0000-0000-0000-000000000001' WHERE id = '11111111-0000-0000-0000-000000000001';
    UPDATE schools SET owner_id = '22222222-0000-0000-0000-000000000003' WHERE id = '11111111-0000-0000-0000-000000000002';

    -- -------------------------------------------------------------------
    -- 3. Instructors (1:1 with users of role INSTRUCTOR)
    -- -------------------------------------------------------------------
    INSERT INTO instructors (id, user_id, school_id, hourly_rate_cents, active, created_at, updated_at)
    VALUES
        ('33333333-0000-0000-0000-000000000001'::uuid, '22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', 6500, TRUE, NOW(), NOW()),
        ('33333333-0000-0000-0000-000000000002'::uuid, '22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002', 7000, TRUE, NOW(), NOW());

    -- -------------------------------------------------------------------
    -- 4. Students (1:1 with users of role STUDENT)
    -- -------------------------------------------------------------------
    INSERT INTO students (id, user_id, school_id, license_stage, target_test_date, created_at, updated_at)
    VALUES
        ('44444444-0000-0000-0000-000000000001'::uuid, '22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000001', 'LEARNER', NOW() + INTERVAL '30 days', NOW(), NOW()),
        ('44444444-0000-0000-0000-000000000002'::uuid, '22222222-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000002', 'LEARNER', NOW() + INTERVAL '45 days', NOW(), NOW());

    -- -------------------------------------------------------------------
    -- 5. Parent-student link
    -- -------------------------------------------------------------------
    INSERT INTO parent_student (parent_user_id, student_id, created_at)
    VALUES
        ('22222222-0000-0000-0000-000000000006', '44444444-0000-0000-0000-000000000001', NOW());

    -- -------------------------------------------------------------------
    -- 6. Sample lesson sessions (past + upcoming)
    -- -------------------------------------------------------------------
    INSERT INTO lesson_sessions (id, instructor_id, student_id, scheduled_start, scheduled_end, status, odometer_start_km, odometer_end_km, notes, created_at, updated_at)
    VALUES
        ('55555555-0000-0000-0000-000000000001'::uuid, '33333333-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000001', NOW() - INTERVAL '3 days' , NOW() - INTERVAL '3 days'  + INTERVAL '1 hour', 'COMPLETED', 12000, 12028, 'Parking + 3-point turn practice.',                                NOW() - INTERVAL '3 days',  NOW() - INTERVAL '3 days'),
        ('55555555-0000-0000-0000-000000000002'::uuid, '33333333-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000001', NOW() + INTERVAL '2 days' , NOW() + INTERVAL '2 days'  + INTERVAL '1 hour', 'SCHEDULED', NULL , NULL , 'Highway merge + lane-change practice.',                            NOW(),                      NOW()),
        ('55555555-0000-0000-0000-000000000003'::uuid, '33333333-0000-0000-0000-000000000002', '44444444-0000-0000-0000-000000000002', NOW() - INTERVAL '1 day'  , NOW() - INTERVAL '1 day'   + INTERVAL '1 hour', 'COMPLETED', 45100, 45132, 'First lesson — vehicle orientation, seat + mirror adjustment.',    NOW() - INTERVAL '1 day',   NOW() - INTERVAL '1 day');

    -- -------------------------------------------------------------------
    -- 7. Session mistakes (pulled from the SGI taxonomy in V3)
    -- -------------------------------------------------------------------
    INSERT INTO session_mistakes (id, session_id, category_id, severity, notes, created_at)
    SELECT
        gen_random_uuid(),
        '55555555-0000-0000-0000-000000000001'::uuid,
        mc.id,
        mc.severity,
        'Recorded during lesson 1.',
        NOW()
    FROM mistake_categories mc
    WHERE mc.jurisdiction = 'SGI' AND mc.code IN ('SIGNAL_FAIL', 'MIRROR_CHECK_MISS');

    -- -------------------------------------------------------------------
    -- 8. Payment record for lesson 1
    -- -------------------------------------------------------------------
    INSERT INTO payments (id, student_id, amount_cents, currency, status, method, description, paid_at, created_at, updated_at)
    VALUES
        ('66666666-0000-0000-0000-000000000001'::uuid, '44444444-0000-0000-0000-000000000001', 6500, 'CAD', 'PAID', 'E_TRANSFER', 'Lesson #1 — Regina Wheels', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

    RAISE NOTICE 'V4 dev seed applied successfully.';
END
$$ LANGUAGE plpgsql;
