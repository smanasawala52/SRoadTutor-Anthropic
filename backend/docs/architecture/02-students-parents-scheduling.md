# 02 — Students, Parent Portal, Scheduling Engine (Modules 2/3/4)

> Companion to `01-foundation-design.md`. Same conventions: every decision is
> numbered (Dnn) and locked into `changelog.md` when the user signs off. Section
> numbers map to the migration / entity / controller layout, so a reader can
> grep for "§5.4" in code reviews and find the matching design rationale.

---

## 1. Decisions captured

| ID  | Decision | Rationale |
|-----|----------|-----------|
| D25 | Student packages are **fixed-count, no expiry**. `students.lessons_remaining` is the running counter; an immutable `lesson_credits` ledger records every delta with reason. | Matches the way SK driving schools sell today (10-lesson, 20-lesson SKUs). Time-bounded subs add a sweep job + grace period UX we don't need for V1. The ledger is the audit trail Stripe + insurance partners will eventually require. |
| D26 | Parent ↔ Student is **M:N**. V1's existing `parent_student` table is reused. Parent's authorization scope is **derived per-request** from `parent_student`, not cached in the JWT. | M:N already supports the divorced-parents / multi-sibling cases the user named. Deriving the scope per-request avoids the "parent removed → still has access until token expiry" bug. Adds one indexed lookup per request — negligible. |
| D27 | Scheduling grid is **30-min slots, school timezone authoritative**. UTC stored in `lesson_sessions.scheduled_at` (already TIMESTAMPTZ). New `schools.timezone` column. `duration_mins` constrained to multiples of 30, range [30, 240]. | 30 min is the industry standard slot for road lessons (60- and 90-min lessons are 2x and 3x slots). School-level TZ keeps the calendar coherent for a single school's instructors and students; per-user TZ would break the shared calendar UX. |
| D28 | Conflict detection (V1) covers **instructor double-booking only**, enforced at the **database level** via `EXCLUDE` constraint on a `tstzrange`. | DB-level guarantee survives concurrent inserts, retries, and any future direct-SQL admin tools — application-level checks under load are a known double-booking source. Student double-booking, vehicles, and strict availability-window enforcement are deferred to V2 with explicit tech-debt entries. |

---

## 2. Module overview

### 2.1 What V1 already gives us

`V1__create_core_schema.sql` already contains:

- `students` — `package_total_lessons`, `lessons_remaining`, `status`, `instructor_id`, `road_test_date`.
- `parent_student` — M:N join with `relationship`, unique on `(parent_user_id, student_id)`.
- `lesson_sessions` — `instructor_id`, `student_id`, `scheduled_at`, `duration_mins`, `status`, `location`, `notes`. (This is the bookings table; renaming would churn V1 with no payoff.)
- `payments` — linked to student + session.
- `reminders` — `channel` ∈ {WHATSAPP, SMS, PUSH, EMAIL}, payload JSONB.

So Modules 2/3/4 are **layered fixes** on top of V1, not greenfield. V9 will:

1. Tighten `students` for D25 (status enum widened, ledger added).
2. Add `schools.timezone` and `lesson_sessions.duration_mins` CHECK for D27.
3. Add `lesson_sessions` cancel-audit columns + state machine.
4. Add `instructor_availability` (recurring) + `instructor_availability_overrides` (one-offs).
5. Add the `EXCLUDE` constraint for D28.

### 2.2 What V1 is missing or wrong

