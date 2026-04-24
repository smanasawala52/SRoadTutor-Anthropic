# SRoadTutor

Driving-school SaaS platform. Offline-first mobile app for instructors in the car,
cloud sync for everyone else. Multi-jurisdiction (SGI live, ICBC/MTO/DMV scaffolded).

---

## What's in this repo

```
SRoadTutor/
├── backend/          # Spring Boot 3.3.x + Java 21 API (deploys to Railway)
├── mobile/           # Flutter app (Android/iOS, offline-first)
├── docs/             # Setup, architecture, ops walkthroughs — READ THESE FIRST
└── .github/workflows # CI pipelines for backend + mobile
```

---

## I am new — where do I start?

Read these docs in order. Each one is written assuming zero prior knowledge.

1. **[docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md)** — overview of what you'll install and why
2. **[docs/INTELLIJ_SETUP.md](docs/INTELLIJ_SETUP.md)** — open the project in IntelliJ Community correctly
3. **[docs/SUPABASE_SETUP.md](docs/SUPABASE_SETUP.md)** — create the 3 databases (dev/QA/prod)
4. **[docs/GOOGLE_OAUTH_SETUP.md](docs/GOOGLE_OAUTH_SETUP.md)** — get Google login working
5. **[docs/FACEBOOK_OAUTH_SETUP.md](docs/FACEBOOK_OAUTH_SETUP.md)** — get Facebook login working
6. **[docs/ENV_SETUP.md](docs/ENV_SETUP.md)** — how env vars work, where to put secrets
7. **[docs/RAILWAY_DEPLOY.md](docs/RAILWAY_DEPLOY.md)** — deploy the backend to Railway
8. **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)** — how to run all the tests
9. **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — how the pieces fit together

Then open `backend/README.md` and `mobile/README.md` for module-specific steps.

---

## Phase 1 scope (what is built right now)

- ✅ Project scaffolding — backend + mobile, env-driven config, CI, tests
- ✅ Auth module — email/password signup + login, Google OAuth, Facebook OAuth, JWT + refresh tokens
- ✅ Database schema (16 tables from blueprint) via Flyway migrations
- ✅ SGI mistake-category seed data (ICBC/MTO/DMV placeholder rows)
- ✅ Offline-first mobile foundation (local SQLite via Drift, sync queue)
- ✅ JUnit unit + integration tests with JaCoCo 80% coverage gate
- ✅ Flutter unit + widget + integration tests
- ✅ GitHub Actions CI for both

Stub modules (to build in follow-up sessions): schools, instructors, students, sessions,
mistakes, payments, reminders, reports, subscriptions, marketplace (P2), AI/data (P3).

---

## Quick sanity check — does it all work?

After following the setup docs:

```bash
# Backend
cd backend
./mvnw clean test            # runs all unit + integration tests
./mvnw spring-boot:run       # starts the API on http://localhost:8080

# Mobile
cd mobile
flutter pub get
flutter test                 # unit + widget tests
flutter run --flavor dev -t lib/main_dev.dart
```

If anything breaks, read the error message aloud — then check `docs/TROUBLESHOOTING`
sections inside each setup doc.

---

## License

Proprietary — © SRoadTutor. All rights reserved.
