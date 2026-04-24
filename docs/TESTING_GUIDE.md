# Testing Guide

How to run every test in the repo, and what each one does.

---

## Backend — Java tests

### Test types

| Type | Location | What it verifies |
|---|---|---|
| **Unit** | `backend/src/test/java/**/*Test.java` (not `*IT.java`) | A single class in isolation, all collaborators mocked. Fast (~ms). |
| **Integration** | `backend/src/test/java/**/*IntegrationTest.java` | Full Spring context + real Postgres via Testcontainers + HTTP calls via MockMvc. Slower (~seconds). |

### Prereqs

- JDK 21 on PATH.
- **Docker Desktop** running — integration tests spin up a throwaway Postgres container. If Docker isn't running, only unit tests run.

### Run them

```bash
cd backend

./mvnw clean test                       # unit + integration tests
./mvnw test -Dtest=JwtServiceTest       # single unit test
./mvnw test -Dtest=*IntegrationTest     # only integration tests
./mvnw verify                           # run tests AND enforce JaCoCo 80% coverage gate
```

`verify` fails the build if:
- any test fails
- line coverage < 80%
- branch coverage < 70%

### Coverage reports

After `./mvnw verify`:

- **HTML**: open `backend/target/site/jacoco/index.html` in a browser. Lines are colour-coded: green = covered, red = not.
- **XML** (for SonarQube): `backend/target/site/jacoco/jacoco.xml`.

### SonarQube

Two ways:

1. **SonarLint in IntelliJ** (live feedback as you type): `Settings` → `Plugins` → search "SonarLint" → install.
2. **SonarCloud in CI** (gated on PRs):
   - Sign up at https://sonarcloud.io with your GitHub account (free for public repos).
   - Create an org + project matching your repo.
   - In GitHub: `Settings` → `Secrets` → add `SONAR_TOKEN` (from SonarCloud under `My Account` → `Security`).
   - Our `.github/workflows/backend-ci.yml` runs `mvn sonar:sonar` on every PR.
   - The **Quality Gate** in SonarCloud blocks merges if coverage or maintainability drops.

---

## Mobile — Flutter tests

### Test types

| Type | Location | What it verifies |
|---|---|---|
| **Unit** | `mobile/test/**/*_test.dart` (no widgets) | Pure Dart logic — usecases, repositories with mocked datasources. |
| **Widget** | `mobile/test/widget/**/*_test.dart` | A single widget in a mock framework. Verifies what renders. |
| **Integration (E2E)** | `mobile/integration_test/**/*_test.dart` | Full app running on a real emulator or device. End-to-end flows. |

### Prereqs

- Flutter SDK on PATH.
- For integration tests: an Android emulator running, OR a connected Android device (USB debugging on), OR an iOS Simulator on Mac.

### Run them

```bash
cd mobile

flutter pub get                     # first time only, or after pubspec.yaml changes
flutter pub run build_runner build --delete-conflicting-outputs   # generate .g.dart / .mocks.dart

flutter test                        # unit + widget tests
flutter test test/features/auth     # only auth tests
flutter test --coverage             # outputs coverage/lcov.info

# Integration (needs a running emulator)
flutter test integration_test/auth_flow_test.dart
```

### Coverage

Open `mobile/coverage/lcov.info` in VSCode (install the "Coverage Gutters" extension)
or convert to HTML:

```bash
brew install lcov   # or apt/choco equivalent
genhtml coverage/lcov.info -o coverage/html
open coverage/html/index.html
```

---

## CI — what GitHub runs on every push

`.github/workflows/backend-ci.yml`:
1. Checkout + cache Maven deps
2. `./mvnw verify` (tests + 80% gate)
3. Upload JaCoCo report
4. `mvn sonar:sonar` → SonarCloud Quality Gate

`.github/workflows/mobile-ci.yml`:
1. Checkout + setup Flutter
2. `flutter pub get`
3. `flutter analyze` (static analysis)
4. `flutter test --coverage`
5. Build dev APK (artifact, so you can download and test on your phone)

Both fail fast — if anything is red, merging is blocked on the PR.

---

## When tests fail — debugging checklist

1. Read the error message. 90% of failures are: missing env var, wrong JDK version, emulator not running, or a generated `.g.dart` file out of date.
2. Re-run only the failing test in your IDE (faster than full suite).
3. For integration tests that pass locally but fail in CI: it's probably an env var. Check `.github/workflows/*.yml` and GitHub Secrets.
4. If you've edited a Flyway migration: you broke the checksum. Either revert, or write a new `V(n+1)__fix.sql`.
5. If you've added a new class with logic → add a matching `*Test.java`. If you don't, JaCoCo will drop below 80% and CI will fail.
