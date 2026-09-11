# BACKEND_PLAN.md — Omni backend, verified against the code

> Written by reading every file under `app/src/main/java/com/example/omni/`, `app/build.gradle.kts`,
> `gradle/libs.versions.toml`, `AndroidManifest.xml` and `google-services.json` on 2026-09-08.
> Every claim about current behaviour in §1–§3 was checked against the source, not assumed.

## 0. How this document relates to the other two

| Document | Authority over | Status |
|---|---|---|
| `UI_ARCHITECTURE.md` | All UI structure, `DesignFrame`, navigation, font policy | **Unchanged. Still binding.** Every UI edit in this plan obeys it. |
| `IMPLEMENTATION_PLAN.md` | Phases, owners, timeline, team split | Mostly good. **§0 "current state" is factually wrong** and two decisions are superseded — see §2. |
| `BACKEND_PLAN.md` (this file) | The backend contract: data model, rules, the exact composable signature each feature needs | New. Use this for *what to build*; use `IMPLEMENTATION_PLAN.md` for *who and when*. |

Read §1 before writing a single line — it explains why the app currently never contacted Firebase at
all, and that one fact reorders everything.

## 0.1 Progress

| Phase | State |
|---|---|
| **0** — Unblock | **Done.** `OmniApplication` created, manifest points at it, `INTERNET` declared. |
| **1** — Auth for real | **Done, verified on a real device.** |
| **2** — Profile + header | **Done, verified on a real device.** One `SessionViewModel` feeds every screen. |
| **3** — Water card | **Done, verified on a real device.** `FieldValue.increment` on `users/{uid}/days/{date}`. |
| **4** — Step counter | **Done, verified on a real device.** `TYPE_STEP_COUNTER` → DataStore → Firestore every 250 steps. |
| **5** — Updates & recommendations | **Done, verified on a real device.** One threshold rule and one bar formula in `domain/Recommendations.kt`; sleep gets an entry sheet. |
| **6** — Nutrition | **Done, verified on a real device.** Week strip, meal add/edit/delete, recomputed roll-up, live gauge numbers — and the Canvas arc replacing both static drawables (`ic_nutri_arc_track` / `ic_nutri_arc_progress` deleted). |
| **7** — First-aid guides | **Done, verified on a real device.** Home's "Explore!" opens `GuidesScreen` (search, severity dots, per-guide percent) → `GuideDetailScreen` (ticks, warnings, back). Ticking steps writes Firestore and Home's CPR card re-renders ("You've read 75% of the guide."). One `GuidesViewModel` owns both pages via `open`. |
| **8** — Feed | **Done in code, compiled; the interactive device pass is still the user's.** Posts bind live (first-page listener + one-shot older pages), like pills flip optimistically, the comments sheet loads and sends live, `ComposePostScreen` creates posts **with an image** (picked through `PickVisualMedia`, downscaled, uploaded to Cloudinary — Firebase Storage needs Blaze), share opens the Android sheet, Following filters on the real follow graph, and an empty feed says so instead of drawing mock posts. **Counters:** the deployed rules forbid client counter writes (DEVIATION 2) and Phase 12's Cloud Functions cannot be deployed on this account, so like and comment counts are read by **server-side aggregation** (`count().get(AggregateSource.SERVER)`) over `posts/{id}/likes` and `posts/{id}/comments` — one read per up-to-1000 documents, and correct without a counter anybody could forge. A like is one document per liker (`likes/{uid}`), never an array on a shared document: the array version lost concurrent likes and was removed. Follower counts work the same way over `users/{uid}/followers`, written as the mirror half of every follow batch. |
| **9** — SOS | **Done, and device-verified (2026-09-10) now that the Firestore database exists and the `hospitals` collection is seeded.** The sheet reads the live directory — "Found 55 facilities within 8 km" of the phone's real fix, nearest first ("Memon-2", 0.7 km) by the haversine sort — and "Call the Hospital" opened the dialer on tap. The hold-to-activate slider writes `sosEvents/{uid}/items/{eventId}` with the fix at the moment of the swipe; the sheet says so honestly when location is off; "Find the Route" opens a `geo:` intent (same verified intent pattern as Call; its tap test was cut short by the phone being picked up mid-test), "Call" falls back to 999 when OSM lists no number, and the alert pill texts every saved emergency contact a Google Maps link via `ACTION_SENDTO`. `SavedEmergenciesScreen` (no Figma frame — §6 rule 11) adds, lists, dials and removes up to 5 contacts under `users/{uid}/emergencyContacts`. **Seeding:** `tools/seed-hospitals.mjs` (Admin SDK, idempotent merge-sets on stable `osm-<id>` document ids, 400-op batches) wrote 177 hospitals from OpenStreetMap data; a second run confirmed idempotency (still 177). |
| **10** — Notifications + FCM | **Done in code, compiled; device test impossible on this machine.** The header bell now opens a real inbox instead of a "coming soon" stub: `NotificationsScreen` (no Figma frame — §6 rule 11) lists `notifications/{uid}/items` newest-first, tints unread rows and dots them by type, marks one read on tap and all read from a header link, and says so plainly when the inbox is empty. The badge on the bell is live. `OmniMessagingService` receives pushes, posts them on the `omni_general` channel through a new `ic_notification_bell`, and re-registers the device token on rotation; the token is written to `users/{uid}.fcmTokens` on sign-in and **removed on sign-out** so the next push for that account cannot land on a phone somebody else is now using. Settings' two switches became real: they read `users/{uid}.prefs` and write it back, and turning push off unregisters this device's token rather than filtering on arrival. `POST_NOTIFICATIONS` is asked for on the notifications page itself (API 33+ only), never at launch. |
| **11** — Messaging | **Done in code, compiled, installed on a real device; interactive device test pending** (the phone was in active use during the window, and the Firestore API is still disabled in the console — see the Phase 8 note). `MessagesScreen` lists conversations and offers a "new chat" picker of verified professionals; `ChatScreen` is the thread with a composer; both are invented UI under §6 rule 11, built from the app's own parts (DesignFrame, palette tokens, existing type styles, previews). One `MessagesViewModel` owns both pages via `open`, the auth session drives the reads, and `MessageRepository` implements the §7 schema (conversation id = the two uids sorted and joined, so a chat is idempotent). The session that wrote it ended with the MainActivity wiring block present but its imports missing — those three imports were added and the build verified green on 2026-09-09. |
| **12** — Verification + admin | **Client half done in code, compiled. The server half is undeployable on this account and is not being faked.** Settings' "Apply for Verification" button was a no-op through Phase 11; it now opens `VerificationScreen` (no Figma frame — §6 rule 11), which writes `verificationRequests/{uid}` with `status: "pending"` and then watches it. The page has three faces and never two at once: the form (profession chips + registration number) while it is the applicant's turn, an "under review" card while it is the reviewer's, and a confirmation once `users/{uid}.verified` is true. Re-applying after a rejection is allowed by a new rule (`rejected` → `pending`, and nothing else); a pending application cannot be edited out from under the reviewer, and an approved one cannot be reopened by the account that benefits from it. **DEVIATION A — no Cloud Functions.** §9's five triggers need the Blaze plan, which this project does not have, so `onVerificationDecision` does not exist: a reviewer approves by hand in the Firebase console (set `verificationRequests/{uid}.status` to `approved`, then `users/{uid}.verified = true` and `role = PROFESSIONAL`). The counter triggers are replaced by the server-side aggregation described in the Phase 8 row. **DEVIATION B — no certificate upload.** The plan's form uploads a certificate to a privately-readable Storage path. There is no Storage here; every image goes through Cloudinary's *unsigned* upload, which returns a public URL, and a licence certificate carries a full name and a registration number. Publishing one to satisfy a checklist is worse than not collecting it, so the form takes the registration number and the reviewer asks for documents out of band. |
| **13** — Rules hardening, offline QA, tests | **Done in code, and the rules are now tested rather than reasoned about.** Five holes closed in `firestore.rules`: a post's `authorId` is immutable on update (an author could otherwise hand their post to another uid, where it would appear under that person's name and badge); comments carry a 500-character ceiling on create *and* update; a message may only be created by a **participant** of that conversation (create previously checked only that `senderId` was the caller's own uid, so any signed-in account could write into any conversation id — a one-way channel into a stranger's thread); `participants` is immutable on update; and `verificationRequests` gained a `rejected → pending` re-application path. **61 rules tests** in `tools/rules.test.mjs` (`npm --prefix tools run test:rules`) run the real rules file in the Firestore emulator — every abuse above is asserted refused, and every write the app actually makes is asserted to still succeed. **42 JVM unit tests** cover `domain/Recommendations.kt`, `StepState.reconcile` (including the clock-correction case that would otherwise double-count a boot's steps), `HealthGoals.clamped`, `compactCount` and `conversationIdOf`. The offline/permission pass found the read paths already correct — every `callbackFlow` that `close(error)`s is collected behind a `.catch` that degrades to an empty value, and both server-side `count()` sites fall back rather than throw — but found one real gap on the **write** side: a refused follow was logged and nothing else, which is indistinguishable from a dead button, so `ProfileUiState.followError` now names the refusal under the button. |

**Phase 10's two deviations, both forced by Phase 12 not existing yet:**

- **The bell badge counts unread items instead of reading `users/{uid}.unread.notifications`.** §7
  denormalises that count onto the profile, but the deployed rules forbid a client writing `unread` and
  the Cloud Functions that would maintain it are Phase 12 — so bound as specified the badge would read 0
  forever, no matter how full the inbox was. `NotificationRepository.observeUnreadCount` counts the
  unread documents directly (capped at 99, since the badge draws a dot rather than a total), which the
  rules do let the owner read. It is ground truth rather than a cache, and when the functions land the
  counter becomes an optimisation to switch to, not a prerequisite.
- **DEVIATION 3 in `firestore.rules`: `notifications/{uid}/items` is now `allow create: if isSelf(uid)`.**
  It was `if false`, which is correct once every item is written by an admin-credentialed function — but
  today it made the inbox permanently unfillable. A user may now write into their **own** inbox and
  nobody else's, so cross-account forgery is still impossible, and the SOS screen can record "your alert
  was saved" as a receipt for a gesture whose every other effect happens somewhere the user cannot check.

Two smaller notes on the same phase. Pushes are honoured against the preference **by token, not by
check**: `onMessageReceived` fires only after delivery, so a client-side test there would still have cost
the round-trip and would be wrong the moment the pref changed on another device — removing the token
means the server has nothing to send to. And the prefs are written as a nested map through
`set(…, merge())` rather than the dotted path `update()` would take: in a `set`, a dot is part of the
field *name*, so `"prefs.pushNotifications"` would have silently created a top-level field of that
literal name and left the real map untouched.

**Phase 9's four deviations, all forced by the same missing thing — a billing account:**

- **A seeded `hospitals` collection instead of the Places API.** The plan's "hospitals within 5 miles"
  assumed Places Nearby Search, which needs an API key *and* an enabled billing account. The directory
  is instead ~40 OpenStreetMap-derived hospitals in `tools/hospitals_import.jsonl`, read-only to
  clients, filtered client-side by haversine distance. It works offline, costs nothing, and is honest
  about being a fixed list.
- **No live Google Map on the SOS sheet.** Would need `maps-compose`, a Maps SDK key and billing. The
  sheet shows the ranked list the map would have annotated.
- **No real drive times.** "12 min away" needs the paid Routes API. The cards show straight-line
  kilometres, labelled as distance rather than time, because a straight line dressed up as a drive time
  is a lie an emergency screen must not tell.
- **Kilometres, not the design's miles.** The app's users are in Dhaka.

**Phase 9 also fixed five defects in the Phase 7/8 code it built on**, all of which would have shipped:
the post composer and the guide search both wrapped their `BasicTextField` in a zero-width box, so
anything typed into either was measured at 0×0 and drawn nowhere; the location permission had a
launcher and a helper but no caller, so the SOS directory could never sort; every `startActivity` in the
SOS path was unguarded and would have crashed the app mid-emergency on a device with no dialer or maps
app (now `startActivitySafely`); and `GuidesScreen`, `GuideDetailScreen` and `ComposePostScreen` were
the only three screens in the app **not wrapped in `DesignFrame`**, so their fixed dp measurements did
not scale with device width and, worse, their `fontScale` was not pinned — a user with large system text
would have burst every pixel-locked row. All three are wrapped now.

**On "verified on a real device" for Phases 9 onward:** this machine has no `adb`, no Android SDK and no
emulator, so nothing after Phase 8 can be installed or loop-tested here. "Done in code, compiled" means
exactly that and nothing more. Two console actions are also outstanding and are the account owner's to
perform: **enable the Cloud Firestore API** in project `omni-2c987`, and **import
`tools/hospitals_import.jsonl`** into a `hospitals` collection. Until the second one runs, the SOS sheet
correctly shows its empty state and offers **Call 999**.

Also landed alongside them, ahead of their phases: `firestore.rules`, `storage.rules`,
`firebase.json` and `.firebaserc` from §8 (with two deviations, marked inline in the rules files);
`google-services.json` moved into `.gitignore` per §1.4.

Phase 2 also settled the four §12 open questions, on the plan's own recommendations, because Phase 3
is blocked on them: water goal **8** glasses (a per-user override is a later `users/{uid}` field), the
step counter reads `TYPE_STEP_COUNTER` with no foreground service, guides ship as bundled JSON, and
`Messages`/`Notifications` get stubs until their designs exist.

Two things Phase 2 added that no phase asked for, both forced by real data replacing mock strings:

- **Coil 3** (`coil-compose` + `coil-network-okhttp`) — nothing in the app could load a remote image
  before, since every avatar was a bundled drawable. The OkHttp artifact is separate in Coil 3; without
  it `AsyncImage` compiles and then fails at runtime on any `http(s)` model.
- **A 40dp touch target and an unread dot on the two header icons** (§4 rule 5's spirit): they had no
  `clickable` at all before. The dot draws only when a count is above 0, so until the Phase 10/11
  functions maintain `users/{uid}.unread`, the header is pixel-identical to Figma.

One thing Phase 3 went further on than §11 asked: the plan says to recompute the date on each **write**
so midnight cannot be cached. The **read** has the same problem — an app left open past midnight would
keep *showing* yesterday's count — so `todayKeyFlow()` sleeps to the local midnight and re-emits, and
the day listener is keyed on it. Both halves of the rule now live in `data/model/DailyMetrics.kt`.

Three places Phase 4 diverges from §11's own sketch, all of them narrowing a way to over-count:

- **The reboot test needs two signals, not one.** §11 says "if the raw value is lower than the stored
  baseline, assume a reboot and set the baseline to 0". `StepState.reconcile` additionally requires the
  derived boot id to have moved. That id is `currentTimeMillis − elapsedRealtime`, so an NTP correction
  shifts it without a reboot; trusting either signal alone would zero the anchor and add a whole boot's
  raw count a second time. The cost is losing a few steps after a reboot that follows very short uptime
  — the right direction to be wrong in.
- **Steps are written absolutely, water is incremented.** A glass is an event only the app knows about;
  a step count is a *reading* the sensor can always reproduce, so `setSteps` is a plain merge-set. An
  increment would double-count the moment a write were retried or replayed from the offline queue. Two
  phones therefore overwrite rather than sum, which is correct — they are counting the same legs — and
  the UI shows `max(local, stored)` so the number never walks backwards.
- **The step tile's percentage is computed, not ported.** §3.2 already flagged Figma's "78%" against
  "5,600 / 10,000"; the default preview now reads 56%.

The permission is asked for the first time Home is composed, once per process, and a refusal leaves the
tile saying *"Step tracking is off, tap to turn it on"* — tapping opens the app's settings page rather
than re-launching a dialog the OS suppresses after a denial, since that would be a dead tap.

**The limitation §11 asked to be stated rather than papered over:** with no foreground service the app
only *observes* while it is alive. The sensor keeps counting in the sensor hub, so reopening the app
catches up on everything walked in between — but steps taken between the last reading and a reboot are
gone, because the counter holding them was reset and nobody read it first.

Four things Phase 5 settled that §3.3 could not have known, because they only appear once the design's
own numbers are asked to obey a rule:

- **Two of the design's three status chips are wrong, and adopting the rule flips them.** Figma pairs
  18/31g of fibre (58%) with "Good" and 6.5/8h of sleep (81%) with "Fair". No single threshold produces
  both, and swapping them is exactly what any threshold does — so `statusFor()`'s 0.80/0.40 is kept and
  the two labels change (`UI_ARCHITECTURE.md` §6 rule 9). CPR's 0% → "Action Needed" is unaffected.
- **The axis marker follows the puck's *centre*, not the end of the fill.** `HomeUpdates.kt` claimed the
  latter and the claim was simply wrong: Figma puts the fibre triangle's centre at 188 of a 346-wide axis
  row, and the puck's centre at 191.8 of the 354 track scales to 187.5 there — a match inside a dp, where
  the puck's *end* sits forty dp away. The two weighted spacers that used to place it worked only because
  they had the axis labels' widths baked into them, which is the one thing that stops being constant once
  the goal is a parameter; the row measures itself with `BoxWithConstraints` now. A side effect worth
  having: because the marker tracks a centre it can never leave the row, ranging 10.3%..89.7%.
- **Three states the design never drew needed copy.** "Goal met" for the two counting cards, and part-way
  and finished for the CPR guide. Every one of them is *shorter* than the sentence it replaces, so no row
  that already fits can overflow, and the guide stops telling someone to "read the 2-min guide" after
  they have read it.
- **Sleep needed a way in, and the design has none.** It is the only metric neither the app nor the phone
  can observe (§3.5), so `ui/home/SleepEntrySheet.kt` is new UI: a half-hour stepper on a bottom sheet
  built from the hospitals sheet's own surface, shadow, handle and reveal. Hand-built rather than
  `ModalBottomSheet`, which is behind an experimental opt-in and hoists its content into a separate window
  — outside `DesignFrame`'s density, which would undo every measurement on the page. It writes to
  **today's** document, not yesterday's, matching what "6.5/h" beside today's water and steps means.

**Nothing below Phase 1 has been run on a device or emulator.** There is no Android SDK or `adb` on
the machine this was written on, so "done" here means *compiles clean and the logic is reasoned
through* — not the §11 "Done when" checklists, every one of which needs a real device and a real
Firebase project. Treat each phase's "Done when" as still outstanding until someone runs it.

Two corrections to §1 that this work turned up:

- §1.1 fact 4 says `SignInViewModel` and `SignUpViewModel` are dead code. They *were* dead, but the
  claim that they are absent from the tree was wrong at the time of writing — both files exist under
  `ui/auth/` and needed no changes, exactly as §11 Phase 1 predicted.
- §1.1's headline defect is fixed: `AppContainer.current.authRepository` is now a real
  `FirebaseAuthRepository` in a running app, and neither auth screen can reach Home without Firebase
  agreeing first.

---

## 1. Verified current state

### 1.1 The blocking defect: the Firebase layer is written but disconnected

Roughly 60% of the auth layer exists and is decent code. **None of it runs.** Four independent
facts, each verified:

1. **`OmniApplication` does not exist.** `AppContainer`'s KDoc says *"`init` runs once from
   `com.example.omni.OmniApplication`"*, but no such file is on disk. `grep -r OmniApplication`
   matches only that comment.
2. **`AndroidManifest.xml`'s `<application>` tag has no `android:name`.** Even if the class existed,
   Android would not load it.
3. **Therefore `AppContainer.init()` is never called**, so `AppContainer.current` always falls
   through to the lazy `preview` container backed by `PreviewAuthRepository`. `FirebaseAuthRepository`
   is never constructed. No Firebase call is ever made.
4. **`SignInViewModel` and `SignUpViewModel` are dead code.** `grep` for `viewModel(` and
   `AppContainer.factory` across `ui/` matches only the ViewModel files themselves. No screen
   instantiates either one.

`MainActivity` closes the loop by navigating on a bare tap:

```kotlin
SignUpScreen(onCreateAccount = { screen = AppScreen.Home })   // no auth call
SignInScreen(onSignIn      = { screen = AppScreen.Home })     // no auth call
```

**Consequence:** the app today lets anyone into `Home` by tapping a button, with no account, no
session and no network. This is the single highest-priority fix and it is about 40 lines of work
(§5).

### 1.2 The manifest declares no permissions at all

Not even `INTERNET`. Firestore, Storage, Places and FCM all need it. `ACTIVITY_RECOGNITION`
(steps) and the location permissions (SOS) are also absent.

### 1.3 The screens are pure and callback-driven — which is good news

Every screen takes callbacks that default to `{}`, and `MainActivity` passes only
`onNavigate`. So `onExploreFirstAid`, `onAddGlass`, `onCompose`, `onSearch`, `onFindRoute`,
`onCallHospital`, `onLogFood` and all six `SettingScreen` callbacks are currently no-ops. (All are
zero-argument except `SettingScreen.onAccountAction: (String) -> Unit`; `WaterCard.onAdd` is the one
required parameter in the codebase, supplied by `HomeScreen`, which itself receives nothing.)

That is exactly the seam we want. The screens hold no Firebase knowledge, so backend wiring is
mostly *widening callback signatures and adding data parameters* — not restructuring. `DesignFrame`,
the enum navigation and the per-screen bottom bar all stay as `UI_ARCHITECTURE.md` requires.

### 1.4 Configuration gaps

- `app/google-services.json` — project `omni-2c987`, package `com.example.omni`, bucket
  `omni-2c987.firebasestorage.app`. **`oauth_client` is empty**, so no SHA-1 is registered and
  Google Sign-In cannot work. `AuthFooter` already exposes `onGoogle` and `onFacebook` callbacks
  that nothing can currently satisfy.
- **`google-services.json` is not in `.gitignore`.** It is merely untracked. A `git add .` will
  commit the API key. Add it to `.gitignore` before the next commit, or accept it (it is a client
  key, but it should still be key-restricted in the Google Cloud console).
- `local.properties` **is** ignored (twice, in fact) — that is where the Places/Maps key goes.
- Absent dependencies: no Coil (so **no image can be loaded from a URL anywhere in the app** —
  avatars and feed images are all local drawables), no Play Services Location, no Maps SDK, no FCM,
  no DataStore, no Room, no WorkManager, no Functions SDK.
- The build has **no `org.jetbrains.kotlin.android` plugin** — AGP 9.1 supplies Kotlin itself.
  Anything needing KSP (Room, Moshi codegen) is therefore a build-configuration risk. §6 avoids KSP
  entirely.

### 1.5 Minor but user-visible

`SignInScreen`'s primary button reads **"Sing In"** (`AuthPrimaryButton(label = "Sing In", …)`).
Worth fixing while you are in the file.

---

## 2. Three conflicts with `IMPLEMENTATION_PLAN.md`

**(a) Its §0 status block is stale.** It states there is "no Firebase, no ViewModels, no repository
pattern, no DI… no google-services config". All four are false — every one of those exists on disk.
Anyone following that section will rebuild what is already there. Replace it with §1 above.

**(b) Hospitals: seeded Firestore vs. Places.** Phase 8 of that plan chose a hand-seeded Firestore
`hospitals` collection and says the Places API was *"avoided entirely"*. You have since chosen
**Google Places + Directions**. This plan follows your choice, with one non-negotiable addition:
Places requires network, and SOS is an emergency feature. §11 therefore mandates a local
last-known-good cache so the screen still shows something with no signal.

**(c) Messaging does not appear in that plan at all.** You explicitly asked for a "message option
for connecting people" in the top bar. It is in neither the plan, the code, nor the Figma prototype.
It is scoped fresh as **Phase 11** in §11.

---

## 3. Design/data mismatches to settle *before* binding live data

These are not bugs in the code — the code faithfully reproduces Figma. They are places where the
design's own numbers disagree with each other, so "bind the real value" has no single correct
answer until you decide.

### 3.1 The water card has 8 glass icons but a goal of 12

`WaterCard` draws `WaterGlassRow(filled = 3)` twice — two rows of four, **8 icons** — while the copy
says `" 7"` + `"/12 glasses"` and `"keep going only 5 glasses left"`. Three numbers, three different
stories (3 filled of 8 drawn, 7 of 12 claimed).

A third row will not fit: the band sits at y=93 and the card is 167 tall, leaving 62dp, and three
rows need 26×3 + 10×2 = 98dp.

**Recommendation: make the goal 8 and keep 8 icons.** Your own words were *"if user click the plus
the water intake of one glass is complete and status should shown in the card with the water glass
already have"* — that describes a **1:1 mapping between one tap and one icon**, which only works
when `goal == iconCount`. Eight 250ml glasses is 2L, a legitimate target. No layout change, no
rounding, and the card becomes self-consistent.

Store `goals.water` per user anyway, and render
`filled = ((glasses.toFloat() / goal) * 8).roundToInt().coerceIn(0, 8)` so a user who edits their
goal in Settings still gets a sensible bar. At `goal = 8` that expression is exactly 1:1.

### 3.2 The steps card's percentage does not match its own numbers

It shows `5,600` `/10,000` and *"You're **78%** towards your daily goal"*. 5600 / 10000 = **56%**.
Compute the percentage from the values; do not port the 78.

### 3.3 The three "updates" bars use absolute Figma weights, not a fraction

`UpdateProgress(trackHeight, lead, fill, tail)` is called with hand-placed weights:

| Card | lead | fill | tail | sum |
|---|---|---|---|---|
| Fiber | 155.5144 | 72.6416 | 125.8440 | 354 |
| Sleep | 196.4393 | 72.6416 | 84.9191 | 354 |
| CPR | 0 | 72.6416 | 281.3584 | 354 |

`fill` is **identical in all three**, and the CPR card's `tail` (281.3584) is exactly
`354 − 72.6416`. So the design is a **fixed-width gradient puck that slides along a 354 track**, and
CPR sits at position 0 — which is why a "0%" card still shows a coloured band. That is intended, not
a bug.

The generalisation is therefore:

```kotlin
private const val Track = 354f
private const val Puck  = 72.6416f          // the gradient segment, constant by design
// progress in 0f..1f
val lead = progress.coerceIn(0f, 1f) * (Track - Puck)
UpdateProgress(trackHeight, lead = lead, fill = Puck, tail = (Track - Puck) - lead)
```

This reproduces the CPR card **exactly** and the fiber card to within 3% (155.51 vs. 163.5 for
18/31g). The sleep card is the outlier: 6.5/8h = 81.25% wants lead 228.6, but Figma drew 196.4 — so
adopting the formula moves that bar right by about 9% of the track. That is the design correcting
itself, and it is worth taking; three bars computed by one rule beat three hand-placed bars that
each tell a different story.

The axis marker triangle must be positioned by the same `progress` value.

### 3.4 The nutrition calorie gauge is a static image

`ic_nutri_arc_progress` is a baked drawable showing 80%. It cannot represent a real number. Making
it live means replacing it with a `Canvas` arc — a UI task governed by `UI_ARCHITECTURE.md` §5/§6.
Deferred to Phase 6; until then the gauge stays static and the numeric labels around it go live
first.

### 3.5 Fiber has no source, and sleep has no input at all

The fiber card needs grams of fibre, but `FoodEntry` carries only `calories, protein, carbs, fat` —
**no fibre field**. Add it to the meal model (§7).

Sleep is worse: **nothing in the app captures sleep.** No sensor, no screen, no field. The card is
decorative. Options are to add a small manual-entry sheet (recommended — tapping the sleep card
opens it) or to drop the card. Anything "automatic" would need Health Connect, which is a separate
integration and out of scope for this term.

### 3.6 Things the header promises but nothing implements

In `OmniChrome.kt`, the avatar **is** clickable (`onProfileClick` → Settings), but the **Messages
icon and the Notifications bell have no `clickable` modifier at all**, no destination screens, and
no counts. `"Good morning!"` is a hardcoded literal, not derived from the clock, and `"Sayed Mahir"`
is hardcoded — as is `"Welcome back Mahir"` in `HomeGreeting()` and the name/email in
`SettingScreen`.

The Figma prototype recording contains **no messages screen and no notifications screen**. Both
features need design before implementation.

---

## 4. The binding contract — rules for every wiring change below

1. **Screens never import Firebase.** A screen takes plain data (`Int`, `String`, an immutable UI
   model) and emits callbacks. Repositories are the only Firebase-aware layer.
2. **Every new parameter gets a default** that reproduces today's hardcoded value. This keeps
   `@DevicePreviews` and every existing call site compiling, and lets you wire one screen at a time.
3. **ViewModels are created in `MainActivity`** via `viewModel(factory = AppContainer.factory())`
   and passed down as data + lambdas. This preserves `UI_ARCHITECTURE.md` §2a's
   `Crossfade → when(screen) → screen` shape exactly; do not introduce nav-graph route wrappers.
4. **Register every new ViewModel in `AppContainer.factory()`** — it throws
   `IllegalArgumentException` for unknown classes by design.
5. **No new scaling system, no `LocalDensity` override, no `fontScale` change** (`UI_ARCHITECTURE.md`
   §3, §4, §9). Live data is often *longer* than mock data — "1,234,567 steps" is wider than
   "5,600". Every field bound below must be length-clamped or given a `maxLines`-safe box.
6. **Firestore offline persistence is on by default** on Android. Writes queue and replay. Do not
   build a custom sync queue.
7. **Counters use `FieldValue.increment`**, never read-modify-write.
8. **Dates are `yyyy-MM-dd` in the device's local zone**, used directly as document IDs.

---

## 5. Step 0 — unblock the app (do this first, it is ~40 lines)

Nothing else in this plan can be tested until `FirebaseAuthRepository` actually runs.

**Create `app/src/main/java/com/example/omni/OmniApplication.kt`:**

```kotlin
package com.example.omni

import android.app.Application
import com.example.omni.di.AppContainer

class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
```

**Edit `AndroidManifest.xml`** — add the permission and point `<application>` at the class:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:name=".OmniApplication"
    ... >
```

**Verify before moving on.** Add a temporary log in `AppContainer.init` and confirm
`AppContainer.current.authRepository` is a `FirebaseAuthRepository`, not the preview fake. If it is
still the fake, the manifest edit did not take.

---

## 6. Dependencies to add

Add to `gradle/libs.versions.toml` and `app/build.gradle.kts`. **Do not paste version numbers from
memory** — this project is on AGP 9.1 / Kotlin 2.4.10 / compileSdk 37, which is newer than most
published examples. Pin each from a real Gradle sync and confirm it resolves.

| Coordinate | Phase | Why |
|---|---|---|
| `androidx.datastore:datastore-preferences` | 1 | Onboarding-seen flag, step-counter baseline. **No KSP.** |
| `io.coil-kt.coil3:coil-compose` + `coil-network-okhttp` | 2 | Nothing in the app can load a remote image today. Needed for avatars and feed photos. |
| `com.google.android.gms:play-services-location` | 9 | Fused location for SOS. |
| `com.google.android.libraries.places:places` | 9 | Nearby hospital search. |
| `com.google.maps.android:maps-compose` + `play-services-maps` | 9 | Replaces the static `map_sos` / `map_hospitals` bitmaps. |
| `com.google.firebase:firebase-messaging` (BOM-managed) | 10 | Push. |
| `com.google.firebase:firebase-functions` (BOM-managed) | 12 | Callable functions from the client. |

**Deliberately excluded:**

- **Room.** `IMPLEMENTATION_PLAN.md` Phase 7 proposed it for offline first-aid guides. It needs KSP,
  which is the riskiest thing to add to this AGP 9 build, and it buys nothing here: bundling
  `app/src/main/assets/guides.json` and parsing it with `kotlinx.serialization` (or
  `org.json`, already on the platform) is **100% offline by construction** and searchable in memory
  across ~12 guides. Use assets.
- **Hilt/Koin.** `AppContainer` already works. Adding a DI framework mid-project is pure risk.
- **navigation-compose.** Forbidden by `UI_ARCHITECTURE.md` §2a.
- **WorkManager.** Firestore's own offline queue covers the write-retry case.

---

## 7. Firestore data model

Design rules: one document per user per day (cheap reads, natural offline unit); denormalise author
identity onto posts (a feed page then costs one query, not N+1); counters via `increment`.

```
users/{uid}
  name, email, role, profession?, verified, photoUrl?, headline?, bio?
  createdAt, updatedAt
  goals:   { water: 8, steps: 10000, sleepHours: 8.0, fiberGrams: 31, calories: 2000 }
  prefs:   { pushNotifications: true, offlineCache: true }
  unread:  { messages: 0, notifications: 0 }        // maintained by Cloud Functions
  fcmTokens: [ "<token>", … ]

users/{uid}/days/{yyyy-MM-dd}
  date, waterGlasses, steps, sleepHours
  calories, protein, carbs, fat, fiber                // rolled up from meals
  updatedAt

users/{uid}/days/{yyyy-MM-dd}/meals/{mealId}
  name, slot(breakfast|lunch|dinner|snack)
  calories, protein, carbs, fat, fiber, loggedAt

users/{uid}/guideProgress/{guideId}
  completedSteps: [Int], percent: Int, updatedAt

users/{uid}/emergencyContacts/{contactId}
  name, phone, relation

users/{uid}/following/{targetUid}
  createdAt

posts/{postId}
  authorId, authorName, authorPhotoUrl, authorVerified, authorProfession
  body, imageUrl?, createdAt
  likeCount, commentCount, repostCount

posts/{postId}/likes/{uid}                 -> { createdAt }
posts/{postId}/comments/{commentId}        -> { authorId, authorName, authorPhotoUrl, body, createdAt }

verificationRequests/{uid}
  uid, name, profession, licenseNumber, certificateUrl
  status(pending|approved|rejected), submittedAt, reviewedBy?, reviewedAt?, note?

notifications/{uid}/items/{notificationId}
  type, title, body, deeplink?, read, createdAt

conversations/{conversationId}             // id = the two uids sorted and joined with "_"
  participants: [uidA, uidB], participantNames: {…}, participantPhotos: {…}
  lastMessage, lastMessageAt, lastSenderId
  unread: { uidA: 0, uidB: 0 }

conversations/{conversationId}/messages/{messageId}
  senderId, text, createdAt, readBy: [uid]

sosEvents/{uid}/items/{eventId}
  lat, lng, accuracy, createdAt, resolvedAt?
```

**Storage layout** (bucket `omni-2c987.firebasestorage.app`):

```
avatars/{uid}.jpg                          public read, owner write
posts/{uid}/{postId}.jpg                   public read, owner write
certificates/{uid}/{filename}              NO public read — admin + owner only
```

**Composite indexes to create:** `posts` ordered by `createdAt desc` (single-field, automatic);
`conversations` where `participants array-contains uid` ordered by `lastMessageAt desc` (**this one
needs a composite index** — create it from the console link Firestore prints on first failure).

### The "Following" tab, honestly

The feed's segmented control currently only recolours tabs — it does not filter. A true
following-feed needs either `whereIn` (capped at 30 authors) or a fan-out write to per-user feed
documents. For a term project, **filter the already-loaded page client-side against the local
following set** and document the limitation: if you follow someone who has not posted in the last
page, their posts will not appear until you scroll. Do not build fan-out.

---

## 8. Security rules

`firestore.rules` — start restrictive, loosen only with a reason.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn()   { return request.auth != null; }
    function isSelf(uid)  { return signedIn() && request.auth.uid == uid; }
    function isAdmin()    { return signedIn() && request.auth.token.admin == true; }
    function isVerified() { return signedIn() && request.auth.token.verified == true; }

    match /users/{uid} {
      allow read: if signedIn();
      allow create: if isSelf(uid);
      // A user may never grant themselves role, verification or unread counts.
      allow update: if isSelf(uid)
                    && !request.resource.data.diff(resource.data)
                         .affectedKeys()
                         .hasAny(['role','verified','profession','unread']);
      allow update: if isAdmin();
      allow delete: if false;

      match /days/{day}          { allow read, write: if isSelf(uid); }
      match /days/{day}/meals/{m}{ allow read, write: if isSelf(uid); }
      match /guideProgress/{g}   { allow read, write: if isSelf(uid); }
      match /emergencyContacts/{c}{ allow read, write: if isSelf(uid); }
      match /following/{target}  { allow read: if signedIn(); allow write: if isSelf(uid); }
    }

    match /posts/{postId} {
      allow read: if signedIn();
      allow create: if signedIn()
                    && request.resource.data.authorId == request.auth.uid
                    && request.resource.data.likeCount == 0
                    && request.resource.data.commentCount == 0
                    && request.resource.data.body.size() <= 2000;
      // The author may edit body/imageUrl; anyone signed in may move the counters only.
      allow update: if signedIn()
                    && request.resource.data.diff(resource.data).affectedKeys()
                         .hasOnly(['likeCount','commentCount','repostCount']);
      allow update, delete: if signedIn() && resource.data.authorId == request.auth.uid;
      allow delete: if isAdmin();

      match /likes/{uid} {
        allow read: if signedIn();
        allow write: if isSelf(uid);
      }
      match /comments/{commentId} {
        allow read: if signedIn();
        allow create: if signedIn() && request.resource.data.authorId == request.auth.uid;
        allow update, delete: if signedIn() && resource.data.authorId == request.auth.uid;
        allow delete: if isAdmin();
      }
    }

    match /verificationRequests/{uid} {
      allow read: if isSelf(uid) || isAdmin();
      // The applicant submits; only an admin may set status.
      allow create: if isSelf(uid) && request.resource.data.status == 'pending';
      allow update: if isAdmin();
      allow delete: if isAdmin();
    }

    match /notifications/{uid}/items/{id} {
      allow read: if isSelf(uid);
      allow update: if isSelf(uid)
                    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['read']);
      allow create, delete: if false;          // Cloud Functions only (they bypass rules)
    }

    match /conversations/{cid} {
      allow read:   if signedIn() && request.auth.uid in resource.data.participants;
      allow create: if signedIn() && request.auth.uid in request.resource.data.participants
                    && request.resource.data.participants.size() == 2;
      allow update: if signedIn() && request.auth.uid in resource.data.participants;

      match /messages/{mid} {
        allow read: if signedIn()
                    && request.auth.uid in get(/databases/$(database)/documents/conversations/$(cid))
                         .data.participants;
        allow create: if signedIn() && request.resource.data.senderId == request.auth.uid;
        allow update, delete: if false;
      }
    }

    match /sosEvents/{uid}/items/{id} { allow read, write: if isSelf(uid); allow read: if isAdmin(); }
  }
}
```

**Known trade-off, stated openly:** the `posts` counter rule lets any signed-in user increment
`likeCount` by any amount. Rules cannot cheaply verify "+1 and a matching like document". The
airtight version moves counters into a Cloud Function (write the like doc, let the trigger update
the count). Given you are on Blaze anyway, **prefer the function** — it is listed in §9. If you keep
client increments for speed of delivery, write it down as a known limitation in your report.

`storage.rules`:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /avatars/{uid} {
      allow read: if true;
      allow write: if request.auth.uid == uid
                   && request.resource.size < 5 * 1024 * 1024
                   && request.resource.contentType.matches('image/.*');
    }
    match /posts/{uid}/{file} {
      allow read: if true;
      allow write: if request.auth.uid == uid
                   && request.resource.size < 8 * 1024 * 1024
                   && request.resource.contentType.matches('image/.*');
    }
    match /certificates/{uid}/{file} {
      allow read:  if request.auth.uid == uid || request.auth.token.admin == true;
      allow write: if request.auth.uid == uid && request.resource.size < 10 * 1024 * 1024;
    }
  }
}
```

---

## 9. Cloud Functions (Blaze required — keep the list short)

| Function | Trigger | Job |
|---|---|---|
| `onVerificationDecision` | `verificationRequests/{uid}` updated | On `approved`: set `users/{uid}.verified = true`, `role = PROFESSIONAL`, `profession`; set custom claims `{verified: true, profession}`; write a notification; push. On `rejected`: notification only. |
| `onLikeWritten` | `posts/{postId}/likes/{uid}` created/deleted | `increment(±1)` on `likeCount`; notify the author on create (skip self-likes). |
| `onCommentCreated` | `posts/{postId}/comments/{id}` created | `increment(+1)` on `commentCount`; notify the author. |
| `onMessageCreated` | `conversations/{cid}/messages/{id}` created | Update `lastMessage`/`lastMessageAt`; `increment(+1)` the recipient's `unread`; push. |
| `grantAdmin` | HTTPS callable, one-time | Sets `{admin: true}` on a uid. Guard it: check a hardcoded bootstrap uid or delete the function after use. |

**Custom claims gotcha:** a claim change does not reach a signed-in client until its ID token
refreshes. After approval, the client must call `user.getIdToken(true)` — trigger this from the
in-app notification handler, or accept up to a one-hour delay. Test this explicitly; it is the most
common "it works in the console but not in the app" bug in verification flows.

---

## 10. Build order

Ordered so each phase is independently demonstrable, and so the three functions you named by name
land early.

| # | Phase | Depends on | Rough size |
|---|---|---|---|
| ~~**0**~~ | ~~Unblock (`OmniApplication`, manifest)~~ — **done** | — | 40 lines |
| ~~**1**~~ | ~~Auth for real: gate, errors, sign-out, onboarding flag~~ — **done in code** | 0 | 1 day |
| ~~**2**~~ | ~~Profile + header binding (name, avatar, greeting)~~ — **done in code** | 1 | 1 day |
| ~~**3**~~ | ~~**Water card + plus button**~~ — **done in code** | 2 | 1 day |
| ~~**4**~~ | ~~**Step counter from the device sensor**~~ — **done in code** | 3 | 1–2 days |
| ~~**5**~~ | ~~**Updates & recommendations**~~ — **done in code** | 3, 4 | 1 day |
| ~~**6**~~ | ~~Nutrition: meals, macros, week strip~~ — **done in code** | 5 | 2–3 days |
| **7** | First-aid guides, 100% offline | 2 | 2 days |
| **8** | Feed: read, like, comment, compose | 2 | 3–4 days |
| **9** | SOS: location, Places, map, call, route | 2 | 3–4 days |
| **10** | Notifications + FCM (**bell icon**) | 8 | 2 days |
| **11** | Messaging (**message icon**) — needs design first | 10 | 3–4 days |
| **12** | Verification + admin + Cloud Functions | 2 | 3 days |
| ~~**13**~~ | ~~Rules hardening, offline QA, tests~~ — **done in code**; 61 rules tests + 42 JVM tests | all | 2 days |

---

## 11. Phase specifications

Each phase gives: the current signature, the target signature, the backend, and what "done" means.

### Phase 1 — Auth, for real

**Files to create:** `data/repo/UserRepository.kt`, `data/local/PreferencesStore.kt`.
**Files to edit:** `MainActivity.kt`, `SignInScreen.kt`, `SignUpScreen.kt`, `AppContainer.kt`.

The screens already hold their own field state (`var email by remember { … }`). Keep that. Widen the
callbacks so the values escape:

```kotlin
// SignInScreen.kt
fun SignInScreen(
    onSignUp: () -> Unit = {},
    onSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: (email: String) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
)

