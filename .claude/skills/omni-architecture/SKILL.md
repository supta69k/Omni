---
name: omni-architecture
description: Omni's stable architecture — native Android Kotlin, Jetpack Compose, manual DI, the enum router, the repository triplet pattern, and AuthRepository.sessionUid. Use before creating or editing any ViewModel, repository, model, or navigation code in this project. Takes precedence over generic Android skills for anything Omni-specific.
---

# Omni architecture

Native Android, Kotlin, Jetpack Compose, Firebase Auth + Cloud Firestore, Cloudinary for media,
MapLibre + MapTiler for maps. **Not** KMP, **not** Compose Multiplatform, **not** Hilt, **not** Room,
**not** Retrofit. If a generic Android skill suggests any of those, this file wins.

## Read these first

`CLAUDE.md` → `UI_ARCHITECTURE.md` (before any UI change) → `.codesight/wiki/architecture.md`.
`.codesight/components.md` is the trustworthy composable inventory; ignore its zeros for models,
libraries and import links, and never trust `--blast` on Kotlin (it reports "no dependents" for every
`.kt` file). Grep is the only reliable dependent list here.

## Layers

- `data/model/` — plain Kotlin data classes plus `DocumentSnapshot.toX()` mappers and `toFirestoreMap()`
  builders. Mapping lives with the model, never in a repository or a ViewModel.
- `data/repo/` — an **interface**, a `Firestore*`/`Device*` implementation, and often a `Preview*` fake
  used by `@Preview` and by tests. Adding a data source means adding to this triplet, not beside it.
- `data/local/` — `PreferencesStore` (DataStore) and `StepCounterSource`.
- `ui/<feature>/` — one `Screen.kt` plus one `ViewModel.kt`. ViewModels expose a single
  `StateFlow<XUiState>` built with `combine` + `stateIn(SharingStarted.WhileSubscribed(…))`.

## Dependency injection

Manual, via `di/AppContainer.kt`. No Hilt, no Koin. ViewModels are constructed in
`AppContainer.factory()`'s `when (modelClass)` block and obtained with
`viewModel(factory = AppContainer.factory())`. A `preview` container supplies fakes.
**ViewModels are Activity-scoped**, which is what lets one screen call another's ViewModel (the profile
page's Message button calls `MessagesViewModel.startWith` and then flips the router).

## Navigation

Manual: `Crossfade(tween(200)) → when (screen)` over `private enum class AppScreen` in
`MainActivity.OmniApp()`. **navigation-compose is not used and must not be introduced.** Route arguments
travel as ordinary state beside `screen` — e.g. `profileUid` for `AppScreen.Profile`.

## Session identity — the one rule that matters most

```kotlin
val sessionUid: StateFlow<String?>   // AuthRepository
```

Every **read** keys on `sessionUid`, normally through `flatMapLatest`. `authState` is an enum and cannot
express a session *change* — signing out of A and into B reads `AUTHENTICATED` at both ends, so a
downstream `distinctUntilChanged` erases the transition and every flow stays bound to A's uid. That was a
real cross-account leak. `currentUid` remains correct for an **action** (a tap reads the session as it is
when the finger lands) but must never be a flow key.

## Firestore layout

`users/{uid}` with subcollections `days/{date}` (+ `days/{date}/meals`), `guideProgress`,
`emergencyContacts`, `following/{targetUid}`, `followers/{followerUid}`.
`posts/{postId}` with subcollections `likes/{uid}`, `reposts/{uid}`, `comments/{commentId}`.
`conversations/{cid}` with subcollection `messages/{mid}`; `cid` is `conversationIdOf(a, b)` — the two
uids sorted and joined with `_`, so a pair has exactly one thread.
Also `stories/{id}`, `notifications/{uid}/items/{id}`, `sosEvents/{uid}/items/{id}`,
`verificationRequests/{uid}`, `hospitals/{id}`.

## Constraints that are not negotiable

- **Firebase Blaze is unavailable.** No Cloud Functions, no Firebase Storage, no FCM server sends.
  Denormalised counters are therefore always 0 and are computed by aggregation on the client.
- **Google Maps Platform is permanently ruled out** (billing blocked). MapLibre + MapTiler only; the
  MapTiler key lives in `local.properties` → `BuildConfig.MAPTILER_KEY` and must never be committed.
- **Cloudinary is unsigned upload only** — cloud `g3zuzufg`, preset `omni_mobile`. The API secret must
  never appear in Android code.

## Working rules

Inspect before modifying. Reuse the existing repository, model, ViewModel and composable rather than
adding a parallel one. Do not restructure packages, do not replace working architecture, and do not
convert anything to another DI or navigation library.
