# Local Verification + Deploy Runbook

This is the “run these commands on your Windows laptop to finish the setup”
document. It assumes:

- You already cloned (or have) `C:\path\to\SRoadTutor` on your computer.
- You have `java -version` → **21.0.6** (confirmed ✅).
- You have your GitHub repo at **https://github.com/smanasawala52/SRoadTutor-Anthropic**.
- You have the 3 Supabase databases created (see `SUPABASE_SETUP.md`).

If any of the above is not true, STOP and finish that doc first.

---

## Section 0 — Orientation (read before typing anything)

The Cowork sandbox that scaffolded this project cannot push to GitHub or run
Docker / Maven / Flutter — those require your local machine. The scaffolded
code lives on disk in your selected `SRoadTutor` folder, and there is a
`sroadtutor-initial-commit.bundle` file at the project root that captures the
first git commit (hash `830e5d5…`).

You will:
1. Push the code to your GitHub repo (Section 1).
2. Install the last missing pieces of tooling (Section 2).
3. Run backend tests twice (Section 3).
4. Run mobile tests twice (Section 4).
5. Populate dev data in the database (Section 5).
6. Deploy backend to Railway (Section 6).

Budget: about 90 minutes end-to-end if nothing explodes, 3 hours if it does.

---

## Section 1 — Push the code to GitHub (do this first)

Open **PowerShell** in the folder you selected in Cowork — the one that
contains `backend/`, `mobile/`, `docs/`, and `sroadtutor-initial-commit.bundle`.

```powershell
cd C:\path\to\SRoadTutor     # adjust to your real path

# Cowork may have left a half-initialized .git folder. Nuke it.
Remove-Item -Recurse -Force .git -ErrorAction SilentlyContinue

# Start a fresh local repo.
git init -b main
git config user.name  "Your Name"
git config user.email "kasim.trophy@gmail.com"

# Stage + commit everything (the .gitignore is already correct).
git add -A
git commit -m "Initial commit: SRoadTutor scaffolding"

# Point at your GitHub repo and push.
git remote add origin https://github.com/smanasawala52/SRoadTutor-Anthropic.git
git push -u origin main
```

> **First-time push?** GitHub will open a browser window and ask you to
> authenticate. Use the GitHub CLI flow (`gh auth login`) if it prompts,
> otherwise a Personal Access Token with `repo` scope works.

**Verify:** open `https://github.com/smanasawala52/SRoadTutor-Anthropic` — you
should see `backend/`, `mobile/`, `docs/`, and the GitHub Actions tab should
show the **Backend CI** and **Mobile CI** workflows running.

> Don't forget: `sroadtutor-initial-commit.bundle` is a helper file; you can
> delete it after the first push — `git rm sroadtutor-initial-commit.bundle`
> then `git commit -m "Remove bundle"` then `git push`.

---

## Section 2 — Install the rest of the tooling

You already have Java 21 and IntelliJ Community. You still need:

| Tool        | Why                                    | Install link                                        |
|-------------|----------------------------------------|-----------------------------------------------------|
| Docker Desktop | Testcontainers uses it for integration tests | https://www.docker.com/products/docker-desktop/ |
| Flutter 3.24  | Mobile build + tests                    | https://docs.flutter.dev/get-started/install/windows |
| Android Studio (just for SDK) | Flutter needs Android SDK + tools | https://developer.android.com/studio |
| Railway CLI (optional) | One-shot deploy from CLI        | `npm i -g @railway/cli` OR use Railway web UI       |

After Flutter installs, run:

```powershell
flutter doctor -v
```

Every row should be green or at worst a yellow warning. If any row is red,
resolve it before continuing. Flutter's own error messages are usually
actionable.

---

## Section 3 — Backend tests (run twice, per your spec)

```powershell
cd C:\path\to\SRoadTutor\backend

# Copy the dev env template into a real .env.dev file.
copy .env.dev.example .env.dev
notepad .env.dev    # fill in JWT_SECRET + the Supabase dev URL + creds
```

### 3a. First run

