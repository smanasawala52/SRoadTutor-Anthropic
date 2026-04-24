# SRoadTutor Backend

Spring Boot 3.3 + Java 21. Packages to a fat JAR. Deploys to Railway via Docker.

---

## Quickstart (5 min)

```bash
# 1. Copy the env template and fill in real values (see docs/SUPABASE_SETUP.md etc.)
cp .env.dev.example .env.dev
# edit .env.dev

# 2. Load env + run
set -a; source .env.dev; set +a
./mvnw spring-boot:run

# 3. API is up:
curl http://localhost:8080/actuator/health      # → {"status":"UP"}
open http://localhost:8080/swagger-ui.html      # interactive API docs
```

IntelliJ users: see `docs/INTELLIJ_SETUP.md` for a one-click run config with the EnvFile plugin.

---

## Directory layout

```
backend/
├── pom.xml                    # Maven build + JaCoCo + Sonar + Failsafe
├── Dockerfile                 # Multi-stage: build fat JAR → run on JRE
├── railway.json               # Railway build/deploy config
├── sonar-project.properties   # SonarCloud config
├── .env.dev.example           # env templates (SAFE to commit)
├── .env.qa.example
├── .env.prod.example
└── src/
    ├── main/
    │   ├── java/com/sroadtutor/
    │   │   ├── SRoadTutorApplication.java
    │   │   ├── config/        # Security, CORS, OpenAPI, ConfigProperties beans
    │   │   ├── auth/          # Auth module (controller/service/dto/model/repo)
    │   │   ├── common/        # Shared API response envelopes
    │   │   └── exception/     # GlobalExceptionHandler + custom exceptions
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-qa.yml
    │       ├── application-prod.yml
    │       ├── application-test.yml
    │       └── db/migration/  # Flyway SQL — V1__, V2__, V3__
    └── test/
        ├── java/com/sroadtutor/
        │   └── auth/          # Unit tests (*Test.java) + integration (*IntegrationTest.java)
        └── resources/
            └── application-test.yml
```

---

## Useful commands

```bash
./mvnw clean compile                 # compile only
./mvnw test                          # unit tests only (fast)
./mvnw verify                        # unit + integration + JaCoCo gate
./mvnw verify -Pfast                 # skip integration tests
./mvnw spring-boot:run               # run locally, picks up SPRING_PROFILES_ACTIVE env var
./mvnw sonar:sonar \
    -Dsonar.token=$SONAR_TOKEN       # push coverage to SonarCloud
./mvnw dependency:tree | grep ...    # debug dependency conflicts
```

---

## Environment variables — the contract

Every `${VAR}` in `application.yml` must be set in the env.  `.env.*.example` enumerates them.

- Local: IntelliJ EnvFile plugin or `set -a; source .env.dev; set +a`.
- Railway: paste into the Variables tab.
- CI: GitHub Secrets.

See `docs/ENV_SETUP.md` for specifics.

---

## Adding a new module — template

1. `src/main/resources/db/migration/V<n>__<name>.sql` — migration.
2. `src/main/java/com/sroadtutor/<feature>/` — feature folder:
   - `model/` — `@Entity` classes
   - `repository/` — `interface ... extends JpaRepository<Entity, UUID>`
   - `service/` — business logic (one service per aggregate)
   - `controller/` — `@RestController`, request validation
   - `dto/` — request/response POJOs
3. `src/test/java/com/sroadtutor/<feature>/` — `*Test.java` (unit) + `*IntegrationTest.java`.
4. Run `./mvnw verify` — must stay above 80% coverage.

---

## Running inside Docker (mimics Railway)

```bash
docker build -t sroadtutor-backend .
docker run --rm -p 8080:8080 --env-file .env.dev sroadtutor-backend
```
