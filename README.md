# Omni — Your AI Health Companion

Omni is an AI-powered health companion for Android: log meals from a photo and get a full nutritional
breakdown from Gemini, track steps and sleep, read a community health feed, and consult verified
doctors — with the entire premium tier monetized through **RevenueCat**.

Built with Kotlin + Jetpack Compose on the client and a TypeScript/Express backend on Render.

## Features

- **AI meal logging** — snap a photo, Gemini vision returns calories, macros, and ingredients; the
  analysis runs server-side so the API key never touches the device.
- **Steps** — hardware step-counter sensor with a rollover-safe anchor state machine; steps taken
  while the app is closed survive midnight rollovers and sync to Firestore.
- **Sleep tracking** — nightly records with a dedicated dashboard.
- **Health feed** — a social feed with posts, likes, and a followed-only story rail.
- **Doctor marketplace** — browse verified doctors, book consultations, and chat in real time with
  FCM push notifications. Doctor verification is an admin-approved intake flow on the backend.
- **Premium tier (RevenueCat)** — the doctor directory and consultations sit behind the
  `omni_pro` entitlement, unlocked through an in-app purchase.

## Monetization (RevenueCat)

This project is a RevenueCat **Shipaton** submission (Next-Gen Award category), and the
integration goes one layer deeper than a paywall:

- **RevenueCat SDK 9.29.1** on Android — offerings drive the paywall UI, purchases and restore run
  through the **Test Store**, so no Play Store listing or paid developer account is needed to try it.
- **Entitlement gating** — `omni_pro` is checked via `PremiumGate` before the doctor directory and
  consultations are reachable; the paywall is rendered from live RevenueCat offerings, not static text.
- **Server-side comping** — when an admin approves a doctor's verification, the backend grants them
  `omni_pro` directly through RevenueCat's REST API (`POST /v1/subscribers/{uid}/entitlements/...`),
  giving verified medical professionals free access to the platform they serve on. The grant happens
  in `backend/src/revenuecat.ts` using the secret API key, which only ever lives in server
  environment variables.

## Architecture

```
┌─────────────────────┐         ┌──────────────────────────┐
│   Android app       │  HTTPS  │   Render backend          │
│   Kotlin/Compose    │◄───────►│   Express + TypeScript    │
│                     │         │                           │
│  • RevenueCat SDK ──┼──purchases──► RevenueCat (Test Store)│
│  • Firebase Auth ───┼── ID token ─► verified per request   │
│  • Firestore        │         │  • Firebase Admin SDK     │
│    (realtime data)  │◄────────┼  • Gemini (meal vision)   │
│  • FCM push ◄───────┼─────────┼  • SendGrid (OTP email)   │
└─────────────────────┘         │  • RevenueCat REST (comp) │
                                └──────────────────────────┘
```

- `app/` — the Android client. Single module, Jetpack Compose, MVVM with repositories over
  Firebase (realtime) and the Render backend (sensitive operations).
- `backend/` — the API server. Every route verifies a Firebase ID token before touching data;
  secrets (Gemini, SendGrid, RevenueCat secret key, Firebase service account) are environment
  variables, never in the client.
- `firestore.rules` — deny-by-default security rules; `firestore.indexes.json` holds the composite
  indexes the queries need.

## Getting started

### Prerequisites

- Android Studio (the project uses AGP 9.1 — use a recent Studio release) with JDK 17
- Node.js 20+
- A Firebase project with **Authentication (Email), Firestore, Cloud Messaging, Storage** enabled
- A [RevenueCat](https://www.revenuecat.com) project (free tier is fine; Test Store works without a
  store developer account)

### Android app

1. **Firebase config** — in the Firebase console add an Android app and download
   `google-services.json` into `app/`. The file shipped in this repo is the reference project's
   public client config (it is not a secret; your backend rules are what protect the data).
2. **RevenueCat public key** — add to `local.properties` (never committed):
   ```properties
   REVENUECAT_API_KEY=goog_YourRevenueCatPublicSdkKey
   ```
   It is injected into `BuildConfig` at build time.
3. **Backend URL** — the client talks to the reference deployment at
   `https://omni-jx01.onrender.com`. To run your own backend, update the base URL in
   `app/src/main/java/com/example/omni/data/repo/` (it appears in `FirebaseAuthRepository.kt`,
   `RenderMealAnalysisRepository.kt`, `MessageRepository.kt`, and `FirestoreFeedRepository.kt`).
4. **Build and run** — open the project in Android Studio and press Run, or:
   ```bash
   ./gradlew assembleDebug
   ```

### Backend

```bash
cd backend
npm install
cp .env.example .env   # then fill it in — see the table below
npm run dev            # tsx watch, or: npm run build && npm start
npm test               # vitest suite
```

| Variable | Purpose |
| --- | --- |
| `FIREBASE_SERVICE_ACCOUNT` | Firebase Admin credentials — the entire service-account JSON on one line |
| `SENDGRID_API_KEY` / `SENDGRID_FROM_EMAIL` | Sends the 6-digit OTP verification emails |
| `FIREBASE_HOSTING_URL` | Base URL used in email verification links |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | AI meal analysis (a free-tier key from [Google AI Studio](https://aistudio.google.com/apikey) works) |
| `REVENUECAT_SECRET_API_KEY` | RevenueCat **v1 secret key** — grants the `omni_pro` comp to verified doctors |
| `ADMIN_BOOTSTRAP_UID` | A Firebase UID granted admin claims on first boot |
| `DOCTOR_COMP_DURATION` | How long a verified doctor's comped entitlement lasts |
| `PORT` | Server port (default 3000) |

### Firestore rules and indexes

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

## RevenueCat setup

1. Create a project, add an **Android** app, and copy the **public SDK key** into
   `local.properties` as shown above.
2. Create an entitlement named `omni_pro` and an offering containing your product(s).
3. Configure the **Test Store** so purchases work in a sideloaded build without store accounts.
4. On the server set `REVENUECAT_SECRET_API_KEY` — the app signs RevenueCat in with the same
   Firebase UID it uses for auth, so the backend can grant entitlements to the right subscriber.

To try the paywall: sign in → **Settings → Upgrade to Omni Plus** → purchase through the Test Store.

## Security notes

- Every secret key (Gemini, SendGrid, RevenueCat secret, Firebase service account) lives only in
  backend environment variables. The Android app holds nothing but RevenueCat's public SDK key.
- Every backend route verifies a Firebase ID token; Firestore rules are deny-by-default and scope
  every read and write to the signed-in participant.
- `google-services.json` contains public client configuration only.

## License

[MIT](LICENSE)
