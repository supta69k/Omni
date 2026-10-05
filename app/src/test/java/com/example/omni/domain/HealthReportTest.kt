package com.example.omni.domain

import com.example.omni.data.model.DailyMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The health report's arithmetic — the numbers the PDF draws, before any pixel exists.
 *
 * The rule under test throughout: **a day that logged nothing is excluded from the averages**. A day
 * with no meals is "no data", not "ate nothing", and averaging it in would flatter the diet.
 */
class HealthReportTest {

    private fun day(date: String, steps: Int = 0, sleep: Float = 0f, water: Int = 0, fiber: Float = 0f, calories: Int = 0) =
        DailyMetrics(date = date, steps = steps, sleepHours = sleep, waterGlasses = water, fiberGrams = fiber, calories = calories)

    private fun build(daysByDate: Map<String, DailyMetrics>) = HealthReport.build(
        month = "2026-09",
        displayName = "Test User",
        waterGoal = 8,
        stepsGoal = 10_000,
        sleepGoal = 8f,
        fiberGoal = 30f,
        calorieGoal = 2_000,
        daysByDate = daysByDate,
        generatedAtLabel = "30 Sep 2026",
    )

    @Test
    fun `empty month produces a zeroed report, not a crash`() {
        val report = build(emptyMap())

        // The table still shows the month's whole shape — 30 zero rows — while every average
        // excludes the days that logged nothing, so an empty month is a zeroed report, not a crash.
        assertEquals(30, report.days.size)
        assertEquals(0, report.loggedDays.size)
        assertEquals(0f, report.steps.average)
        assertEquals(0, report.steps.goalDays)
        assertEquals("September 2026", report.monthLabel)
    }

    @Test
    fun `averages exclude days that logged nothing`() {
        val report = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 10_000, sleep = 8f, water = 8, fiber = 30f, calories = 2_000),
                "2026-09-02" to day("2026-09-02"), // touched nothing — no document would exist
            ),
        )

        assertEquals("The table still shows the whole month's shape", 30, report.days.size)
        assertEquals("But only the day that logged something feeds the averages", 1, report.loggedDays.size)
        assertEquals(10_000f, report.steps.average)
        assertEquals(8f, report.sleep.average)
        assertEquals(2_000f, report.calories.average)
    }

    @Test
    fun `goal days count only days that reached each target`() {
        val report = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 12_000, sleep = 8f, water = 9, fiber = 35f, calories = 1_800),
                "2026-09-02" to day("2026-09-02", steps = 5_000, sleep = 6f, water = 3, fiber = 10f, calories = 2_500),
            ),
        )

        assertEquals(1, report.steps.goalDays)
        assertEquals(1, report.sleep.goalDays)
        assertEquals(1, report.water.goalDays)
        assertEquals(1, report.fiber.goalDays)
        // Calories is a ceiling goal: at or under counts, over does not.
        assertEquals(1, report.calories.goalDays)
    }

    @Test
    fun `the month shape is filled for every day of the month`() {
        val report = build(mapOf("2026-09-15" to day("2026-09-15", steps = 4_000)))

        assertEquals(30, report.days.size) // September has 30 days
        assertEquals("15 Sep", report.days[14].dayLabel)
        assertTrue(report.days.all { it.date.startsWith("2026-09") })
    }

    @Test
    fun `progress fraction caps at the goal`() {
        val report = build(mapOf("2026-09-01" to day("2026-09-01", steps = 40_000)))

        assertEquals(1f, report.steps.progressFraction)
    }

    @Test
    fun `averages mix across the logged days`() {
        val report = build(
            mapOf(
                "2026-09-01" to day("2026-09-01", steps = 6_000, sleep = 7f),
                "2026-09-02" to day("2026-09-02", steps = 10_000, sleep = 9f),
            ),
        )

        assertEquals(8_000f, report.steps.average)
        assertEquals(8f, report.sleep.average)
        assertEquals("Only the 10k day reached the goal", 1, report.steps.goalDays)
    }
}
