# IntelliJ IDEA Community Setup

IntelliJ Community is free and 100% sufficient for this project. Here is exactly
how to open SRoadTutor correctly.

---

## 1. Install JDK 21

IntelliJ needs a Java Development Kit pointed at it.

1. Open IntelliJ → welcome screen → `Customize` → `All settings…` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner` → `JRE`.
2. If `21` isn't listed: `File` → `Project Structure` → `SDKs` → `+` → `Download JDK…` → pick **Eclipse Temurin 21**. IntelliJ downloads and registers it automatically.

---

## 2. Open the backend

1. Welcome screen → `Open` → select the **`backend`** folder (not the repo root). IntelliJ detects the `pom.xml` and offers "Import as Maven project" — accept.
2. Wait for Maven to download all dependencies (first time takes 2–5 min; progress bar at the bottom).
3. When done: `File` → `Project Structure` → `Project` → set `SDK` to 21 and `Language level` to 21.

**Verify it works:**
- Open `src/main/java/com/sroadtutor/SRoadTutorApplication.java`.
- Click the green ▶ arrow in the gutter next to the `main` method → `Run 'SRoadTutorApplication'`.
- You should see console output ending in `Started SRoadTutorApplication in ... seconds`.
- If it fails because of missing `.env.dev`, that's expected — finish the Supabase + OAuth setup first, then come back.

---

## 3. Open the mobile module

IntelliJ Community **can** run Flutter, but Android Studio (which is IntelliJ
underneath) is usually smoother because it comes with the Android SDK. Either works.

### Using IntelliJ Community:
1. Install the Flutter plugin: `File` → `Settings` → `Plugins` → search "Flutter" → install. It auto-installs the Dart plugin too.
2. Set the Flutter SDK path: `File` → `Settings` → `Languages & Frameworks` → `Flutter` → point at wherever you installed Flutter (e.g. `~/flutter` or `C:\src\flutter`).
3. Open the `mobile` folder as a new IntelliJ window (`File` → `Open`).

### Using Android Studio (alternative):
1. Install → `Plugins` → add Flutter plugin.
2. Open `mobile` folder.

Either way, run:

```bash
flutter doctor
```

in a terminal (View → Tool Windows → Terminal inside IntelliJ). It tells you
exactly what's missing (Android SDK licenses, Xcode on Mac, etc.) and gives you
the command to fix each issue.

---

## 4. Project windows — one repo, two IntelliJ projects

The cleanest setup is **two IntelliJ windows open at the same time**: one for
`backend/`, one for `mobile/`. Don't try to open the repo root as a single
project — IntelliJ gets confused mixing Maven and Flutter.

`File` → `Open Recent` remembers both.

---

## 5. Useful plugins (optional)

In `File` → `Settings` → `Plugins`, install:

- **Lombok** — reduces Java boilerplate. Our backend uses it.
- **EnvFile** — lets you load `.env.dev` into a Run Configuration without hardcoding vars. See `docs/ENV_SETUP.md`.
- **Flutter** — if not already installed for the mobile module.
- **SonarLint** — shows code-quality warnings inline (same rules SonarQube enforces in CI).

---

## 6. Run configurations — the clean way to switch envs

### Backend run configs

For each env, create a Run Configuration:

1. `Run` → `Edit Configurations…` → `+` → `Application`.
2. Name: `Backend — dev`.
3. Main class: `com.sroadtutor.SRoadTutorApplication`.
4. `Modify options` → enable `Add VM options` → paste: `-Dspring.profiles.active=dev`.
5. `Modify options` → enable `Environment variables` → click the folder icon → "Use EnvFile plugin" → add `backend/.env.dev`.
6. Save. Duplicate the config and change `dev` → `qa` for the QA run config. (You'd rarely run `prod` locally.)

### Mobile run configs (Flutter)

The Flutter plugin auto-creates these when it sees the `main_*.dart` files. To
run a specific flavor from the dropdown:

1. `Run` → `Edit Configurations…` → `+` → `Flutter`.
2. Name: `Mobile — dev`.
3. Dart entrypoint: `mobile/lib/main_dev.dart`.
4. Additional args: `--flavor dev`.
5. Save. Duplicate for qa and prod.

Now the dropdown at the top of IntelliJ lets you switch envs in one click.

---

## Troubleshooting

- **"Cannot resolve symbol X" everywhere in Java code**: re-import the Maven project. Right-click `pom.xml` → `Maven` → `Reload project`.
- **"Invalid bound statement" or missing `.g.dart` files in Flutter**: run `flutter pub run build_runner build --delete-conflicting-outputs`.
- **"Target file not found" on Flutter run**: your working directory is wrong. In the run config, `Working directory` must be `mobile/`.
