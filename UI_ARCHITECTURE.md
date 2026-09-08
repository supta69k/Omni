# UI_ARCHITECTURE.md — Omni UI rules (single source of truth)

> **READ THIS BEFORE TOUCHING ANY UI.**
> Before you create, edit, redesign, or move **any** UI-related file in this project — a screen, a
> composable, a card, a button, navigation, onboarding, auth, Home, an illustration/image, a
> dimension, a typography style, screen padding, or responsive behavior — you **must** read and
> understand this file first. **Do not start implementing UI changes before reading it.** This applies
> to every developer and every AI agent, on every task.
>
> This file is the **single source of truth** for UI development in Omni. If code and this file appear
> to conflict, **stop and investigate the real architecture** before assuming — do not silently invent
> a new one.

---

## 1. Why this file exists

A responsive bug shipped once and it is worth understanding so it never ships again:

- Some screens were **not** wrapped in `DesignFrame`, and `DesignFrame` used to let the **device
  `fontScale` leak** into a pixel-locked 415dp design.
- Result: the Android Studio Preview (which always renders at `fontScale = 1`) looked perfect, while a
  real phone with slightly enlarged system text **clipped and overlapped** text — `"…5 glasses left"`
  lost `"left"`, tab labels lost their last letter, the steps hint collided with the runner
  illustration.

The root cause was **architectural inconsistency**, not a bad `sp` value. This document locks the
architecture in so the inconsistency cannot recur.

---

## 2. The layout hierarchy

```
MainActivity  (enableEdgeToEdge, setContent)
    │
    └─ OmniTheme                      // Material theme: fixed light color scheme + Typography
         │
         └─ OmniApp()                 // holds `screen`; the session gate reconciles it (§2a rule 6)
              │
              └─ Crossfade(screen)    // the app's top-level navigation + its page transition
                   │
                   └─ when(screen) { … }
                        │
                        └─ Top-level screen  // SplashScreen / OnboardingScreen / SignUpScreen / SignInScreen
                             │              // HomeScreen / FeedScreen / SosScreen / NutritionScreen / SettingScreen
                             └─ DesignFrame { }  // ← THE scaling boundary. Exactly one per screen, at the root.
                                  │
                                  └─ Screen content (Column / Box / verticalScroll, systemBarsPadding, …)
                                       │
                                       └─ Reusable components (cards, fields, nav, bento tiles, …)
                                            // These NEVER create their own DesignFrame or override density.
```

### 2a. Navigation and page transitions

Navigation is a single `AppScreen` enum plus a `when` inside `MainActivity`'s `OmniApp()` — deliberately
no navigation library. Six rules keep it honest:

1. **Pages crossfade; the motion lives in the navbar, not the page.** `Crossfade` fades one whole
   page into the next (`PageFadeMillis`, ~200ms) with no directional slide — an earlier horizontal
   slide fought the bottom bar's morph-in-place animation and was removed. `AppScreen`'s declaration
   order therefore no longer drives any direction, but it is still declared in the order the user
   moves through it (entry flow, then the four bottom-bar destinations in `OmniNavItem`'s own
   left-to-right order, then the three the header opens) so the enum reads as the app's map; adding a
   tab means placing it at the position it occupies in the bar.
