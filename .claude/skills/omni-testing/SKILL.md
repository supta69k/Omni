---
name: omni-testing
description: Omni's verification workflow — the exact Gradle and emulator commands, the JVM test stack and its quirks, the two-account isolation matrix, and the rule that device behaviour is never claimed without a device. Use before claiming any work is complete.
---

# Omni testing

## Commands

```bash
./gradlew.bat :app:compileDebugKotlin      # the primary gate
./gradlew.bat :app:testDebugUnitTest       # JVM unit tests
./gradlew.bat :app:assembleDebug           # packaging; APK in app/build/outputs/apk/debug/
npm --prefix tools run test:rules          # Firestore rules, on the emulator
/e/Android/Sdk/platform-tools/adb.exe devices
```

`compileDebugKotlin` is the standing default the project owner asked for — prefer it over
`assembleDebug` for a quick loop, and run `assembleDebug` when an APK is actually wanted.

Tally results honestly:

```bash
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/*.xml \
  | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {print "tests="t, "skipped="s, "failures="f, "errors="e}'
```

## Test stack and its quirks

`kotlinx-coroutines-test` 1.10.2: `runTest(dispatcher)`, `StandardTestDispatcher`,
`Dispatchers.setMain/resetMain`, `advanceUntilIdle`, and `backgroundScope.launch { flow.collect {} }` to
keep a `WhileSubscribed` flow hot.

`android.util.Log` is stubbed to **throw** in JVM tests; the project handles this globally with
`testOptions { unitTests { isReturnDefaultValues = true } }`. Do not remove it.

Fakes live in `app/src/test/java/.../SocialFakes.kt`. `FakeFeedRepository.lastAuthorIds` is what makes
"Following is a query, not a filter" assertable — assert on what the repository was *asked* for, not
only on what came back.

Fixtures must respect real ordering: the story rail is newest-author-first, playback inside a tile is
oldest-first. A fixture that gets this backwards produces an `IndexOutOfBoundsException` that looks like
a product bug and is not one.

The Firestore emulator is pinned to port **8085**. On Windows the Java child can outlive
`emulators:exec`; recover with `netstat -ano | grep ':8085.*LISTENING'` then `taskkill //PID <pid> //F`.

## Two-account isolation matrix

Run it in both directions, because a leak often only appears on the way back:

```
A signs in  → A's data
sign out
B signs in  → B's data, no trace of A
sign out
A signs in  → A's data again, unchanged
```

Cover every user-scoped surface: steps, water, sleep, meals, goals, posts, comments, likes, follows,
stories, profile, messages and inbox, personal information, auth state. The mechanism that makes this
pass is `AuthRepository.sessionUid` as the flow key — an enum `authState` cannot express a session
change, and a ViewModel that survives the switch will otherwise keep serving the previous account.

Anything persisted on the device must be scoped by **uid *and* date**, never by a single global key.

## What must be tested for each area

- **Comments** — submission writes to Firestore; author resolves from `comment.authorId`; blank rejected.
- **Steps** — same uid+date survives reload; logout/login restores; A and B are separate; a new date
  starts a new record; A→B→A restores A.
- **Profile** — the target uid's identity renders, never the viewer's; loading is a state, not a
  fallback name.
- **Messaging** — the other participant resolves regardless of array order; inbox and header identity.
- **Stories** — active only; multiple per user; Add Story stays reachable; expiry; one tile per author.
- **Goals** — value/unit layout; `+`/`−`; persistence across restart.
- **Fitness** — month selector; a previous month; a selected date loads that date's data.

## Honesty rules

Never say "verified" for something a JVM test cannot see. Run the command, read the output, and report
counts. If a device is not attached, `adb devices` lists nothing and every gesture, IME, density and
sensor behaviour stays **unverified** — list it as device-only rather than implying it passed. Never
claim a feature works when only its UI exists.
