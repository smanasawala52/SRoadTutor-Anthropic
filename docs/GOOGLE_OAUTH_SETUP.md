# Google OAuth Setup

Users will tap "Sign in with Google" in the mobile app. Google gives us a
one-time ID token. We send that token to our backend, verify it, and issue
our own JWT.

You need:
- A Google Cloud project (free) with the **People API** enabled.
- **3 OAuth Client IDs**: one for Android, one for iOS, one for the backend (a "Web application" client that verifies the ID token server-side).

---

## 1. Create the Google Cloud project

1. Go to https://console.cloud.google.com/ → sign in.
2. Top bar → project dropdown → `New Project`.
3. Name: `sroadtutor` (you can share this single project across dev/QA/prod — we separate per env via *client IDs*, not projects).
4. Wait for it to create, then select it.

Enable APIs:

1. Left menu → `APIs & Services` → `Enabled APIs & services` → `+ Enable APIs and Services`.
2. Search for **"People API"** → Enable.
3. Also enable **"Identity Toolkit API"** (sometimes called "Google Identity").

---

## 2. Configure the OAuth consent screen

Required before you can create client IDs.

1. Left menu → `APIs & Services` → `OAuth consent screen`.
2. User Type: **External** (unless you have Google Workspace — then Internal).
3. Fill in:
   - App name: `SRoadTutor`
   - User support email: your email
   - Developer contact: your email
4. Scopes → `Add or remove scopes` → pick `openid`, `email`, `profile`. Save.
5. Test users → add your own Gmail address so you can log in while the app is in testing.
6. Save.

---

## 3. Create the 3 OAuth Client IDs

### 3a. Backend (Web application) — required even for mobile-only apps

`APIs & Services` → `Credentials` → `+ Create Credentials` → `OAuth client ID`.

- Application type: **Web application**
- Name: `SRoadTutor Backend`
- Authorized redirect URIs: leave empty (we only verify tokens server-side, no redirect flow)
- Create → copy the **Client ID** and **Client secret**.

Put these in **all three** `backend/.env.dev`, `.env.qa`, `.env.prod` as:

```
GOOGLE_OAUTH_CLIENT_ID=<the-web-client-id>
GOOGLE_OAUTH_CLIENT_SECRET=<the-web-client-secret>
```

You can reuse the same Web client ID across all 3 envs, OR create 3 separate ones if you want stricter isolation. Reusing is fine for a small team.

### 3b. Android client ID

Each Flutter flavor (dev/qa/prod) has its own **applicationId**, so you need
**3 Android client IDs** — one per applicationId.

For each of:
- `com.sroadtutor.app.dev` (dev flavor)
- `com.sroadtutor.app.qa` (qa flavor)
- `com.sroadtutor.app` (prod flavor)

Do this:

1. `+ Create Credentials` → `OAuth client ID`.
2. Application type: **Android**.
3. Package name: the applicationId above (e.g. `com.sroadtutor.app.dev`).
4. SHA-1 signing certificate: run this in `mobile/android/`:
   ```bash
   ./gradlew signingReport
   ```
   Copy the `SHA1` of the `debug` variant. (For release builds later, you'll generate your own keystore — see `docs/RAILWAY_DEPLOY.md`.)
5. Create.

**You don't need a client secret for Android/iOS clients** — Google doesn't issue them. The mobile app uses just the client ID to request a token.

### 3c. iOS client ID

Same drill, 3 times (one per flavor's Bundle ID):

1. `+ Create Credentials` → `OAuth client ID` → iOS.
2. Bundle ID: `com.sroadtutor.app.dev` / `qa` / (prod = no suffix).
3. Create → download the `GoogleService-Info.plist` if offered (we don't need it for google_sign_in alone, but keep it for Firebase later).

---

## 4. Wire it into the mobile app

The `google_sign_in` Flutter package handles the platform-side plumbing. You
configure the **Web client ID** (yes, the backend one from step 3a) in the
Dart code — this is what it sends in the token so the backend can verify it.

We load the Web client ID from a flavor-specific `.env` file in Flutter (see
`mobile/lib/app/config/env_config.dart`). Put these values in `mobile/.env.dev`,
`.env.qa`, `.env.prod`:

```
GOOGLE_WEB_CLIENT_ID=<the-web-client-id-from-step-3a>
```

---

## 5. Test it

1. Backend running locally on port 8080.
2. Flutter app on an emulator: `flutter run --flavor dev -t lib/main_dev.dart`.
3. Tap "Sign in with Google" → pick your test-user Gmail.
4. You should be redirected back into the app, logged in, and see your profile.
5. Check the backend logs — you should see `Google OAuth login successful for user=<email>`.

---

## Troubleshooting

- **`Access blocked: This app's request is invalid`**: usually a missing SHA-1 or wrong package name in the Android client. Re-check step 3b.
- **`ApiException: 10` in google_sign_in**: SHA-1 mismatch. Run `./gradlew signingReport` again and update the Google Console.
- **Backend returns `Invalid Google ID token`**: the Web client ID in `.env` doesn't match the one the mobile app is using. Make sure `GOOGLE_WEB_CLIENT_ID` (mobile) and `GOOGLE_OAUTH_CLIENT_ID` (backend) are **the same Web client ID**.
- **"This app isn't verified"** screen on first login: expected during development. Click "Advanced" → "Go to SRoadTutor (unsafe)". For prod, submit the app for Google verification (takes a few days).
