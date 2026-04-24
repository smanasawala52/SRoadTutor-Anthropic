# Supabase Setup — 3 Projects for dev / QA / prod

Supabase gives us a hosted Postgres database, plus optional auth/storage features.
We'll create **three separate free-tier projects** so dev/QA/prod data never mix.

---

## 1. Create a Supabase account

1. Go to https://supabase.com → `Start your project` → sign in with GitHub.
2. Create a Personal Organization (or a Team one, doesn't matter).

---

## 2. Create the 3 projects

For each of `dev`, `qa`, `prod`:

1. Click `New project`.
2. Name: `sroadtutor-dev`, `sroadtutor-qa`, `sroadtutor-prod` (three separate times).
3. Database password: **generate a strong password** (the dashboard has a button) — **save it immediately**. You can't recover it later.
4. Region: pick the one closest to your users (e.g. `ca-central-1` for Saskatchewan/Canada).
5. Pricing plan: **Free**.

Wait ~2 minutes per project for Supabase to provision the database.

---

## 3. Collect the connection details for each project

For each project, open its dashboard and go to `Project Settings` (gear icon) → `Database`.

Under **Connection string**, pick the `URI` tab and copy the string. It looks like:

```
postgresql://postgres.ab12cd34ef56:[YOUR-PASSWORD]@aws-0-ca-central-1.pooler.supabase.com:6543/postgres
```

Also grab these values (same page):

- `Host` — e.g. `aws-0-ca-central-1.pooler.supabase.com`
- `Database name` — `postgres`
- `Port` — `6543` (transaction pooler) or `5432` (session, if you need long connections — for Flyway migrations use `5432`)
- `User` — `postgres.ab12cd34ef56`
- `Password` — the one you saved

### Important: two ports, two uses

- **Port 5432** = direct connection. Use this in your **`.env`** for `SPRING_DATASOURCE_URL` (Flyway migrations need long-lived sessions).
- **Port 6543** = transaction pooler. Slightly better for stateless production workloads, but Flyway does not support it.

For this project, use **5432** everywhere to keep things simple.

---

## 4. Fill in the `.env` files

Copy the template and fill in real values. You do this separately for each env.

```bash
cp backend/.env.dev.example backend/.env.dev
cp backend/.env.qa.example backend/.env.qa
cp backend/.env.prod.example backend/.env.prod
```

Then edit each one. For `.env.dev`:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-ca-central-1.pooler.supabase.com:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.ab12cd34ef56
SPRING_DATASOURCE_PASSWORD=<dev-password-you-saved>
```

Repeat for `qa` and `prod`, using the details from each Supabase project.

> **Git safety**: the `.env.dev`, `.env.qa`, `.env.prod` files are already in `.gitignore`. Only the `.example` files are committed.

---

## 5. Run the Flyway migrations

When the backend starts up, Flyway will **automatically** apply the `V1__*.sql`,
`V2__*.sql`, `V3__*.sql` migrations in `backend/src/main/resources/db/migration/`.

Bootstrapping each env:

```bash
# dev
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# qa
./mvnw spring-boot:run -Dspring-boot.run.profiles=qa
```

After the first startup, log into each Supabase project and open the `Table Editor` —
you should see all 16 tables created. `mistake_categories` should have a few rows seeded for SGI.

---

## 6. Supabase Auth — are we using it?

**Short answer: not directly in Phase 1.** We're handling auth in the Spring Boot
backend (JWT + BCrypt + OAuth2) for full control and because it works the same
way offline-first for the mobile app. We only use Supabase as a Postgres host.

You can enable Supabase Auth later if you want features like magic-link email
login — it coexists fine with our custom auth.

---

## 7. Backups (prod only)

Supabase's free tier does daily backups automatically. For real production:

1. Supabase dashboard → prod project → `Database` → `Backups`.
2. Upgrade the project to the $25/mo **Pro** plan for 7-day point-in-time recovery.
3. Also, enable `Database` → `Webhooks` to export logs to your monitoring if needed.

**Don't run the app in prod on the free tier with real paying customers' data.**

---

## Troubleshooting

- **`FATAL: password authentication failed`**: double-check the password. Supabase passwords are case-sensitive and have special chars — wrap the `SPRING_DATASOURCE_PASSWORD` value in single quotes if it contains `$` or `!`.
- **`could not translate host name`**: you're offline, or the region is wrong.
- **Flyway: `Migration checksum mismatch`**: you edited an already-applied migration. Never edit a migration after it's been run — create a new `V(n+1)__fix.sql` instead. See `docs/ARCHITECTURE.md` for migration rules.
- **Connection pool timeout on Railway**: switch to the **transaction pooler** port `6543` in Railway's env vars. (Flyway runs once at startup on port 5432; runtime queries can then use 6543 — we handle this via two separate datasource URLs in `application-prod.yml`.)
