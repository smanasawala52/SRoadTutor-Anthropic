# SRoadTutor — Manual Testing Guide

A step-by-step walk-through for verifying the four user roles end-to-end:
**OWNER**, **INSTRUCTOR**, **STUDENT**, **PARENT**.

> Run the **backend** first (`cd backend && ./mvnw spring-boot:run` — it boots
> against the dev DB defined in `application-dev.yml`). Then build & run the
> **Android app** from Android Studio (Run ▶︎ on the `app` module, or
> `./gradlew :app:installDebug`). The debug build points at
> `http://10.0.0.228:8080/`, so either run on an emulator on the same machine
> or change `API_BASE_URL` in `app/build.gradle.kts` to your LAN IP.

If you ever see a 401 popup, it means the access token expired and the
refresh-token rotation is working. The next request will succeed.

---

## 0. One-time setup

1. Backend boots clean → hit <http://localhost:8080/swagger-ui.html> in a
   browser; you should see the live OpenAPI viewer with all 77 endpoints.
2. The dev profile auto-seeds the SGI mistake catalog and dealership /
   insurance broker reference rows. No manual seed needed.
3. The app launcher icon is "SRoadTutor". On first launch you land on the
   splash screen → Login.

---

## 1. OWNER workflow

You are the driving-school owner. You sign up, create the school, invite
staff and students, manage the calendar, and watch revenue roll up.

### 1.1 Sign up as an OWNER
1. Tap **Create Account** on the login screen.
2. Enter full name, email, password (≥ 8 chars), pick **OWNER**, tap **Sign Up**.
3. You land on the email-verification screen. The dev backend logs the
   verification token to the server console — copy the 6-digit code from
   the log line `New verification token issued ...` and paste it in the
   field, tap **Verify**.
4. App routes to the **Dashboard**. (If verification mail is wired in your
   environment, click the link in the email instead.)

### 1.2 Create your school
1. Tap **Schools** in the bottom nav.
2. Tap the ➕ FAB, fill in name / jurisdiction (e.g. `SK`) / timezone
   (`America/Regina`).
3. Tap **Create**. The school appears in the list. Note: this also auto-
   creates a Stripe Customer when `STRIPE_SECRET_KEY` is set.

### 1.3 Invite an instructor
1. Open **Invites** → ➕ → **Instructor** tab.
2. Enter their name + email → **Send Invite**.
3. The dev backend logs the accept URL. Copy it (looks like
   `sroadtutor://invitations/<token>`) — you'll use it later as the instructor.

### 1.4 Add a student directly (no invitation)
1. Open **Students** → ➕ FAB.
2. Fill name / email / `10` lessons → **Add**.
3. Confirm the student appears in the list with their **fullName** and
   **email** displayed (this was a bug — backend now joins User table).
4. Tap the student to open the detail pane → tap **Manage Phone Numbers**
   → ➕ → enter a Canadian E.164 number (`+13065551234`), tick **WhatsApp
   opt-in** → **Save**.

### 1.5 Book and complete a session
1. Open **Sessions** in the bottom nav → ➕ FAB.
2. Enter the **Instructor ID** (UUID — copy from the instructor row) and
   **Student ID** (UUID — copy from the student row), pick a future ISO
   time (the dialog pre-fills tomorrow at 10:00 UTC), keep duration `60`.
3. Tap **Book**. The list refreshes; new session appears with status
   **SCHEDULED**.
4. Tap **Complete** on the session card. Status flips to **COMPLETED**;
   `lessonsRemaining` on the student decrements; an UNPAID payment row
   is auto-created.

### 1.6 Mark the payment paid
1. Reopen the student detail pane → **Financial Ledger**.
2. Tap a payment row → **Mark Paid**. The total-outstanding on the
   dashboard drops accordingly.

### 1.7 Fire a manual reminder
1. Open **Alerts** → pick a pending reminder (the cron pre-generates
   them 24 h before each scheduled session) → **Fire**.

### 1.8 Mark a student PASSED → graduation triggers
1. Reopen the student detail → **Deactivate Student** is the toggle for
   inactive; for graduation use **Update** with `status=PASSED` (via
   the SwaggerUI in dev). Doing that fires:
   - `LeadRoutingService.onStudentPassed` → if a dealership exists in the
     same province a `dealership_leads` row is created.
   - `InsuranceLeadService.onStudentPassed` → an `insurance_leads` row is
     created for a province-matched broker.
2. Confirm under **Market** and **Cover** tabs respectively.

### 1.9 Convert a marketplace lead → bounty payout
1. **Market** → tap a NEW dealership lead → **Convert**.
2. Open **Team** → tap the instructor → scroll to payouts → there should
   be a `$100 PENDING` row. Tap **Mark Paid** when the e-transfer is sent.

