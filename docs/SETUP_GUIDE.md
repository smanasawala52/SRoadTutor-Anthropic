# Setup Guide — The Big Picture

This doc tells you *what* to install and *why*. The other docs in this folder
walk you through the actual steps. Read this first so nothing feels random.

---

## The stack in plain English

**Backend** (`backend/`) — a Java program that exposes an HTTP API.
- Java 21 (LTS — "Long Term Support", supported until 2029)
- Spring Boot 3.3 (the framework that makes Java web apps easy)
- Maven (the tool that downloads libraries and builds the project)
- Runs on your laptop during development, deploys to **Railway** in production.

**Database** (Postgres on **Supabase**) — where all the data lives.
- We create **3 separate databases**: `dev`, `qa`, `prod` so you never mix test data with real data.
- Supabase gives you Postgres + Auth + File Storage on a free tier.

**Mobile app** (`mobile/`) — what your users actually see.
- Flutter (one codebase → Android APK + iOS app)
- **Offline-first** — the app works without internet. It saves changes locally and syncs when online.
- Share APK files with friends for testing before you publish to Google Play / App Store.

**CI/CD** (`.github/workflows/`) — automated checks.
- Every time you `git push`, GitHub runs the tests and tells you if something broke.
- Blocks merges if coverage drops below 80% or tests fail.

---

## What you need to install once

| Tool | Why | Install from |
|---|---|---|
| **JDK 21** (Java) | Compile and run the backend | https://adoptium.net (pick Temurin 21 LTS) |
| **Maven** | Build the backend. Already bundled as `./mvnw` — no separate install needed | bundled |
| **Flutter SDK** | Compile and run the mobile app | https://flutter.dev/docs/get-started/install |
| **IntelliJ IDEA Community** | Your IDE | https://www.jetbrains.com/idea/download (scroll to Community) |
| **Android Studio** (or the command-line tools) | Android SDK + emulator for Flutter | https://developer.android.com/studio |
| **Git** | Version control | https://git-scm.com |
| **Docker Desktop** (optional but recommended) | For running tests that need a disposable Postgres | https://www.docker.com/products/docker-desktop |

**Accounts to create** (all free tiers):
- GitHub (for code + CI)
- Supabase (for databases)
- Railway (for hosting the backend)
- Google Cloud Console (for Google OAuth client)
- Meta for Developers (for Facebook OAuth app)

---

## The three environments (dev / QA / prod)

This repo is set up so you can **never accidentally run dev code against prod data**.

| Env | When you use it | Backend URL | Database |
|---|---|---|---|
| **dev** | Day-to-day coding on your laptop | `http://localhost:8080` | `sroadtutor-dev` on Supabase |
| **qa** | After dev — you, or a tester, checks it works end-to-end before shipping | `https://sroadtutor-qa.up.railway.app` | `sroadtutor-qa` on Supabase |
| **prod** | Real users | `https://sroadtutor.up.railway.app` | `sroadtutor-prod` on Supabase |

You switch between them using a Spring Boot feature called **profiles**, and Flutter **flavors**.
Both are automated — you just pick one when you run the app.

---

## Config / secret management — the golden rule

> **Never put a secret directly in a file that's committed to git.**

A "secret" is anything sensitive: DB passwords, API keys, OAuth client secrets, JWT signing keys.

**How this repo handles it:**

- Every env-specific file has a safe template like `backend/.env.dev.example` (committed to git).
- The real files are named `backend/.env.dev` (NOT committed — `.gitignore` blocks them).
- Spring Boot reads values using `${ENV_VAR_NAME}` placeholders in `application.yml`.
- On Railway, you paste the same values into the Railway dashboard under "Variables".
- On the Flutter side, we use `--dart-define` or a `.env.dev/qa/prod` file loaded at startup.

**Step-by-step:** see `docs/ENV_SETUP.md`.

---

## Typical first-time workflow

1. Clone the repo to your laptop.
2. Follow `docs/INTELLIJ_SETUP.md` to open both modules.
3. Follow `docs/SUPABASE_SETUP.md` to create 3 database projects.
4. Follow `docs/GOOGLE_OAUTH_SETUP.md` and `docs/FACEBOOK_OAUTH_SETUP.md` to create OAuth credentials.
5. Copy `.env.dev.example` → `.env.dev` and fill in real values.
6. `cd backend && ./mvnw spring-boot:run` → API running locally.
7. `cd mobile && flutter run --flavor dev -t lib/main_dev.dart` → app running on an emulator.
8. When you're ready: follow `docs/RAILWAY_DEPLOY.md` to put the backend online.

Don't rush. Each doc is maybe 10-20 minutes. Total setup: a few hours the first time,
5 minutes every time after.
