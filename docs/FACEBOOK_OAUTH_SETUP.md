# Facebook OAuth Setup

Flow is conceptually identical to Google: mobile app gets a token from
Facebook → sends it to our backend → backend verifies it by calling the
Facebook Graph API → issues our own JWT.

---

## 1. Create a Meta for Developers account

1. Go to https://developers.facebook.com → `Log in` with your personal Facebook account.
2. Accept the developer TOS.

---

## 2. Create an app

1. `My Apps` → `Create App`.
2. Use case: **Authenticate and request data from users with Facebook Login**.
3. App type: **Consumer**.
4. App name: `SRoadTutor`.
5. App contact email: your email.
6. Create.

You now have an **App ID** and **App Secret** (under `Settings` → `Basic`).

Put these in all three `backend/.env.*`:

```
FACEBOOK_OAUTH_APP_ID=<app-id>
FACEBOOK_OAUTH_APP_SECRET=<app-secret>
```

As with Google, you **can** create separate apps per environment for stricter
isolation — but a single app with multiple platforms is the common practice.

---

## 3. Add the Facebook Login product

1. Left sidebar → `Add a Product` → `Set up` under **Facebook Login**.
2. Platform: pick **Android** and **iOS** (you do both separately).

### 3a. Android config

Add to **Facebook Login → Settings → Valid OAuth Redirect URIs**:
- none needed for mobile SDK flow.

Add **Android platform** (top-level `Settings` → `Basic` → `+ Add Platform` → `Android`):
- Package name: for each flavor — `com.sroadtutor.app.dev`, `com.sroadtutor.app.qa`, `com.sroadtutor.app`. Add three platform entries.
- Class name: `com.sroadtutor.app.MainActivity` (adjust suffix per flavor).
- Key hash: Facebook uses a base64-encoded SHA-1 of your signing certificate. Generate it:

  ```bash
  # debug keystore
  keytool -exportcert -alias androiddebugkey \
    -keystore ~/.android/debug.keystore \
    -storepass android -keypass android \
    | openssl sha1 -binary | openssl base64
  ```

  Paste the output (looks like `abc123xyz=`) into the "Key Hashes" field.

Toggle **"Single Sign On"** on.

### 3b. iOS config

Add **iOS platform**:
- Bundle ID: `com.sroadtutor.app.dev` / `qa` / (prod).
- URL Scheme Suffix: leave blank.

---

## 4. App mode — Development vs Live

While your app is in `Development` mode, only users listed under
`App Roles` → `Roles` (admin, tester, developer) can log in.

- Add your own Facebook account as a tester for development.
- For prod, submit the app for review under `App Review` → `Permissions and Features` → request `email` permission. Takes a few days.

---

## 5. Put the App ID in the mobile app

In `mobile/.env.dev`, `.env.qa`, `.env.prod`:

```
FACEBOOK_APP_ID=<app-id>
FACEBOOK_CLIENT_TOKEN=<client-token>
```

`Client Token` is under `Settings` → `Advanced` → `Security` → `Client token`.

On Android, `mobile/android/app/src/main/res/values/strings.xml` (generated per flavor) picks these up automatically from the `.env` file via `flutter_dotenv`.

On iOS, they go into `mobile/ios/Runner/Info.plist`.

---

## 6. Test it

1. Backend running locally.
2. `flutter run --flavor dev -t lib/main_dev.dart`.
3. Login screen → "Sign in with Facebook" → pick the test user.
4. Watch the backend log: `Facebook OAuth login successful for user=<email>`.

---

## Troubleshooting

- **"App not set up: This app is still in development mode"**: add your Facebook account as a Tester, or submit the app for review.
- **"Invalid key hash"** on Android: the SHA-1 → base64 conversion is very picky. Re-run the exact command above, and don't include trailing newlines.
- **Graph API error on backend (`OAuthException: 190`)**: the access token expired or was for a different app. Check `FACEBOOK_OAUTH_APP_ID` matches on both ends.
- **`email` permission missing from response**: Facebook doesn't always return email. Our backend falls back to `facebook_<id>@users.noreply.sroadtutor.app` as a placeholder. User can update it from settings later.
