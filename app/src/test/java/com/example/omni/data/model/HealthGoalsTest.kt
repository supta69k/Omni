package com.example.omni.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The five daily targets, and the one place that decides what a legal target is.
 *
 * [HealthGoals.clamped] is applied on the way *out* as well as on the way in, which is the whole point
 * of testing it: the editor's steppers cannot leave the range on their own, but a goal written by an
 * older build, a hand edit in the Firebase console, or a future screen can — and every card in the app
 * is measured for a number inside these bounds.
 */
class HealthGoalsTest {

    @Test
    fun `a legal set of goals passes through untouched`() {
        val goals = HealthGoals(water = 8, steps = 10_000, sleepHours = 8f, fiberGrams = 31f, calories = 2_000)

        assertEquals(goals, goals.clamped())
    }

    @Test
    fun `a target above the ceiling comes back at the ceiling`() {
        val clamped = HealthGoals(
            water = 500,
            steps = 1_000_000,
            sleepHours = 40f,
            fiberGrams = 900f,
            calories = 99_000,
        ).clamped()

        assertEquals(MaxWaterGoal, clamped.water)
        assertEquals(MaxStepsGoal, clamped.steps)
        assertEquals(MaxSleepGoal, clamped.sleepHours, Tolerance)
        assertEquals(MaxFiberGoal, clamped.fiberGrams, Tolerance)
        assertEquals(MaxCalorieGoal, clamped.calories)
    }

    /** Zero and negative targets are the ones a console edit produces, and every card divides by them. */
    @Test
    fun `a target below the floor comes back at the floor`() {
        val clamped = HealthGoals(
            water = 0,
            steps = -5,
            sleepHours = 0f,
            fiberGrams = -1f,
            calories = 0,
        ).clamped()

        assertEquals(MinWaterGoal, clamped.water)
        assertEquals(MinStepsGoal, clamped.steps)
        assertEquals(MinSleepGoal, clamped.sleepHours, Tolerance)
        assertEquals(MinFiberGoal, clamped.fiberGrams, Tolerance)
        assertEquals(MinCalorieGoal, clamped.calories)
    }

    /**
     * `coerceIn` on a `NaN` returns the `NaN`, so the two float goals need their own guard — without
     * it a non-finite target would reach a progress ring and paint nothing at all.
     */
    @Test
    fun `a non-finite float goal falls back to the default rather than propagating`() {
        val clamped = HealthGoals(
            sleepHours = Float.NaN,
            fiberGrams = Float.POSITIVE_INFINITY,
        ).clamped()

        assertEquals(DefaultSleepGoal, clamped.sleepHours, Tolerance)
        assertEquals(DefaultFiberGoal, clamped.fiberGrams, Tolerance)
    }

    @Test
    fun `clamping is idempotent`() {
        val once = HealthGoals(water = 500, steps = -5, sleepHours = 40f).clamped()

        assertEquals(once, once.clamped())
    }

    private companion object {
        const val Tolerance = 0.0001f
    }
}

/** The design's compact count format, for the like and comment numbers on a feed pill. */
class CompactCountTest {

    /** Three digits fit the pill, so 999 shows whole. */
    @Test
    fun `counts below a thousand show whole`() {
        assertEquals("0", compactCount(0))
        assertEquals("50", compactCount(50))
        assertEquals("100", compactCount(100))
        assertEquals("999", compactCount(999))
    }

    @Test
    fun `a thousand and up collapse to k`() {
        assertEquals("1k", compactCount(1_000))
        assertEquals("1.2k", compactCount(1_200))
        assertEquals("9.9k", compactCount(9_900))
    }

    /** Past ten thousand the decimal is dropped — the pill has no room for four characters plus a k. */
    @Test
    fun `five figures drop the decimal`() {
        assertEquals("15k", compactCount(15_000))
        assertEquals("15k", compactCount(15_900))
        assertEquals("1234k", compactCount(1_234_567))
    }
}

/**
 * The thread id — the two uids sorted and joined.
 *
 * Sorted so it is the same string whichever end computes it, which is what makes opening a chat
 * idempotent. Without it, two people tapping each other's name at the same moment would create two
 * threads holding half a conversation each.
 */
class ConversationIdTest {

    @Test
    fun `both ends compute the same id`() {
        assertEquals(conversationIdOf("aaa", "zzz"), conversationIdOf("zzz", "aaa"))
    }

    @Test
    fun `the id is the sorted pair joined with an underscore`() {
        assertEquals("aaa_zzz", conversationIdOf("zzz", "aaa"))
        assertEquals("aaa_zzz", conversationIdOf("aaa", "zzz"))
    }

    /** Firebase uids are case-sensitive and mixed-case, so the ordering must be too — not a locale sort. */
    @Test
    fun `ordering is by code point, not by locale`() {
        assertEquals("Zed_alice", conversationIdOf("alice", "Zed"))
    }
}
