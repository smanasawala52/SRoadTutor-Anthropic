# Architecture Decision Changelog

Plain-text record of every change to `01-foundation-design.md`. Append-only.

## 2026-04-24 — Initial foundation design committed

- D1–D21 captured from product Q&A round 1 (roles, multi-school, schema metadata, V7 gating).
- D13–D16 added from WhatsApp/phone-number requirements round 2.
- §11 Q6 (WhatsApp verification mechanism) flagged as the single remaining blocker before PR2 schema work.
- PR sequence finalized as PR1 (Facebook removal) → PR2 (V8 schema) → PR3 (auth hardening) → PR4 (phone CRUD + WhatsApp) → PR5 (email + invitations) → PR6 (school + instructor controllers + RBAC matrix).

## 2026-04-25 — PR1 landed: Facebook OAuth removed

- Deleted `FacebookOAuthService`, the `/auth/oauth/facebook` route, and `AppProperties.OAuth.Facebook`.
- Cleaned the dangling `@Mock FacebookOAuthService` in `AuthServiceTest` and the `Facebook` record reference in `RefreshTokenServiceTest`.
- D17 (Facebook removal) closed; V8 retains a defensive `DELETE FROM users WHERE auth_provider = 'FACEBOOK'` for any legacy rows that escape PR1 cleanup.

## 2026-04-25 — PR2 kickoff: D22 / D23 / D24 locked

- **D22 (Q1a)** — `phone_numbers.parent_id` is intentionally not modeled. PARENT is a role on `users`, so a parent's phones reuse `phone_numbers.user_id`. Schools / instructors / students keep their own FK columns because they are independent entities.
- **D23 (Q2a)** — `users.email_verified BOOLEAN` is dropped in V8. Replaced by `email_verified_at TIMESTAMPTZ` (null = unverified). Existing rows with `email_verified = TRUE` get backfilled from `updated_at` (or `created_at` when null). Java derives `isEmailVerified()` from `emailVerifiedAt != null`.
- **D24 (Q3a)** — `users.phone` column dropped. Canonical NA phones (matching `^\+1[0-9]{10}$` or `^[0-9]{10}$`) are backfilled into `phone_numbers` as the user's primary, with `is_whatsapp = TRUE`, `whatsapp_opt_in = TRUE`, `verified_at = NULL`. Free-form values are intentionally not parsed; PR4's phone CRUD becomes the canonical entry point.
- **§9 step 13 marked N/A** — `auth_provider` is `VARCHAR(32)`, not a Postgres native enum. There is no enum value to drop; the defensive `DELETE` in step 11 covers all Facebook DB cleanup.

## 2026-04-25 — PR2 landed: V8 migration + foundation entities