### 1.10 Risk score (Phase 3)
1. Student detail → **View Risk Diagnostics** → tap **Generate**.
2. Backend computes a tier (LOW/MEDIUM/HIGH/CRITICAL) and stores a
   PIPEDA-compliant SHA-256 hash. Hit **Refresh** to read the row by hash.
3. Dashboard now shows a **Risk Distribution** card with counts per tier.

---

## 2. INSTRUCTOR workflow

You're an instructor who was invited by an OWNER (or you self-registered).

### 2.1 Accept the OWNER's invitation
1. Open the link from step 1.3 — `sroadtutor://invitations/<token>`. The
   app auto-routes to the **Invitation Acceptance** screen (deep-link
   was wired in `MainActivity.extractInvitationToken`).
2. Set a password → **Accept**. App routes you to **Login**.
3. Log in with the email + new password.

### 2.2 Self-register your instructor profile
If you are an INSTRUCTOR user without a pre-populated row (e.g. signed up
directly), the **Team** tab will show a "complete your profile" CTA. Fill
in license / SGI cert / vehicle / hourly rate / working hours and **Save**.
Your `instructor.id` is now cached in `SessionManager` and used as the
default filter for the calendar.

### 2.3 View your calendar
1. Open **Sessions** — the list is filtered to YOUR scheduled sessions
   (the backend resolves your `instructor.id` from the JWT for INSTRUCTOR
   role; the `instructorId` query param is ignored for safety).
2. On a SCHEDULED card: **Log Mistakes**, **Reschedule**,
   **No-Show**, **Cancel**, **Complete** — exactly the same actions as an
   OWNER but only for YOUR sessions.

### 2.4 Log a mistake on a completed session
1. Tap **Log Mistakes** on a SCHEDULED or just-completed card.
2. Pick a mistake from the SGI catalog (the screen pulls from
   `/api/mistakes/categories/SK`), set demerits, tap **Log**.
3. App routes you to the **Telemetry attach** screen — paste a JSON
   payload like `{"speed":54,"steering":-0.2}` and an `offsetMs` (negative
   = lead-up, positive = recovery), **Attach**.

### 2.5 Tap a student's report card PDF
1. From any session, open the student detail (via the OWNER side — V1
   instructors don't get the full student detail; they read mistakes
   directly).

---

## 3. STUDENT workflow

You're a student who was added by an OWNER (no signup needed). Your
account uses the dummy password `test123` and forces a rotation on first
login (`mustChangePassword = true`).

### 3.1 First login
1. Tap **Login** → enter the email used by the OWNER + `test123`.
2. The backend response sets `mustChangePassword = true`. (V1: the SPA
   doesn't yet present the rotation form on first login — track this
   under the "Up next" backlog. For now, log in and proceed.)

### 3.2 View your calendar
1. **Sessions** — the list shows ALL your scheduled lessons. Backend
   resolves your `student.id` from your JWT, so any `studentId` query
   param is ignored for safety.
2. Tap a session card → **View Mistakes** to see what your instructor
   logged on past lessons.

### 3.3 Read your readiness score
1. Open **Dashboard** → if a parent isn't visible yet, navigate via
   `/api/students/me/readiness-score` in Swagger UI to see the raw JSON.
   In V1 the student-side readiness widget lives on the OWNER dashboard;
   the student app reads via the same endpoint.

### 3.4 Tap-to-WhatsApp your instructor
1. From any screen with a **WhatsApp** button (e.g. student detail when
   logged in as OWNER), tap. App calls `/api/whatsapp/links`, opens
   `wa.me/...` in WhatsApp. If WhatsApp isn't installed you get a toast.

---

## 4. PARENT workflow

You're a parent who was linked to a student by the OWNER (or invited
directly via **Invites → Parent**).

### 4.1 Accept the parent invitation
1. Open `sroadtutor://invitations/<token>`. App deep-links into
   **Invitation Acceptance** → set password → **Accept**.
2. Log in with email + new password.

### 4.2 Your dashboard
PARENT users see:
- **Dashboard** (read-only welcome state — no school metrics).
- **Sessions** — read-only, but PARENT must pick a child first. The
  calendar screen shows an "Open a child's profile to view their
  calendar" hint until a `studentId` is selected. (V1 limitation —
  there's no parent-side child picker yet; use the matchmaker screen
  to link.)
- **Auto** — the **First Car Matchmaker** form. Submit vehicle prefs,
  budget, financing flag → backend creates one NEW dealership lead
  per child (re-submit overwrites).

### 4.3 Submit the matchmaker form
1. **Auto** → fill make / model / budget → **Submit**.
2. The lead is queued for the OWNER to convert at graduation.

### 4.4 Tap-to-WhatsApp the instructor or owner
Same as the student flow — appears when the corresponding contact has
a primary phone with `whatsapp_opt_in = true`.

---

## 5. Cross-role smoke test (5 min)

