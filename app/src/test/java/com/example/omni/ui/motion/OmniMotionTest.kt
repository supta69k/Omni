package com.example.omni.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `OmniMotion.animationsEnabled` is a global singleton flag flipped once at startup from
 * `Settings.Global.TRANSITION_ANIMATION_SCALE`. Nothing in the app asserts on it, so a regression
 * here would only surface as an accessibility user seeing motion they asked the system to remove —
 * invisible in a build, invisible in a screenshot, visible only on a device with the toggle off.
 *
 * These tests pin the two halves of the contract: with the flag on the specs carry the documented
 * durations, and with it off every spec collapses to a zero-duration step while the interaction
 * helpers stop moving things at all. `@After` restores the flag, because the object is shared by
 * every other test in the JVM run.
 */
class OmniMotionTest {

    @After
    fun restoreMotion() {
        OmniMotion.animationsEnabled = true
    }

    private fun durationOf(spec: FiniteAnimationSpec<Float>): Int =
        (spec as TweenSpec<Float>).durationMillis

    @Test
    fun `specs use their documented durations while motion is enabled`() {
        OmniMotion.animationsEnabled = true

        assertEquals(OmniMotion.DurationFast, durationOf(OmniMotion.fast()))
        assertEquals(OmniMotion.DurationMedium, durationOf(OmniMotion.medium()))
        assertEquals(OmniMotion.DurationSlow, durationOf(OmniMotion.slow()))
        assertEquals(OmniMotion.DurationProgress, durationOf(OmniMotion.progress()))
    }

    @Test
    fun `every spec collapses to an instant step while reduced motion is on`() {
        OmniMotion.animationsEnabled = false

        assertEquals(0, durationOf(OmniMotion.fast()))
        assertEquals(0, durationOf(OmniMotion.medium()))
        assertEquals(0, durationOf(OmniMotion.slow()))
        assertEquals(0, durationOf(OmniMotion.progress()))
        // spring() resolves to a zero-duration tween (not a SpringSpec) while reduced.
        assertTrue(OmniMotion.spring<Float>() is TweenSpec)
        assertEquals(0, (OmniMotion.spring<Float>() as TweenSpec<Float>).durationMillis)
    }

    @Test
    fun `stagger collapses to zero while reduced motion is on`() {
        OmniMotion.animationsEnabled = true
        assertEquals(3 * OmniMotion.StaggerDelayMillis, OmniMotion.staggerDelay(3))

        OmniMotion.animationsEnabled = false
        assertEquals(0, OmniMotion.staggerDelay(0))
        assertEquals(0, OmniMotion.staggerDelay(3))
        assertEquals(0, OmniMotion.staggerDelay(9))
    }

    @Test
    fun `entrance transitions are None while reduced motion is on`() {
        OmniMotion.animationsEnabled = false

        assertEquals(EnterTransition.None, OmniMotion.fadeInEnter())
        assertEquals(ExitTransition.None, OmniMotion.fadeOutExit())
        assertEquals(EnterTransition.None, OmniMotion.slideUpEnter())
        assertEquals(ExitTransition.None, OmniMotion.slideDownExit())
    }
}