2. **Every screen keeps its own `DesignFrame` and its own `OmniBottomNav`.** The transition crossfades
   whole pages, so the bar is redrawn per screen rather than hoisted into a shared scaffold. This is
   what keeps §3's "exactly one frame per screen" rule true — do not hoist the nav without
   revisiting that rule. The bar shows **four** tabs (`Home`, `Feed`, `Sos`, `Fitness`) in a
   `SpaceBetween` row; the selected one becomes a white pill whose label springs out of the icon while
   the siblings reflow, all on a bouncy `spring` (plus a `ripple` on tap) — that overshoot is the
   "fluid" quality, so keep the springs, not linear tweens. The pill wraps its content rather than
   sitting in a fixed cell, which is what lets it grow and the others slide.

   **Measure the track's insets to the icons, not to the cells.** Figma only ever draws this bar with
   `Home` selected, so its literal `start = 4` is a measurement to the *pill*, which carries 19 of its
   own padding — what the design places 23 from the edge is the icon. Hard-coding the 4 leaves the
   bare `Home` icon jammed 4 from the edge in every other state while the far end keeps its 26, and
   selecting the last tab exposes that worst. `OmniBottomNav` therefore derives both insets from
   `NavIconInsetStart`/`NavIconInsetEnd` (23 / 26) and gives back the pill's own padding whenever the
   edge cell *is* the pill, so `Home` still lands on the design's exact 4 / 55 / 55 / 55 / 26 and
   every state keeps the same 23-to-354 band of icon ink. The inset and the pill padding share one
   spring spec (`NavShapeSpring`) so their sum stays constant frame for frame and the outer icons hold
   still through the switch; anything that animates a `Dp` into a `Modifier.padding` must also be
   `coerceAtLeast(0.dp)`, since padding rejects negatives and a bouncy spring undershoots.
3. **Three destinations open from the header, not the bar.** Tapping the `OmniHeader` avatar calls
   `onNavigate(OmniNavItem.Setting)`; its two trailing icons call `onNavigate(OmniNavItem.Messages)`
   and `onNavigate(OmniNavItem.Notifications)`. All three go through the *same* `onNavigate` a screen
   already passes for its tabs, which is why they are `OmniNavItem` members at all: one lambda per
   screen instead of a callback per icon. This keeps the bar to four tabs (more room for the pill
   animation) and matches the reference UX. Those three entries route to real screens — `SettingScreen`
   and two `ComingSoonScreen` stubs — they just are not among the four `NavBarItems` the bar renders, so
   each passes its own bar-absent item as `selected` and lights no pill, which is the honest read: they
   are not bottom-bar destinations.
4. **A nav destination must never route to a different screen than the tab it lights.** Each screen
   hardcodes its own `OmniBottomNav(selected = …)`, so pointing a tab at another screen lights the
   wrong pill. **Figma is not the authority on which pill is lit.** Several frames were duplicated
   from an earlier one and still draw the *previous* screen's selection — both SOS frames draw Home's
   pill. The tab that opens a screen is the tab that lights, whatever the mock shows. A future
   destination that lands before its design does gets a neutral stub carrying its own tab
   (`ComingSoonScreen`) — never a stand-in screen, and never a no-op tap.
5. **Two frames that are two states of one page stay one screen.** SOS is drawn twice in Figma (the
   swipe track, then the hospitals sheet over the same map), and `SosScreen` renders both from one
   internal `activated` flag. Splitting them into two `AppScreen` entries would crossfade the whole
   page between two states of the same page and make the SOS pill fight itself, so a state change
   inside a screen is animated inside that screen.
6. **The session decides the entry screen, and it does so in one place.** `AppScreen.Splash` is the
   value `screen` starts on: it means "not decided yet", because both the Firebase session and the
   DataStore onboarding flag arrive asynchronously and a gate that guessed would flash Onboarding for
   a frame on every cold start of a signed-in app. A single `LaunchedEffect(authState, onboardingSeen)`
   then moves `screen` on or off the `EntryScreens` set. It is keyed on the *session*, never on
   `screen`, which is what lets a successful sign-in navigate straight to Home without the effect
   second-guessing it. Screens never call `signOut` and then navigate themselves — `SettingScreen`
   only clears the session and the gate does the rest, so there is one rule for where a signed-out app
   goes rather than two that can disagree. `SplashScreen` is not in Figma; it is deliberately the
   quietest thing in the app (background plus `AuthLogo`) so the hand-off to sign-in reads as one page.

Key files:

