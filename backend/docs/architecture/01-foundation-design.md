# SRoadTutor — Foundation Design (Phase 1)

**Status:** Active
**Owner:** Shabbir K
**Last updated:** 2026-04-24
**Scope:** Auth + RBAC + Instructor Onboarding + School + Phone/WhatsApp + Multi-school

This is the canonical reference for the Phase-1 foundation. Every code change in PR1–PR5 traces back to a section here. If reality diverges from this doc, this doc is wrong — fix it before committing.

---

## 1. Decisions captured

| # | Decision | Source |
|---|---|---|
| D1 | 4 roles only: `OWNER`, `INSTRUCTOR`, `STUDENT`, `PARENT`. | User Q1 |
| D2 | "Supervisor" is contextual — an Instructor supervising a learner. No 5th role. | User Q1 |
| D3 | Instructor is a first-class citizen. Can self-register without a school. | User clarification |
| D4 | Self-registered instructor with no school auto-becomes `OWNER` of a synthetic one-person school. | User clarification |
| D5 | One Instructor ↔ many Schools (or zero). M:N via `instructor_schools` join table. | User clarification |
| D6 | Owner can pre-create a User row with dummy password `test123` AND/OR send a tokenized invite email. Both flows live. | User clarification |
| D7 | `users.username` is required AND globally unique. `users.email` is also globally unique. | User clarification |
| D8 | Email is verified at signup via Spring Mail SMTP (free tier). | User clarification + Q4 |
| D9 | WhatsApp number is verified post-signup via Option B (self-attest with admin confirmation). Email is hard-verified at signup. See §11. | User Q4, Q6 |
| D10 | No fixed working hours table. Calendar-style availability with cancel/reschedule will be modelled separately in Phase 2. | User clarification |
| D11 | License # and SGI cert are placeholder columns — no validation logic. | User clarification |
| D12 | Schools carry GST, PST, HST, business registration #, plus a `metadata JSONB` for forward-compat. | User clarification |
| D13 | All actors (School, User, Instructor, Student, Parent) carry 1..N phone numbers via a single `phone_numbers` table. | User WhatsApp requirement |
| D14 | Phone rows are **duplicated per owner** when shared (parent + student). Simpler than a join table. | User Q2 |
| D15 | Every phone has `is_whatsapp` + `whatsapp_opt_in` booleans. WhatsApp delivery uses **wa.me click-to-chat from the sender's own device** — backend never sends. | User Q3 |
| D16 | Default country code `+1`, but UI exposes a country-code picker. Storage is full E.164 (`+15551234567`). | User Q5 |
| D17 | Facebook OAuth is **removed entirely** — code, deps, tests, env templates, README. | User original |
| D18 | Twilio is not used. Confirmed absent from the codebase. | Audit |
| D19 | V7 customer seed is gated behind `seed-customer` placeholder. DEV/QA load it; PROD starts empty. | User Q-H |
| D20 | Secret rotation deferred until higher environments. | User Q-G |
| D21 | DEV profile points at the QA Supabase project `yemojzjcxdbdwlmuvqhm` (intentional). The unused `pnakuzxvbcbxzcfubaev` project stays untouched. | User Q-I |
| D22 | `phone_numbers.parent_id` is intentionally not modeled. PARENT is a role on `users`, so a parent's phones reuse `phone_numbers.user_id`. Schools / instructors / students keep their own FK columns because they are independent entities. | User Q1a (PR2 kickoff) |
| D23 | `users.email_verified BOOLEAN` is dropped in V8. Replaced by `email_verified_at TIMESTAMPTZ` (null = unverified). Existing `email_verified=TRUE` rows get `email_verified_at` backfilled from `updated_at` (or `created_at`). Java derives `isEmailVerified()` from `emailVerifiedAt != null`. | User Q2a (PR2 kickoff) |
| D24 | `users.phone` column dropped in V8. Canonical NA phones (matching `^\+1[0-9]{10}$` or `^[0-9]{10}$`) are backfilled into `phone_numbers` as the user's primary, `is_whatsapp=TRUE`, `whatsapp_opt_in=TRUE`, `verified_at=NULL`. Free-form values are not parsed; PR4's phone CRUD is the canonical entry point going forward. | User Q3a (PR2 kickoff) |

