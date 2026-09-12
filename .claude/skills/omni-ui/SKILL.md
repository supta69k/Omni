---
name: omni-ui
description: Omni's existing UI system — DesignFrame density scaling, the palette and type tokens in ui/theme, the hand-built sheet convention, and the rule that the shipped design is repaired rather than replaced. Use before creating or editing any screen, composable, dimension, or navigation affordance.
---

# Omni UI

`UI_ARCHITECTURE.md` is the authority; this is the working summary. **Read that file before any UI
change** — it is required by `CLAUDE.md`, not optional.

The standing instruction from the project owner: **improve and repair the existing Omni UI rather than
replacing it.** Do not invent a new design system, do not restyle screens you were not asked about, and
do not refactor merely to match a Figma frame more literally.

## DesignFrame

Every screen's root is exactly one `DesignFrame`. It scales density so the 415dp Figma artboard maps to
any device width, and it **pins `fontScale` to 1f** — so text does not grow with the system font
setting, and a layout that fits in the preview fits on a device. `StoryViewer` is the one deliberate
exemption (a story is a full-bleed photograph).

Consequence: "it clipped on my phone but not in the preview" is almost always a real layout bug, not a
density artefact. Fix it with constraints, not with a smaller font.

## Tokens

All colour lives in `ui/theme/Color.kt` and all type in `Type.kt` / `HomeType.kt` / `ScreenTypes.kt`.
**Never hardcode a hex value in a screen.** Representative tokens actually in use:

`OmniInk #302E2E`, `OmniBackground`, `OmniOnInk`, `OmniAuthHeading #8D84F9`,
`OmniNutriChipCarbs #B184E1`, `OmniHeroPink #F1BBDC`, `OmniFieldSurface #F5F5F5`
(= `OmniFeedSurface`), `OmniTabInactive #C8C8C8` (= `OmniFeedHint`), `OmniBody #6C6C6C`
(= `OmniFeedVerified`), `OmniFeedPostBody #041F1E`, `OmniSheetSurface`, `OmniScrim`.

Type metrics worth knowing: `Hint16` 16/20/−0.16, `Meta12` 12/18, `StoryCaption` 10/12.5,
`SegmentLabel` 16/25, `ActionValue` 16/18.

## Composables and navigation

`.codesight/components.md` is the reliable inventory (172 composables with paths) — check it before
writing a new component. Reuse `ui/components/OmniChrome.kt`, the feature packages under `ui/`, and the
existing theme.

Navigation is the `AppScreen` enum + `Crossfade` in `MainActivity`; navigation-compose is not used.

## Sheets and overlays

`ModalBottomSheet` is **not** used. A sheet is hand-built in the hosting screen's root `Box`:
scrim + `AnimatedVisibility(slideInVertically + fadeIn, tween(280))`, `RoundedCornerShape(top 20dp)`,
`OmniSheetSurface`, the `ic_hosp_grab` handle, `navigationBarsPadding()` and `imePadding()`.
`CommentsSheet`, `CreateStorySheet`, `SleepEntrySheet` and the hospitals sheet all follow it.

Any sheet with a text field needs `imePadding()` **and** a scroll, or the keyboard pushes its heading
off the top and its primary button behind the IME.

## Drawables

There is **no** close/clear/cross icon. The project's own × is `Text("×")` in a 28dp circular chip.
The only arrow asset is `ic_set_arrow_right`, mirrored with `.scale(scaleX = -1f, scaleY = 1f)` for
"back". Feed icons: `ic_feed_search`, `ic_feed_add`, `ic_feed_badge_check`, `ic_feed_share_plus`,
`ic_feed_heart_add`, `ic_feed_comment`, `ic_feed_repost`, `ic_feed_share`, `feed_story_share_avatar`.

## Rules carried from UI_ARCHITECTURE.md

- **§6 rule 8** — binding live data into a fixed Figma box is a layout change. Clamp, ellipsise or
  reword, and record which in the KDoc *with the measurement*.
- **§6 rule 9** — divergence from the design is allowed; **silent** divergence is not. Say why, in the
  KDoc, with the arithmetic.
- **§6 rule 10** — no dead taps. A control that does nothing must not be drawn.
- **§6 rule 11** — invented UI (no Figma frame) borrows an existing frame's geometry and says which.
- **§9** — forbidden: hex in screens, and patching overlap with a random `offset()`. Use real
  constraints (`weight`, `widthIn`, `Alignment`, baseline-aware `Row`).

## Layout fixes, done properly

Clipped or colliding text is fixed with `weight(1f)`, `widthIn`, `maxLines` + `TextOverflow.Ellipsis`,
`Arrangement`, or a wrapping `Column` — not by shrinking type and not by nudging with offsets. Number +
unit pairs align with `Row(verticalAlignment = Alignment.Bottom)` or `Alignment.LastBaseline`, and are
centred **as one group** rather than centring each part.
