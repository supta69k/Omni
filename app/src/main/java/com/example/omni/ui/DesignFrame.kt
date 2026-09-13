package com.example.omni.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.ui.theme.OmniBackground

/**
 * Lays its content out in the Figma artboard's own coordinate space.
 *
 * Every screen in this app is drawn on a 415-wide frame (`iPhone 14 & 15 Pro`). A real phone is
 * 393dp, 5.3% narrower, and rendering the design's numbers as-is scales the error two ways at once:
 * a 383-wide block laid out with 16 gutters becomes 361, and — the part the eye catches first — a
 * 32sp headline occupies 32/393 = 8.14% of the screen where the design meant 32/415 = 7.71%, so all
 * type reads about 5.6% too large against the layout around it.
 *
 * Rather than nudge every child, the density is scaled once: at 393dp physical, one design dp becomes
 * 393/415 = 0.947 physical dp, and the content genuinely *is* 415 wide. Figma's dp *and* sp then need
 * no translation, which is why the screens inside can carry the design's numbers verbatim.
 *
 * The scale is keyed to the **short edge** — `min(maxWidth, maxHeight)` — not to the width. The short
 * edge is physically the phone's portrait width whichever way the phone is held, so one design-dp is
 * the *same physical size* in portrait and landscape. That is the property the whole landscape design
 * rests on: because a 383-dp card or a 46-dp field keeps its exact physical size in both orientations,
 * portrait stays pixel-identical while landscape simply gains room. Keying to `maxWidth` alone was the
 * original rotation bug: in landscape `maxWidth` is the phone's *long* edge (~851dp), which blew the
 * density up ~2× and exploded a tall portrait design into a short viewport.
 *
 * ## Portrait vs. landscape (the width unlock)
 *
 * The short-edge density gives the window a size in *design-dp* of roughly 415×899 in portrait and
 * ~899×415 in landscape — [designWindow] computes it and the frame publishes it as [LocalDesignWindow].
 *
 * - **Portrait** keeps the historical behaviour exactly: content is clamped to `width(415.dp)` and
 *   centred, so nothing about the portrait layout moves.
 * - **Landscape** *unlocks* the width — content is given the whole ~899 design-dp instead of being
 *   letterboxed into a centred 415 column. Screens read [LocalDesignWindow] to reflow their single
 *   portrait column into the wider space (a left navigation rail via `OmniTabScaffold`, 2-column grids,
 *   forms centred at a max width). This is the sanctioned successor to the old "letterbox + scroll"
 *   landscape treatment; see `UI_ARCHITECTURE.md` §3.
 *
 * The frame still paints [OmniBackground] behind everything so any gap a screen leaves (a centred form,
 * a letterboxed media surface) shows the app background rather than the raw window colour. Landscape
 * has less height than the design budgeted (~415 design-dp), which is why every screen that can exceed
 * its viewport carries its own vertical scroll; the frame does not add one, because a screen that fits
 * must not gain a bounce.
 *
 * `fontScale` is pinned to 1 rather than passed through, and that is deliberate. This design's text
 * sits in single-line, non-wrapping boxes whose widths were measured against the glyph advances at the
 * design's own size, so the moment the system font scale rises the text spills its box. Because the
 * whole page is a scaled artboard, the font is part of that artboard and scales with it, not with a
 * second independent axis. To restore the accessibility setting later, change `fontScale = 1f` back to
 * `outer.fontScale` once the text boxes are made flexible.
 *
 * System insets are still consumed *inside* this scope, where they are read back out of raw pixels —
 * `statusBarsPadding` / `systemBarsPadding` therefore reserve exactly the physical space the bars
 * occupy.
 *
 * A tablet's short edge is wider than 415, so the artboard scales up rather than reflowing further;
 * that is correct for a phone-frame design until there is a dedicated tablet layout.
 *
 * One known landscape limitation: the soft keyboard (`adjustResize`) shrinks the window height, which in
 * landscape is the short edge the scale is keyed to — so typing rescales the artboard live. Portrait is
 * unaffected (its short edge is the width, which the keyboard does not touch). Screens with landscape
 * text focus root-scroll so the shrunk viewport can pan; a full fix would size against the pre-IME
 * height and is left alone deliberately rather than carry that window-inset machinery.
 */
@Composable
fun DesignFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Paints anything a screen leaves uncovered (centred forms, letterboxed media) so the raw
            // window background never shows through.
            .background(OmniBackground),
    ) {
        val outer = LocalDensity.current
        val budget = designFrameScale(maxWidth, maxHeight)
        val window = designWindow(maxWidth, maxHeight)
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = outer.density * budget,
                fontScale = 1f,
            ),
            LocalDesignWindow provides window,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                // Portrait clamps to the 415 artboard and centres it (unchanged). Landscape fills the
                // whole ~899 design-dp width so screens can reflow into it.
                val contentModifier = if (window.isLandscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.width(DesignFrameWidth).fillMaxHeight()
                }
                Box(modifier = contentModifier) {
                    content()
                }
            }
        }
    }
}

/**
 * The density multiplier the frame applies: the short edge as a fraction of the artboard's 415dp.
 *
 * Pulled out of the composable so the rotation invariant it encodes — portrait and landscape of the same
 * phone resolve to the *same* scale — is unit-testable without a Compose harness. `Dp / Dp` is a plain
 * `Float` ratio; `minOf` works because `Dp` is `Comparable`.
 */
internal fun designFrameScale(maxWidth: Dp, maxHeight: Dp): Float =
    minOf(maxWidth, maxHeight) / DesignFrameWidth

/**
 * The window measured in the artboard's own **design-dp**, derived from the raw physical constraints and
 * the short-edge [designFrameScale]. A design-dp is `budget` physical-dp, so a physical extent divided by
 * `budget` is that extent expressed in design-dp — the space screens actually lay out against.
 *
 * `isLandscape` comes from the physical constraints (`maxWidth > maxHeight`), which is safe; it is the
 * width *magnitude* in physical dp that must never drive layout, because the frame rescales it away.
 * Pure so the design-dp math (portrait ≈ 415×899, landscape ≈ 899×415, boolean flips at the square) is
 * unit-testable without a Compose harness.
 */
internal fun designWindow(maxWidth: Dp, maxHeight: Dp): DesignWindow {
    val budget = designFrameScale(maxWidth, maxHeight)
    return DesignWindow(
        isLandscape = maxWidth > maxHeight,
        widthDp = maxWidth / budget,
        heightDp = maxHeight / budget,
    )
}

/**
 * The size of the window in artboard design-dp, plus the orientation flag, as one immutable value.
 *
 * This is the sanctioned signal screens branch on for landscape reflow — **not** `WindowSizeClass` or
 * `LocalConfiguration.screenWidthDp`, both of which report *physical* dp that the frame's density scaling
 * has already rescaled away (`UI_ARCHITECTURE.md` §3). Read it via [LocalDesignWindow] from inside a
 * [DesignFrame]; the default is a portrait phone so `@DevicePreviews` and any composable used outside a
 * frame still render.
 */
data class DesignWindow(
    val isLandscape: Boolean,
    val widthDp: Dp,
    val heightDp: Dp,
)

/** The design-dp window a screen is being laid out in; see [DesignWindow]. Set by [DesignFrame]. */
val LocalDesignWindow = staticCompositionLocalOf {
    DesignWindow(isLandscape = false, widthDp = DesignFrameWidth, heightDp = 899.dp)
}

/** The width of every Figma frame in this file — `iPhone 14 & 15 Pro`. */
val DesignFrameWidth = 415.dp
