# Android Gradle Bootstrap

## Why this doc exists

The Cowork sandbox that wrote the scaffolding can only create text files — it
cannot produce the handful of **binary** files an Android Gradle build needs:

- `mobile/android/gradle/wrapper/gradle-wrapper.jar` (a compiled Java archive)
- `mobile/android/gradlew`  (Unix shell wrapper — technically text but with
  exec bits that the mount doesn't preserve)
- `mobile/android/gradlew.bat` (Windows wrapper — same caveat)
- `mobile/android/app/src/main/res/mipmap-*/ic_launcher.png` (PNG icons)
- `mobile/android/local.properties` (generated per-machine, contains the path
  to your Flutter SDK)

All the **text** pieces (`settings.gradle`, `build.gradle`, `gradle.properties`,
`gradle-wrapper.properties`, `MainActivity.kt`, `AndroidManifest.xml`,
`styles.xml`, `strings.xml`, `launch_background.xml`, `proguard-rules.pro`)
are already written.

Before your first Android build, you need to materialise those binary files
— it takes one command.

---

## The one-time fix

Open PowerShell (Windows) or your terminal in the `mobile/` folder:

```powershell
cd C:\path\to\SRoadTutor\mobile

# Install Dart/Flutter deps (also creates android/local.properties).
flutter pub get

# Regenerate missing platform scaffolding. This WILL NOT overwrite files
# that already exist, so our customised build.gradle / AndroidManifest /
# MainActivity are safe.
flutter create . `
    --platforms=android,ios `
    --project-name=sroadtutor `
    --org=com.sroadtutor `
    --description="SRoadTutor mobile client"
```

`flutter create` on an existing project is deliberately non-destructive — it
only fills in what's missing. You'll see output like:

```
Running "flutter pub get" in mobile...
Resolving dependencies...
Got dependencies.
Wrote 8 files.

All done!
You can find the application at:
  C:\path\to\SRoadTutor\mobile
```

> **8 files**, not 80 — because everything else was already there.

Expected new files after this command:
- `mobile/android/gradlew`
- `mobile/android/gradlew.bat`
- `mobile/android/gradle/wrapper/gradle-wrapper.jar`
- `mobile/android/app/src/main/res/mipmap-*/ic_launcher.png` (several densities)
- `mobile/android/local.properties` (ignored by git)
- `mobile/ios/` — the whole iOS scaffolding (Runner.xcodeproj, Info.plist, etc.)

Expected UNCHANGED (confirm these still look like what you expect):
- `mobile/android/build.gradle`
- `mobile/android/settings.gradle`
- `mobile/android/gradle.properties`
- `mobile/android/app/build.gradle`
- `mobile/android/app/src/main/AndroidManifest.xml`
- `mobile/android/app/src/main/kotlin/com/sroadtutor/app/MainActivity.kt`
- All the `strings.xml`, `styles.xml`, `launch_background.xml` files

Do a `git diff` to sanity-check.

---

## Verify the bootstrap worked

```powershell
cd C:\path\to\SRoadTutor\mobile\android

# Windows
.\gradlew.bat --version
# macOS / Linux (you may need to: chmod +x gradlew)
./gradlew --version
```

You should see something like:

```
Gradle 8.3
------------------------------------------------------------
Kotlin:       1.9.22
Groovy:       3.0.17
JVM:          17.x.x (Eclipse Adoptium 17.x.x+y)
OS:           Windows 11 10.0 amd64
```

Now the Google OAuth SHA-1 lookup works either way:

```powershell
.\gradlew.bat signingReport
```

…or, if you prefer not to run Gradle, `keytool` (covered in
`docs/GOOGLE_OAUTH_SETUP.md` §3b) gives you the same SHA-1.

---

## Troubleshooting

**`flutter create` errors: "the current directory is not a Flutter project"**
Your working directory is wrong. Make sure `pwd` prints `.../SRoadTutor/mobile`
and that `pubspec.yaml` is in that folder.

**`flutter create` errors: "Could not determine flutter project name"**
Pass `--project-name=sroadtutor` explicitly (shown above).

**`flutter create` overwrote one of our custom files**
Restore it from git: `git checkout -- mobile/android/app/build.gradle`. This
shouldn't happen, but commit before running if you're worried.

**`./gradlew --version` errors: "gradle-wrapper.jar not found"**
The `flutter create` step didn't finish. Re-run it, then re-check
`mobile/android/gradle/wrapper/gradle-wrapper.jar` exists (should be ~60 KB).

**Android Studio / IntelliJ complains about missing Gradle JVM**
Set File → Settings → Build Tools → Gradle → Gradle JVM = your JDK 17 (not
21). Android Gradle Plugin 8.1.x needs JVM 17; the backend uses JVM 21. Both
JDKs can coexist — use `sdkman` or the IntelliJ JDK dropdown to switch.
