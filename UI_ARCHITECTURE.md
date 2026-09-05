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
         └─ when(screen) { … }        // the app's simple top-level navigation
              │
              └─ Top-level screen      // OnboardingScreen / SignUpScreen / SignInScreen / HomeScreen
                   │
                   └─ DesignFrame { }  // ← THE scaling boundary. Exactly one per screen, at the root.
                        │
                        └─ Screen content (Column / Box / verticalScroll, systemBarsPadding, …)
                             │
                             └─ Reusable components (cards, fields, nav, bento tiles, …)
                                  // These NEVER create their own DesignFrame or override density.
```

Key files:

| File | Role |
|------|------|
| `ui/DesignFrame.kt` | The **only** density/font scaling system. `DesignFrame { }` + `DesignFrameWidth = 415.dp`. |
| `ui/DevicePreviews.kt` | The **only** approved multi-width `@Preview` set (`@DevicePreviews`). |
| `ui/theme/Type.kt` | `Typography` (Material slots) + font families (`PlusJakartaSans`, `BodyFont`). |
| `ui/theme/HomeType.kt` | `HomeType` — the ~20 named styles the dashboard needs beyond Material's slots. |
| `ui/theme/Color.kt` | The palette. **Single source of truth for color** — never hardcode hex in a screen. |
| `ui/theme/Theme.kt` | `OmniTheme` — fixed light scheme, dynamic color disabled, no dark theme yet. |
| `ui/auth/AuthCommon.kt` | Shared auth pieces (logo, field, button, footer). |
| `ui/home/HomeScreen.kt`, `HomeBento.kt`, `HomeUpdates.kt` | Home screen + its components. |
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
