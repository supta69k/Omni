package com.example.omni.ui.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.domain.HealthInsights
import com.example.omni.domain.formatAmount
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Health Insights Hub — this month against last, computed by [HealthInsights], rendered here.
 *
 * **There is no Figma frame for this page**, so `UI_ARCHITECTURE.md` §6 rule 11 governs it: every
 * part is one the design already owns. The back row is the settings sub-pages' (a mirrored
 * `ic_set_arrow_right` in a 40 hit target, exactly as [com.example.omni.ui.sleep.SleepScreen]'s);
 * each metric card is the nutrition/settings `#F5F5F5` tray at radius 8; the progress bar is the
 * goal card's fill on the same tray; the type is [HomeType] and every colour is from `Color.kt`.
 *
 * **No bottom bar and no rail** (§3a): Insights is reached from Settings, so its only way out is
 * the back arrow and the system back — a plain scrolling `Column` inside the single [DesignFrame].
 *
 * Stateless by construction: it renders an [InsightsViewModel.UiState] and forwards taps.
 */
@Composable
fun InsightsScreen(
    state: InsightsViewModel.UiState,
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Box(modifier = Modifier.fillMaxSize().background(OmniBackground)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = PagePadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))
                BackRow(onBack = onBack)

                Spacer(Modifier.height(IntroTop))
                IntroText(state)

                Spacer(Modifier.height(SectionTop))
                val insights = state.insights
                if (insights == null) {
                    // First paint, before the first month emission — the tray with a pulse, not a blank.
                    LoadingCard()
                } else if (!insights.current.hasData) {
                    EmptyCard(monthLabel = insights.current.label)
                } else {
                    PerfectDaysCard(insights)
                    Spacer(Modifier.height(SectionTop))
                    insights.all.forEachIndexed { index, metric ->
                        if (index > 0) Spacer(Modifier.height(CardGap))
                        MetricCard(insights.current.label, metric)
                    }
                }

                Spacer(Modifier.height(ContentBottomGap))
            }
        }
    }
}

/** The settings sub-pages' back row — mirrored arrow in a 40 hit target, then the page title. */
@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BackRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(BackButtonSize)
                .pressEffect()
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_arrow_right),
                contentDescription = "Back",
                modifier = Modifier.size(24.dp).scale(scaleX = -1f, scaleY = 1f),
            )
        }
        Text(text = "Health Insights", style = HomeType.SectionTitle, color = OmniCardInk, maxLines = 1)
    }
}

@Composable
private fun IntroText(state: InsightsViewModel.UiState) {
    val text = when {
        state.loading -> "Reading your months…"
        else -> "How this month compares with the one before it — streaks, best days, and the small moves that add up."
    }
    Text(text = text, style = HomeType.CardFootnoteWrapped, color = OmniNutriMacroLabel)
}

/** The header's lead number: how many of this month's logged days hit every goal at once. */
@Composable
private fun PerfectDaysCard(insights: HealthInsights) {
    CardTray {
        Text(
            text = "${insights.perfectDays} perfect ${if (insights.perfectDays == 1) "day" else "days"}",
            style = HomeType.CardTitle,
            color = OmniCardInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${insights.current.label} · days that hit all five goals at once",
            style = HomeType.CardFootnote,
            color = OmniNutriMacroLabel,
        )
    }
}

/** One metric's month-over-month story: average with delta, goal bar, streak, best day. */
@Composable
private fun MetricCard(currentMonthLabel: String, metric: HealthInsights.MetricInsight) {
    CardTray {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = metric.label, style = HomeType.CardTitle, color = OmniCardInk)
            if (metric.deltaLabel.isNotEmpty()) {
                Text(
                    text = metric.deltaLabel,
                    style = HomeType.AxisLabel,
                    color = if (metric.isImprovement) OmniSetApply else OmniNutriMacroLabel,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatAmount(metric.average)}${unitFor(metric.label)} this month" +
                if (metric.previousAverage > 0f) {
                    " · ${formatAmount(metric.previousAverage)}${unitFor(metric.label)} last month"
                } else {
                    ""
                },
            style = HomeType.CardFootnote,
            color = OmniNutriMacroLabel,
        )

        Spacer(Modifier.height(8.dp))
        GoalBar(metric.progressFraction.coerceIn(0f, 1f))

        Spacer(Modifier.height(8.dp))
        val streak = metric.bestStreak
        val bestDay = metric.bestDay
        val streakText = if (streak > 0) {
            "$streak-day streak" + metric.bestStreakEnd?.let { " · to ${it.format(DayLabel)}" }.orEmpty()
        } else {
            "No streak yet"
        }
        val bestText = bestDay?.let { "Best day: ${it.date.formatDateLabel()}" } ?: "No logged days yet"
        Text(
            text = "$streakText · $bestText",
            style = HomeType.CardFootnote,
            color = OmniNutriMacroLabel,
        )
    }
}