// SignUpScreen.kt
fun SignUpScreen(
    onSignIn: () -> Unit = {},
    onCreateAccount: (name: String, email: String, password: String, confirm: String) -> Unit =
        { _, _, _, _ -> },
    isLoading: Boolean = false,
    errorMessage: String? = null,
)
```

`SignInViewModel.signIn` and `SignUpViewModel.signUp` already take exactly these parameters and
already expose `isLoading`/`errorMessage` in their `UiState`. They need no changes at all — they were
written for this and never plugged in. Better still, both already carry a trailing
`onSuccess: () -> Unit = {}`, which is precisely the navigation hook: pass
`{ screen = AppScreen.Home }` there and navigation happens **only** after Firebase confirms, which
is the actual bug in §1.1.

**`MainActivity` becomes the auth gate.** `FirebaseAuthRepository.authState` starts at `LOADING` and
flips when the `AuthStateListener` fires, so a naive gate flashes Onboarding for one frame on every
cold start. Handle all three states:

```kotlin
val authState by AppContainer.current.authRepository.authState.collectAsStateWithLifecycle()
when (authState) {
    AuthState.LOADING         -> SplashScreen()          // new, minimal, inside DesignFrame
    AuthState.UNAUTHENTICATED -> if (screen in EntryScreens) … else screen = AppScreen.Onboarding
    AuthState.AUTHENTICATED   -> if (screen in EntryScreens) screen = AppScreen.Home
}
```

**Error and loading UI does not exist yet.** Neither auth screen has anywhere to put a message.
`UI_ARCHITECTURE.md` §4 pins `fontScale = 1f` and the layouts are pixel-locked, so a message that
appears from nothing will shove the button down. **Reserve a fixed-height slot** (e.g. a 20dp `Box`
above `AuthPrimaryButton` that is empty when `errorMessage == null`) rather than conditionally
inserting a composable. For loading, swap the button label for a small indicator inside the same
34dp box — do not resize the button.

Also in this phase: `PreferencesStore` with an `onboardingSeen` flag so returning users skip the
carousel, and `SettingScreen`'s `onLogOut` wired to `authRepository.signOut()`.

**Done when:** a real Firebase account is created and visible in the console; force-quitting and
reopening lands on Home without re-authenticating; a wrong password shows the mapped message from
`firebaseAuthErrorMessage`; airplane mode shows the network message rather than hanging.

**Not in this phase:** Google and Facebook sign-in. `AuthFooter` exposes `onGoogle`/`onFacebook`, but
`google-services.json` has an empty `oauth_client`. To enable Google later you must register the
debug **and** release SHA-1 in the Firebase console and re-download the file. Facebook needs its own
SDK and a Meta app. Leave both as no-ops and say so in your report.

### Phase 2 — Profile and the shared header

**Files:** `data/repo/UserRepository.kt` (a `Flow<User?>` snapshot listener on `users/{uid}`),
`ui/components/OmniChrome.kt`, `ui/home/HomeScreen.kt`, `ui/settings/SettingScreen.kt`.

```kotlin
fun OmniHeader(
    modifier: Modifier = Modifier,
    userName: String = "Sayed Mahir",
    photoUrl: String? = null,
    greeting: String = "Good morning!",
    unreadMessages: Int = 0,
    unreadNotifications: Int = 0,
    onProfileClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
)
```

Add `.clickable` to the messages and bell icons — they have none today. Until Phases 10 and 11 land,
point them at a neutral stub screen carrying its own tab, per `UI_ARCHITECTURE.md` §2a rule 4
("never a stand-in screen, and never a no-op tap").

`greeting` is derived from the clock, not stored: morning < 12, afternoon < 17, evening otherwise.

The avatar is `R.drawable.home_avatar` today. Remote photos need **Coil** (§6) — use
`AsyncImage(model = photoUrl, placeholder = painterResource(R.drawable.home_avatar))` so the
fallback is the current asset and the layout never changes size.

`HomeGreeting()` hardcodes `"Welcome back Mahir"`; take a `name` parameter. **Clamp it** — the
greeting box is fixed-width and a long display name will clip. First name only, `maxLines = 1`.

**Done when:** the name shown on Home, in the header and in Settings all come from
`users/{uid}`; editing the document in the Firebase console updates the UI live without a restart.

### Phase 3 — The water card and its plus button

*Your first named function.* Settle §3.1 before you start; the target below assumes goal = 8.

**Files:** `data/model/DailyMetrics.kt`, `data/repo/MetricsRepository.kt`,
`ui/home/HomeViewModel.kt`, `ui/home/HomeBento.kt`, `ui/home/HomeScreen.kt`.

```kotlin
internal fun WaterCard(
    glasses: Int = 7,
    goal: Int = 8,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Inside, replace the two literal `WaterGlassRow(filled = 3)` / `WaterGlassRow(filled = 0)` calls:

```kotlin
val filledIcons = ((glasses.toFloat() / goal.coerceAtLeast(1)) * 8f).roundToInt().coerceIn(0, 8)
WaterGlassRow(filled = filledIcons.coerceAtMost(4))
WaterGlassRow(filled = (filledIcons - 4).coerceIn(0, 4))
```

The three text lines all become computed. Watch the hint line — the file's own KDoc records that
`"keep going only 5 glasses left"` already overflows its Figma box at 146.86dp against a 142dp
source, and only fits because the end inset was dropped to 2dp. So the live string has **almost no
slack**. Handle the boundary cases explicitly and keep every variant no longer than the original:

- `remaining > 0` → `"keep going only N glasses left"` (and `"1 glass left"`, singular)
- `remaining <= 0` → `"goal reached, nice work"` — shorter than the original, so it is safe

**Write path.** The tap must feel instant and survive being offline:

```kotlin
suspend fun addGlass(uid: String, date: String) {
    firestore.document("users/$uid/days/$date")
        .set(mapOf(
            "date" to date,
            "waterGlasses" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp(),
        ), SetOptions.merge())
}
```

`increment` on a merge-set creates the document if it is missing, so there is no
read-then-write race and no separate "create today's doc" step. Firestore's local cache applies the
increment immediately and the snapshot listener re-renders before the network round-trip — the card
updates instantly, offline included.

**Edge cases:** guard against double-taps inflating the count (debounce the click or disable it
briefly); decide whether tapping past the goal keeps counting (recommended: yes, cap the *display*
at 8 filled icons but keep the true number); and handle the date rolling over at midnight while the
app is open — recompute `date` on each write rather than caching it in the ViewModel.

**Done when:** tapping the plus fills one more glass icon immediately, the count survives a restart,
and tapping it in airplane mode still updates the card and syncs when the network returns.

### Phase 4 — Steps from the device sensor

**Files:** `data/local/StepCounterSource.kt`, `data/local/PreferencesStore.kt` (extend),
`ui/home/HomeViewModel.kt`, `ui/home/HomeBento.kt`.

```kotlin
internal fun StepsCard(
    steps: Int = 5600,
    goal: Int = 10000,
    modifier: Modifier = Modifier,
)
```

Format with grouping separators to match the design's `"5,600"` / `"/10,000"`, and compute the
percentage rather than porting the design's incorrect 78 (§3.2). The hint box is 110dp for three
lines — verify the longest realistic string (`"You're 100% towards your daily goal,just keep
moving"`) still breaks into three lines and does not clip.

**Manifest:** `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />`. It is a
runtime permission on API 29+; `minSdk` is 26, so request it at runtime and handle refusal by
showing the goal with 0 steps plus a tap-to-enable affordance — never crash, never show a fake
number.

**The reboot problem.** `Sensor.TYPE_STEP_COUNTER` reports steps **since the device last booted**, as
a monotonically rising float that resets to 0 on reboot. Today's step count is therefore not the
sensor value. Track a baseline:

```kotlin
// Approximate boot time; stable within a boot session, changes across reboots.
val bootId = System.currentTimeMillis() - SystemClock.elapsedRealtime()

fun onSensorChanged(raw: Int) {
    if (savedBootId == null || abs(bootId - savedBootId) > REBOOT_TOLERANCE_MS) {
        savedBootId = bootId
        baseline = 0            // the counter restarted at 0
        carried = todaySteps    // keep what we already banked today
    }
    if (raw < baseline) baseline = 0                    // defensive
    if (savedDate != today) { carried = 0; baseline = raw; savedDate = today }
    todaySteps = carried + (raw - baseline)
}
```

Persist `savedBootId`, `baseline`, `carried` and `savedDate` in **DataStore**, not memory — the
process dies constantly. Allow a tolerance of a few seconds on `bootId` because
`elapsedRealtime()` and wall-clock drift slightly.

**Sync policy.** Do not write to Firestore on every sensor event — that is thousands of writes a
day and real money. Write when the delta exceeds ~250 steps, on `onStop`, and on date rollover.
DataStore is the source of truth during a session; Firestore is the cross-device record.

**Done when:** walking increases the count on Home; the count survives a reboot without resetting to
zero or double-counting; it resets at midnight; and a refused permission degrades gracefully.

Note the honest limitation for your report: without a foreground service the app only observes steps
taken while it is alive. `TYPE_STEP_COUNTER` counts in hardware regardless, so reading the delta on
resume recovers most of it — but steps taken between the last reading and a reboot are lost. That is
acceptable; say so rather than claiming perfect fidelity.

### Phase 5 — Updates and recommendations

*Your third named function.* Adopt the formula in §3.3 first.

**Files:** `domain/Recommendations.kt` (new, pure Kotlin — no Android, no Firebase, so it is unit
testable), `ui/home/HomeUpdates.kt`.

```kotlin
internal fun FiberCard(grams: Float = 18f, goalGrams: Float = 31f, modifier: Modifier = Modifier)
internal fun SleepCard(hours: Float = 6.5f, goalHours: Float = 8f, modifier: Modifier = Modifier)
internal fun CprCard(percent: Int = 0, modifier: Modifier = Modifier)
```

`UpdateProgress` keeps its signature; the call sites compute `lead`/`fill`/`tail` from `progress`.

Status thresholds — one rule for all three cards, in the pure module:

```kotlin
fun statusFor(progress: Float): Status = when {
    progress >= 0.80f -> Status.Good           // green dot
    progress >= 0.40f -> Status.Fair           // amber dot
    else              -> Status.ActionNeeded   // red dot
}
```

Sources: **fiber** rolls up from `days/{date}.fiber` (Phase 6 fills it; until then it reads 0, which
is honest); **sleep** has no input at all (§3.5) — add a small manual-entry sheet opened by tapping
the card, writing `days/{date}.sleepHours`; **CPR** reads
`users/{uid}/guideProgress/cpr.percent` (Phase 7 fills it).

Note the scope boundary: the three cards are **fixed** in the design, not a dynamic list. This phase
binds three fixed cards to live values, which is what "based on their activity and goals" means
here. A ranked pool that swaps which three cards appear is a genuinely different feature — worth
doing later, but it needs new copy and new design, so do not let it creep into this phase.

### Phase 6 — Nutrition

**Files:** `data/model/Meal.kt`, `data/repo/MealRepository.kt`, `ui/nutrition/NutritionViewModel.kt`,
`ui/nutrition/NutritionScreen.kt`, plus a new log-food entry sheet.

Four separate pieces of work, in order:

1. **The week strip.** `Days` is a fixed `S01`–`S07` list. Make it the current week ending today,
   each entry carrying a `LocalDate`. `selectedDay` currently changes nothing — make it drive which
   `days/{date}` document is loaded. Do not let it select a future date.
2. **Macro and activity rings.** `MacroRings` (30g/23g/17g) and `ActivityRings` (`"5.6/10k"`,
   `"7/12gla"`, `"6.5/8h"`) become parameters. Note that the activity ring values are **the same
   three metrics as Home** — one `DailyMetrics` document feeds both screens, so the water goal fix
   from §3.1 applies here too (`"7/12gla"` → `"7/8gla"`).
3. **Food log.** `FoodEntry` gains `id` and — critically — a **`fiber` field**, which it lacks today
   and which the fiber card in Phase 5 depends on. `onLogFood` opens a new entry sheet; rows become
   tappable for edit/delete. Recompute the day's rollup from its meals on every change.
4. **The calorie gauge.** Static drawable (§3.4). Replacing it with a `Canvas` arc is a real UI task
   — read `UI_ARCHITECTURE.md` §5 and §6 first, match the existing arc's stroke width, cap and sweep
   exactly, and check it in `@DevicePreviews` at all four widths. Do this last; the numeric labels
   can go live before the arc does.

**Done when:** logging a meal updates the macros, the calorie number, the day rollup and the fiber
card on Home; switching days shows that day's real data.

### Phase 7 — First-aid guides, 100% offline

**Files:** `app/src/main/assets/guides.json`, `data/repo/GuideRepository.kt`, and two new screens
(guide list, guide detail) — both need design before implementation.

Ship the guide content **in the APK**, not in Firestore. Your brief promises 100% offline access,
and a bundled asset is offline by construction with no cache-warming step, no first-run network
dependency and no failure mode. Firestore would need a successful online fetch before it could ever
work offline — which is exactly wrong for an emergency feature.

`guides.json` carries id, title, category, severity, ordered steps (text + optional image asset +
optional audio asset), and "call an ambulance if…" warnings. Bundle the audio and animation assets
alongside. In-memory search over roughly a dozen guides is instant, which is why §6 drops Room.

The only per-user state is `users/{uid}/guideProgress/{guideId}` — which steps were completed. That
feeds the CPR card in Phase 5 and is the one thing that syncs.

Wire `FirstAidCard`'s existing `onExplore` callback (currently a no-op from `MainActivity`) to the
guide list.

**Done when:** guides open, play audio and record progress with the device in airplane mode from a
fresh install.

### Phase 8 — Feed

**Files:** `data/model/Post.kt`, `data/repo/FeedRepository.kt`, `ui/feed/FeedViewModel.kt`,
`ui/feed/FeedScreen.kt`, plus a new compose-post screen.

`FeedScreen`'s `private data class Post` gains `id`, `authorId`, `createdAt`, `imageUrl`,
`likedByMe`, and — importantly — the fields the design currently hardcodes on **every** post:
`"Verified by omni"` and `"3 min ago"` are literals in the composable, not `Post` fields. Both must
become per-post data, and the timestamp must be a relative formatter over `createdAt`.

Everything on this screen is currently unclickable. There is more missing wiring here than anywhere
else in the app:

| Element | Today | Needs |
|---|---|---|
| `ActionPill` (like / comment / repost) | **No `onClick` parameter at all** | Add one; three distinct actions |
| Header add button | No handler | Opens the compose screen |
| Search pill | No handler | Post/author search |
| Share icon | No handler | Android share sheet |
| Story tiles | Only the share tile calls `onCompose` | Decide the feature or remove it |
| Discover / Following | Recolours tabs only | Actually filter (see §7's honest note) |

Pagination: `orderBy("createdAt", DESCENDING).limit(20)` with `startAfter(lastSnapshot)`. Use a
one-shot `get()` for pages and a snapshot listener only for the first page — a listener over a
growing paginated list re-reads everything and gets expensive fast.

Liking writes `posts/{id}/likes/{uid}` and lets `onLikeWritten` move the counter (§9). Optimistically
flip the local state so the pill responds instantly.

Post images upload to `posts/{uid}/{postId}.jpg`. **Compress before upload** — an unresized modern
phone photo is 3–8MB and will make the feed unusable on Bangladeshi mobile data. Target the longest
edge at 1080px and JPEG quality ~80.

Author name, photo and verified badge are denormalised onto each post (§7). Accept that renaming a
user leaves old posts showing the old name; a fan-out function to fix that is not worth building
here — just note it.

### Phase 9 — SOS

**Files:** `data/repo/LocationRepository.kt`, `data/remote/PlacesDataSource.kt`,
`data/repo/HospitalRepository.kt`, `ui/sos/SosViewModel.kt`, `ui/sos/SosScreen.kt`.

**Manifest:**

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CALL_PHONE" />   <!-- only for ACTION_CALL -->
```

Prefer `ACTION_DIAL` over `ACTION_CALL` — it opens the dialer pre-filled, needs no permission, and
in an emergency the extra tap is a feature, not a cost. Only add `CALL_PHONE` if you truly want
one-tap dialling.

**The API key never goes in the manifest literally.** Put it in `local.properties` (already
gitignored, twice), read it in `build.gradle.kts`, and inject it via `manifestPlaceholders`.
Restrict the key in the Google Cloud console to your package name and both SHA-1 fingerprints, and
enable billing — Places is not free.

**Callback signatures must carry identity.** Today `onFindRoute` and `onCallHospital` take no
arguments, so the screen cannot say *which* hospital:

```kotlin
fun SosScreen(
    onNavigate: (OmniNavItem) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFindRoute: (Hospital) -> Unit = {},
    onCallHospital: (Hospital) -> Unit = {},
    hospitals: List<Hospital> = Hospitals,
    isLocating: Boolean = false,
    onActivate: () -> Unit = {},
    startActivated: Boolean = false,
)
```

`"Found 5 facilities within 5 miles "` is a literal unrelated to the list — bind it to
`hospitals.size` and fix the trailing space. Also switch the unit: the app targets Bangladesh, so
**kilometres, not miles**, throughout this screen.

**Data flow:** fused location → Places Nearby Search (`includedTypes: ["hospital"]`, ranked by
distance) → Place Details for `rating` and `nationalPhoneNumber`. Compute distance with the haversine
formula locally rather than paying for a Distance Matrix call; only call the Routes API for a real
drive time if you decide the "4 mins" label must be accurate, and then only for the top few results.

**Routing:** hand off to Google Maps with a `geo:` or `google.navigation:` intent. It is free,
familiar, and better than anything you would build.

**Offline is mandatory here, not optional.** You chose Places over a seeded collection, which means
zero network gives zero hospitals — in the one feature where that matters most. Cache the last
successful result set (with its timestamp and the location it was fetched from) in DataStore, show
it when offline with a visible "last updated N hours ago" note, and always keep the emergency dial
button working regardless of network state.

**The map itself is two static bitmaps** (`map_sos` / `map_hospitals`). A real map means adding
`maps-compose` — a substantial UI change under `UI_ARCHITECTURE.md`. Sequence it as: bind the
hospital list and the call/route actions first with the static map still in place (which is
demonstrable on its own), then swap in the live map.

**The SOS activation currently reports nothing outward.** Add `onActivate`, and on activation
capture location, write `sosEvents/{uid}/items/{id}`, and notify saved emergency contacts — which is
what `SettingScreen`'s existing `onSavedEmergencies` callback is for.

### Phase 10 — Notifications and FCM

**Files:** `data/repo/NotificationRepository.kt`, `service/OmniMessagingService.kt`, a notifications
screen (**needs design — it does not exist in Figma**).

Register `FirebaseMessagingService` in the manifest; store the FCM token on `users/{uid}.fcmTokens`
and refresh it in `onNewToken`. On API 33+ request `POST_NOTIFICATIONS` at runtime. Create a
notification channel. Honour `users/{uid}.prefs.pushNotifications` — which means `SettingScreen`'s
`pushNotifications` toggle needs an outward callback, since it is local-only state today with no way
to persist.

The bell already has a badge drawable (`ic_home_bell_badge`); bind its visibility to
`users/{uid}.unread.notifications`.

### Phase 11 — Messaging

**This is the largest genuinely new feature, and it has no design.** Nothing exists: not in the
code, not in `IMPLEMENTATION_PLAN.md`, not in the Figma prototype. Two new screens are needed (a
conversation list and a chat thread), and per `UI_ARCHITECTURE.md` §5 they must be designed on the
415 artboard, wrapped in their own `DesignFrame`, and given `@DevicePreviews` before they are built.

Schema is in §7. Conversation IDs are the two uids sorted and joined, so a conversation is
idempotent — opening a chat twice cannot create two threads. `onMessageCreated` (§9) maintains
`lastMessage` and the unread counters and sends the push.

Scope control: text only. No images, no typing indicators, no read receipts beyond a `readBy` array,
no group chats. Those are how messaging features consume an entire term.

Reachability question to settle early: **who can message whom?** Anyone, or only users → verified
professionals? The latter is a better fit for the product (it is a consultation channel, not a
social DM system) and it is much easier to moderate. Decide before writing the rules.

### Phase 12 — Verification and admin

**Files:** `data/repo/VerificationRepository.kt`, an apply-for-verification screen, and the Cloud
Functions in §9.

`SettingScreen.onApplyForVerification` already exists as a no-op. It opens a form: profession
(Doctor / Nutritionist), licence number, and a certificate upload to
`certificates/{uid}/{filename}` — which, per §8, is the one Storage path that is **not** publicly
readable. Writing `verificationRequests/{uid}` with `status: 'pending'` is all the client may do;
rules forbid it from setting any other status.

**There is no admin UI anywhere in the app.** Building an admin console is a whole third app
surface. For a term project, review requests in the **Firebase console** (or a tiny web page) and
let `onVerificationDecision` do the rest. If your team wants an in-app admin screen, treat it as a
stretch goal after Phase 13, not a dependency.

Bootstrap the first admin with the `grantAdmin` callable, then delete or disable it.

### Phase 13 — Hardening

Deploy the rules from §8 and test them with the **Firestore emulator's rules unit tests** — writing
rules without testing them is how projects ship a database that is open to the world. Verify at
minimum: a user cannot set their own `role` or `verified`; a user cannot read another user's `days`;
a non-participant cannot read a conversation; only an admin can change a verification status.

Then: a full offline pass (airplane mode from cold start through every screen), a permission-refusal
pass (deny location, deny activity recognition, deny notifications — none may crash), and unit tests
over the pure modules (`domain/Recommendations.kt`, the step-counter baseline logic, the water
rounding). `PreviewAuthRepository` already exists as the fake for repository-level tests.

Finally, re-run the `UI_ARCHITECTURE.md` §8 checklist on every screen you touched — live data is
longer than mock data, and text clipping is exactly the bug that document was written to prevent.

---

## 12. Risks and open decisions

**Decide before Phase 3:**

| Question | Recommendation |
|---|---|
| Water goal: 8 or 12? (§3.1) | **8** — makes one tap equal one glass icon, which is what you described |
| Distance units on SOS | **Kilometres** — the app targets Bangladesh; the design's "miles" is a template leftover |
| Who can message whom? (Phase 11) | Users → verified professionals only; easier to moderate and truer to the product |
| Counters: client `increment` or Cloud Function? | **Function** — you are on Blaze already, and rules cannot make the client version safe |

**Risks worth watching:**

*Billing.* Places, Directions and Cloud Functions all require Blaze. Set a **budget alert** on the
project on day one. A loop that calls Places on every recomposition is a genuinely expensive
mistake, and the free tier will not protect you.

*Firestore write volume.* The step counter is the danger — every sensor event is a potential write.
The 250-step threshold in Phase 4 is what keeps this in the free tier.

*Build configuration.* AGP 9.1 with built-in Kotlin is new enough that library compatibility is not
guaranteed. Add dependencies **one at a time** and sync after each, so a failure has one obvious
cause. This is also why §6 avoids anything needing KSP.

*Google Sign-In.* Currently impossible (empty `oauth_client`). If the demo needs it, register the
SHA-1s early — re-downloading `google-services.json` at the last minute is a bad afternoon.

*Undesigned surfaces.* Messages, notifications, compose-post, guide list, guide detail, edit
profile, apply-for-verification and the log-food sheet are all **screens that do not exist in Figma**.
That is eight designs. Get them started now in parallel with backend work; they are the most likely
thing to become the critical path.

*Scope.* Thirteen phases is a lot for one term. If time compresses, the defensible cut is Phases
0–9 plus 13 — a complete, secure, offline-capable prevention-and-action app. Messaging, push and
in-app verification are the honest things to defer, and a report that says so deliberately reads far
better than one with three half-finished features.


