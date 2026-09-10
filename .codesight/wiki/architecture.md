# Architecture

> Hand-written supplement to the codesight-generated wiki. codesight's detectors are
> web-framework-oriented; on this Kotlin/Compose app they only find the component inventory
> (see [ui.md](./ui.md)). This article covers what they miss: navigation, DI, repositories,
> data models, and Firestore usage. Re-verify against source before acting on it.

## What Omni is

A health-companion Android app (Jetpack Compose, single `app` module, package
`com.example.omni`) with Firebase Auth + Firestore + Storage + FCM, a MapLibre-based SOS map
with OSRM routing, and offline first-aid guides. UI is a 1:1 build of a Figma design on a
415dp artboard — `UI_ARCHITECTURE.md` (repo root) is the authoritative UI rulebook;
`BACKEND_PLAN.md` and `IMPLEMENTATION_PLAN.md` document the phased backend work.

## Navigation — no navigation library

There is **no** Navigation-Compose/fragment/`NavController`. `MainActivity.kt` is the router:
`OmniApp()` holds a `var screen by remember { mutableStateOf(...) }` of the private enum
`AppScreen` (Splash, Onboarding, SignUp, SignIn, Home, Feed, Sos, Nutrition, Setting,
Messages, Notifications, FirstAid, ComposePost, SavedEmergencies, Goals) and renders one
`Crossfade` over a `when(screen)` — the pattern defined in `UI_ARCHITECTURE.md` §2a.

- The same composable is the **auth gate**: a `LaunchedEffect` reconciles `screen` against
  `authState`/`onboardingSeen` so a signed-in user lands on Home and a signed-out one is
  moved off it.
- Bottom bar tabs: Home, Feed, SOS, Fitness(opens `Nutrition`). The header additionally opens
  Setting, Messages, Notifications. Inner pages: FirstAid, ComposePost, SavedEmergencies,
  Goals.
- `lastTab` is the entire back stack; `BackHandler` sends secondary tabs back to Home.
- ViewModels are constructed per-screen via `viewModel(factory = AppContainer.factory())`
  and their state is handed down as plain values + lambdas — screens never touch Firebase.

## Dependency injection — manual `AppContainer`

`di/AppContainer.kt` is a hand-written singleton container (no Hilt/Koin). `AppContainer.init()`
runs from `OmniApplication`; before init (previews/tests) `AppContainer.current` falls back to
a **preview container** wired with `Preview*Repository` fakes. Every repository with a Firebase
or Android dependency is behind a lazy provider so previews never initialise Firebase.

`AppContainer.factory()` maps every ViewModel class to constructor injection of repositories:

| ViewModel | Repositories |
|---|---|
| SignInViewModel / SignUpViewModel | authRepository |
| SessionViewModel | auth, user, notification, message |
| HomeViewModel | auth, metrics, steps, guideProgress |
| NutritionViewModel | auth, metrics, meal |
| GuidesViewModel | auth, BundledGuideRepository (compiled-in bundle), guideProgress |
| FeedViewModel | auth, feed, media |
| SosViewModel | auth, hospital, location, routing, sos, emergencyContact, notification |
| EmergencyContactsViewModel | auth, emergencyContact |
| GoalsViewModel | auth, user |
| NotificationsViewModel | auth, notification |
| MessagesViewModel | auth, user, message |

## Repository layer — interface + Firestore impl + Preview fake

`data/repo/` follows one pattern per domain: an interface (`UserRepository`), a Firestore
implementation (`FirestoreUserRepository`), and an in-memory preview fake
(`PreviewUserRepository`). Interfaces live in `data/repo/`, data classes in `data/model/`.

Firestore collections (constants in each repo file): `users`, `posts`, `comments`, `likes`,
`meals`, `days`, `conversations`, `messages`, `notifications`, `hospitals`, `sosEvents`,
`emergencyContacts`, `guideProgress`. Most user data is nested under `users/{uid}/...`
(e.g. `metrics`, `meals`, `notifications` subcollections).

Network/routing: `OsrmRoutingRepository(BuildConfig.OSRM_BASE_URL)` — OSRM base URL comes from
`local.properties` (default `https://router.project-osrm.org`), set as a BuildConfig field at
build time. Map tiles are MapLibre (`maplibre.android` dependency).

## Data models (`data/model/`)

`User` (roles USER/PROFESSIONAL/ADMIN, `HealthGoals`, denormalised FCM tokens), `Post` +
`Comment`, `Meal` + `MealSlot` + `DayNutrition`, `DailyMetrics`, `Hospital` + `LatLng` +
`Route`, `Guide` + `GuideStep` + `GuideSeverity` (compiled-in bundle, offline), `Message` +
`Conversation`, `AppNotification` + `NotificationType`, `EmergencyContact`, `AuthState`
(LOADING/AUTHENTICATED/UNAUTHENTICATED).

## Key files

- `MainActivity.kt` — router + auth gate + permission launchers (steps, location, push)
- `di/AppContainer.kt` — DI container, ViewModel factory, preview/real split
- `ui/theme/` — `OmniTheme`, `Color.kt` (OmniInk, OmniCream, OmniLavender, ...),
  `Type.kt` (Plus Jakarta Sans), `HomeType.kt`, `ScreenTypes.kt`
- `ui/components/OmniChrome.kt` — shared header, bottom nav, `OmniNavItem`
- `ui/DevicePreviews.kt` — preview device specs; `ui/DesignFrame.kt` — 415dp design frame
- `service/OmniMessagingService.kt` — FCM
- `data/local/` — `PreferencesStore` (DataStore), `StepCounterSource`, `StepState`
- Root docs: `UI_ARCHITECTURE.md`, `BACKEND_PLAN.md`, `IMPLEMENTATION_PLAN.md`

## Where the UI lives

See [ui.md](./ui.md) for the full 149-composable inventory with parameters. Conventions:
screens in `ui/<feature>/`, one shared chrome in `ui/components/OmniChrome.kt`, design tokens
in `ui/theme/`. Previews use `@DevicePreviews` (in `ui/DevicePreviews.kt`).

## What codesight does not see here (known gaps)

- No routes (no web framework — the single `ACTIVITY /MainActivity` entry is the manifest).
- No schema (Firestore has no ORM declarations to parse).
- No import graph / hot files / blast radius (Kotlin imports are not linked).
- ViewModels/repositories/models are not indexed — only `@Composable` functions are.
