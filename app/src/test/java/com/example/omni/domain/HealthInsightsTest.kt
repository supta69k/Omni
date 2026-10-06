package com.example.omni.domain

import com.example.omni.data.model.DailyMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Health Insights Hub's arithmetic — deltas, streaks, best days and perfect days — before any
 * pixel exists.
 *
 * The rules under test throughout mirror [HealthReportTest]'s: **a day that logged nothing is
 * excluded from every average** (it is "no data", not "ate nothing"), a zero-day breaks a streak,
 * and calories is the one *ceiling* goal — at or under counts, over does not, and a zero-day never
 * claims it.
 */
class HealthInsightsTest {

    private fun day(date: String, steps: Int = 0, sleep: Float = 0f, water: Int = 0, fiber: Float = 0f, calories: Int = 0) =
        DailyMetrics(date = date, steps = steps, sleepHours = sleep, waterGlasses = water, fiberGrams = fiber, calories = calories)

    private fun build(
        currentDays: Map<String, DailyMetrics>,
        previousDays: Map<String, DailyMetrics> = emptyMap(),
    ) = HealthInsights.build(
        currentMonth = YearMonth.of(2026, 9),
        currentDays = currentDays,
        previousMonth = YearMonth.of(2026, 8),
        previousDays = previousDays,
        waterGoal = 8,
        stepsGoal = 10_000,
        sleepGoal = 8f,
        fiberGoal = 30f,
        calorieGoal = 2_000,
    )

    // ---- Averages -----------------------------------------------------------------

