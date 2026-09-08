# IMPLEMENTATION_PLAN.md — Omni full-app build plan (UI → backend → release)

> Companion to `UI_ARCHITECTURE.md`. That file governs **how screens are built**; this file governs
> **everything behind and around them**. Nothing in this plan changes the UI architecture: every
> existing screen keeps its `DesignFrame`, its `OmniBottomNav`, and the enum + `Crossfade`
> navigation. Backend work plugs in *behind* the screens via ViewModels — screens never talk to
> Firebase directly.

**Project:** Omni — 360° lifestyle + emergency healthcare app (Premier University)
**Team:** Supta Baul (1093) — Admin & Config · Arifa Rahman Mumtahina Sikder (1082) ·
Md. Muslim Mia Munshi (1089) · Syed Ridwanul Hoque (1109)

---

## 0. Where the project stands today

Done (UI layer, ~70% of screens):
- Onboarding, Sign In, Sign Up, Home (bento dashboard), Feed, SOS + hospitals sheet,
  Nutrition, Settings — all pixel-matched to Figma.
- Shared chrome (`OmniHeader`, `OmniBottomNav`), theme (`Color.kt`, `Type.kt`), `DesignFrame`
  scaling system, `UI_ARCHITECTURE.md` conventions.

Not done (this plan):
- Any data layer: no Firebase, no Room, no ViewModels, no repository pattern, no DI.
- All screens render hardcoded mock data; navigation state is a plain enum with no auth gate.
- No google-services config, no API keys, no permissions beyond basics.

**Architecture target (MVVM + repository, manual DI):**

```
ui/            existing screens + one ViewModel per screen (new, sits behind the screen)
data/model/    plain Kotlin data classes (User, Post, DailyMetrics, Meal, Hospital, Guide…)
data/repo/     AuthRepository, UserRepository, MetricsRepository, FeedRepository,
               VerificationRepository, HospitalRepository, GuideRepository
data/firebase/ Firestore sources (one file per repo, if it grows)
data/local/    Room DB (first-aid guides, optional metrics cache)
di/            AppContainer — created in OmniApplication, holds Firebase + repos (no Hilt)
```

**Deliberate decisions (do not relitigate without team discussion):**
- **No Hilt/Koin.** Manual `AppContainer` injection — zero KSP setup, easy for 4 people to trace.
  (Room will bring KSP in Phase 7 anyway; DI can migrate to Hilt later if it ever hurts.)
- **Keep enum navigation.** `UI_ARCHITECTURE.md` §2a chose no navigation library. The enum gains an
  auth-driven start destination instead: `MainActivity` observes an `AuthState` flow and swaps the
  launch screen between Onboarding/SignIn and Home. No NavHost.
- **Screens observe StateFlow; repos expose suspend + Flow APIs.** Composables stay dumb.

---

## Phase 1 — Foundation & Firebase project setup *(everything else depends on this)*

**Goal:** project compiles with Firebase wired, DI skeleton in place, first Auth calls working.

1. **Firebase console (one person, ~1h):**
   - Create project `omni-healthcare` → add Android app `com.example.omni` → download
     `google-services.json` → put in `app/`.
   - Enable **Authentication → Email/Password** (Google sign-in in Phase 9, Facebook skip).
   - Enable **Cloud Firestore** (start in test mode, lock down in step 5). Region: `asia-south1`.
   - Enable **Storage** ⚠ see Risks — new projects may need Blaze (pay-as-you-go with free tier)
     for Storage. If the team can't enable billing now, defer image upload (avatar/post images)
     to Phase 9 and keep text-only; Auth + Firestore work on the free Spark plan.
2. **Gradle (version catalog — add to `libs.versions.toml`):**
   - Plugin `com.google.gms.google-services` (apply in root + app).
   - `firebase-bom` (BoM; pin current latest), then `firebase-auth`, `firebase-firestore`,
     `firebase-storage` (no versions — BoM manages), `firebase-analytics` optional.
   - `androidx.lifecycle:lifecycle-viewmodel-compose`.