| File | Role |
|------|------|
| `ui/DesignFrame.kt` | The **only** density/font scaling system. `DesignFrame { }` + `DesignFrameWidth = 415.dp`. |
| `ui/DevicePreviews.kt` | The **only** approved multi-width `@Preview` set (`@DevicePreviews`). |
| `ui/SplashScreen.kt` | The launch gate's "not decided yet" page. No design source; keep it minimal. |
| `ui/ComingSoonScreen.kt` | Rule 4's neutral stub: a title, a line of copy, and the caller's own tab. |
| `ui/theme/Type.kt` | `Typography` (Material slots) + font families (`PlusJakartaSans`, `BodyFont`). |
| `ui/theme/HomeType.kt` | `HomeType` — the ~20 named styles the dashboard needs beyond Material's slots. |
| `ui/theme/ScreenTypes.kt` | `FeedType` / `NutritionType` / `MapType` / `SettingsType` — the same contract, per later frame. |
| `ui/theme/Color.kt` | The palette. **Single source of truth for color** — never hardcode hex in a screen. |
| `ui/theme/Theme.kt` | `OmniTheme` — fixed light scheme, dynamic color disabled, no dark theme yet. |
| `ui/auth/AuthCommon.kt` | Shared auth pieces (logo, field, button, footer). |
| `ui/components/OmniChrome.kt` | The shared sticky header + floating bottom bar, `OmniHeaderState` and `OmniNavItem`. |
| `ui/home/HomeScreen.kt`, `HomeBento.kt`, `HomeUpdates.kt` | Home screen + its components. |
| `ui/home/SleepEntrySheet.kt` | The dashboard's only invented UI: a half-hour stepper on a hand-built bottom sheet (§6 rule 11). |
| `domain/Recommendations.kt` | Pure Kotlin, no Compose: the progress-bar formula and the one status-threshold rule the cards share. |
| `ui/feed/FeedScreen.kt`, `ui/sos/SosScreen.kt`, `ui/nutrition/NutritionScreen.kt`, `ui/settings/SettingScreen.kt` | The other four bottom-bar screens. |
| `ui/onboarding/OnboardingScreen.kt`, `OnboardingPage.kt` | Onboarding carousel + page data. |

---

## 3. DesignFrame — how it works and the rules

Every Omni screen is a **415dp Figma artboard** (`iPhone 14 & 15 Pro`), reproduced with Figma's own
coordinates verbatim. A real phone is narrower (~360–412dp). `DesignFrame` reconciles the two by
**scaling density once at the screen root** so the content is *genuinely 415 wide*:

- **Width scaling:** `density = outer.density * (maxWidth / 415.dp)`. At 393dp physical, one design dp
  becomes 393/415 = 0.947 physical dp, so a child laid out at `415.dp` fills the screen and every
  Figma dp/sp is literally correct without translation. This is what makes the app responsive across
  360 / 393 / 412+ **without** per-child tweaks.
- **Density:** overridden **only here**, via `CompositionLocalProvider(LocalDensity provides …)`.
- **Font scaling:** **pinned to `fontScale = 1f`** (see §4).
- **Insets:** `statusBarsPadding` / `systemBarsPadding` are consumed *inside* the frame, so they still
  reserve the correct physical pixels.

### Rules

1. **Every top-level screen must wrap its root in `DesignFrame { }`** — exactly one, at the very top of
   the composable's layout.
2. **Individual screens must NOT create their own density scaling.** Use `DesignFrame`, never a private
   copy.
3. **Reusable components must NOT wrap themselves in `DesignFrame`** and must **NOT** override
   `LocalDensity`. They inherit the frame from the screen that hosts them. A component wrapped in its
   own frame would be scaled twice.
4. **Never create a second, competing responsive/scaling system.** There is one: `DesignFrame`.
5. **Nested components** just use normal Compose layout (`fillMaxWidth`, `weight`, `Arrangement`,
   Figma dp coordinates). Because the frame already made the space 415 wide, Figma's absolute numbers
   are valid inside them.

---

## 4. Font-scale policy (READ BEFORE CHANGING)

`DesignFrame` pins **`fontScale = 1f`**. This is deliberate and keeps Preview, emulator, and physical
device rendering identical.

**The trade-off, stated honestly:** the OS system-wide "font size" accessibility setting does **not**
currently enlarge Omni's text. That is the accepted cost of a pixel-locked design whose text sits in
fixed-width, non-wrapping boxes with almost no slack (e.g. `"Wellness"` fills 67.4 of its 71dp slot).

