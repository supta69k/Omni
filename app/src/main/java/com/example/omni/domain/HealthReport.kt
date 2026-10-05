package com.example.omni.domain

import com.example.omni.data.model.DailyMetrics
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * The health report's *content*, computed pure — no Android, no Firestore, no formatting beyond the
 * values themselves — so every number the PDF will draw is unit-testable before a single pixel exists.
 *
 * Built from one month of [DailyMetrics] (the `users/{uid}/days/{yyyy-MM-dd}` documents, fetched in a
 * single query) plus the account's goals. Absent documents are absent days: a day with nothing logged
 * is a zero row in the daily table but is **excluded from the averages**, because "no meals logged"
 * is not the same fact as "ate nothing" — averaging it in would flatter the diet.
 *
 * @property month the `yyyy-MM` the report covers.
 * @property displayName the account holder's name for the header.
 * @property goals the account's targets, paired with the month's averages on the summary page.
 * @property days every logged day of the month, oldest first.
 */
data class HealthReport(
    val month: String,
    val displayName: String,
    val goals: Goals,
    val days: List<DayRow>,
    val generatedAtLabel: String,
) {

    /** The account's five targets — the same numbers the dashboard's cards draw against. */
    data class Goals(
        val waterGlasses: Int,
        val steps: Int,
        val sleepHours: Float,
        val fiberGrams: Float,
        val calories: Int,
    )

    /** One logged day, flattened for the table. */
    data class DayRow(
        val date: String,
        val steps: Int = 0,
        val sleepHours: Float = 0f,
        val waterGlasses: Int = 0,
        val fiberGrams: Float = 0f,
        val calories: Int = 0,
    ) {
        val dayLabel: String get() = LocalDate.parse(date).format(DayLabelFormat)
    }

    /** One metric's month summary: the average against its goal, and how many days hit the goal. */
    data class MetricSummary(
        val label: String,
        val unit: String,
        val average: Float,
        val goal: Float,
        /** Days where the value reached the goal, out of the days that logged anything at all. */
        val goalDays: Int,
        val loggedDays: Int,
    ) {
        /** 0..1 for the progress bar; capped so a 20,000-step day-average cannot overflow the track. */
        val progressFraction: Float get() = if (goal <= 0f) 0f else (average / goal).coerceIn(0f, 1f)
    }

    /** Days that logged anything at all — the denominator for every average and goal count. */
    val loggedDays: List<DayRow> get() = days.filter { it.steps > 0 || it.calories > 0 || it.sleepHours > 0f || it.waterGlasses > 0 }

    val steps: MetricSummary = MetricSummary(
        label = "Steps",
        unit = "",
        average = loggedDays.averageOf { it.steps.toFloat() },
        goal = goals.steps.toFloat(),
        goalDays = loggedDays.count { it.steps >= goals.steps },
        loggedDays = loggedDays.size,
    )

    val sleep: MetricSummary = MetricSummary(
        label = "Sleep",
        unit = "h",
        average = loggedDays.averageOf { it.sleepHours },
        goal = goals.sleepHours,
        goalDays = loggedDays.count { it.sleepHours >= goals.sleepHours },
        loggedDays = loggedDays.size,
    )

    val water: MetricSummary = MetricSummary(
        label = "Water",
        unit = " glasses",
        average = loggedDays.averageOf { it.waterGlasses.toFloat() },
        goal = goals.waterGlasses.toFloat(),
        goalDays = loggedDays.count { it.waterGlasses >= goals.waterGlasses },
        loggedDays = loggedDays.size,
    )

    val fiber: MetricSummary = MetricSummary(
        label = "Fiber",
        unit = " g",
        average = loggedDays.averageOf { it.fiberGrams },
        goal = goals.fiberGrams,
        goalDays = loggedDays.count { it.fiberGrams >= goals.fiberGrams },
        loggedDays = loggedDays.size,
    )

    val calories: MetricSummary = MetricSummary(
        label = "Calories",
        unit = " kcal",
        average = loggedDays.averageOf { it.calories.toFloat() },
        goal = goals.calories.toFloat(),
        // Calories is a ceiling goal, not a floor: "on target" means at or under.
        goalDays = loggedDays.count { it.calories in 1..goals.calories },
        loggedDays = loggedDays.size,
    )

    /** The five summaries in the order the report draws them. */
    val summaries: List<MetricSummary>
        get() = listOf(steps, sleep, water, fiber, calories)

    /** The month's human label — "September 2026" — for the header. */
    val monthLabel: String
        get() = YearMonth.parse(month).format(MonthLabelFormat)

    companion object {
        private val DayLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
        private val MonthLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        private fun List<DayRow>.averageOf(selector: (DayRow) -> Float): Float =
            if (isEmpty()) 0f else map(selector).average().toFloat()

        /**
         * Builds the report from a month's documents plus the account's goals. Days the user never
         * logged still appear in [days] as zero rows (the table shows the whole month shape), while
         * [loggedDays] gates the averages.
         */
        fun build(
            month: String,
            displayName: String,
            waterGoal: Int,
            stepsGoal: Int,
            sleepGoal: Float,
            fiberGoal: Float,
            calorieGoal: Int,
            daysByDate: Map<String, DailyMetrics>,
            generatedAtLabel: String,
        ): HealthReport {
            val yearMonth = YearMonth.parse(month)
            // Every day of the month gets a row — an unlogged day is a zero row, not a missing one,
            // so the table shows the month's whole shape. The averages still exclude it via
            // [HealthReport.loggedDays], because "no data" is not "ate nothing".
            val days = (1..yearMonth.lengthOfMonth()).map { day ->
                val date = yearMonth.atDay(day).toString()
                daysByDate[date]?.let {
                    DayRow(
                        date = date,
                        steps = it.steps,
                        sleepHours = it.sleepHours,
                        waterGlasses = it.waterGlasses,
                        fiberGrams = it.fiberGrams,
                        calories = it.calories,
                    )
                } ?: DayRow(date = date)
            }
            return HealthReport(
                month = month,
                displayName = displayName,
                goals = Goals(waterGoal, stepsGoal, sleepGoal, fiberGoal, calorieGoal),
                days = days,
                generatedAtLabel = generatedAtLabel,
            )
        }
    }
}