3. **Skeleton code:**
   - `OmniApplication` (register in manifest) → builds `AppContainer`.
   - `AppContainer`: lazy singletons — `FirebaseAuth`, `FirebaseFirestore`, repos.
   - `data/model/User.kt` with `role: UserRole` (`USER`, `PROFESSIONAL`, `ADMIN`),
     `profession: Profession?` (`DOCTOR`, `NUTRITIONIST`), `verified: Boolean`.
   - `AuthRepository`: `val authState: StateFlow<AuthState>` (Unauthenticated / Authenticated /
     NeedsOnboarding), `signUp(name, email, password)`, `signIn(email, password)`, `signOut()`.
     SignUp also writes the `users/{uid}` document in a Firestore transaction.
   - `MainActivity`: launch screen becomes `when (authState)`-driven instead of the constant.
4. **Wire the existing auth screens:** `SignInScreen`/`SignUpScreen` get `SignInViewModel` /
   `SignUpViewModel` — loading state on the button, error text under the fields, real calls.
   No visual redesign; bind to the existing composables.
5. **Security rules v1:** users can read/create their own doc; nothing else. (Full rules in Phase 6.)

**Acceptance:** real account create/login/logout works on a device; wrong password shows an error;
killing the app keeps you logged in; Onboarding shows only for fresh installs.

**Est:** 1 week · **Owner:** Syed Ridwanul (auth flows) + Supta (console/config)

---

## Phase 2 — Profiles, Settings persistence, avatar

1. `UserRepository`: `getUser(uid): Flow<User>`, `updateProfile(...)`, `uploadAvatar(uri)` →
   Storage path `avatars/{uid}.jpg` (compress to ≤512px before upload), update `photoUrl`.
2. `HomeScreen` header binds to the real user (name from Firestore, avatar from URL — Coil for
   remote images; add `coil-compose`).
3. `SettingScreen`:
   - Display name/email/avatar → real data; "Edit" opens a small dialog → `updateProfile`.
   - Push-notifications + offline-cache toggles → persisted to `users/{uid}/prefs` (doc merge);
     offline-cache toggle gates Phase 7's download behaviour.
   - Log out button → `AuthRepository.signOut()` → auth state flips the app back to SignIn.
4. Seed the demo admin: create one account manually, set `role=ADMIN` in console (this is the
   account the team uses for Phase 6 review).

**Acceptance:** avatar upload → shows in header; toggle persists across restarts; logout returns to
auth flow with back-stack cleared (no back-into-app).

**Est:** 1 week · **Owner:** Arifa (profile/settings) with Syed (repos)

---

## Phase 3 — Daily wellness trackers (Pillar A: Prevention)

The Home bento currently shows hardcoded numbers. Make them real, per-day, offline-tolerant.

1. **Model:** `DailyMetrics(date: LocalDate, waterGlasses, sleepHours, steps, fiberGrams)`.
2. **Firestore layout:** `users/{uid}/metrics/{yyyy-MM-dd}` — one doc/day, merged writes.
   Enable **Firestore offline persistence** (it is on by default on Android — verify, and raise
   cache size to unlimited) so the dashboard works without network.
3. **MetricsRepository:** `metricsFor(date): Flow<DailyMetrics>` (source = Firestore doc listener,
   `setOptions(merge())` writes), `addGlass()`, `setSleep(hours)`, `setSteps(n)`, `addFiber(g)`.
4. **Water card:** + button → `addGlass()`; the 12-glass row fills from data. Goal constants
   (12 glasses, 8h sleep, 10k steps, 31g fiber) live in `users/{uid}/goals`, defaults seeded,
   editable later — keep hardcoded for MVP.