```powershell
# Required env for the test profile (tests use Testcontainers, NOT Supabase).
$env:JWT_SECRET="test-secret-at-least-32-bytes-long-aaaaaaaaaaaaaaa"

.\mvnw.cmd clean verify
```

Expected outcome on a clean machine:
- ✅ All unit tests green (Surefire report)
- ✅ All integration tests green (Failsafe report) — these spin up a
  Postgres 16 Docker container automatically.
- ✅ JaCoCo report shows ≥80% line coverage, ≥70% branch coverage.
- ✅ Build ends with `BUILD SUCCESS`.

Open the coverage report at `backend/target/site/jacoco/index.html` in your
browser to eyeball it.

### 3b. Second run (idempotency check)

```powershell
.\mvnw.cmd clean verify
```

Should produce the same result in ~70% of the wall time of the first run
(Maven caches dependencies; Docker caches the Postgres image).

### 3c. Smoke test the API locally

```powershell
# .env.dev must have real Supabase dev-DB creds for this step.
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
```

In another PowerShell window:
```powershell
# Health check
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}

# Swagger UI
start http://localhost:8080/swagger-ui.html
```

On the first `spring-boot:run` against the dev DB, Flyway will execute V1 → V4
and the dev seed will populate. You will see logs like:

```
Migrating schema "public" to version "1 - init schema"
Migrating schema "public" to version "2 - indexes"
Migrating schema "public" to version "3 - sgi mistake categories"
Migrating schema "public" to version "4 - seed dev data"
Successfully applied 4 migrations
```

---

## Section 4 — Mobile tests (run twice, per your spec)

> **First time only:** before the very first `flutter test` or `flutter run`,
> follow **`docs/ANDROID_GRADLE_BOOTSTRAP.md`** to generate the binary files
> (`gradlew`, `gradle-wrapper.jar`, `ic_launcher.png`, iOS scaffolding) that
> the Cowork sandbox couldn't write. One command — `flutter create .` with a
> few flags — fills everything in and does **not** overwrite our custom files.
> Skip this step on subsequent runs.

```powershell
cd C:\path\to\SRoadTutor\mobile
flutter pub get
dart run build_runner build --delete-conflicting-outputs
```

### 4a. First run

```powershell
flutter analyze
flutter test --coverage
```

Expected:
- ✅ `flutter analyze` reports zero errors (warnings are OK).
- ✅ `flutter test` shows all unit + widget tests passing.
- ✅ Coverage file written to `mobile/coverage/lcov.info`.

### 4b. Second run

```powershell
flutter test --coverage
```

Same result. Coverage should be deterministic.

### 4c. Launch the app on an emulator or phone

```powershell
# List devices
flutter devices

# Launch the dev flavor pointing at localhost:8080
flutter run --flavor dev -t lib/main_dev.dart
```

If running on a physical Android phone, note that `localhost:8080` from the
phone’s perspective is the phone itself, not your laptop. Edit
`mobile/lib/env/env_dev.dart` temporarily to point at your laptop's LAN IP
(e.g., `http://192.168.0.14:8080`).

Log in with any seed account:

| Email                           | Password    | Role       |
|---------------------------------|-------------|------------|
| owner@sroadtutor.dev            | Password1   | OWNER      |
| instructor1@sroadtutor.dev      | Password1   | INSTRUCTOR |
| student1@sroadtutor.dev         | Password1   | STUDENT    |
| parent1@sroadtutor.dev          | Password1   | PARENT     |

---

## Section 5 — Verify dev data populated

Open your Supabase dev project → SQL Editor:

```sql
SELECT email, role, enabled FROM users ORDER BY role, email;
-- Expect 6 rows: 1 owner, 2 instructors, 2 students, 1 parent.

SELECT name, jurisdiction FROM schools;
-- Expect 2 rows: Regina Wheels, Saskatoon Road Masters.

SELECT code, severity FROM mistake_categories WHERE jurisdiction = 'SGI'
ORDER BY code;
-- Expect all SGI categories from V3__sgi_mistake_categories.sql.

SELECT COUNT(*) FROM lesson_sessions;
-- Expect 3 (2 completed, 1 scheduled).
```

