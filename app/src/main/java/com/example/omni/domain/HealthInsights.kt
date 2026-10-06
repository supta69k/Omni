package com.example.omni.domain

import com.example.omni.data.model.DailyMetrics
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Health Insights Hub's *content*, computed pure — no Android, no Firestore, no formatting — so
 * every number the screen draws is unit-testable before a single pixel exists. The sibling of
 * [HealthReport]: the report flattens one month into a PDF, the insights compare two months and rank
 * their days.
 *
 * Same semantics as the report, because it is the same data: a day that logged nothing is absent
 * from the month map, and it is **excluded from every average** — "no meals logged" is not the same
 * fact as "ate nothing". A zero-day *is* allowed to break a streak (a streak is about showing up),
 * but it never lowers an average and it is never named a best day.
 *
 * @property current the month on screen.
 * @property previous the month before it — the comparison baseline.
 * @property goals the account's five targets, the same numbers the dashboard's cards draw against.
 */
data class HealthInsights(
    val current: MonthData,
    val previous: MonthData,
    val goals: Goals,
) {

    /** One month's flattened days, the logged ones gated out of the arithmetic. */
    data class MonthData(
        val month: YearMonth,
        val daysByDate: Map<String, DailyMetrics>,
    ) {
        /** Days that logged anything at all, oldest first — the denominator for every average. */
        val loggedDays: List<DailyMetrics> =
            daysByDate.values
                .filter { it.steps > 0 || it.calories > 0 || it.sleepHours > 0f || it.waterGlasses > 0 }
                .sortedBy { it.date }

        val hasData: Boolean get() = loggedDays.isNotEmpty()

        /** The month's human label — "September 2026". */
        val label: String
            get() = month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year
    }

    /** The account's five targets — [User]'s goal fields, restated here so this file stays pure. */
    data class Goals(
        val waterGoal: Int,
        val stepsGoal: Int,
        val sleepGoal: Float,
        val fiberGoal: Float,
        val calorieGoal: Int,
    )

    /** One metric's month-over-month story: this month against last, plus its best day and streak. */
    data class MetricInsight(
        val label: String,
        /** This month's average over logged days, or 0 when nothing was logged. */
        val average: Float,
        /** Last month's average over its logged days — 0 when last month was empty. */
        val previousAverage: Float,
        /** average − previousAverage. The screen decides whether that is good (calories is a ceiling). */
        val delta: Float,
        /** Average / goal, uncapped — the screen clamps the bar; over 1 is honest here. */
        val progressFraction: Float,
        /** Longest run of consecutive days that reached the goal, anywhere in the month. */
        val bestStreak: Int,
        /** The streak's last day, so the screen can say "ran to 24 Sep". */
        val bestStreakEnd: LocalDate?,
        /** The month's single strongest day for this metric — the first one on a tie. */
        val bestDay: DailyMetrics?,
    ) {
        /** Whether the delta moved in this metric's *good* direction — a floor goal is good when up; calories, the one ceiling goal, is good when down. */
        val isImprovement: Boolean get() = if (label == CALORIES_LABEL) delta < 0 else delta > 0

        /** "Up 12%" / "Down 8%" / "Level" — empty when last month was empty, because there is nothing to compare against. */
        val deltaLabel: String
            get() {
                if (previousAverage <= 0f) return ""
                val percent = kotlin.math.abs((delta / previousAverage * 100).toInt())
                if (percent < 1) return "Level"
                val word = if (isImprovement) "Up" else "Down"
                return "$word $percent%"
            }
    }

    val steps: MetricInsight = insight(
        label = "Steps",
        value = { it.steps.toFloat() },
        goal = goals.stepsGoal.toFloat(),
        goalReached = { day, goal -> day.steps >= goal },
    )

    val sleep: MetricInsight = insight(
        label = "Sleep",
        value = { it.sleepHours },
        goal = goals.sleepGoal,
        goalReached = { day, goal -> day.sleepHours >= goal },
    )

    val water: MetricInsight = insight(
        label = "Water",
        value = { it.waterGlasses.toFloat() },
        goal = goals.waterGoal.toFloat(),
        goalReached = { day, goal -> day.waterGlasses >= goal },
    )

    val fiber: MetricInsight = insight(
        label = "Fiber",
        value = { it.fiberGrams },
        goal = goals.fiberGoal,
        goalReached = { day, goal -> day.fiberGrams >= goal },
    )

    val calories: MetricInsight = insight(
        label = CALORIES_LABEL,
        value = { it.calories.toFloat() },
        goal = goals.calorieGoal.toFloat(),
        // Calories is a ceiling goal, not a floor: "on target" means at or under — and only a day
        // that logged food can claim it, so a zero-day never counts as hitting it.
        goalReached = { day, goal -> day.calories in 1..goal.toInt() },
    )

    /** The five insights in the order the screen draws them. */
    val all: List<MetricInsight> get() = listOf(steps, sleep, water, fiber, calories)

    /**
     * How many of this month's logged days hit *all five* goals at once — the header's lead number.
     */
    val perfectDays: Int
        get() = current.loggedDays.count { day ->
            day.steps >= goals.stepsGoal &&
                day.sleepHours >= goals.sleepGoal &&
                day.waterGlasses >= goals.waterGoal &&
                day.fiberGrams >= goals.fiberGoal &&
                // The one ceiling goal: at or under, and only a day that logged food can claim it.
                day.calories in 1..goals.calorieGoal
        }

    private fun insight(
        label: String,
        value: (DailyMetrics) -> Float,
        goal: Float,
        goalReached: (DailyMetrics, Float) -> Boolean,
    ): MetricInsight {
        val average = current.loggedDays.averageOf { value(it) }
        val previousAverage = previous.loggedDays.averageOf { value(it) }

        // The streak walks every day *of the month* in order: an unlogged day breaks it, so a
        // skipped week inside the month splits the run — the honest reading of "consecutive".
        var best = 0
        var bestEnd: LocalDate? = null
        var run = 0
        (1..current.month.lengthOfMonth()).map(current.month::atDay).forEach { date ->
            val day = current.daysByDate[date.toString()]
            if (day != null && goalReached(day, goal)) {
                run += 1
                if (run > best) {
                    best = run
                    bestEnd = date
                }
            } else {
                run = 0
            }
        }

        val bestDay = current.loggedDays.maxByOrNull(value)
        return MetricInsight(
            label = label,
            average = average,
            previousAverage = previousAverage,
            delta = average - previousAverage,
            progressFraction = if (goal <= 0f) 0f else average / goal,
            bestStreak = best,
            bestStreakEnd = bestEnd,
            bestDay = bestDay,
        )
    }

    companion object {

        private const val CALORIES_LABEL = "Calories"

        private fun List<DailyMetrics>.averageOf(selector: (DailyMetrics) -> Float): Float =
            if (isEmpty()) 0f else map(selector).average().toFloat()

        /**
         * Builds the insights from two months' documents plus the account's goals. Days the user
         * never logged are simply absent from each map; every average gates on the logged ones.
         */
        fun build(
            currentMonth: YearMonth,
            currentDays: Map<String, DailyMetrics>,
            previousMonth: YearMonth,
            previousDays: Map<String, DailyMetrics>,
            waterGoal: Int,
            stepsGoal: Int,
            sleepGoal: Float,
            fiberGoal: Float,
            calorieGoal: Int,
        ): HealthInsights = HealthInsights(
            current = MonthData(currentMonth, currentDays),
            previous = MonthData(previousMonth, previousDays),
            goals = Goals(waterGoal, stepsGoal, sleepGoal, fiberGoal, calorieGoal),
        )
    }
}