| Defect | Found at | Fix |
|---|---|---|
| `students.status` set to `ACTIVE \| PASSED \| DROPPED` — no PAUSED, no INACTIVE. | `V1__create_core_schema.sql:87` | V9 widens to `ACTIVE \| PAUSED \| GRADUATED \| DROPPED \| INACTIVE`, with backfill `PASSED → GRADUATED`. |
| `instructors.working_hours_json JSONB` — too unstructured for conflict detection. | `V1:70` | V9 introduces `instructor_availability` typed table; the JSONB column is left in place but marked *DEPRECATED* in `01-foundation-design.md` §12 tech-debt; PR9 stops reading from it. |
| No `schools.timezone`. | `V1:16-26` | V9 adds it with default `America/Regina` and a `CHECK timezone IN (SELECT name FROM pg_timezone_names)` materialized as a function (Postgres can't do subquery in CHECK directly). |
| No way to audit `lessons_remaining` adjustments. | `V1:80` | V9 adds `lesson_credits` ledger; PR7 service writes to it on every change. |
| `lesson_sessions` has no `cancelled_at`, `cancelled_by_user_id`, `cancellation_reason`. | `V1:112` | V9 adds them, plus a CHECK that ties them to `status='CANCELLED'`. |
| No DB-level booking-conflict guard. | `V1:112` | V9 adds the `EXCLUDE` constraint. |

---

## 3. Module 2 — Student management

### 3.1 Schema changes (V9)

```sql
-- Status widening (D25 paused state needed for paused packages)
ALTER TABLE students DROP CONSTRAINT IF EXISTS chk_students_status;
UPDATE students SET status = 'GRADUATED' WHERE status = 'PASSED';
ALTER TABLE students
    ADD CONSTRAINT chk_students_status
    CHECK (status IN ('ACTIVE','PAUSED','GRADUATED','DROPPED','INACTIVE'));

-- Add a contact_email_override for cases where students don't have a login yet
ALTER TABLE students
    ADD COLUMN contact_email VARCHAR(254);  -- nullable; falls back to users.email when null

-- Lesson credits ledger (D25 — append-only)
CREATE TABLE lesson_credits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    delta           INT NOT NULL,                                  -- +N for purchase, -1 per completed lesson, +/-N for adjustment
    reason          VARCHAR(32) NOT NULL,                          -- PURCHASE | LESSON_COMPLETED | LESSON_REFUND | MANUAL_ADJUSTMENT | PACKAGE_PAUSE_RESUME (no-op delta)
    session_id      UUID REFERENCES lesson_sessions(id) ON DELETE SET NULL,
    payment_id      UUID REFERENCES payments(id) ON DELETE SET NULL,
    actor_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_lesson_credits_reason CHECK (reason IN
        ('PURCHASE','LESSON_COMPLETED','LESSON_REFUND','MANUAL_ADJUSTMENT'))
);
CREATE INDEX idx_lesson_credits_student ON lesson_credits(student_id, created_at DESC);
CREATE INDEX idx_lesson_credits_session ON lesson_credits(session_id);
```

> **Why a ledger instead of just trusting the counter?** When a parent calls in
> three months from now asking "why does my kid show 3 lessons left when I paid
> for 10?", the only honest answer is the per-row reason chain. Ledgers also
> let us reconstruct `lessons_remaining` on demand — a useful invariant test in
> integration tests (sum(delta) == students.lessons_remaining).

### 3.2 Entity + repository inventory

```
com.sroadtutor.student
├── model
│   ├── Student.java                     — JPA entity, mirrors students table
│   ├── StudentStatus.java               — enum {ACTIVE,PAUSED,GRADUATED,DROPPED,INACTIVE}
│   ├── LessonCredit.java                — JPA entity, mirrors lesson_credits
│   └── LessonCreditReason.java          — enum
├── repository
│   ├── StudentRepository.java           — findBySchoolId, findByInstructorId, search
│   └── LessonCreditRepository.java      — findByStudentId, sumDeltaByStudent
├── dto
│   ├── StudentCreateRequest.java        — record (fullName, email, phoneE164?, parentEmails[], packageLessons, instructorId?)
│   ├── StudentUpdateRequest.java        — record (fullName?, status?, instructorId?, roadTestDate?)
│   ├── StudentResponse.java             — record (id, schoolId, fullName, email, phone, parentIds, instructorId, packageTotal, lessonsRemaining, status, roadTestDate, createdAt)
│   ├── LessonCreditAdjustRequest.java   — record (delta, reason, note)
│   └── LessonCreditResponse.java        — record (id, delta, reason, note, actorUserId, createdAt)
├── service
│   ├── StudentService.java              — orchestrates user-row creation, parent linking, package init
│   └── LessonCreditService.java         — atomic delta-and-counter update inside a single @Transactional, optimistic lock on students.lessons_remaining via @Version
└── controller
    └── StudentController.java           — REST under /api/students
```

`Student` reuses the **UUID FK pattern** (no `@ManyToOne User`) consistent with
`RefreshToken` and the entities created in PR2. Optimistic locking via a new
`students.version BIGINT NOT NULL DEFAULT 0` column + Hibernate `@Version`
prevents lost updates when two instructors decrement the counter concurrently
(rare in practice, fatal when it happens).

### 3.3 API surface

| Verb | Path | Auth | Body | Notes |
|---|---|---|---|---|
| POST | `/api/students` | OWNER, INSTRUCTOR (school-scoped) | `StudentCreateRequest` | Creates a `users` row (role=STUDENT) **only if** `email` is supplied. Else creates a "shadow student" — a row in `students` with `user_id = NULL` (requires a V9 nullable change) so the school can track lessons before the kid logs in. |
| GET | `/api/students` | OWNER, INSTRUCTOR (school-scoped); PARENT (own kids only); STUDENT (self only) | — | Pagination + filter by status, instructorId. |
| GET | `/api/students/{id}` | OWNER+INSTRUCTOR same school; PARENT linked; STUDENT self | — | 404 instead of 403 if out of scope (avoids leaking existence). |
| PATCH | `/api/students/{id}` | OWNER, INSTRUCTOR same school | `StudentUpdateRequest` | Status flip is the pause/resume mechanism. |
| DELETE | `/api/students/{id}` | OWNER only | — | Soft delete: sets `status='INACTIVE'`. We never hard-delete a student row because `lesson_sessions`, `payments`, and `road_test_results` reference it. |
| POST | `/api/students/{id}/credits` | OWNER, INSTRUCTOR same school | `LessonCreditAdjustRequest` | Atomic: writes ledger row + updates counter inside one TX. |
| GET | `/api/students/{id}/credits` | OWNER+INSTRUCTOR same school; PARENT linked; STUDENT self | — | Returns ledger history. |

> **Open fork (S-1):** the "shadow student" without a user account requires
> `students.user_id` to be nullable. Currently it's `NOT NULL UNIQUE`. Migration
> drops `NOT NULL`. **Confirm before V9 lands** — do we ever want a student in
> the system without a login, or should every student get a provisioned account
> (with `must_change_password=TRUE`) at creation time? The PR2 invitations
> table already supports `DUMMY_PWD`, so we can go either way. Default
> recommendation: **always provision a user**; flip the question if disagreed.

### 3.4 RBAC matrix (delta vs. PR6's matrix)

```
                       OWNER  INSTRUCTOR(same school)  PARENT(linked)  STUDENT(self)
POST   /students        ✅    ✅                       ❌              ❌
GET    /students         ✅¹   ✅¹                      ✅²             ✅³
GET    /students/{id}    ✅¹   ✅¹                      ✅²             ✅³
PATCH  /students/{id}    ✅    ✅                       ❌              ❌
DELETE /students/{id}    ✅    ❌                       ❌              ❌
POST   /students/{id}/credits  ✅  ✅                  ❌              ❌
GET    /students/{id}/credits  ✅¹ ✅¹                 ✅²             ✅³
```
¹ scoped to the JWT's `school_id` (and `school_ids` from D5 when instructor is multi-school)
² scoped to `student_id IN (SELECT student_id FROM parent_student WHERE parent_user_id = :jwtUserId)`
³ scoped to `student.user_id = :jwtUserId`

Implementation: a single `@PreAuthorize("@studentSecurity.canRead(#id, principal)")` SpEL bean centralizes the check; controller-level `@PreAuthorize("hasRole('OWNER') or hasRole('INSTRUCTOR')")` gates the create/update endpoints.

### 3.5 Lesson-credit transaction semantics

```java
@Transactional
public void adjust(UUID studentId, int delta, LessonCreditReason reason,
                   UUID actorUserId, String note,
                   UUID sessionId /* nullable */, UUID paymentId /* nullable */) {
    Student s = studentRepository.findByIdForUpdate(studentId)        // SELECT … FOR UPDATE
        .orElseThrow(() -> new NotFoundException(...));
    int newRemaining = s.getLessonsRemaining() + delta;
    if (newRemaining < 0) {
        throw new BadRequestException("INSUFFICIENT_CREDITS",
            "Student has " + s.getLessonsRemaining() + " lessons remaining; cannot apply delta " + delta);
    }
    s.setLessonsRemaining(newRemaining);
    if (reason == PURCHASE) {
        s.setPackageTotalLessons(s.getPackageTotalLessons() + delta);
    }
    studentRepository.save(s);

    LessonCredit row = LessonCredit.builder()
        .studentId(studentId).delta(delta).reason(reason)
        .sessionId(sessionId).paymentId(paymentId)
        .actorUserId(actorUserId).note(note).build();
    lessonCreditRepository.save(row);
}
```

`SELECT … FOR UPDATE` prevents the lost-update race; `@Version` on the entity
gives us a second line of defence. Tests will exercise concurrent decrement via
two threads booking the same student's last lesson.

---

## 4. Module 3 — Parent portal

### 4.1 Schema changes (V9)

None for the portal itself — V1's `parent_student` already covers D26. The
portal adds:

- `parent_student.status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'` (`ACTIVE | INACTIVE`) — so unlinking a parent without losing history is a status flip, not a delete.

```sql
ALTER TABLE parent_student
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN ended_at TIMESTAMPTZ;
ALTER TABLE parent_student
    ADD CONSTRAINT chk_parent_student_status CHECK (status IN ('ACTIVE','INACTIVE'));
```

### 4.2 API surface (read-only by design)

| Verb | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/parent/me/students` | PARENT | List of linked students (active links only). |
| GET | `/api/parent/me/students/{studentId}/schedule` | PARENT (linked) | Upcoming + recent `lesson_sessions` for that student. |
| GET | `/api/parent/me/students/{studentId}/progress` | PARENT (linked) | `lessons_remaining`, recent `session_mistakes` summary, road-test readiness. |
| GET | `/api/parent/me/students/{studentId}/payments` | PARENT (linked) | `payments` list scoped to the student. |
| POST | `/api/parent/me/students/{studentId}/whatsapp-optin` | PARENT (linked) | Toggles `whatsapp_opt_in` on the parent's primary `phone_numbers` row. The **only** write endpoint a PARENT has — and only on their own opt-in flag. |

The link/unlink of parent↔student is OWNER/INSTRUCTOR-scoped under `/api/students/{id}/parents` (covered in Module 2). Parents themselves never see those endpoints.

### 4.3 Authorization — parent scope is derived, not claimed

Per **D26**, the JWT does NOT carry a `linkedStudentIds` claim. Reasons:

1. **Staleness window.** Adding a parent at 09:00 should give them access immediately; with a 60-minute access-token TTL, a JWT-claimed list would lag up to 60 minutes.
2. **Token bloat.** A grandparent linked to 5 grandkids would already be a 5-UUID claim; multiply across schools and the token grows.
3. **Cheap to derive.** `parent_student` is indexed on `parent_user_id`; the lookup is sub-ms.

Implementation:

```java
@Component
public class ParentScopeChecker {
    public boolean canRead(UUID parentUserId, UUID studentId) {
        return parentStudentRepository
            .existsByParentUserIdAndStudentIdAndStatus(parentUserId, studentId, "ACTIVE");
    }
}
// @PreAuthorize("hasRole('PARENT') and @parentScopeChecker.canRead(principal.id, #studentId)")
```

### 4.4 WhatsApp alerts — event-driven via reminders

Triggers (each writes to `reminders` and emits a `whatsapp_message_log` row when a wa.me link is generated):

| Event | When | To | Template (PR4 templates) |
|---|---|---|---|
| Lesson booked | `BookingService.book()` succeeds | Linked parents (status=ACTIVE, whatsapp_opt_in=TRUE) | `lesson_booked_v1` |
| Lesson cancelled | `BookingService.cancel()` | Linked parents | `lesson_cancelled_v1` |
| Lesson rescheduled | `BookingService.reschedule()` | Linked parents | `lesson_rescheduled_v1` |
| 24h reminder | scheduled job 24h before `scheduled_at` | Linked parents + the student | `lesson_reminder_24h_v1` |
| Lesson completed | instructor marks `status='COMPLETED'` | Linked parents | `lesson_completed_v1` |
| Package low (<=2 remaining) | `LessonCreditService.adjust` after decrement | Linked parents | `package_low_v1` |

> **Open fork (P-1):** the wa.me model in D14 is click-to-chat — the school
> staff sends, no automated outbound. Do we surface these "alerts" as
> **draft links in the school's dashboard** (instructor clicks a button to
> notify the parent), or as **a notification feed inside the parent's portal**
> (the parent gets a push/email notification, opens the portal, taps the
> wa.me button to start a chat)? **The user's brief says "Gets WhatsApp
> alerts" — which the current wa.me model can't deliver without a sender on
> the school side.** Recommend confirming. Default plan is to log all events
> to `reminders` + send a real EMAIL via PR5's mailer for each, and the wa.me
> link is provided in the email body so the parent can chat the school back.

---

## 5. Module 4 — Scheduling engine

### 5.1 Schema changes (V9)

```sql
-- D27: school timezone
ALTER TABLE schools
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Regina';

CREATE OR REPLACE FUNCTION is_valid_timezone(tz TEXT) RETURNS BOOLEAN AS $$
    SELECT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = tz);
