# Railway Deployment — Backend

Railway is where our Spring Boot API lives in production. It auto-builds from
the GitHub repo using the `Dockerfile` in `backend/`. Every push to the right
branch triggers a redeploy.

---

## 1. Create Railway account

1. Go to https://railway.app → sign in with GitHub.
2. Free trial includes $5 of credit. At small scale, one service costs ~$5/mo.

---

## 2. Two services — QA and Prod

We'll create **two Railway projects** (one per env). You rarely need dev on
Railway because dev runs on your laptop.

### 2a. Create the QA project

1. `New Project` → `Deploy from GitHub repo`.
2. Install the Railway GitHub app, grant access to your `SRoadTutor` repo.
3. Pick the repo. When asked which directory: select **`backend`** (the sub-folder).
4. Railway auto-detects the `Dockerfile` and starts building. It will **fail** the first time because env vars aren't set yet. That's expected.

### 2b. Set env vars on the QA service

Click the service → `Variables` tab → paste each line from your `backend/.env.qa`.

**Important Railway-specific vars:**

```
SPRING_PROFILES_ACTIVE=qa
PORT=8080                     # Railway sets this; use it in application.yml via ${PORT:8080}
```

Also add:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<QA-supabase-host>:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.<QA-project-ref>
SPRING_DATASOURCE_PASSWORD=<QA-password>
JWT_SECRET=<unique-64-char-string-for-QA>
JWT_ACCESS_TOKEN_EXPIRATION_MINUTES=15
JWT_REFRESH_TOKEN_EXPIRATION_DAYS=30
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...
FACEBOOK_OAUTH_APP_ID=...
FACEBOOK_OAUTH_APP_SECRET=...
CORS_ALLOWED_ORIGINS=https://sroadtutor-qa.up.railway.app
```

After saving, Railway redeploys automatically.

### 2c. Settings → Networking

In the service's `Settings` tab:

- `Generate Domain` → gives you a public URL like `sroadtutor-qa-production.up.railway.app`. Save this — you'll use it as `API_BASE_URL` in the QA mobile flavor.
- Watchtower / Health check path: `/actuator/health`. Railway will only route traffic once this returns 200.

### 2d. Repeat for prod

Same process, new Railway project, name it `sroadtutor-prod`. Use the **prod** Supabase project + prod env vars.

---

## 3. Which branch deploys where?

By default Railway watches your **default branch** (`main`). Set up branch-based
deployment:

- QA service: `Settings` → `Source` → `Branch` = `qa`
- Prod service: `Settings` → `Source` → `Branch` = `main`

Workflow:
- Push to `qa` branch → QA redeploys → test it.
- Merge `qa` → `main` → prod redeploys.

Create the `qa` branch once: `git checkout -b qa && git push -u origin qa`.

---

## 4. Railway Dockerfile — how does it build?

Our `backend/Dockerfile` is a multi-stage build:

```dockerfile
# Stage 1: compile with Maven wrapper inside a JDK image
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -DskipTests package

# Stage 2: run the fat JAR on a smaller JRE-only image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Why multi-stage: final image is ~200 MB instead of ~700 MB.

If you change Java versions, update both `FROM` lines.

---

## 5. Logs and debugging

- `Deployments` tab → click the latest → `View Logs` shows stdout/stderr.
- `Metrics` tab shows CPU + memory + response time.
- Common issues:
  - **"connection refused" on startup**: wrong DB URL or password.
  - **"Port already in use"**: your app isn't binding to `${PORT}`. Check `server.port` in `application-prod.yml`.
  - **OOM killed**: free plan has 512 MB RAM. Add `-Xmx400m` to the JAVA_TOOL_OPTIONS env var in Railway.

---

## 6. Zero-downtime deploys

Railway's default `Rolling` deploy strategy already does this — the new
container must pass `/actuator/health` before the old one is shut down. Don't
change it unless you know why.

---

## 7. Rollback

Something broke in prod? Don't panic.

1. Railway → your prod service → `Deployments` tab.
2. Find the last green deploy → `...` menu → `Rollback to this deploy`.
3. Railway restores that exact build within seconds.

For DB rollbacks: Flyway migrations are forward-only. If you shipped a bad
migration, write a new `V(n+1)__revert_<bad>.sql` and deploy again. **Never**
edit a migration that already ran in prod.

---

## 8. Costs (quick reality check)

- Free trial: $5 credit → lasts ~1 month for one always-on service.
- After trial: **$5/mo** hobby plan per service + usage. Two services = ~$10/mo.
- Supabase free tier: $0 (500 MB db, 50k rows, 50 k auth users).
- OAuth (Google + Facebook): $0.

Expect **~$10/mo** for QA + prod backend, $0 for DBs, $0 for auth. That's it.

---

## Troubleshooting

- **Build fails with "permission denied: mvnw"**: on Windows, Git may drop exec bits. Run `git update-index --chmod=+x backend/mvnw` and commit.
- **Build succeeds but app crashes with `Failed to determine a suitable driver class`**: env vars not applied yet. Check the `Variables` tab; save again; Railway redeploys.
- **Flyway fails with "already applied migration"**: someone ran the migrations manually. Delete the `flyway_schema_history` row for the conflicting version or roll the DB back to a snapshot.
- **CORS errors from the mobile app**: add the mobile app's origin to `CORS_ALLOWED_ORIGINS`.