5. **Steps:** hardware step counter (`Sensor.TYPE_STEP_COUNTER`, needs
   `ACTIVITY_RECOGNITION` runtime permission on API 29+) — a foreground-lite approach for MVP:
   read the sensor on app open, compute today's steps from the boot-relative counter, write to
   Firestore. (Full background service = stretch goal, not needed to demo.)
6. **Sleep + fiber:** manual entry for MVP (tap card → small edit sheet). Sleep can later read
   from Health Connect — stretch goal, flag it in the report as "future work".
7. `HomeViewModel` exposes `StateFlow<DashboardUiState>` = user + today's metrics; HomeScreen
   binds every number to it. Water/steps/sleep/fiber cards each bind their field.

**Acceptance:** add a glass offline → UI updates instantly → data syncs when back online; new day
shows zeros; two devices see the same numbers.

**Est:** 1.5 weeks · **Owner:** Md. Muslim Mia (trackers)

---

## Phase 4 — Meals & calorie logging (Pillar A cont.)

1. Model `Meal(id, name, mealType, calories, protein, carbs, fat, photoUrl?, date, time)`.
2. Firestore: `users/{uid}/meals/{id}`; query by date desc.
3. `NutritionScreen`: macro rings + food log rows bind to `NutritionViewModel`
   (today's totals = sum of today's meals; rings animate to % of goals).
4. Add-meal flow: bottom sheet matching the app's style — name, type (breakfast/lunch/dinner/
   snack), macros, optional photo (Storage `meals/{uid}/{id}.jpg`).
5. Food presets: bundle ~30 common foods (JSON in assets) with typical macros → searchable picker;
   custom entry free-form. (Real food-recognition API = explicitly out of scope for v1.)
6. Delete meal (long-press or swipe) + edit.

**Acceptance:** log breakfast → rings + "X cal" update; delete restores; day switcher on the
weekday chips shows that day's log.

**Est:** 1 week · **Owner:** Arifa

---

## Phase 5 — Community feed (Pillar B: Communication)

1. **Model:** `Post(id, authorUid, authorName, authorRole, authorVerified, body, imageUrl?,
   likeCount, commentCount, createdAt)`, `Comment(id, postPath, authorName, body, createdAt)`.
2. Firestore:
   - `posts/{id}` — **denormalise author name/role/verified into the post** at write time
     (display never needs a join; updates to a badge are rare and acceptable to fan out).
   - `posts/{id}/likes/{uid}` (exists = liked) and `posts/{id}/comments/{cid}`.
   - Counter updates via `FieldValue.increment` + a transaction for "unlike".
3. `FeedRepository`: paged feed (`orderBy(createdAt).limit(20)` + `startAfter` cursor),
   `toggleLike(postId)`, `addComment`, `createPost`.