- **V8 migration** (`V8__phase1_foundation.sql`) covers: users column adds (`username`, `must_change_password`, `email_verified_at`, `phone_verified_at`) and drops (`email_verified`, `phone`); schools metadata (tax IDs, JSONB metadata, `is_synthetic`); `instructors.school_id` made nullable + `instructor_schools` M:N join; `phone_numbers` polymorphic-owner table with exactly-one-owner CHECK and partial unique indexes for primary + per-owner-e164; `invitations` (TOKEN | DUMMY_PWD); `whatsapp_templates` with two partial unique indexes covering school-override and platform-default; `whatsapp_message_log` audit; `email_verification_tokens`; defensive Facebook DELETE.
- **`User` entity** dropped `phone` and `emailVerified`, added `username`, `mustChangePassword`, `emailVerifiedAt`, `phoneVerifiedAt`, plus `@Transient` `isEmailVerified()` / `isPhoneVerified()` helpers.
- **`SignupRequest`** drops the `phone` field — phone capture moves into PR4's `/api/phone-numbers` CRUD so all numbers (incl. WhatsApp opt-in) flow through one path.
- **`AuthResponse.UserDto`** drops `phone`, adds `username`, `phoneVerified`, `mustChangePassword` so the SPA can route to the password-rotation flow on owner-pre-created accounts.
- **`AuthService`** generates a deterministic-but-unique initial username from the email's local-part + 8-char UUID suffix (lowercased) — stop-gap until D7's username chooser ships.
- **New entities + repositories**: `PhoneNumber`, `InstructorSchool` (with `InstructorSchoolId` composite key), `Invitation`, `WhatsappTemplate`, `WhatsappMessageLog`, `EmailVerificationToken`. All use UUID FK columns rather than `@ManyToOne` to mirror the existing `RefreshToken` pattern; JSONB columns use Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`.

## 2026-04-25 — Modules 2 (Student) + 3 (Parent portal) + 4 (Scheduling) kickoff: D25 / D26 / D27 / D28 locked

- **D25 (Q4a) — Student package model.** Fixed-count, no expiry. `students.lessons_remaining` is the live counter; every adjustment (purchase, completed lesson, refund, manual correction) writes a row to a new `lesson_credits` ledger so the counter is always reproducible. Pause = status flip (no counter change); refund = ledger row with negative delta. Time-bounded subscriptions and hybrid SKUs explicitly out for V1.
- **D26 (Q5a) — Parent ↔ Student cardinality.** M:N. Already modelled in V1 as `parent_student(parent_user_id, student_id, relationship)`. No schema change needed. Parent role's authorization scope at request time is `SELECT student_id FROM parent_student WHERE parent_user_id = :jwtUserId` — derived, never cached in JWT (avoids stale-claim bugs when a parent is added/removed mid-token-lifetime).
- **D27 (Q6a) — Slot granularity + timezone authority.** 30-min slots, school-level timezone authoritative. New column `schools.timezone VARCHAR(64) NOT NULL DEFAULT 'America/Regina'` (validated against `pg_timezone_names`). All `lesson_sessions.scheduled_at` stays in UTC (TIMESTAMPTZ); rendering converts to school TZ. `lesson_sessions.duration_mins` gets a `CHECK (duration_mins % 30 = 0 AND duration_mins BETWEEN 30 AND 240)`.
- **D28 (Q7a) — Conflict detection scope (V1).** Instructor double-booking only — enforced at the database level via a Postgres `EXCLUDE` constraint using `btree_gist` over `(instructor_id WITH =, tstzrange(scheduled_at, scheduled_at + duration_mins*interval '1 minute') WITH &&)` filtered to non-cancelled rows. Student double-booking, vehicle resources, and availability-window enforcement explicitly out for V1 (logged in tech-debt register).
- **PR sequence extended:** PR3 → PR4 → PR5 → PR6 (foundations) **must land before** PR7 (Student management), PR8 (Parent portal), PR9 (Scheduling engine). New module design captured in `02-students-parents-scheduling.md`.

## 2026-04-25 — Module 2/3/4 open forks resolved (D29 / D30 / D31 / D32 / D33)

- **D29 (S-1)** — Every student gets a provisioned `users` row at creation time with `auth_provider='LOCAL'` and `must_change_password=TRUE`. No "shadow students" without a user account. `students.user_id` stays `NOT NULL` — V9 will NOT drop that constraint.
- **D30 (P-1)** — Parent "WhatsApp alerts" delivered as **email containing a wa.me click-to-chat link** in V1. Native push via Meta WhatsApp Cloud API tracked as TD-17, deferred to V2+.
- **D31 (P-2)** — PARENT role can create bookings on behalf of any linked student (`parent_student.status='ACTIVE'`). Reschedule + cancel rights granted to parents on linked students within the same window granted to STUDENT (D32).
- **D32 (P-3)** — Student-side / parent-side cancellation window is **24 hours** before `scheduled_at`. OWNER and INSTRUCTOR (own bookings) bypass the window. Hard-coded for V1; surface as `schools.cancellation_window_hours` later (TD-15).
- **D33 (P-4)** — Booking creation does NOT decrement `lessons_remaining`. The `LESSON_COMPLETED` ledger row writes the `-1` only when the instructor marks `status='COMPLETED'`. Bookings that never get marked leak credits (mitigation tracked as TD-16).

## 2026-04-25 — PR3 landed: Auth hardening (TD-01..TD-07)

- **TD-01 (AuthServiceTest false-green)** — `@BeforeEach` activated on `tokenStubs()`; class-level `@MockitoSettings(strictness = Strictness.LENIENT)` added so tests that throw before reaching the JWT mocks don't fail with `UnnecessaryStubbingException`. Two false-green assertions fixed: `login_succeedsWithCorrectPassword` and `refresh_rotatesAndReturnsNewTokens` no longer assert `accessToken().isNull()`; both now assert `.isEqualTo("access-token")` and verify the rest of the response shape. Redundant explicit `tokenStubs()` calls removed from two tests now that the setup hook owns it.
- **TD-02 (missing @Test annotation)** — `loginWithGoogle_reusesExistingProviderMatch` was silently never executed by JUnit. `@Test` restored, the previously-commented `assertThat(resp.user().id()).isEqualTo(existing.getId())` assertion uncommented, and a `verify(userRepository, never()).save(any())` added to lock the no-insert contract for provider-matched logins.
- **TD-03 (dev DDL footgun)** — `application-dev.yml` flipped from `ddl-auto: update` to `ddl-auto: validate`. Flyway is now the single source of truth for schema in every profile, including dev — Hibernate fails fast at boot if a JPA entity drifts from the migration-built schema instead of silently mutating dev DBs.
- **TD-04 (overbroad catch in JwtAuthenticationFilter)** — `catch (Exception ignored)` narrowed to `catch (UnauthorizedException ex)` with `log.debug("Rejected JWT from {}: [{}] {}", ...)`. Real bugs (NPE, IllegalStateException, …) now propagate to the global error handler instead of being silently masked under a 401.
- **TD-05 (empty-string schoolId claim)** — `JwtService.generateAccessToken()` now omits the `schoolId` claim entirely when the user has no school, instead of writing `""`. Built via mutable `HashMap` so the conditional `put` works (the previous `Map.of(...)` was immutable and forced the empty-string footgun). Downstream consumers no longer have to special-case `""` alongside `null`.
- **TD-06 (documentation)** — Captured implicitly via this PR3 changelog entry; no separate file change.
- **TD-07 (V7 customer seed gating)** — `V7__seed_customer_data.sql` now gated by Flyway placeholder `${seed-customer}`. Body short-circuits with a `RAISE NOTICE` unless the placeholder is the literal string `"true"`. Mirrors the `${seed-dev}` pattern from V4. All 5 profile yml files updated: `application.yml` base default `"false"`; `application-dev.yml` flips to `"true"` (alongside `seed-dev: "true"`); `application-qa.yml`, `application-prod.yml`, `application-test.yml` all explicitly pin `"false"` belt-and-suspenders. Idempotency `IF EXISTS` check kept as a second line of defence in dev re-runs.
- **PR3 scope confirmation:** No production code paths touched beyond exception narrowing and claim shape. Token issuance contract unchanged for school-scoped users; only schoolId-less users (system / pre-onboarding OWNER pre-school) see a different JWT shape, which the `JwtService.parseAndValidate()` consumer already tolerates because nothing currently reads `CLAIM_SCHOOL_ID`.
- **Post-verify follow-ups (caught by activated tests, both fixed):**
  - **AuthServiceTest line 276** — second false-green `.isNull()` assertion in `refresh_rotatesAndReturnsNewTokens` was missed in the first pass. Now asserts `accessToken == "access-token"`, `refreshToken == "refresh-token"`, plus user.id round-trip and role preserved through refresh.
  - **AuthServiceTest line 230** — `loginWithGoogle_reusesExistingProviderMatch` was hitting `AuthService.upsertOAuthUser`'s name-refresh branch (lines 122–125) because the test built `existing` without a `fullName`, so the verified `"Owner"` triggered `userRepository.save(existing)`. The unstubbed mock returned null, the local variable was reassigned to null, and `issueTokens` NPE'd at `user.getId()`. **Not a production bug** (Spring Data JPA's `save()` never returns null), but a clear test-intent mismatch: the test claims to verify the "provider-matched, no insert" reuse path. Fix: build `existing` with `fullName("Owner")` so the name-refresh branch is skipped, preserving the `verify(userRepository, never()).save(any())` assertion as a meaningful contract.