### The one rule that must never be broken casually

**Do NOT change `fontScale = 1f` back to `fontScale = outer.fontScale` on its own.** Doing that without
first redesigning the layouts will immediately reintroduce: text clipping, text overflow, illustration
overlap, card breakage, and Preview↔device mismatch.

Enabling real system font scaling is a **project**, not a one-line edit. It requires first making the
UI genuinely reflowable:

- flexible text widths (no fixed-width single-line labels),
- text wrapping where labels can grow,
- dynamic component heights (cards that grow with their content),
- responsive tabs (the segmented control must not clip),
- illustrations that reserve space instead of overlapping by fixed offset,
- dynamic layout constraints instead of absolute Figma coordinates.

Only **after** that complete responsive/accessibility pass may the `fontScale` policy change.

---

## 5. Before creating a NEW UI file

1. **Read this file completely.**
2. **Inspect existing similar files first.** New screen → read `HomeScreen` / `OnboardingScreen`. New
   card → read `HomeBento.kt` / `HomeUpdates.kt`. New auth UI → read `SignInScreen` + `SignUpScreen` +
   `AuthCommon.kt`. Modifying Home → read `HomeScreen.kt` and its components.
3. **Decide: is it a top-level screen or a reusable component?**
4. **If a top-level screen:** wrap the root in `DesignFrame { }`, use `systemBarsPadding`, add a
   `@DevicePreviews` preview. Follow the documented pattern (§7).
5. **If a reusable component:** do **NOT** add a `DesignFrame` or `LocalDensity` override. Take a
   `modifier: Modifier = Modifier` param, use normal layout, let the hosting screen supply the frame.
6. **Reuse** existing constants, `Color.kt`, `Type.kt`/`HomeType.kt`, and established patterns instead
   of inventing new ones.
7. **Check responsive behavior** (§8) before calling it done.

---

## 6. Before MODIFYING existing UI

1. Read this file.
2. Identify the file's parent layout and confirm whether it is already inside `DesignFrame` (all four
   screens are).
3. Do **not** introduce a second scaling system.
4. Do **not** randomly change density.
5. Do **not** randomly change `fontScale` (see §4).
6. Do **not** add fixed widths derived from raw device width — the coordinate system is the 415
   artboard, not the physical screen.