4. `FeedScreen` binds: real posts, like buttons optimistic-update, comment sheet, "add post"
   (only for professionals in v1? — **decision: all users can post, professionals get the
   verified badge on their posts**; matches the pitch's "users, doctors and nutritionists share").
5. Post images: compress → Storage `posts/{id}.jpg`; show via Coil.
6. Stories row: keep as static UI for v1 (stretch: 24h-expiring story docs).

**Acceptance:** two accounts see each other's posts live; like/comment counts sync; badge shows on
the seeded professional account.

**Est:** 1.5 weeks · **Owner:** Syed Ridwanul

---

## Phase 6 — Verification portal + Admin review (Pillar B cont.)

*This is the feature the pitch hangs "misinformation-free" on — demo-critical.*

1. **Professional onboarding flow** (new screens, same visual language):
   - In Settings, "Healthcare Professional?" card (already in the UI) → opens application form:
     profession (Doctor/Nutritionist), registration number, speciality, certificate photo(s).
   - Submit → uploads images to Storage `certificates/{uid}/{n}.jpg` + writes
     `verifications/{uid}` `{status: PENDING, certUrls, profession, regNo, submittedAt}` +
     sets user `role=PROFESSIONAL, verified=false`.
2. **Admin review (in-app admin mode — recommend over a separate web panel for the demo):**
   - If `role=ADMIN`, Settings shows "Review Queue". Admin screen lists PENDING applications
     (photo, details, zoomable certificates), Approve/Reject with optional note.
   - Approve → transaction: `verifications/{uid}.status=APPROVED`, `users/{uid}.verified=true`.
     Reject → status=REJECTED + note; user can re-apply.
   - Audit fields: `reviewedBy`, `reviewedAt`.
3. **Badge propagation:** feed reads `authorVerified` (denormalised — new posts pick it up
   automatically; for old posts, backfill with one console script).
4. **Security rules v2 (lock everything down now):**
   - `users/{uid}`: read own + read public profile fields of others (name/role/verified/avatar via
     `get` rules); write own but **never the `role`/`verified` fields** (rule: request only touches
     allowed fields).
   - `verifications/{uid}`: create/read own; status transitions only for `request.auth.token.admin`
     — since custom claims need the Admin SDK, MVP rule = only the seeded admin UID may write
     status (hardcode the admin uid in rules, replace with custom claims if time allows).
   - `posts`: create if authenticated; update/delete only author; likes/comments create-if-own.
   - `hospitals`: read-only for everyone, write nobody (client).
5. Professional-only powers on the feed (e.g. "diet plan" reply formatting) can stay cosmetic for v1.

**Acceptance:** full loop — apply → admin sees queue → approve → badge appears on next post;
reject → applicant sees status + can reapply.

**Est:** 1.5 weeks · **Owner: Supta (owns admin panel + rules) with Syed (application flow)**

---

## Phase 7 — Offline first-aid guides (Pillar C: Action, part 1)

1. **Content first (the real work):** author 8–12 guides as a bundled JSON
   (`assets/guides.json`): title, category, intro, steps[] (text + optional illustration +
   optional audio note), warnings, duration. Sources: rewrite Red Cross/WHO public guidance in
   your own words (cite sources in an About screen). Start with: CPR, choking, bleeding, burns,
   fracture, fainting, snake bite, drowning, heat stroke, poisoning.
2. **Room (adds KSP to the build):** `GuideEntity`, `GuideStepEntity`, DAOs. On first launch
   (gated by the Settings offline-cache toggle from Phase 2): parse `assets/guides.json` → seed DB.
   Illustrations ship as drawables (the existing 3D style); audio: MVP = **Android TTS**
   (`TextToSpeech`) reading the step text aloud — real recorded audio is a stretch goal
   (Storage `guides-audio/{id}.mp3`, downloaded on demand).
3. `GuideRepository`: `guides(): Flow<List<Guide>>`, `guide(id)`, `speakStep(text)`,
   `stopSpeaking()`; everything works in airplane mode by design.
4. **UI:** the "Explore!" button on the Home hero opens the guide list; guide detail screen with
   step pager, large text, ▶ listen button (TTS), next/prev. Looping animations: existing
   Lottie-style assets if available, else subtle Compose animations (scale/pulse on the
   illustration).
5. Search by title/category (Room query).

**Acceptance:** airplane mode on → open app → full guide list + detail + TTS audio all work.

**Est:** 1.5 weeks · **Owner:** Md. Muslim Mia (content + Room) + Supta (TTS/detail UI)

---

## Phase 8 — SOS + hospitals + routing (Pillar C: Action, part 2)

**Key decision — maps without a billing headache:** Google Maps SDK + Places API require an active
billing account (free monthly credit covers a demo, but setup friction is real). Recommended stack:

- **Map rendering:** Maps SDK for Android *if* the team can enable billing (free tier is plenty for
  a demo). Fallback: `osmdroid` (OpenStreetMap, completely free, no key) — decide in the first
  sprint of this phase and write the choice here.
- **Hospital data: seeded Firestore collection, not Places.** `hospitals/{id}`
  `{name, lat, lng, rating, phone, address, type}` — compile ~40 real Chittagong + Dhaka hospitals
  (names/coords/phones are public knowledge; rating = app-side 4–5 seed values). This gives the
  "top-rated nearby" feature deterministically, offline-cacheable, and free.
- **Routing: `geo:` deep link** → opens Google Maps navigation (free, no key):
  `geo:destLat,destLng?mode=d` — plus a "Call" `tel:` intent (UI already has the button).

1. `HospitalRepository`: seed check (one-time console import script or first-run import from a
   bundled JSON), `nearby(lat, lng, radiusKm): List<Hospital>` = in-memory haversine sort of the
   cached collection (fetch collection once, cache in Room/memory — tiny dataset).
2. **Location:** Play Services fused location (`getCurrentLocation`), runtime permission flow
   (the SOS screen should degrade gracefully: "location off → showing saved area").
3. `SosScreen`: real location → sorted hospital list (the existing bottom sheet binds to it),
   distance + rating + Call + Route buttons functional. Map pin cluster for nearby hospitals.
4. Emergency contacts strip (optional): `users/{uid}/emergencyContacts` → one-tap call/SMS.

**Acceptance:** with location on, "Find the Route" opens Google Maps nav to the selected hospital;
"Call" dials; list is sorted by real distance; works with the seeded dataset offline.

**Est:** 1.5 weeks · **Owner:** Syed (maps/location) + Supta (dataset + sheet wiring)

---

## Phase 9 — Notifications, Google sign-in, polish

1. **FCM:** notifications for — verification approved/rejected, comments on your post, replies.
   Needs a token registry `users/{uid}/fcmToken` + a trigger (Cloud Functions on Firestore
   writes — this is the one Functions use-case worth doing; skip if out of time, poll instead).
2. **Google Sign-In** (the UI button exists): SHA-1 in Firebase console →
   `firebase-auth` Google provider → Credential Manager flow. Facebook button: hide or leave
   decorative (decide; don't ship dead buttons in the demo — hide it).
3. **Polish pass:** loading states (shimmer on feed/cards), empty states ("no meals logged yet"),
   error toasts + retry, input validation everywhere, keyboard-inset handling on auth + add-post.
4. **Session edge cases:** logged-out on token expiry; Firestore permission-denied handling.

**Est:** 1 week · **Owner:** all four, split by their screens

---

## Phase 10 — Testing, hardening, demo & release

1. **Unit tests** (repos with a fake/emulator Firebase via `firebase-emulator` — worth it:
   run Auth + Firestore emulators in CI-less local tests): metrics merge logic, feed pagination,
   verification state machine.
2. **Instrumented:** auth flow, add-glass offline → online sync, guide opens in airplane mode.
3. **Security rules tests** in the emulator (the Phase 6 rules, asserted).
4. **Release:** `minifyEnabled` decision (currently off — turn on with proguard rules for
   Firebase/Coil/Maps, or leave off if release build breaks late), version bump, signed APK
   (generate keystore, store the password in the team vault, **never** commit it),
   internal distribution (Drive link) for the panel, Play Store optional.
5. **Demo data pack:** a scripted demo — seeded hospitals, admin account, one approved doctor,
   three posts, two days of metrics — so the live demo never depends on network luck.

**Est:** 1 week · **Owner:** all

---

## Firestore schema (single source of truth)

```
users/{uid}
  name, email, photoUrl, role: USER|PROFESSIONAL|ADMIN,
  profession: DOCTOR|NUTRITIONIST|null, verified: bool, createdAt
  users/{uid}/prefs            { pushEnabled, offlineCache }
  users/{uid}/goals            { water:12, sleep:8, steps:10000, fiber:31 }
  users/{uid}/metrics/{date}   { waterGlasses, sleepHours, steps, fiberGrams }
  users/{uid}/meals/{id}       { name, mealType, calories, protein, carbs, fat, photoUrl, at }
  users/{uid}/emergencyContacts/{id} { name, phone }

posts/{id}
  authorUid, authorName, authorRole, authorVerified, body, imageUrl,
  likeCount, commentCount, createdAt
  posts/{id}/likes/{uid}       { }
  posts/{id}/comments/{cid}    { authorName, authorVerified, body, createdAt }

verifications/{uid}
  profession, regNo, speciality, certUrls[], status: PENDING|APPROVED|REJECTED,
  note?, submittedAt, reviewedBy?, reviewedAt?

hospitals/{id}   { name, lat, lng, rating, phone, address, type }
```

Room (device-only): `guides`, `guide_steps`, plus `hospitals` cache.

Storage paths: `avatars/{uid}.jpg` · `certificates/{uid}/{n}.jpg` · `posts/{id}.jpg` ·
`meals/{uid}/{id}.jpg` · `guides-audio/{guideId}.mp3` (stretch)

---

## Screen-by-screen wiring map (full code audit, 2026-09-08)

Every Kotlin file was read for this map: `MainActivity`, all 8 screens, `AuthCommon`, `OmniChrome`,
`DesignFrame`, `HomeScreen/HomeBento/HomeUpdates`, theme. The screens are **callback-driven with
local `remember` state and hardcoded content lists** — which is exactly the seam the backend plugs
into. Rules first, then the per-screen map.

### Integration rules (how this stays smooth)

1. **Screens keep their signatures.** Each screen gains one leading parameter — its ViewModel or a
   single `UiState` — with a **default value equal to today's hardcoded mock**. Previews and the
   existing Figma-pixel layout keep working; migration is per-screen and incremental, never
   big-bang.
2. **Callbacks stay callbacks.** `onAddGlass`, `onFindRoute`, `onApplyForVerification`… remain the
   surface; MainActivity (or the screen) points them at ViewModel methods instead of nothing.
   No screen ever imports a Firebase class.
3. **Local `remember` state graduates, it is not deleted.** Every `var x by remember { … }` listed
   below moves into its ViewModel as `StateFlow`; the composable keeps rendering identically from
   the hoisted value.
4. **Hardcoded content lists become `UiState` fields with the current list as the default.**
   (`FeedPosts`, `Hospitals`, `FoodLog`, `RingPages`, `Days`, `StoryImages`…)
5. **`MainActivity` gets one auth gate.** `launchScreen` constant → `val start by
   authViewModel.startDestination.collectAsState()` returning `Onboarding`/`SignIn`/`Home`; the
   enum + `Crossfade` stay exactly as `UI_ARCHITECTURE.md` §2a specifies.
6. **New UI follows `UI_ARCHITECTURE.md`.** Compose-post, comments, verification form, admin queue,
   guides: new Figma first (or explicitly minimal in-style), each with `DesignFrame` at the root.

### Unbound callbacks — the exact seams (verified against MainActivity)

These exist in the screens but MainActivity passes nothing for them (they default to `{}`).
Each is where its phase's ViewModel plugs in:

| Screen | Unbound callback | Plugs into |
|---|---|---|
| `HomeScreen` | `onExploreFirstAid`, `onAddGlass` | Phase 7 (guide list) / Phase 3 (`addGlass`) |
| `FeedScreen` | `onCompose` | Phase 5 (compose post) — note: the header's ➕ button has **no onClick at all** yet; `StoryStrip`'s share tile does use `onCompose` |
| `SosScreen` | `onSearch`, `onFindRoute`, `onCallHospital` | Phase 8 (location / `geo:` intent / `tel:` intent) |
| `NutritionScreen` | `onLogFood` | Phase 4 (add-meal sheet) |
| `SettingScreen` | `onEditProfile`, `onAccountAction("Edit"/"Change")`, `onSavedEmergencies`, `onApplyForVerification`, `onLogOut` | Phases 2 / 6 |
| `SignInScreen` | `onForgotPassword` | Phase 1 (reset-email dialog) — currently dead |

### Per-screen state → backend map

**OnboardingScreen** (`onFinish`, `onSignIn`) — no data. Optional: a `hasSeenOnboarding` flag
(DataStore) so the auth gate can skip it for returning users.

**SignUpScreen** — local: `name, email, password, confirmPassword`; `TermsRow` is **static art**
(no checked state — add a real toggle); `SocialButton` exists in `AuthCommon` but **no screen
renders it** (social sign-in is new UI in Phase 9, not a rewire).
→ `SignUpViewModel`: field validation (email shape, password ≥ 6, match), `loading`, `error`;
button → `AuthRepository.signUp(name, email, pass)` → `users/{uid}` doc.

**SignInScreen** — local: `email, password`; labels "Sing In"/"Forget Password?" are the design's
own typos, kept. → `SignInViewModel`: `signIn()`, `resetPassword(email)` for `onForgotPassword`
(Firebase `sendPasswordResetEmail` + a small dialog). `AuthPrimaryButton`/`AuthField` gain optional
`enabled`/`error`/`supportText` params (default null) — the only change to `AuthCommon`.

**HomeScreen** — no data params; hardcoded: greeting name (in `OmniHeader`), water `7/12`, steps
`5,600/10,000`, `78%`, fiber/sleep/CPR update-cards content.
→ `HomeViewModel` → `DashboardUiState(user, metrics, guides)`. `OmniHeader` gains optional
`name: String` / `avatarUrl: String?` params (defaults = today's constants) — it is shared by
Home/Feed/SOS/Nutrition, so defaults keep every call site compiling. Water `+` → `onAddGlass` →
`MetricsRepository.addGlass()`; card values bind to `metrics` flow; update-cards bind to
meals/sleep/guide-progress aggregates.

**FeedScreen** — local: `selectedSegment` (Discover/Following, filter only). Hardcoded: `FeedPosts`
(2×`Post` — author/badge/avatar/image/likes/comments/reposts), `StoryImages`, "3 min ago"
timestamps, decorative ➕ in the header.
→ `FeedViewModel`: `posts: StateFlow<List<Post>>` (paged), `toggleLike(id)`, `openComments(id)`;
`Post` model replaces the private data class (same fields + `id`, `authorUid`, `imageUrl`,
`createdAt`); likes optimistic; timestamp = `DateUtils.getRelativeTimeSpanString`. `onCompose` →
Compose-post screen (new, Phase 5). Segment filter → query filter (`Following` = authors the user
follows — v1 can alias Discover if follow-graph is cut).

**SosScreen** — local: `activated` (swipe → sheet; `startActivated` param already exists for
deep-links). Hardcoded: `Hospitals` (2 entries: name/type/drive/rating/photo), static map bitmaps.
→ `SosViewModel`: `location: StateFlow<Location?>` (fused client + permission flow),
`hospitals: StateFlow<List<Hospital>>` (seeded collection, haversine-sorted, drive time = rough
distance/speed or "x.x mi" only), `onCallHospital(hospital)` = `tel:` intent,
`onFindRoute(hospital)` = `geo:` intent — so `onFindRoute`/`onCall` change from `() -> Unit` to
`(Hospital) -> Unit`. Map stays the bitmap in v1; pins are a Phase 8 stretch (Maps SDK decision).

**NutritionScreen** — local: `selectedDay` (index, default 3 = "W 04"). Hardcoded: `Days` ("01".."07"
— **not real dates**), `FoodLog` (3×`FoodEntry`), `RingPages` (macro + activity values), weight
"59kg" / height "5.7foot", gauge "80%".
→ `NutritionViewModel`: **week window = today-centred 7 real dates** (the chips' `Day(initial,
number)` gains a `date: LocalDate`; default selection = today, not index 3); `meals` per selected
date; rings bind to metric totals (protein/carbs/fat from meals, steps/water/sleep from metrics);
gauge % = calories vs goal; weight/height → `users/{uid}/profile` (editable via the add-meal-style
sheet). `onLogFood` → add-meal sheet (Phase 4).

**SettingScreen** — local: `pushNotifications`, `offlineCache` toggles. Hardcoded: name
"Sayed Mahir", email; `AccountRows` "Edit"/"Change" both route `onAccountAction(label)`.
→ `SettingsViewModel`: `user` flow for the profile row; toggles → `users/{uid}/prefs` (push gate is
cosmetic until FCM in Phase 9; offlineCache gates Phase 7 seeding); `onLogOut` → `signOut()` →
auth gate returns to SignIn; `onApplyForVerification` → application flow (Phase 6); `onEditProfile`
→ edit dialog (name/photo); `onAccountAction` → same dialog ("Change" = password reset email).

### Sequencing note

The map above is the wiring view of Phases 1–8; phase order is unchanged. Recommended first PR
(Phase 1, day 1): `AppContainer` + `AuthRepository` + `MainActivity` auth gate + `SignInViewModel`
— it exercises rules 1–5 on the simplest screen before anything else touches the UI files.

---

## Timeline (10–11 weeks, parallelised for 4 people)

| Weeks | Track A (Syed) | Track B (Arifa) | Track C (Muslim) | Track D (Supta) |
|---|---|---|---|---|
| 1 | Auth repo + wiring | Profile models | Metrics models | Firebase console, config, rules v1 |
| 2 | Auth polish, MainActivity gate | Settings + avatar | Metrics repo + Home binding | Firestore schema seed, demo data |
| 3 | Feed repo + paging | Meals repo | Steps sensor | Feed security rules |
| 4 | Feed UI binding | Add-meal sheet + presets | Water/sleep edit sheets | Verification rules |
| 5 | Like/comment | Nutrition rings bind | **Tracker QA offline sync** | Admin queue UI |
| 6 | Post images (Coil+Storage) | Meals QA | Guides content writing | Approve/reject flow |
| 7 | SOS location + permissions | Guide list UI | Room + seeding | Hospital dataset |
| 8 | SOS map + routing | Guide detail + TTS | Guides QA (airplane) | Sheet wiring + Call/Route |
| 9 | FCM (optional) / Google sign-in | Empty/error states | Unit tests | Rules tests + emulator |
| 10–11 | Polish + release + demo pack (everyone) | | | |

---

## Risks & open decisions

| # | Risk / decision | Call |
|---|---|---|
| 1 | **Storage needs Blaze (billing) on new projects** | Check on day 1. If blocked: text-only until Phase 9; avatars can be initials + colour |
| 2 | Maps SDK needs billing account | Decide Phase 8 week 1: Maps SDK (if billing ok) vs osmdroid (free) |
| 3 | Places API cost/complexity | **Avoided entirely** — seeded hospital collection |
| 4 | Facebook button dead in demo | Hide it (UI change is one `if`) |
| 5 | Security rules complexity (custom claims need Admin SDK) | MVP: hardcoded admin UID in rules; custom claims = stretch |
| 6 | Firestore offline conflicts (two devices, same metric) | Merge-writes per field make last-write-wins acceptable at this scale; document it |
| 7 | Step counter varies by device | Fallback to manual +/- entry on the steps card |
| 8 | Content licensing for guides | Rewrite guidance in own words, cite sources in-app |
| 9 | `fontScale` pinning in `DesignFrame` vs accessibility | Already documented in UI_ARCHITECTURE.md — keep; mention as known trade-off in report |

**Definition of done per phase:** acceptance criteria met on a real device, code reviewed by one
other teammate, and the phase's section here checked off with the owner's name.
