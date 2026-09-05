package com.example.omni.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

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
 * `fontScale` is pinned to 1 rather than passed through, and that is deliberate. This design is a
 * fixed artboard: its text sits in single-line, non-wrapping boxes whose widths were measured against
 * the glyph advances at the design's own size — "Wellness" fills 67.4 of its 71, the hydration hint
 * 146.9 of its 151. Those boxes have no slack, so the moment the system font scale rises even a few
 * percent the text spills its box: the Android Studio preview (which always renders at fontScale 1)
 * stays perfect while a real phone with "Large" system text clips "left" off the hydration hint, drops
 * the last letter of the tab labels, and pushes the step hint into the runner. Because the whole page
 * is a scaled artboard, the font is part of that artboard and has to scale with it, not with a second
 * independent axis. Pinning it makes every device match the preview exactly.
 *
 * The trade-off is that the system-wide "font size" accessibility setting no longer enlarges this
 * app's text. Honouring it would mean giving every one of those boxes room to reflow — a real layout
 * change the fixed design cannot absorb — so it is a deliberate, documented choice, not an oversight.
 * To restore it later, change `fontScale = 1f` back to `outer.fontScale` once the text boxes are made
 * flexible.
 *
 * System insets are still consumed *inside* this scope, where they are read back out of raw pixels —
 * `statusBarsPadding` / `systemBarsPadding` therefore reserve exactly the physical space the bars
 * occupy.
 *
 * The remaining trade-off is that a tablet scales everything up rather than reflowing. That is correct
 * for a phone-frame design and the honest thing to do until there is a tablet layout to reflow into.
 */
@Composable
fun DesignFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = outer.density * (maxWidth / DesignFrameWidth),
                fontScale = 1f,
            ),
            content = content,
        )
    }
}

/** The width of every Figma frame in this file — `iPhone 14 & 15 Pro`. */
val DesignFrameWidth = 415.dp