---

## 2. Roles and access model

```
OWNER       — owns one or more schools. Full CRUD on their school(s),
              instructors, students, parents, payments, dashboards.
INSTRUCTOR  — teaches at 0..N schools. CRUD on their own students,
              sessions, mistakes, payouts. Read-only on schools they belong to.
STUDENT     — sees their own profile, sessions, mistakes, payments,
              upcoming lessons, road-test results.
PARENT      — sees one or more linked students (parent_student M:N).
              Read-only access to their kids' progress + payments.
```

Enforcement is Spring Security `@PreAuthorize` on every controller method.
Tenant guard: if a JWT carries `school_id`, requests touching school-scoped
resources MUST match that `school_id` or get 403. A self-registered Instructor
who hasn't joined any school carries `school_id = null` and can only see their
own data.

`schoolIds` (plural) is added to the JWT as a string array claim because
D5 makes one instructor ↔ many schools possible.

---

## 3. Phase-1 entity inventory

| Table | New / Changed | Purpose |
|---|---|---|
| `users` | CHANGED | + `username UNIQUE NOT NULL`, + `phone_verified_at`, + `email_verified_at` |
| `schools` | CHANGED | + `gst_number`, `pst_number`, `hst_number`, `business_registration_number`, `metadata JSONB` |
| `instructors` | CHANGED | `school_id` → NULLABLE; `license_number` and `sgi_cert_number` already nullable |
| `instructor_schools` | NEW | M:N join — `(instructor_id, school_id, role_at_school, joined_at, left_at)` |
| `phone_numbers` | NEW | All phone numbers for any owner type. See §4. |
| `invitations` | NEW | Tokenized owner→user invitations. See §6. |
| `whatsapp_templates` | NEW | Reusable message templates with `{{placeholders}}`. |
| `whatsapp_message_log` | NEW | Audit of every wa.me link generated/clicked. |
| `email_verification_tokens` | NEW | One-shot tokens for email verification. |

---

## 4. Phone number model

### 4.1 Table shape

```sql
CREATE TABLE phone_numbers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ownership: exactly ONE of these FK columns is non-null (CHECK constraint)
    -- NOTE D22 (Q1a): there is intentionally no parent_id column. PARENT is a
    -- role on users; a parent's phones use user_id like every other login-capable
    -- actor. Schools / instructors / students keep their own FK because they ARE
    -- separate entities.
    user_id            UUID REFERENCES users(id)        ON DELETE CASCADE,
    school_id          UUID REFERENCES schools(id)      ON DELETE CASCADE,
    instructor_id      UUID REFERENCES instructors(id)  ON DELETE CASCADE,
    student_id         UUID REFERENCES students(id)     ON DELETE CASCADE,

    -- E.164 storage
    country_code       VARCHAR(4)  NOT NULL,            -- "1", "91", "44"
    national_number    VARCHAR(20) NOT NULL,            -- "3065551234"
    e164               VARCHAR(20) NOT NULL,            -- "+13065551234" (computed/checked)

    -- presentation + flags
    label              VARCHAR(40),                     -- "Mobile", "Office", "Home"
    is_primary         BOOLEAN NOT NULL DEFAULT FALSE,
    is_whatsapp        BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_opt_in    BOOLEAN NOT NULL DEFAULT TRUE,

    -- verification
    verified_at        TIMESTAMPTZ,                     -- null = unverified

    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- exactly-one-owner enforcement (D22: parent_id intentionally omitted)
    CONSTRAINT phone_owner_exactly_one CHECK (
        (CASE WHEN user_id       IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN school_id     IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN instructor_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN student_id    IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT phone_e164_format CHECK (e164 ~ '^\+[1-9][0-9]{6,18}$')
);

-- one primary per owner (partial unique indexes)
CREATE UNIQUE INDEX ux_phone_primary_user
    ON phone_numbers(user_id)       WHERE is_primary AND user_id       IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_school
    ON phone_numbers(school_id)     WHERE is_primary AND school_id     IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_instructor
    ON phone_numbers(instructor_id) WHERE is_primary AND instructor_id IS NOT NULL;
CREATE UNIQUE INDEX ux_phone_primary_student
    ON phone_numbers(student_id)    WHERE is_primary AND student_id    IS NOT NULL;

-- a number cannot appear twice on the SAME owner (still allowed across owners per D14)
CREATE UNIQUE INDEX ux_phone_no_dupe_per_user
    ON phone_numbers(user_id, e164) WHERE user_id IS NOT NULL;
-- (mirror for school/instructor/student)

CREATE INDEX ix_phone_e164          ON phone_numbers(e164);
CREATE INDEX ix_phone_whatsapp_lookup ON phone_numbers(e164) WHERE is_whatsapp AND whatsapp_opt_in;
```

