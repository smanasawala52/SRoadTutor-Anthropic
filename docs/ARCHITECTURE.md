# Architecture

High-level diagrams and the reasoning behind each major choice.

---

## 1. System overview

```
┌──────────────────────────┐      ┌──────────────────────────┐
│   Flutter mobile app     │      │   Flutter mobile app     │
│   (Android / iOS)        │      │   (Android / iOS)        │
│                          │      │                          │
│  ┌─ Local SQLite (Drift)─┤      │  offline queue           │
│  │  + pending-sync queue │      │  retries on reconnect    │
└──┼──────────┬────────────┘      └──────────────────────────┘
   │          │ HTTPS + JWT
   ▼          ▼
┌──────────────────────────────────────────────────────────┐
│  Spring Boot API on Railway                              │
│                                                           │
│  ┌────────────┐ ┌─────────┐ ┌────────────┐ ┌──────────┐ │
│  │  /auth     │ │ /schools│ │ /sessions  │ │ /reports │ │
│  └─────┬──────┘ └────┬────┘ └─────┬──────┘ └────┬─────┘ │
│        └─────────────┴────────────┴─────────────┘        │
│                        │                                 │
│               JPA / Hibernate + Flyway                   │
└────────────────────────┼─────────────────────────────────┘
                         │
                         ▼
                ┌────────────────┐
                │ Supabase       │
                │ Postgres       │
                │ (dev/qa/prod)  │
                └────────────────┘
```

---

## 2. Backend — layered architecture

Each feature folder (`auth/`, `school/`, `student/`, etc.) has the same structure:

```
feature/
├── controller/     # HTTP endpoints. Thin — validates input, calls service, returns DTO.
├── service/        # Business logic. The only layer that talks to the repository.
├── repository/     # Spring Data JPA interface. Query generation.
├── model/          # JPA entities (@Entity), maps to DB rows.
└── dto/            # Request/response shapes. Never leak entities across the HTTP boundary.
```

**Why this split:**
- Swapping the DB or the HTTP framework in future only touches one layer.
- Unit tests mock the repository; integration tests hit a real one.
- DTOs give us JSON-schema stability even when entities change shape.

---

## 3. Mobile — clean architecture (per feature)

Each feature folder has 3 layers:

```
features/auth/
├── data/
│   ├── models/          # JSON-serialisable shapes (fromJson / toJson)
│   ├── datasources/     # Remote (Dio) + Local (Drift) sources
│   └── repositories/    # Implements domain repository, orchestrates remote+local
├── domain/
│   ├── entities/        # Pure Dart classes, no JSON/DB code
│   ├── repositories/    # Abstract interfaces (what presentation can ask for)
│   └── usecases/        # One-method classes: LoginUseCase, LogoutUseCase, ...
└── presentation/
    ├── providers/       # Riverpod state providers
    ├── screens/         # Stateless widgets rendered by the router
    └── widgets/         # Reusable UI pieces
```

**Rule of thumb**: never import from `data/` inside `presentation/`. Always go
through `domain/`. This is what makes the code easy to test and to swap storage.

---

## 4. Offline-first — how sync works

1. User taps "log mistake" in the car (no signal).
2. The use case writes to **Drift** (local SQLite) and appends an entry to the `sync_queue` table: `{ op: 'POST', path: '/mistakes/session/42/log', body: {...} }`.
3. `ConnectivityService` (uses `connectivity_plus` package) notices reconnection.
4. `SyncEngine` drains `sync_queue` in order, hitting the backend. Each success clears the row.
5. If a call fails with `409 Conflict`, we fetch the server state, merge using **last-write-wins** per field, and re-enqueue.
6. The UI always reads from Drift — so it's instant even on 3G.

---

## 5. Auth — JWT + refresh tokens + OAuth

```
┌──────────┐          ┌──────────┐           ┌──────────┐
│  Mobile  │          │ Backend  │           │  Google  │
└─────┬────┘          └────┬─────┘           └────┬─────┘
      │  POST /auth/google │                      │
      │  {id_token:...}    │                      │
      ├───────────────────▶│                      │
      │                    │  verify id_token     │
      │                    ├─────────────────────▶│
      │                    │◀─────────────────────┤
      │                    │  email, sub, name    │
      │                    │                      │
      │                    │  upsert User by email│
      │                    │  sign access+refresh │
      │                    │                      │
      │◀───────────────────┤ {access_token,       │
      │                    │  refresh_token}      │
```

**Access token** — 15 min, signed with `JWT_SECRET`. Stateless: backend
doesn't store it; validates the signature on every request.

**Refresh token** — 30 days, stored in Postgres `refresh_tokens` table.
Server can revoke it any time (logout, password change). When the access
token expires, mobile app calls `/auth/refresh` with the refresh token,
gets a new pair.

**Password storage** — `BCryptPasswordEncoder(strength=12)`. Never plaintext.

---

## 6. Database — migrations, never live DDL

Flyway owns the schema. Rules:

- `backend/src/main/resources/db/migration/V<n>__<description>.sql` — immutable after commit.
- Never edit `V1__...` after it's run somewhere. Write `V2__`.
- Rollbacks = new `V<n+1>__revert_...sql` migration, never `flyway undo` (paid feature).
- Applied migrations are tracked in the `flyway_schema_history` table.

Current migrations:

| Version | File | What it does |
|---|---|---|
| V1 | `V1__create_core_schema.sql` | schools, users, instructors, students, parent_student |
| V2 | `V2__auth_tables.sql` | refresh_tokens, seeds the `users.auth_provider` enum rows |
| V3 | `V3__seed_mistake_categories.sql` | mistake_categories with SGI rows; empty rows for ICBC/MTO/DMV |

As we build more modules, new migrations (`V4__sessions.sql` etc.) get added.

---

## 7. Why these tech choices?

| Decision | Why |
|---|---|
| Java 21 + Spring Boot 3.3 | Industry standard, free, huge ecosystem, Railway-native, IntelliJ Community supports it fully. |
| Postgres via Supabase | Free tier big enough for MVP, pgSQL is battle-tested, Supabase gives us Auth/Storage on standby. |
| Flyway over Liquibase | Simpler mental model (SQL files, versioned, applied on boot), fewer XML surprises. |
| Flutter over React Native | One codebase, closer-to-native performance, in-car UX needs large tap targets and offline reliability. |
| Drift over Hive | Type-safe SQLite queries with compile-time checks; same SQL mental model as the backend. |
| Riverpod over Provider/Bloc | Compile-time safety, no `BuildContext`, easy testing, great for clean-architecture split. |
| JWT + refresh over server sessions | Stateless = horizontally scalable on Railway without session stickiness. Refresh tokens give us revocation. |
| Railway over AWS/GCP | Zero config, free trial, single-click GitHub deploy. When you outgrow it, Dockerfile moves to ECS / Cloud Run in an hour. |

---

## 8. Future modules (pointers)

When we build schools / instructors / students / sessions etc., they all follow
the same pattern:

1. New Flyway migration for tables.
2. New feature folder `backend/src/main/java/com/sroadtutor/<feature>/`.
3. Mirror feature folder on mobile `mobile/lib/features/<feature>/`.
4. Unit tests for service, integration test for controller, widget tests for screens.
5. Add endpoints to `mobile/lib/core/network/api_endpoints.dart`.
6. Add Dio interceptor handling (auth header, offline queue) — already global.

No special casing. No god-classes. No "let me just put it in a helper" temptations.