If any row count is zero, check:
- The Spring profile is `dev` (run `echo $env:SPRING_PROFILES_ACTIVE`).
- `application-dev.yml` has `spring.flyway.placeholders.seed-dev: "true"`.
- `V4__seed_dev_data.sql` migration succeeded (check Flyway's
  `flyway_schema_history` table).

---

## Section 6 — Deploy backend to Railway

Follow `docs/RAILWAY_DEPLOY.md` — it has the full walkthrough. Short version:

1. Go to https://railway.app → New Project → Deploy from GitHub repo →
   select `smanasawala52/SRoadTutor-Anthropic`.
2. In the service settings, set the root directory to `/backend`.
3. Add environment variables (from your `.env.prod.example`):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://…` (Supabase prod)
   - `SPRING_DATASOURCE_USERNAME=…`
   - `SPRING_DATASOURCE_PASSWORD=…`
   - `JWT_SECRET=<generate 32+ random bytes>`
   - `GOOGLE_OAUTH_CLIENT_ID=…`
   - `GOOGLE_OAUTH_CLIENT_SECRET=…`
   - `FACEBOOK_OAUTH_APP_ID=…`
   - `FACEBOOK_OAUTH_APP_SECRET=…`
   - `CORS_ALLOWED_ORIGINS=https://your-mobile-frontend.com`
4. Railway detects the `Dockerfile` and builds automatically.
5. Watch the deploy logs. First deploy takes ~3–4 min.
6. Once green, hit `https://<your-railway-url>/actuator/health` → `"UP"`.
7. Mobile QA build: edit `mobile/lib/env/env_qa.dart` to point
   `apiBaseUrl` at the Railway URL, rebuild:
   ```powershell
   flutter build apk --release --flavor qa -t lib/main_qa.dart
   ```

---

## Section 7 — Troubleshooting cheat sheet

| Symptom                                         | Likely cause                               | Fix                                                               |
|-------------------------------------------------|--------------------------------------------|-------------------------------------------------------------------|
| `./mvnw: Permission denied`                     | Wrapper not executable                     | `git update-index --chmod=+x backend/mvnw` then commit            |
| Testcontainers: `Could not connect to docker`   | Docker Desktop not running                 | Start Docker Desktop, wait for it to be green                     |
| `JWT_SECRET must be at least 32 bytes`          | Env var missing or too short               | Generate: `openssl rand -base64 48`                               |
| Flyway migration fails with `${seed-dev}`       | Placeholder not interpolated               | Confirm `application-<profile>.yml` has the placeholder set       |
| Flutter test: `MissingPluginException`          | Code-gen didn't run                        | Re-run `dart run build_runner build --delete-conflicting-outputs` |
| Flutter analyzer: unused import warnings        | Normal, non-blocking                       | Ignore for now                                                    |
| Railway deploy fails: `ERR_NO_MATCHING_PATTERN` | Root dir not set                           | Service settings → Root dir = `/backend`                          |
| CORS rejected in browser                        | `CORS_ALLOWED_ORIGINS` missing             | Add the exact origin (scheme + host + port) as env var on Railway |

---

## Section 8 — What's next (not in this commit)

Phase-1 scaffolding is complete. Follow-up sessions should build:
- `schools` / `instructors` / `students` REST modules (skeletons expected in
  `backend/src/main/java/com/sroadtutor/…`).
- Mobile feature modules for the above (`mobile/lib/features/…`).
- Payments integration (Stripe sandbox → prod).
- Offline sync job (mobile → backend) that flushes the Drift queue.
- Playwright end-to-end browser tests (P2, once there's a web frontend).

Each new feature MUST:
- Add JUnit unit + integration tests keeping JaCoCo ≥80% line coverage.
- Add Flutter unit + widget + integration tests.
- Update the Flyway migration chain (V5, V6, …).
- Never read secrets from `application.yml` — always via `${ENV_VAR}`.