    @Test
    fun `averages exclude days that logged nothing`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000, sleep = 8f),
                "2026-09-02" to day("2026-09-02"), // touched nothing — no document would exist
            ),
        )

        assertEquals(1, insights.current.loggedDays.size)
        assertEquals(10_000f, insights.steps.average)
        assertEquals(8f, insights.sleep.average)
        assertEquals(0f, insights.water.average)
    }

    @Test
    fun `an empty month is a zeroed insight, not a crash`() {
        val insights = build(emptyMap())

        assertEquals(0f, insights.steps.average)
        assertNull(insights.steps.bestDay)
        assertNull(insights.steps.bestStreakEnd)
        assertEquals(0, insights.steps.bestStreak)
        assertEquals(0, insights.perfectDays)
    }

    // ---- Deltas -------------------------------------------------------------------

    @Test
    fun `deltas compare this month's average to last month's`() {
        val insights = build(
            currentDays = mapOf(
                "2026-09-01" to day("2026-09-01", steps = 6_000),
                "2026-09-02" to day("2026-09-02", steps = 10_000),
            ),
            previousDays = mapOf(
                "2026-08-01" to day("2026-08-01", steps = 5_000),
                "2026-08-02" to day("2026-08-02", steps = 7_000),
            ),
        )

        // This month 8,000 vs last month 6,000 — up by 2,000.
        assertEquals(2_000f, insights.steps.delta)
        assertEquals("Up 33%", insights.steps.deltaLabel)
        assertTrue(insights.steps.isImprovement)
    }

    @Test
    fun `a drop in calories is an improvement, not a decline`() {
        val insights = build(
            currentDays = mapOf("2026-09-01" to day("2026-09-01", calories = 1_800)),
            previousDays = mapOf("2026-08-01" to day("2026-08-01", calories = 2_200)),
        )

        assertTrue("Calories is a ceiling: down is good", insights.calories.isImprovement)
        assertEquals("Up 18%", insights.calories.deltaLabel)
    }

    @Test
    fun `a rise in calories reads as Down`() {
        val insights = build(
            currentDays = mapOf("2026-09-01" to day("2026-09-01", calories = 2_400)),
            previousDays = mapOf("2026-08-01" to day("2026-08-01", calories = 2_000)),
        )

        assertEquals("Down 20%", insights.calories.deltaLabel)
    }

    @Test
    fun `an empty previous month shows no delta at all`() {
        val insights = build(currentDays = mapOf("2026-09-01" to day("2026-09-01", steps = 8_000)))

        assertEquals("", insights.steps.deltaLabel)
    }

    @Test
    fun `a tiny change reads as Level`() {
        val insights = build(
            currentDays = mapOf("2026-09-01" to day("2026-09-01", steps = 10_040)),
            previousDays = mapOf("2026-08-01" to day("2026-08-01", steps = 10_000)),
        )

        assertEquals("Level", insights.steps.deltaLabel)
    }

    // ---- Streaks ------------------------------------------------------------------

    @Test
    fun `the streak counts consecutive goal days and remembers where it ended`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000),
                "2026-09-02" to day("2026-09-02", steps = 12_000),
                "2026-09-03" to day("2026-09-03", steps = 11_000),
            ),
        )

        assertEquals(3, insights.steps.bestStreak)
        assertEquals(LocalDate.of(2026, 9, 3), insights.steps.bestStreakEnd)
    }

    @Test
    fun `an unlogged day breaks a streak`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000),
                // the 2nd logged nothing — no document
                "2026-09-03" to day("2026-09-03", steps = 10_000),
                "2026-09-04" to day("2026-09-04", steps = 10_000),
            ),
        )

        assertEquals("The 2-day run from the 3rd is the best, not the split around it", 2, insights.steps.bestStreak)
        assertEquals(LocalDate.of(2026, 9, 4), insights.steps.bestStreakEnd)
    }

    @Test
    fun `a day that logged something but missed the goal breaks the streak too`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000),
                "2026-09-02" to day("2026-09-02", steps = 4_000),
                "2026-09-03" to day("2026-09-03", steps = 10_000),
            ),
        )

        assertEquals(1, insights.steps.bestStreak)
    }

    @Test
    fun `streaks are per metric`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000, water = 2),
                "2026-09-02" to day("2026-09-02", steps = 10_000, water = 3),
            ),
        )

        assertEquals(2, insights.steps.bestStreak)
        assertEquals(0, insights.water.bestStreak)
    }

    @Test
    fun `a zero-calorie day never claims the calories streak`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", calories = 1_800),
                "2026-09-02" to day("2026-09-02"), // nothing logged — 0 is "under goal" but not on target
            ),
        )

        assertEquals(1, insights.calories.bestStreak)
    }

    // ---- Best days ----------------------------------------------------------------

    @Test
    fun `the best day is the strongest logged day`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 4_000),
                "2026-09-02" to day("2026-09-02", steps = 15_000),
                "2026-09-03" to day("2026-09-03", steps = 9_000),
            ),
        )

        assertEquals("2026-09-02", insights.steps.bestDay?.date)
    }

    @Test
    fun `a day that logged nothing is never the best day`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", calories = 1_500),
                "2026-09-02" to day("2026-09-02"), // zero would "win" every ceiling metric
            ),
        )

        assertEquals("2026-09-01", insights.calories.bestDay?.date)
    }

    // ---- Perfect days ---------------------------------------------------------------

    @Test
    fun `perfect days require all five goals at once`() {
        val insights = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000, sleep = 8f, water = 8, fiber = 30f, calories = 1_900),
                "2026-09-02" to day("2026-09-02", steps = 10_000, sleep = 8f, water = 8, fiber = 30f, calories = 2_100), // over the ceiling
                "2026-09-03" to day("2026-09-03", steps = 10_000, sleep = 8f, water = 8, fiber = 10f, calories = 1_900), // fiber short
            ),
        )

        assertEquals(1, insights.perfectDays)
    }

    // ---- Shapes -------------------------------------------------------------------

    @Test
    fun `all five metrics come in drawing order`() {
        val insights = build(emptyMap())

        assertEquals(listOf("Steps", "Sleep", "Water", "Fiber", "Calories"), insights.all.map { it.label })
    }

    @Test
    fun `month labels read like September 2026`() {
        val insights = build(emptyMap())

        assertEquals("September 2026", insights.current.label)
        assertEquals("August 2026", insights.previous.label)
    }
}