$$ LANGUAGE SQL IMMUTABLE;

ALTER TABLE schools
    ADD CONSTRAINT chk_schools_timezone CHECK (is_valid_timezone(timezone));

-- D27: 30-min slot grid
ALTER TABLE lesson_sessions
    ADD CONSTRAINT chk_lesson_sessions_duration
    CHECK (duration_mins % 30 = 0 AND duration_mins BETWEEN 30 AND 240);

-- Cancel audit + state machine widening
ALTER TABLE lesson_sessions
    ADD COLUMN cancelled_at          TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN cancellation_reason   VARCHAR(500),
    ADD COLUMN completed_at          TIMESTAMPTZ;

ALTER TABLE lesson_sessions
    ADD CONSTRAINT chk_lesson_sessions_status
    CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW'));

ALTER TABLE lesson_sessions
    ADD CONSTRAINT chk_lesson_sessions_cancel_audit
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL));

-- D28: instructor double-booking prevention via DB constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE lesson_sessions
    ADD CONSTRAINT excl_lesson_sessions_no_double_book
    EXCLUDE USING gist (
        instructor_id WITH =,
        tstzrange(scheduled_at, scheduled_at + (duration_mins || ' minutes')::interval, '[)') WITH &&
    ) WHERE (status IN ('SCHEDULED','COMPLETED'));