1. **Backend up**, **App debug build installed**, **emulator running**.
2. Sign up an OWNER → create school → add an INSTRUCTOR (invite) → add a
   STUDENT directly with a `parentEmail` filled in (this auto-creates the
   parent user with dummy password) → book a session → complete it.
3. Open Swagger UI → log in as the auto-created student
   (`student@email + test123`) at `/auth/login`, copy the `accessToken`,
   hit `GET /api/students/me/readiness-score` → 200 OK.
4. Same with parent: `/auth/login` with parent email + `test123`, then
   `POST /api/marketplace/matchmaker` with a body like
   `{"studentId": "...", "preferredMake": "Toyota", "budgetCents": 1500000}`
   → 201 CREATED.
5. Mark the student PASSED via `PUT /api/students/{id}` body
   `{"status":"PASSED"}` → check `/api/marketplace/schools/{schoolId}/leads`
   shows a new ROUTED lead.

If all five steps pass, the four-role end-to-end is healthy.

---

## 6. Multilingual UI

The app ships with full UI localization for **9 languages**: English (default),
Punjabi, Hindi, Urdu, Bengali, Arabic, Korean, Cantonese (zh-HK), and Filipino.

**Where to switch languages:**
- **Pre-login** — tap the globe icon in the top-right of the **Login** screen.
- **In-app** — every main screen has a globe icon in the top app bar (or
  navigation rail on tablets) next to the logout button.

**Behaviour:**
1. Tap the globe → **Select Language** screen lists all 9 languages with their
   native script labels and a checkmark on the current selection.
2. Tap any row. The activity recreates with the new locale applied via
   `attachBaseContext`.
3. **Arabic / Urdu** automatically flip the layout direction (RTL): the bottom
   nav, top bar, and form fields mirror correctly because
   `android:supportsRtl="true"` is set in the manifest and `Configuration#setLayoutDirection` is wired in `AppLocales.wrap`.
4. The user's choice is persisted to DataStore and survives app restarts.
5. On signup, the SPA forwards the chosen language to the backend as
   `languagePref` (8-char tag — e.g. `pa`, `yue`, `fil`) so future
   server-rendered emails / PDFs / WhatsApp templates pick the right
   localization.

**Language test (~3 min):**
1. Fresh install → tap the globe on the login screen → pick **ਪੰਜਾਬੀ** (Punjabi).
2. Confirm: app title `SRoadTutor`, login button reads `ਲੌਗਇਨ`, password
   placeholder reads `ਪਾਸਵਰਡ`.
3. Pick **العربية** (Arabic) → confirm the layout flips RTL (bottom nav
   reverses order, the globe icon now shows on the top-left).
4. Pick **English** → app reverts.

---

## 7. Booking dropdowns (OWNER / INSTRUCTOR)

The **Book Session** dialog now shows two dropdowns instead of UUID text fields:

- **Instructor** dropdown — populated from `GET /api/schools/{schoolId}/instructors`,
  shows `Full Name · email`. First instructor is selected by default.
- **Student** dropdown — populated from `GET /api/schools/{schoolId}/students`,
  shows `Full Name · email`. First student is selected by default.

If the role can't list the rosters (e.g. STUDENT/PARENT pre-login), the dialog
falls back to plain UUID text fields automatically. After tapping **Book** the
calendar refreshes itself — no manual reload needed.

---

## 8. Known gaps that fail manual testing if you hit them

These are tracked but not fixed in this round:

| Gap | Workaround |
| --- | --- |
| First-login `mustChangePassword` flow doesn't redirect into a rotation screen yet | Manually call `PUT` against the User to clear it, or just log in normally — the flag is informational in V1. |
| Parent has no UI to pick which linked child's calendar to view | Use the matchmaker screen (which only fires for a single child) or hit `/api/sessions?studentId=<id>` via Swagger UI. |
| OWNER cannot edit instructor working hours from the app — only via Swagger PUT `/api/instructors/{id}` | Use Swagger UI; the form-builder is a v1.1 item. |
| `report-card.pdf` downloads the PDF to memory but doesn't save / open it | Pull the bytes via Swagger UI; the file-save UX is a v1.1 item. |
| Forgot-password email is a placeholder button | Use Swagger UI `POST /auth/email-verify/send` for now if account access is lost. |
| Stripe Checkout link returned by `/api/subscriptions/upgrade` opens a browser tab — the in-app webview is a v1.1 item | Open the URL manually until the WebView screen lands. |
| Google Sign-in button is hidden when the build's `googleWebClientId` gradle property is unset | Set it in `~/.gradle/gradle.properties` (`googleWebClientId=xxxxx.apps.googleusercontent.com`) and rebuild. |

If you find anything in steps 1–5 that doesn't behave like above, file
the broken endpoint + payload and I'll patch it next round.