/** The goal card's fill — [OmniFieldSurface] tray, radius 8, then the rounded fill on the track. */
@Composable
private fun GoalBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniSetApply),
        )
    }
}

@Composable
private fun LoadingCard() {
    CardTray {
        Text(
            text = "Reading your months…",
            style = HomeType.CardFootnote,
            color = OmniNutriMacroLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/** An empty current month: the honest "nothing to compare yet", with the way forward. */
@Composable
private fun EmptyCard(monthLabel: String) {
    CardTray {
        Text(
            text = "Nothing logged in $monthLabel yet",
            style = HomeType.CardTitle,
            color = OmniCardInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Log meals, steps, sleep and water through the month and your insights will build " +
                "themselves here — month over month, streak by streak.",
            style = HomeType.CardFootnoteWrapped,
            color = OmniNutriMacroLabel,
        )
    }
}

/** The nutrition/settings `#F5F5F5` tray at radius 8 — every card on this page is one of these. */
@Composable
private fun CardTray(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(OmniFieldSurface)
            .padding(horizontal = CardPadding, vertical = 14.dp),
    ) {
        content()
    }
}

/** Each metric's unit, as the dashboard's own cards word them. */
private fun unitFor(label: String): String = when (label) {
    "Steps" -> ""
    "Sleep" -> "h"
    "Water" -> " glasses"
    "Fiber" -> "g"
    "Calories" -> " kcal"
    else -> ""
}

private fun String.formatDateLabel(): String =
    runCatching { LocalDate.parse(this).format(DayLabel) }.getOrDefault(this)

private val DayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

// ---- Dimensions (the sleep page's own, so the two sub-pages match) ----

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val SectionTop = 20.dp
private val CardGap = 12.dp
private val CardPadding = 16.dp
private val CardShape = RoundedCornerShape(8.dp)
private val ContentBottomGap = 32.dp

// ---- Previews ----

@DevicePreviews
@Composable
private fun InsightsScreenPreview() {
    val current = mapOf(
        "2026-09-01" to com.example.omni.data.model.DailyMetrics("2026-09-01", waterGlasses = 8, steps = 12_400, sleepHours = 8f, fiberGrams = 32f, calories = 1_950),
        "2026-09-02" to com.example.omni.data.model.DailyMetrics("2026-09-02", waterGlasses = 9, steps = 11_100, sleepHours = 8.5f, fiberGrams = 35f, calories = 1_880),
        "2026-09-03" to com.example.omni.data.model.DailyMetrics("2026-09-03", waterGlasses = 6, steps = 8_200, sleepHours = 7f, fiberGrams = 21f, calories = 2_300),
        "2026-09-04" to com.example.omni.data.model.DailyMetrics("2026-09-04", waterGlasses = 8, steps = 10_600, sleepHours = 8f, fiberGrams = 30f, calories = 1_990),
    )
    val previous = mapOf(
        "2026-08-01" to com.example.omni.data.model.DailyMetrics("2026-08-01", waterGlasses = 5, steps = 7_400, sleepHours = 6.5f, fiberGrams = 18f, calories = 2_400),
        "2026-08-02" to com.example.omni.data.model.DailyMetrics("2026-08-02", waterGlasses = 6, steps = 9_100, sleepHours = 7f, fiberGrams = 22f, calories = 2_200),
    )
    OmniTheme {
        InsightsScreen(
            state = InsightsViewModel.UiState(
                insights = HealthInsights.build(
                    currentMonth = java.time.YearMonth.of(2026, 9),
                    currentDays = current,
                    previousMonth = java.time.YearMonth.of(2026, 8),
                    previousDays = previous,
                    waterGoal = 8,
                    stepsGoal = 10_000,
                    sleepGoal = 8f,
                    fiberGoal = 30f,
                    calorieGoal = 2_000,
                ),
                loading = false,
            ),
        )
    }
}

@DevicePreviews
@Composable
private fun InsightsScreenEmptyPreview() {
    OmniTheme {
        InsightsScreen(
            state = InsightsViewModel.UiState(
                insights = HealthInsights.build(
                    currentMonth = java.time.YearMonth.of(2026, 9),
                    currentDays = emptyMap(),
                    previousMonth = java.time.YearMonth.of(2026, 8),
                    previousDays = emptyMap(),
                    waterGoal = 8,
                    stepsGoal = 10_000,
                    sleepGoal = 8f,
                    fiberGoal = 30f,
                    calorieGoal = 2_000,
                ),
                loading = false,
            ),
        )
    }
}