### 4.2 Why "exactly one owner FK" instead of polymorphic `(owner_type, owner_id)`

Polymorphic FKs are unenforceable in Postgres without triggers. With one
nullable FK per owner type plus a `CHECK` ensuring exactly one is non-null,
we get full referential integrity and `ON DELETE CASCADE` for free. The
extra columns are cheap; the data correctness guarantee is not.

### 4.3 wa.me URL generation contract

```
public String generateWaMeLink(String e164, String renderedMessage) {
    // strip the leading "+" — wa.me wants digits only
    String phone = e164.replaceFirst("^\\+", "");
    String text  = URLEncoder.encode(renderedMessage, StandardCharsets.UTF_8);
    return "https://api.whatsapp.com/send/?phone=" + phone
         + "&text=" + text
         + "&type=phone_number&app_absent=0";
}
```

Validation invariants the service MUST enforce before generating a link:
- recipient phone has `is_whatsapp = TRUE` and `whatsapp_opt_in = TRUE`
- recipient phone is **verified** (or feature-flag opt-out for early dev)
- caller is the sender (audit log captures `sender_user_id`)
- template body is rendered server-side from `whatsapp_templates` with placeholder substitution; raw user-supplied bodies are still allowed but go through a length/profanity guard

### 4.4 Audit log

```sql
CREATE TABLE whatsapp_message_log (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id     UUID NOT NULL REFERENCES users(id),
    recipient_phone_id UUID NOT NULL REFERENCES phone_numbers(id),
    template_id        UUID REFERENCES whatsapp_templates(id),  -- nullable for ad-hoc
    rendered_body      TEXT NOT NULL,
    link_generated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    link_clicked_at    TIMESTAMPTZ,            -- set via /whatsapp/log/{id}/clicked beacon
    school_id          UUID REFERENCES schools(id),  -- tenant context
    correlation_id     VARCHAR(64)
);
```

We can't observe whether the user actually hit "send" inside WhatsApp — that's
out of our trust boundary. We CAN observe the click on our generated link
(front-end posts to `/whatsapp/log/{id}/clicked` immediately before opening
the wa.me URL in a new tab/window).

---

## 5. Email + WhatsApp verification at signup

### 5.1 Email verification (Spring Mail + free SMTP)

- Provider: free SMTP (Brevo / Mailtrap / SendGrid free tier — pluggable via
  `spring.mail.host` env vars). NOT a code dependency choice.
- On signup: backend generates a single-use token in `email_verification_tokens`
  (24 h TTL, sha-256 hashed at rest), emails the user a link
  `https://app.sroadtutor.com/verify-email?token=...`.