-- Instructor availability (recurring weekly pattern)
CREATE TABLE instructor_availability (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id   UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    day_of_week     SMALLINT NOT NULL,                              -- 0=Sun..6=Sat
    start_time      TIME NOT NULL,                                  -- in school's TZ
    end_time        TIME NOT NULL,
    valid_from      DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until     DATE,                                           -- nullable = open-ended
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_avail_day CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT chk_avail_time CHECK (end_time > start_time),
    CONSTRAINT chk_avail_minute_grid CHECK (
        EXTRACT(MINUTE FROM start_time)::INT % 30 = 0
        AND EXTRACT(MINUTE FROM end_time)::INT % 30 = 0
    ),
    CONSTRAINT chk_avail_window CHECK (valid_until IS NULL OR valid_until >= valid_from)
);
CREATE INDEX idx_instructor_avail_lookup ON instructor_availability(instructor_id, day_of_week);

-- One-off availability overrides (vacation, special open hours)
CREATE TABLE instructor_availability_overrides (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instructor_id   UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    on_date         DATE NOT NULL,                                  -- in school's TZ
    start_time      TIME,                                           -- null + null = full-day blackout
    end_time        TIME,
    type            VARCHAR(16) NOT NULL,                           -- AVAILABLE | UNAVAILABLE
    reason          VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_avail_override_type CHECK (type IN ('AVAILABLE','UNAVAILABLE')),
    CONSTRAINT chk_avail_override_times CHECK (
        (start_time IS NULL AND end_time IS NULL)                   -- full-day
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);
CREATE INDEX idx_instructor_avail_override ON instructor_availability_overrides(instructor_id, on_date);
```

> **Why `tstzrange` and not `tsrange`?** `lesson_sessions.scheduled_at` is
> `TIMESTAMPTZ`. Using `tstzrange` keeps the comparison correct across DST
> transitions, since instructors near the SK/AB border do not observe DST but
> some other provinces do — a future expansion concern.
>
> **Why is the `EXCLUDE` filtered to `SCHEDULED|COMPLETED`?** A cancelled
> session should not block a new booking in the same slot. Filtering on status
> means we don't have to delete cancelled rows.

### 5.2 Entity inventory

```
com.sroadtutor.scheduling
├── model
│   ├── LessonSession.java                          — JPA entity (existing table)
│   ├── LessonSessionStatus.java                    — enum
│   ├── InstructorAvailability.java                 — JPA entity (new)
│   └── InstructorAvailabilityOverride.java         — JPA entity (new)
├── repository
│   ├── LessonSessionRepository.java                — calendar queries (school + date range)
│   ├── InstructorAvailabilityRepository.java       — by-instructor lookup
│   └── InstructorAvailabilityOverrideRepository.java
├── dto
│   ├── BookingCreateRequest.java                   — record (instructorId, studentId, scheduledAt, durationMins, location?, notes?)
│   ├── BookingUpdateRequest.java                   — record (scheduledAt?, durationMins?, location?, notes?)
│   ├── BookingCancelRequest.java                   — record (reason)
│   ├── BookingResponse.java                        — record (id, instructorId, studentId, scheduledAt[ISO-UTC], scheduledAtLocal[ISO+offset], durationMins, status, …)
│   ├── AvailabilityRuleRequest.java                — record (dayOfWeek, startTime, endTime, validFrom?, validUntil?)
│   ├── AvailabilityOverrideRequest.java            — record (onDate, startTime?, endTime?, type, reason?)
│   └── CalendarResponse.java                       — record (instructorId, weekStart, slots[], bookings[])
├── service
│   ├── BookingService.java                         — book / reschedule / cancel; catches the EXCLUDE violation and translates to BadRequestException("SLOT_CONFLICT")
│   ├── AvailabilityService.java                    — CRUD on rules + overrides
│   └── ConflictTranslator.java                     — JDBC SQLState '23P01' → typed exception
└── controller
    ├── BookingController.java                      — REST under /api/bookings
    └── AvailabilityController.java                 — REST under /api/instructors/{id}/availability
```

### 5.3 Booking transaction

```java
@Transactional
public BookingResponse book(BookingCreateRequest req, UUID actorUserId, Role actorRole) {
    // 1. Validate the slot lies on the 30-min grid
    if (!isOn30MinGrid(req.scheduledAt(), req.durationMins())) {
        throw new BadRequestException("SLOT_NOT_ON_GRID", "scheduledAt must be on a :00/:30 boundary");
    }

    // 2. Validate instructor availability (rules + overrides) in school TZ
    if (!availabilityService.isAvailable(req.instructorId(), req.scheduledAt(), req.durationMins())) {
        // V1 (D28): availability is *advisory only* — log a warning and proceed.
        // The DB EXCLUDE still prevents double-booking; this just lets us tighten
        // the rule in V2 without a schema migration.
        log.warn("Booking outside instructor availability — instructor={} at={}",
                 req.instructorId(), req.scheduledAt());
    }

    // 3. Validate student has lessons remaining (uses lock from Module 2)
    Student s = studentRepository.findByIdForUpdate(req.studentId()).orElseThrow(...);
    if (s.getLessonsRemaining() <= 0) {
        throw new BadRequestException("NO_CREDITS", "Student has no lessons remaining");
    }

    // 4. Insert booking — DB enforces the no-overlap constraint
    LessonSession ls = LessonSession.builder()
        .schoolId(s.getSchoolId())
        .instructorId(req.instructorId())
        .studentId(req.studentId())
        .scheduledAt(req.scheduledAt())
        .durationMins(req.durationMins())
        .location(req.location())
        .notes(req.notes())
        .status(SCHEDULED)
        .build();
    try {
        ls = lessonSessionRepository.save(ls);
        lessonSessionRepository.flush();    // force the EXCLUDE check now, not at commit
    } catch (DataIntegrityViolationException ex) {
        if (conflictTranslator.isExclusionViolation(ex)) {
            throw new BadRequestException("SLOT_CONFLICT",
                "That time slot is already booked for this instructor");
        }
        throw ex;
    }

    // 5. Fire alerts (PR4 wa.me message log + parent reminder rows)
    notificationService.lessonBooked(ls, actorUserId);

    return BookingResponse.from(ls, schoolTimezoneFor(s.getSchoolId()));
}
```

Two non-obvious bits:

1. The **`flush()` after save** forces Hibernate to send the `INSERT` to the
   DB before the method returns. Without it, the conflict surfaces at commit
   time — outside the controller's exception-translation layer — and the user
   sees a 500 instead of a 400.
2. **Availability is advisory in V1.** The user explicitly said the booking
   path covers "instructor sets availability… student/owner/instructor can
   book." We treat availability rules as a UX hint (the calendar greys out
   unavailable slots) but don't reject the booking server-side. This is the
   cheapest form of D28; tightening to "reject" is a one-line change in step
   2 once we have customer feedback.

### 5.4 Calendar API

| Verb | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/calendar` | OWNER (own school), INSTRUCTOR (own bookings + own school's), STUDENT (own bookings), PARENT (linked students' bookings) | Query: `instructorId?`, `studentId?`, `from=ISO`, `to=ISO`. Default range = current week in school TZ. |
| POST | `/api/bookings` | OWNER, INSTRUCTOR, STUDENT (self), PARENT (for linked students)* | *Question P-2 below. |
| PATCH | `/api/bookings/{id}` | OWNER, INSTRUCTOR (own), STUDENT (own, future only) | Reschedule = same booking row updated; triggers the EXCLUDE check again. |
| POST | `/api/bookings/{id}/cancel` | OWNER, INSTRUCTOR (own), STUDENT (own, ≥24h out)* | Sets status=CANCELLED, fills cancel-audit columns. *Cancellation window in P-3 below. |
| POST | `/api/bookings/{id}/complete` | INSTRUCTOR (own) | Sets status=COMPLETED, writes `lesson_credits` -1 row. |
| GET | `/api/instructors/{id}/availability` | OWNER same school, INSTRUCTOR self, STUDENT (any in same school) | Returns rules + overrides combined into a calendar overlay. |
| POST/PATCH/DELETE | `/api/instructors/{id}/availability` | OWNER, INSTRUCTOR (self) | CRUD on rules + overrides. |

> **Open fork (P-2):** The user said "Student/owner/instructor can book" — it's
> ambiguous whether **PARENT** can book on a kid's behalf. Defaulting to
> **YES, parent can book for linked students** (matches the typical use case:
> a 16-year-old's parent is paying and managing the calendar). Confirm.
>
> **Open fork (P-3):** Student-initiated cancellation window. Default to
> **24 hours** (matches typical school policy and gives instructor time to
> backfill). Owner/instructor can override at any time. Confirm.
>
> **Open fork (P-4):** When a STUDENT books, do they consume a lesson credit
> immediately at booking time, or only when the lesson completes? Default to
> **on completion** (the `LESSON_COMPLETED` ledger row in §3.5) — protects
> against dropped bookings burning credits. Owners can choose differently per
> school in V2.

### 5.5 Why the EXCLUDE constraint is the right answer

A common alternative is application-side `SELECT … FOR UPDATE` on the
instructor's bookings before insert. Drawbacks compared to `EXCLUDE`:

| Concern | Application-side check | `EXCLUDE` constraint |
|---|---|---|
| Concurrent inserts at the millisecond boundary | Race window between SELECT and INSERT | Atomic at the index level |
| Direct SQL from admin tools / Supabase studio | Bypasses the check | Caught |
| Clarity in code review | Requires reviewer to trace lock + query semantics | "The DB enforces it" |
| Performance | Two round trips per booking | One round trip (fails fast) |

The `EXCLUDE` constraint is a few lines of SQL and a `btree_gist` extension. It
moves the booking guarantee from "we hope the application got the locks
right" to "the schema mathematically forbids it." Worth every penny.

---

## 6. V9 migration outline (`V9__phase1_modules_2_3_4.sql`)

```
1.  CREATE EXTENSION btree_gist (idempotent)
2.  schools.timezone column + is_valid_timezone() function + CHECK
3.  students:
      - widen status CHECK + backfill PASSED→GRADUATED
      - add contact_email, version (BIGINT for @Version)
      - drop NOT NULL on user_id IF S-1 confirmed
4.  lesson_credits table + indexes
5.  parent_student.status + ended_at + CHECK
6.  lesson_sessions:
      - duration_mins CHECK (multiples of 30, 30..240)
      - cancel/complete audit columns
      - status CHECK widening
      - status↔cancelled_at consistency CHECK
      - EXCLUDE constraint
7.  instructor_availability + instructor_availability_overrides tables
8.  Index review on existing tables for the new query patterns:
      - lesson_sessions (school_id, scheduled_at) — already covered in V1
      - lesson_sessions (instructor_id, scheduled_at) — already covered
      - lesson_sessions (student_id, scheduled_at) — already covered
9.  Backfill / no-op cleanup
```

Same conventions as V8: defensive `IF NOT EXISTS`, no DROP of pre-PR2 columns,
all CHECKs validated against test fixtures before the migration is committed.

---

## 7. PR sequence

| PR | Scope | Status |
|---|---|---|
| PR1 | Remove Facebook OAuth | ✅ landed 2026-04-25 |
| PR2 | V8 foundation migration + JPA entities | ✅ landed 2026-04-25 |
| PR3 | Auth hardening (TD-01..TD-07 from §12 of `01-foundation-design.md`) | pending |
| PR4 | Phone CRUD + WhatsApp wa.me + audit log | pending |
| PR5 | Email verification + invitations | pending |
| PR6 | School + Instructor controllers + RBAC matrix | pending |
| **PR7** | **Module 2 — Student management (V9 student parts + entities + controller + tests)** | pending |
| **PR8** | **Module 3 — Parent portal (parent_student.status + read-only endpoints + parent-scope checker)** | pending |
| **PR9** | **Module 4 — Scheduling engine (V9 scheduling parts + booking + availability + EXCLUDE conflict translator)** | pending |

PR7/8/9 cannot land before PR3–PR6 because they depend on:

- PR3's tightened JWT filter (RBAC enforcement in PR7/8/9 controllers).
- PR4's `phone_numbers` write surface (Student creation populates it).
- PR4's `whatsapp_message_log` writes (Module 3 alerts log into it).
- PR5's email verification + invitations (Student creation when no email is on file).
- PR6's `/api/instructors` endpoints (Module 4's availability endpoints reuse the auth guard).

---

## 8. Engineering-lens callouts (for the four slash commands invoked)

### `/engineering:code-review`

What a strict reviewer should flag in this design before code is written:

- **`students.user_id` nullability** is a contract change. Want explicit sign-off on S-1 before V9.
- **Counter + ledger consistency** must be tested with concurrent decrement (two threads, last-credit case). Add `LessonCreditServiceConcurrentTest` (Testcontainers) to PR7's exit criteria.
- **EXCLUDE constraint translation** needs a unit test that targets the `'23P01'` SQLState directly. Add `BookingServiceConflictTranslationTest`.
- **wa.me alert volume** could spam parents if a student's package is exhausted (decrement to 0 fires `package_low_v1`, then to -1 fires nothing). Implement a *one-shot guard* via `students.last_low_alert_at`.

### `/engineering:architecture`

- **Service boundaries.** Module 2 owns `Student` + `LessonCredit`; Module 4 owns `LessonSession` + availability. They communicate through the `LessonCreditService` (booking completion → credit decrement). No reverse dependency from Module 2 to Module 4.
- **Multi-school instructors (D5).** Availability rules are instructor + school keyed. An instructor working at 2 schools has 2 separate availability rule sets — they're not deduped. Trade-off: the instructor maintains both, but bookings stay clean.
- **TZ at the edge.** All times stored UTC; conversion happens at the controller boundary using `ZoneId.of(school.timezone)`. Service + repo layers are TZ-naïve.

### `/engineering:system-design`

- **Hot path: `GET /api/calendar`.** Likely 60% of read traffic. Indexes already on `(school_id, scheduled_at)` and `(instructor_id, scheduled_at)`. Add a covering composite if the query plan shows an extra heap fetch.
- **Cold path: lesson_credits ledger reconstruction.** Used only for audit + invariant tests; no online query path needs to scan it.
- **Hot path: booking insert.** `EXCLUDE` constraint uses a GiST index — `O(log n)` per insert. For a school with 50 instructors × 8 hours × 2 slots × 250 working days = 200k rows/year. GiST scales fine.

### `/engineering:tech-debt`

Net-new tech-debt entries to record in `01-foundation-design.md` §12 (numbered after the existing TD-01..TD-10):

| ID | Item | Severity | Pay-off cost |
|---|---|---|---|
| TD-11 | `instructors.working_hours_json` deprecated; PR9 stops reading it but column remains until V10 | medium | 30 min — DROP COLUMN in V10 |
| TD-12 | Availability rules are advisory in V1 (booking outside is allowed, only logged) | medium | 1 day — flip to enforce + add override flag for owners |
| TD-13 | Student double-booking not enforced | low | 1 day — second EXCLUDE on (student_id, range) |
| TD-14 | Vehicle / car resource not modelled | low | 2 days — `vehicles` table + extra constraint |
| TD-15 | Cancellation window hard-coded to 24h | low | 2 hr — surface as `schools.cancellation_window_hours` |
| TD-16 | Booking → lesson_credits decrement runs at completion only; bookings that never get marked COMPLETED leak credits forever | medium | 4 hr — scheduled job to auto-mark `NO_SHOW` after 48h |
| TD-17 | Parent alert delivery is email + wa.me link, not native WhatsApp push (P-1) | high if customer expects push | 2 weeks — Meta WhatsApp Cloud API integration (deferred per Option B in §11 of `01-foundation-design.md`) |

---

## 9. What this design explicitly does NOT cover

- Group lessons (1 instructor → many students at once). All bookings are 1:1.
- Recurring bookings (e.g., "every Tuesday at 4pm for 8 weeks"). Students book one slot at a time in V1; the UI can submit 8 sequential POSTs for the same effect.
- Waitlists for fully-booked instructors.
- Instructor self-onboarding without owner approval — Module 2 + the existing PR2 invitations flow already cover the owner-invites case; instructor self-signup remains as covered by the existing auth flow but does not create the `instructors` row until Owner activation (PR6 surface).
- Mobile push notifications (PR4 covers wa.me + email; native push is V2).
- Stripe-integrated package purchase. PR7 lets owners record manual `PURCHASE` ledger rows; Stripe webhooks land in V2.

---

## 10. Sign-off checklist

Before kicking off PR7/PR8/PR9 implementation work, the following user
decisions must be confirmed (recommended defaults shown in **bold**):

| ID | Question | Default |
|----|----------|---------|
| S-1 | Drop `NOT NULL` on `students.user_id` to support shadow students, or always provision a user? | **Always provision a user with `must_change_password=TRUE` (no schema change)** |
| P-1 | Parent "WhatsApp alerts" delivery — email-with-wa.me-link, dashboard task list for school staff, or full Meta Cloud API integration? | **Email + wa.me link in V1; Cloud API tracked as TD-14** |
| P-2 | Can a PARENT create bookings for linked students? | **Yes** |
| P-3 | Student-initiated cancellation window? | **24 hours** |
| P-4 | When does a STUDENT booking decrement `lessons_remaining`? | **On `COMPLETED`, not on book** |

Once those are confirmed, the implementation order is:
PR3 → PR4 → PR5 → PR6 → PR7 → PR8 → PR9.

— end —
