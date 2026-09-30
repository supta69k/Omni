package com.example.omni.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The Omni Motion System — Phase 16.
 *
 * Centralized timing, easing, and feedback for the entire application. Every animation in Omni
 * should derive its spec from here rather than hardcoding durations and curves, so the feel stays
 * cohesive as the app grows.
 *
 * Principles:
 *
 * - Subtle over dramatic: a 200ms fade beats a 500ms flourish.
 * - Fast over slow: users tap to accomplish something; the UI should never make them wait.
 * - Functional over decorative: animation should clarify state or guide attention, not exist for its own sake.
 * - Consistent: the same transition for the same action, every time.
 * - Performant: avoid heavy Canvas, blur, or recomposition on every list item.
 * - Accessible: motion must never be required to understand or operate the app.
 */
object OmniMotion {

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // DURATIONS (milliseconds)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /** Very fast UI feedback: press scale, quick alpha changes. */
    const val DurationInstant = 100

    /** Standard content entrance: cards fading in, subtle slides. */
    const val DurationFast = 200

    /** Medium transitions: sheet open/close, state changes. */
    const val DurationMedium = 300

    /** Longer emphasis: success confirmation, important state change. */
    const val DurationSlow = 400

    /** Progress animations: rings filling, bars growing. Smooth but not sluggish. */
    const val DurationProgress = 600

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // EASING
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Standard easing for most animations: starts quickly, settles gently. The Compose default
     * [FastOutSlowInEasing] maps to Material's standard curve and feels natural for content entrance.
     */
    val EasingStandard: Easing = FastOutSlowInEasing

    /**
     * Emphasized easing for important transitions: slightly more "pop" than standard. A custom
     * cubic-bezier approximating Material 3's emphasized curve (0.2, 0.0, 0, 1.0).
     */
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

    /** Linear for progress indicators where the rate of change itself is meaningful. */
    val EasingLinear: Easing = LinearEasing

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // ANIMATION SPECS
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /** Instant feedback: press scale, hover alpha. */
    fun <T> instant(): FiniteAnimationSpec<T> = tween(durationMillis = DurationInstant, easing = EasingStandard)

    /** Fast content entrance: cards, list items, subtle slides. */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(durationMillis = DurationFast, easing = EasingStandard)

    /** Medium transitions: sheets, dialogs, navigation, state changes. */
    fun <T> medium(): FiniteAnimationSpec<T> = tween(durationMillis = DurationMedium, easing = EasingStandard)

    /** Slow emphasis: success confirmation, important state change. */
    fun <T> slow(): FiniteAnimationSpec<T> = tween(durationMillis = DurationSlow, easing = EasingEmphasized)

    /** Progress rings and bars: smooth growth without feeling sluggish. */
    fun <T> progress(): FiniteAnimationSpec<T> = tween(durationMillis = DurationProgress, easing = EasingStandard)

    /**
     * Spring animation for physical-feeling interactions: bottom nav indicator, drag-released sheets.
     * Lower stiffness than Compose's default spring — feels more settled, less bouncy.
     */
    fun <T> spring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // INTERACTION FEEDBACK
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /** The scale applied to a tappable component while pressed: 1.0 → 0.98. Extremely subtle. */
    const val PressScale = 0.98f

    /**
     * Adds subtle press feedback to a tappable component: scales from 1.0 → [PressScale] while
     * the user's finger is down. Does NOT invoke the click — pair this with `.clickable(...)` or
     * handle the tap yourself.
     *
     * Use sparingly: cards, primary buttons, and emphasized actions. Do NOT apply to every list item
     * or the entire screen will feel squishy.
     *
     * @param enabled Whether press feedback is active. When false, no scale change occurs.
     *
     * Example:
     * ```
     * Box(
     *     modifier = Modifier
     *         .pressEffect()
     *         .clickable(onClick = { ... })
     * )
     * ```
     */
    fun Modifier.pressEffect(enabled: Boolean = true): Modifier = composed {
        if (!enabled) return@composed this

        var pressed by remember { mutableStateOf(false) }
        val scale = if (pressed) PressScale else 1f

        this
            .scale(scale)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // STAGGER TIMING
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Delay between consecutive items in a staggered entrance animation (e.g., a list of cards
     * appearing one after another). Keep this small: 30ms is enough to create the stagger effect
     * without making the user wait.
     *
     * Use only when the list is short (≤ 10 items) and the entrance happens once. Do NOT stagger
     * every scroll frame or dynamic content update.
     */
    const val StaggerDelayMillis = 30

    /**
     * Returns the stagger delay for the item at [index]: index * [StaggerDelayMillis].
     *
     * Example:
     * ```
     * items.forEachIndexed { index, item ->
     *     val delay = OmniMotion.staggerDelay(index)
     *     LaunchedEffect(Unit) {
     *         delay(delay.toLong())
     *         visible = true
     *     }
     *     AnimatedVisibility(visible = visible) { ItemCard(item) }
     * }
     * ```
     */
    fun staggerDelay(index: Int): Int = index * StaggerDelayMillis

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // ENTRANCE ANIMATIONS
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Standard content entrance: fade in from 0 to 1 alpha.
     * Use for cards, sections, and content that should appear gracefully.
     */
    fun fadeInEnter() = androidx.compose.animation.fadeIn(animationSpec = fast())

    /**
     * Standard content exit: fade out from 1 to 0 alpha.
     */
    fun fadeOutExit() = androidx.compose.animation.fadeOut(animationSpec = fast())

    /**
     * Slide in from bottom with fade — good for sheets and modal content.
     */
    fun slideUpEnter() = androidx.compose.animation.slideInVertically(
        animationSpec = medium(),
        initialOffsetY = { it / 4 }, // Start 25% down
    ) + fadeInEnter()

    /**
     * Slide out to bottom with fade.
     */
    fun slideDownExit() = androidx.compose.animation.slideOutVertically(
        animationSpec = medium(),
        targetOffsetY = { it / 4 },
    ) + fadeOutExit()
}
