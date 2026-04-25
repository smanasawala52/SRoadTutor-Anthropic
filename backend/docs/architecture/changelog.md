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
