# Environment & Secrets Management

> **The ONE rule**: never commit a real secret to git.

This doc explains exactly how config flows through the system for all 3 envs.

---

## The layers, top to bottom

```
┌────────────────────────────────────────────┐
│  Your .env.dev / .env.qa / .env.prod       │   ← NOT committed. Real values.
│  (or Railway "Variables" in production)    │
└─────────┬──────────────────────────────────┘
          │ injected as OS environment variables
          ▼
┌────────────────────────────────────────────┐
│  application.yml                            │   ← Committed. Uses ${PLACEHOLDERS}.
│  application-dev.yml / qa.yml / prod.yml    │
└─────────┬──────────────────────────────────┘
          │ Spring Boot resolves placeholders
          ▼
┌────────────────────────────────────────────┐
│  Running backend                            │
└────────────────────────────────────────────┘
```

If an env var is missing, Spring Boot fails fast on startup with a clear
message — you cannot silently deploy with a missing secret.

---

## The `.env.*.example` files (committed templates)

`backend/.env.dev.example`, `.env.qa.example`, `.env.prod.example` live in git.
They contain the variable **names** with safe placeholder values so you (or a
teammate) can just copy them:

```
cp backend/.env.dev.example backend/.env.dev
# now edit .env.dev with real values
```

The `.gitignore` blocks anything named `.env*` except files ending in `.example`.

---

## The real `.env` files (NOT committed)

Same names without `.example`. You create these locally. Each looks like:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-ca-central-1.pooler.supabase.com:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.abc123
SPRING_DATASOURCE_PASSWORD=super-secret
JWT_SECRET=a-64-char-random-string-that-you-generate-yourself
GOOGLE_OAUTH_CLIENT_ID=...
...
```

---

## How Spring Boot reads them (local dev)

### Option A: IntelliJ run config (recommended — easy)

Install the **EnvFile** plugin, then on your Run Config:
1. `Modify options` → `Environment variables` → check "Enable EnvFile".
2. `+` → add `backend/.env.dev`.

Every time you Run, IntelliJ injects those vars into the Spring Boot process.

### Option B: Shell before running (works on any setup)

**Bash / Zsh (Mac / Linux):**
```bash
set -a
source backend/.env.dev
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**PowerShell (Windows):**
```powershell
Get-Content backend\.env.dev | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value)
}
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Option C: Docker Compose (if you containerize locally later)
The provided `docker-compose.yml` references `env_file: .env.dev` — nothing more to do.

---

## How Railway reads them (production)

Railway doesn't use `.env` files. You paste each variable into the Railway
dashboard:

1. Open your Railway project → your backend service → `Variables` tab.
2. Click `+ New Variable` for each line in `.env.prod`.
3. Save. Railway redeploys automatically.

We also set `SPRING_PROFILES_ACTIVE=prod` as a Railway variable so the `prod`
profile kicks in.

See `docs/RAILWAY_DEPLOY.md` for the full walkthrough.

---

## Which variables do I need to set?

Everything marked `${VAR}` in `application.yml` or `application-*.yml` must be set.
Here's the full list for Phase 1:

| Variable | Example | Where it's used |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev`, `qa`, `prod` | picks which yml profile to layer on |
| `SERVER_PORT` | `8080` (local) / `${PORT}` on Railway | which port the API listens on |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://.../postgres` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `postgres.abc123` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `...` | DB password |
| `JWT_SECRET` | 64-char random string | signs JWTs (see below) |
| `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | `15` | access-token lifetime |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `30` | refresh-token lifetime |
| `GOOGLE_OAUTH_CLIENT_ID` | `xxxxx.apps.googleusercontent.com` | verify Google ID tokens |
| `GOOGLE_OAUTH_CLIENT_SECRET` | `GOCSPX-...` | for server-side Google flow |
| `FACEBOOK_OAUTH_APP_ID` | `123456789` | verify Facebook tokens |
| `FACEBOOK_OAUTH_APP_SECRET` | `abcdef...` | for server-side Facebook flow |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*,capacitor://localhost` | mobile app origins |

---

## Generating a JWT secret (safe way)

**Never** use `password123` or anything guessable. The JWT_SECRET signs every
token — leaking it lets an attacker impersonate any user.

Generate one (any of these work):

```bash
# Mac / Linux
openssl rand -base64 64

# Cross-platform Python
python -c "import secrets; print(secrets.token_urlsafe(64))"
```

Copy the output. Use a **different JWT_SECRET** for dev, qa, and prod — so a
leaked dev secret can't be used to forge prod tokens.

---

## Flutter-side env (mobile)

The mobile app has its own env flow. Flavor-specific files in `mobile/`:

- `mobile/.env.dev` — read by `main_dev.dart`
- `mobile/.env.qa` — read by `main_qa.dart`
- `mobile/.env.prod` — read by `main_prod.dart`

These contain **non-sensitive, public values only**:

```
API_BASE_URL=http://10.0.2.2:8080     # Android emulator loopback to your laptop
GOOGLE_WEB_CLIENT_ID=xxxxx.apps.googleusercontent.com
FACEBOOK_APP_ID=123456789
FACEBOOK_CLIENT_TOKEN=...
SENTRY_DSN=                            # optional, add later
```

> **Never put backend secrets (DB password, JWT_SECRET, OAuth client secrets) in mobile env** — they ship in the APK and can be extracted by anyone.

Loaded at runtime via `flutter_dotenv` (see `mobile/lib/app/config/env_config.dart`).

---

## CI/CD secrets (GitHub Actions)

For automated tests in CI, we use **GitHub Secrets**:

1. Repo → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`.
2. Add the same vars as above, but use throwaway test values (e.g. point `SPRING_DATASOURCE_URL` at a Testcontainers-managed Postgres in CI).

The workflow files in `.github/workflows/*.yml` reference them as
`${{ secrets.NAME }}` — never in plaintext.

---

## Quick checklist before deploying a new env

- [ ] `.env.<env>` exists locally, `.gitignore` prevents committing it
- [ ] A fresh `JWT_SECRET` that's different from other envs
- [ ] DB URL/user/password from the correct Supabase project
- [ ] Google/Facebook client IDs correspond to this env's mobile flavor
- [ ] `CORS_ALLOWED_ORIGINS` includes the mobile app's origin (or `*` only in dev)
- [ ] `SPRING_PROFILES_ACTIVE` set to the env name