- Until verified, login succeeds but JWT carries `emailVerified=false`. Routes
  that require verified email use `@PreAuthorize("@authz.requireEmailVerified(authentication)")`.

### 5.2 WhatsApp verification — see §11 (open question)

---

## 6. Owner-invites-user flow

```
1. OWNER calls POST /schools/{id}/invitations
     body: { email, username, role, instructorDetails?, deliveryMode: "TOKEN" | "DUMMY_PWD" }

2a. TOKEN mode:
     - users row created with email_verified=false, password_hash=null
     - invitations row created with token (24h-7d TTL)
     - email sent with link /accept-invite?token=...
     - on accept, user sets password, password_hash gets set, invitation marked accepted

2b. DUMMY_PWD mode:
     - users row created with password_hash = bcrypt("test123"), must_change_password=true
     - no email sent (or optional notification)
     - user logs in with the dummy password, is forced to change on first login
     - this is the offline-friendly path for non-tech-savvy instructors

3. Either way: instructor_schools row gets created linking the new instructor
   to the inviting owner's school.
```

Open question — `must_change_password`: should we add this column on `users`
to force the dummy-password path to rotate on first login? **Locked: YES.**

---

## 7. Multi-school instructor model

```
instructors
    id (PK)
    user_id      FK -> users(id) NOT NULL UNIQUE
    school_id    FK -> schools(id) NULLABLE       -- "primary" school, optional
    license_number     VARCHAR(80)  NULL          -- placeholder, no validation
    sgi_cert_number    VARCHAR(80)  NULL          -- placeholder, no validation
    vehicle_make       VARCHAR(80)  NULL
    vehicle_model      VARCHAR(80)  NULL
    vehicle_year       INTEGER       NULL
    vehicle_plate      VARCHAR(20)  NULL
    bio                TEXT          NULL
    created_at, updated_at

instructor_schools  (NEW — M:N join)
    instructor_id    FK -> instructors(id)
    school_id        FK -> schools(id)
    role_at_school   VARCHAR(40) NOT NULL  -- "OWNER" | "HEAD" | "REGULAR"
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
    left_at          TIMESTAMPTZ NULL
    PRIMARY KEY (instructor_id, school_id)
```

`instructors.school_id` is kept (nullable) as the convenient "default school"
pointer, but the source of truth for membership is `instructor_schools`. Code
that asks "what schools does this instructor see?" reads from the join.

The self-register-no-school path:
1. User signs up with role=INSTRUCTOR, no school chosen.
2. Backend auto-creates a synthetic school: `name="${user.fullName} Driving"`,
   `is_synthetic=true`, owner_user_id = new user.
3. Promotes user role to OWNER (per D4).
4. Creates `instructor_schools` row with `role_at_school='OWNER'`.
5. Sets `instructors.school_id` to the synthetic school.

---

## 8. Schools — added columns

```sql
ALTER TABLE schools
    ADD COLUMN gst_number                  VARCHAR(40),
    ADD COLUMN pst_number                  VARCHAR(40),
    ADD COLUMN hst_number                  VARCHAR(40),
    ADD COLUMN business_registration_number VARCHAR(80),
    ADD COLUMN metadata                    JSONB NOT NULL DEFAULT '{}'::JSONB,
    ADD COLUMN is_synthetic                BOOLEAN NOT NULL DEFAULT FALSE;
```

`metadata` JSONB is the forward-compat escape hatch — owners want to add
fields like "city license number", "insurance carrier", etc. without
migrations. We expose it as a typed map on the API and validate keys via
JSON Schema in the service layer.

---

## 9. Migration plan — `V8__phase1_foundation.sql`

V8 is a single atomic migration covering all of §3–§8. Splitting it across
multiple files would mean intermediate states where tests can't compile.
Order inside V8:

1. `ALTER TABLE users ADD COLUMN username, must_change_password, email_verified_at, phone_verified_at`
2. `ALTER TABLE schools ADD COLUMN gst_number, pst_number, hst_number, business_registration_number, metadata, is_synthetic`
3. `ALTER TABLE instructors ALTER COLUMN school_id DROP NOT NULL`
4. `CREATE TABLE instructor_schools (...)`
5. `CREATE TABLE phone_numbers (...)` + indexes + check constraints
6. `CREATE TABLE invitations (...)`
7. `CREATE TABLE whatsapp_templates (...)`
8. `CREATE TABLE whatsapp_message_log (...)`
9. `CREATE TABLE email_verification_tokens (...)`
10. Backfill: for every existing `users` row, set `username = email`-prefix-`+ id-suffix` to satisfy NOT NULL UNIQUE. Document this in the migration header.
11. Backfill: for every existing instructor with non-null `school_id`, insert an `instructor_schools` row with `role_at_school='REGULAR'`.
12. Defensive: `DELETE FROM users WHERE auth_provider='FACEBOOK'` — should be 0 rows in QA but executes anyway.
13. ~~`ALTER TYPE auth_provider DROP VALUE 'FACEBOOK'`~~ — **N/A.** V1 stored `auth_provider` as `VARCHAR(32)`, not a Postgres native enum, so there is no enum value to drop. Step 12's defensive `DELETE` is the only Facebook DB cleanup needed.

Idempotency: every `CREATE TABLE` is bare `CREATE TABLE` (Flyway tracks
applied state). No `IF NOT EXISTS` — we want the migration to fail loudly
if state drifts.

---

## 10. PR sequence (revised)

| PR | Scope | Blocking? |
|---|---|---|
| **PR1** | Remove Facebook everywhere (code, deps, env, tests, README, V8 defensive cleanup row). | **DONE 2026-04-25** |
| **PR2** | V8 migration + JPA entities for new tables. No business logic yet, just schema + repos + entity tests. | **DONE 2026-04-25** (Q6 answered as Option B; D22/D23/D24 locked) |
| **PR3** | Auth hardening (test fixes, ddl-auto: validate, JwtFilter exception tightening, gate V7). | Independent of PR2 |
| **PR4** | Phone-number CRUD + verification flow + WhatsApp template + wa.me URL service + audit log endpoints. | Needs PR2 |
| **PR5** | Email verification (Spring Mail SMTP), invitation flow, multi-school join logic. | Needs PR2 |
| **PR6** | School controller (CRUD, owner-only) + Instructor controller (self-register, invite-accept, /me) + RBAC enforcement matrix + integration test pyramid. | Needs PR4 + PR5 |

Each PR ends with `./mvnw verify` green and JaCoCo gate (50%/50%) holding.

---

## 11. ~~OPEN QUESTION~~ RESOLVED — WhatsApp verification mechanism (Q6 → Option B)

**Resolution (2026-04-24):** ship **Option B** in PR4 — email-only verification at signup, WhatsApp self-attest with admin confirmation. Architecture exposes `PhoneVerificationService` so we can drop in `MetaCloudApiPhoneVerifier` (Option A) later without touching callers. Original analysis preserved below for context.

### 11.1 Original analysis

D9 says "WhatsApp must be verified at signup". D15 says "backend never sends
WhatsApp messages — wa.me click-to-chat from the sender's phone only". These
are in tension at signup time, because at signup the *backend* needs to
verify the user's *incoming* phone, but we have no inbound WhatsApp channel.

Three production-viable options:

### Option A — Meta WhatsApp Cloud API (free tier) for OTP only
- Meta Cloud API ≠ Twilio. It's Meta-direct, 1,000 free service conversations/month.
- Use it ONLY for outbound OTP at signup. All other messaging stays wa.me.
- Pro: real OTP verification. Tens of thousands of signups before any cost.
- Con: requires WhatsApp Business profile, phone-number verification with Meta, template approval (24-48h). Meaningful setup overhead.