7. Confirm the change behaves the same in Preview **and** on a real device / emulator.
8. **Binding live data into a Figma box is a layout change.** Every text slot in this app was measured
   against a mock string, several of them with no slack at all (`WaterCard`'s hint line already overflows
   its source box by 5dp; `HomeGreeting`'s 248 has no room to grow). So a parameter that replaces a
   literal must come with a plan for the strings the literal never covered: clamp it, ellipsise it, or
   reword the long variant so it is never wider than the original — and say which, in the KDoc, with the
   measurement. A count that can reach two digits, a name that can be one word or five, and an empty
   value while the data is still loading are all the *normal* case once real data arrives.
9. **When the design's own numbers disagree, fixing them is allowed — recording it is required.** The
   water card drew 8 glasses, claimed a goal of 12 and a remainder of 5; no binding could satisfy all
   three, so the goal became 8 (BACKEND_PLAN §3.1) and the default preview now fills 7 glasses instead of
   Figma's 3. That is a deliberate divergence from the mock, noted in the composable's KDoc. Silent
   divergence is what is forbidden, not divergence. The step tile's percentage is the same story: Figma
   says 78% over "5,600 / 10,000", so the number is computed and the preview reads 56%. And the three
   dashboard status chips are the same story again, at its sharpest: Figma calls 58% of the fibre goal
   "Good" and 81% of the sleep goal "Fair", which no single threshold can produce, so one rule
   (`domain/Recommendations.kt`) decides all three and **two of the design's labels change**. Whenever a
   rule replaces hand-placed values, check the design against the rule before trusting either — the same
   pass found `HomeUpdates.kt`'s claim that the axis marker points at the end of the filled bar, which
   Figma's own coordinates disprove: it tracks the puck's centre, forty dp away.
10. **A card only becomes tappable when the tap does something, and it never asks the system anything
    itself.** `StepsCard` takes `permissionNeeded: Boolean` plus an `onEnableTracking` lambda and adds its
    `clickable` only in that state — a permanently-tappable tile whose tap is a no-op in the normal case is
    rule 4's dead tap wearing a card. The composable does not know what `ACTIVITY_RECOGNITION` is: the
    launcher, the resume re-check and the fallback to the system settings page all live in `MainActivity`
    (`rememberStepPermission`), which is the only place holding an Activity. A design with no state for a
    refused permission still needs one — write the honest line rather than a plausible number.
11. **When real data needs UI the design does not contain, build it out of parts the design already
    owns.** Sleep is the one metric nothing can observe, so it has to be typed, and Figma draws no way to
    type it: `ui/home/SleepEntrySheet.kt` is therefore invented UI, assembled from the hospitals sheet's
    surface, shadow, grab handle and reveal timing so it adds behaviour without adding vocabulary. Its one
    genuinely new value, a scrim, is aliased to an alpha the palette already carries. Prefer a hand-built
    overlay inside the screen's own root `Box` to a Material component that hoists content into a separate
    window — `ModalBottomSheet` would place its content outside `DesignFrame`, which silently invalidates
    every measurement on the page, and it still needs an experimental opt-in.

---

## 7. Standard screen skeleton

```kotlin
@Composable
fun ExampleScreen(/* callbacks with default {} */) {
    DesignFrame {                       // ← always the root
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .systemBarsPadding()    // consumed inside the frame → correct physical insets
                // .verticalScroll(rememberScrollState())  // if the content can exceed the viewport
                .padding(horizontal = 16.dp),
        ) {
            // Figma coordinates are valid here: the frame made this space 415 wide.
        }
    }
}

@DevicePreviews                          // the approved multi-width preview set (ui/DevicePreviews.kt)
@Composable
private fun ExampleScreenPreview() {
    OmniTheme { ExampleScreen() }
}
```

**Preview rule:** top-level screens use `@DevicePreviews` (renders 415 / 360 / 393 / 412 + a 1.5×
font pane). Because `fontScale` is pinned, all panes should look proportionally identical — a pane
that differs is your signal that something bypassed the frame. Do not hand-stack `@Preview` matrices
in individual files, and do not over-engineer new preview systems.

---

## 8. Definition-of-done checklist (verify before finishing any UI task)

- [ ] `UI_ARCHITECTURE.md` was read before implementation.
- [ ] The correct top-level screen architecture is used.
- [ ] The screen does not bypass `DesignFrame`.
- [ ] No second scaling system was introduced.
- [ ] No unnecessary `LocalDensity` override was introduced.
- [ ] No unnecessary `fontScale` override was introduced.
- [ ] Text stays inside its intended area; important text is not clipped.
- [ ] Illustrations do not unintentionally overlap text.
- [ ] Layout follows the 415 artboard coordinate system.
- [ ] Preview behavior checked (via `@DevicePreviews`).
- [ ] Real-device / emulator behavior checked.
- [ ] `./gradlew.bat :app:assembleDebug` still succeeds.

---

## 9. Forbidden patterns

Do not introduce these without an explicit, documented architectural reason:

- A top-level screen that bypasses `DesignFrame`.
- Direct `LocalDensity` overrides inside random screens/components.
- Multiple competing screen-scaling systems.
- Random `fontScale` overrides (see §4).
- Manual density scaling duplicated across files (there must be exactly one, in `DesignFrame.kt`).
- Hardcoding dimensions from assumptions about one physical phone.
- Using Preview dimensions as proof the UI works on all devices.
- A new screen architecture inconsistent with the existing project.
- "Fixing" text clipping only by hiding text (`TextOverflow`/`maxLines`) when the real problem is the
  layout system.
- Adding random `offset()` to visually patch overlap without understanding the parent constraints.
- Hardcoding hex colors in screens instead of using `Color.kt`.
