# SRoadTutor — Mobile App (Flutter)

Offline-first driving-school app, Android + iOS from one codebase.

> **Noob-friendly reminder:** Flutter is a UI toolkit from Google. One Dart
> codebase → real native Android and iOS apps. We use **Riverpod** for state,
> **GoRouter** for navigation, **Drift** (SQLite) for offline storage, and
> **Dio** for HTTP. You don't need to install any of this manually — it's
> all listed in `pubspec.yaml` and pulled by `flutter pub get`.

---

## 1. One-time machine setup

### 1.1 Install Flutter
1. Grab the stable SDK: <https://docs.flutter.dev/get-started/install>
2. Extract it somewhere simple like `C:\flutter` (Windows) or `~/flutter` (macOS/Linux).
3. Add the `flutter/bin` folder to your `PATH`.
4. Verify:
   ```bash
   flutter --version      # should print Flutter 3.24.x or newer
   flutter doctor          # fix anything marked with an ✗
   ```

### 1.2 Install platform toolchains
- **Android**: Install Android Studio → open it once → it will install SDK + cmdline-tools. Then in a terminal run `flutter doctor --android-licenses` and accept all.
- **iOS (macOS only)**: Install Xcode from the App Store, then `sudo xcodebuild -runFirstLaunch` and `sudo xcode-select -s /Applications/Xcode.app`.

### 1.3 IntelliJ Community Edition
Install the **Flutter** and **Dart** plugins:
`File → Settings → Plugins` → search "Flutter" → Install → restart.

---

## 2. Open the project

1. In IntelliJ: `File → Open → <repo>/mobile`.
2. IntelliJ will prompt you to run `flutter pub get` — click it (or run it in the terminal).
3. The `.env.dev`, `.env.qa`, `.env.prod` files are already in place but contain `REPLACE_ME` values. Fill them in **after** you finish:
   - `docs/GOOGLE_OAUTH_SETUP.md`
   - `docs/FACEBOOK_OAUTH_SETUP.md`
   - `docs/RAILWAY_DEPLOY.md` (for QA/prod `API_BASE_URL`)

---

## 3. Run the app

The project has **3 flavors** (dev / qa / prod) so you can install all three on
one phone side-by-side (different package IDs). Each flavor has its own entry
point.

```bash
# Android emulator / connected phone:
flutter run --flavor dev  -t lib/main_dev.dart
flutter run --flavor qa   -t lib/main_qa.dart
flutter run --flavor prod -t lib/main_prod.dart

# iOS simulator (macOS only):
open -a Simulator
flutter run --flavor dev -t lib/main_dev.dart
```

In IntelliJ: use the **Run Configurations** dropdown (top-right). We ship
three pre-made configs (`mobile-dev`, `mobile-qa`, `mobile-prod`).

---

## 4. Build a shareable APK for friends

```bash
# From repo/mobile
flutter build apk --release --flavor qa -t lib/main_qa.dart
```

Output: `build/app/outputs/flutter-apk/app-qa-release.apk`

Drop that APK into Google Drive / Telegram / WhatsApp → friend installs it on
their Android phone (they need to allow "install from unknown sources" once).

For iOS friends, you need a TestFlight build — covered in
`docs/RAILWAY_DEPLOY.md` §6.

---

## 5. Code generation

Several dependencies (`drift`, `riverpod_annotation`) generate code. Run:

```bash
# One-off:
dart run build_runner build --delete-conflicting-outputs

# Or keep it watching while you code:
dart run build_runner watch --delete-conflicting-outputs
```

Generated files end in `.g.dart` — **don't edit them by hand**, they're
recreated every time.

---

## 6. Tests

```bash
# Static analysis (lints):
flutter analyze

# Unit + widget tests:
flutter test

# Integration test on a running device / emulator:
flutter test integration_test/
```

See `docs/TESTING_GUIDE.md` for what each test type covers.

---

## 7. Project layout

```
mobile/
├── lib/
│   ├── main_common.dart         # shared bootstrap for all flavors
│   ├── main_dev.dart            # dev flavor entrypoint
│   ├── main_qa.dart             # qa  flavor entrypoint
│   ├── main_prod.dart           # prod flavor entrypoint
│   ├── app/
│   │   ├── app.dart             # MaterialApp.router root
│   │   ├── config/env_config.dart
│   │   ├── router/app_router.dart
│   │   └── theme/app_theme.dart
│   ├── core/                    # cross-cutting plumbing
│   │   ├── network/             # Dio, interceptors, endpoints
│   │   ├── storage/             # Drift DB + secure storage
│   │   ├── sync/                # offline queue + connectivity
│   │   └── errors/              # Failure types + exception mapping
│   └── features/
│       └── auth/                # data / domain / presentation layers
├── test/                        # unit + widget tests
├── integration_test/            # real-device end-to-end tests
├── .env.dev / .env.qa / .env.prod
├── analysis_options.yaml
└── pubspec.yaml
```

---

## 8. Common issues

| Symptom | Fix |
|---|---|
| `FileNotFoundError: .env.dev` at startup | You ran without `-t lib/main_dev.dart`. Always pass the flavor entrypoint. |
| `Connection refused` on Android emulator | You set `API_BASE_URL=http://localhost:8080`. Use `http://10.0.2.2:8080` instead. |
| `cleartext-traffic not permitted` | That's Android blocking plain `http://`. Our dev flavor whitelists it via `usesCleartextTraffic="true"`. |
| Generated files out of date | `dart run build_runner build --delete-conflicting-outputs` |
| `pod install` fails on iOS | `cd ios && pod repo update && pod install` |