### Option B — Email-only verification at signup, WhatsApp self-attest
- At signup: hard-verify email (D8). Capture WhatsApp number, mark `verified_at = NULL`.
- After signup: user clicks "Verify my WhatsApp" → app generates a wa.me link to a *platform support number* with a short token in the message body → user sends → support inbox sees it → admin (or a simple daemon polling WhatsApp Web) marks the row verified.
- Pro: zero new dependencies. Ships today.
- Con: not real-time. Verification UX is awkward. Risk of unverified-forever rows.

### Option C — Phone trusted on entry, WhatsApp verified by first successful message
- At signup: capture phone, mark unverified.
- First time the owner generates a wa.me link to that number AND the click-beacon fires AND the recipient replies in WhatsApp to *the sender* (not the platform), we flip `verified_at`.
- Pro: zero infra. Verification is implicit in actual usage.
- Con: we never observe the recipient's reply (it goes user-to-user, not through us). So verification is effectively "the sender says it worked". Not really verification.

**My recommendation: Option B for MVP, with the architecture leaving a clean seam for Option A later.** Specifically:

- `PhoneVerificationService` interface with two methods: `requestVerification(phoneId)` and `confirmVerification(phoneId, code)`.
- `EmailFallbackPhoneVerifier` impl (Option B): emails the user a wa.me-formatted link they're meant to click and send manually; backend exposes a "I sent it" button that doesn't actually verify but creates a pending-review record.
- Future `MetaCloudApiPhoneVerifier` impl (Option A): swaps in via Spring profile / property without touching callers.

But you're the product owner — **which option do you want me to ship in PR4?**

If unsure, I default to Option B and we revisit when signup volume justifies the Meta setup.

---

## 12. Tech-debt register (carried forward)

| ID | Item | Severity | Owner | Resolved in |
|---|---|---|---|---|
| TD-01 | `AuthServiceTest.@BeforeEach tokenStubs()` is commented out — multiple false-green tests. | High | Auth | PR3 |
| TD-02 | `AuthServiceTest.loginWithGoogle_reusesExistingProviderMatch` missing `@Test` annotation — never runs. | High | Auth | PR3 |
| TD-03 | `application-dev.yml` has `spring.jpa.hibernate.ddl-auto: update` while Flyway owns schema. Drift risk. | High | Config | PR3 |
| TD-04 | `JwtAuthenticationFilter` has overly broad `catch (Exception ignored)` — swallows real bugs. | Medium | Auth | PR3 |
| TD-05 | `JwtService` writes empty-string `schoolId` claim instead of omitting when null. Confuses downstream parsers. | Low | Auth | PR3 |
| TD-06 | `.env.dev` checked in with real secrets. Rotate at QA→PROD promotion. | Low (per D20) | Ops | Phase-2 promotion |
| TD-07 | V7 customer seed runs unconditionally — risk of seeding PROD on first deploy. | High | DB | PR3 (gate w/ placeholder) |
| TD-08 | Empty packages `com/sroadtutor/{school,user}/{controller,dto,model,repository,service}` in main code. Misleading. | Low | Cleanup | PR6 |
| TD-09 | `CorsConfig` splits on raw comma — fragile if env vars accidentally have spaces. | Low | Config | Future |
| TD-10 | `FlywayDevConfig` runs `repair()` before every dev migrate. Fine for local dev but document loudly so no one ports it to QA. | Info | Config | Documented here |

---

## 13. What this design explicitly does NOT cover

- Calendar / availability / booking (Phase 2)
- Lesson session lifecycle, mistake categorization runtime, road-test results capture (Phase 2)
- Payments + Stripe (Phase 3)
- Reminders engine + cron (Phase 3)
- Subscriptions / billing (Phase 3)
- Dealership leads + risk scoring (Phase 4)
- WhatsApp inbound / two-way conversation (out of scope per D15)

Anything in those buckets that surfaces during Phase 1 work goes into the
backlog, not this doc.

---

*End of foundation design. Diff against this doc lives in `docs/architecture/changelog.md`.*
