package com.example.omni.ui.nutrition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.theme.NutritionType
import com.example.omni.ui.theme.OmniNutriDayIdle
import com.example.omni.ui.theme.OmniNutriDaySelected
import com.example.omni.ui.theme.OmniNutriDaySurface
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The month behind the week strip — the third sheet the design does not contain.
 *
 * The nutrition page shows seven days and a month label, and the label had nowhere to go: there was no way
 * to read a day from earlier in the month, let alone from another one. So this is invented UI under
 * `UI_ARCHITECTURE.md` §6 rule 11, assembled from parts the page already owns — [MealEntrySheet]'s surface,
 * shadow, grab handle and 280ms reveal, and the week strip's own chip (`OmniNutriDaySurface` idle,
 * `OmniNutriDaySelected` chosen, `OmniNutriDayIdle` ink) repeated into a grid.
 *
 * Each square carries the day's logged calories under its number, which is what makes this a *view* of the
 * month rather than only a date picker: an empty square is a day with nothing logged, and the shaded ones
 * are the days there is something to go and read. Those totals are the day documents' own roll-up, so they
 * agree with the gauge the page draws when the day is opened.
 *
 * @param calories the month's logged days. Absent means nothing logged, which is drawn as an empty square
 *   rather than as "0 cal" — the two are different statements and only one of them is true of a fresh day.
 */
@Composable
internal fun MonthPickerSheet(
    visible: Boolean,
    month: YearMonth,
    selected: LocalDate,
    calories: Map<LocalDate, Int>,
    onDismiss: () -> Unit,
    onBrowseMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared scrim + slide envelope (ui/components/OmniSheetScaffold).
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        MonthSheet(
            month = month,
            selected = selected,
            calories = calories,
            onDismiss = onDismiss,
            onBrowseMonth = onBrowseMonth,
            onSelectDate = onSelectDate,
        )
    }
}

@Composable
private fun MonthSheet(
    month: YearMonth,
    selected: LocalDate,
    calories: Map<LocalDate, Int>,
    onDismiss: () -> Unit,
    onBrowseMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)

    // Which column Monday sits in is a local matter — a US calendar starts on Sunday and most of Europe on
    // Monday — so the first column is the locale's, not a constant. The header row and the leading blanks
    // are both derived from it, which is the only way the two cannot disagree.
    val firstColumn = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weeks = remember(month, firstColumn) { month.weekGrid(firstColumn) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            // A tap on the sheet's own blank surface must not fall through to the scrim behind it.
            .sheetNoRipple(onClick = {})
            .navigationBarsPadding()
            .padding(horizontal = SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GrabTop))
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Close",
            modifier = Modifier.width(59.dp).height(5.dp).sheetNoRipple(onClick = onDismiss),
        )

        Spacer(Modifier.height(HeadingTop))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow(
                forward = false,
                // There is no floor on how far back a user may look: the account may be years old, and a
                // month with nothing in it simply draws an empty grid.
                enabled = true,
                description = "Previous month",
                onClick = { onBrowseMonth(month.minusMonths(1)) },
            )

            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}",
                style = NutritionType.SectionTitle,
                color = OmniSectionTitle,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            MonthArrow(
                forward = true,
                // Forward stops at the current month. A calendar that can be paged into next year would
                // be pages of squares that cannot hold anything, and each one would cost a query.
                enabled = month.isBefore(thisMonth),
                description = "Next month",
                onClick = { onBrowseMonth(month.plusMonths(1)) },
            )
        }

        Spacer(Modifier.height(WeekdayTop))

        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(DaysInWeek) { column ->
                val day = firstColumn.plus(column.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        .take(1)
                        .uppercase(Locale.getDefault()),
                    style = NutritionType.DayWeekday,
                    color = OmniNutriMacroLabel,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(GridTop))

        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CellGap),
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(CellHeight))
                    } else {
                        DayCell(
                            date = date,
                            calories = calories[date],
                            selected = date == selected,
                            today = date == today,
                            // A day that has not happened cannot be logged against, and the day page
                            // would have nothing to show — the same rule the week strip's chips follow.
                            enabled = !date.isAfter(today),
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(CellGap))
        }

        Spacer(Modifier.height(FooterTop))
        Text(
            text = "Jump to today",
            style = NutritionType.FoodCalories,
            color = OmniNutriDaySelected,
            maxLines = 1,
            modifier = Modifier.sheetNoRipple { onSelectDate(today) },
        )

        Spacer(Modifier.height(SheetBottom))
    }
}

/**
 * One square: the day, and what was eaten on it.
 *
 * The shading is the signal that a day holds something, so it is driven by [calories] rather than by
 * whether the square is in the past — most past days in a fresh account are empty, and shading all of them
 * would say the opposite of the truth.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    calories: Int?,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CellCorner)
    val surface = when {
        selected -> OmniNutriDaySelected
        calories != null -> OmniNutriDaySurface
        else -> Color.Transparent
    }
    val ink = when {
        selected -> OmniOnInk
        enabled -> OmniNutriDayIdle
        else -> OmniNutriDayIdle.copy(alpha = IdleAlpha)
    }

    // Today is ringed rather than filled, so "the day I am on" and "the day I am looking at" stay two
    // different statements — they are usually the same square and occasionally not.
    val outline = if (today && !selected) {
        Modifier.border(TodayRing, OmniNutriDaySelected, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(CellHeight)
            .clip(shape)
            .background(surface)
            .then(outline)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = NutritionType.DayNumber,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            if (calories != null) {
                Text(
                    text = calories.toString(),
                    style = NutritionType.FoodChip,
                    color = if (selected) OmniOnInk else OmniNutriMacroLabel,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The one arrow asset this app owns, mirrored for the back direction as the chat header mirrors it. */
@Composable
private fun MonthArrow(
    forward: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ArrowTapSize)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_set_arrow_right),
            contentDescription = description,
            // Drawn dimmed rather than removed: a header that loses its right-hand control re-centres the
            // title and the whole row jumps as the calendar is paged (`UI_ARCHITECTURE.md` §6 rule 10 is
            // about controls that do nothing at all, not ones that are visibly unavailable).
            alpha = if (enabled) 1f else DisabledArrowAlpha,
            modifier = Modifier
                .size(ArrowSize)
                .scale(scaleX = if (forward) 1f else -1f, scaleY = 1f),
        )
    }
}

/**
 * The month as rows of seven, `null` where a square belongs to a neighbouring month.
 *
 * Blanks rather than the previous month's tail: those days are readable from their own month, and drawing
 * them here would put two squares with the same number a row apart.
 */
private fun YearMonth.weekGrid(firstColumn: DayOfWeek): List<List<LocalDate?>> {
    val lead = ((atDay(1).dayOfWeek.value - firstColumn.value) + DaysInWeek) % DaysInWeek
    val cells = buildList<LocalDate?> {
        repeat(lead) { add(null) }
        (1..lengthOfMonth()).forEach { day -> add(atDay(day)) }
        while (size % DaysInWeek != 0) add(null)
    }
    return cells.chunked(DaysInWeek)
}

private const val DaysInWeek = 7

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** `MealEntrySheet`'s gutter, handle inset and heading gap — one sheet in three rooms. */
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 20.dp

private val WeekdayTop = 18.dp
private val GridTop = 8.dp
private val CellGap = 4.dp
private val CellCorner = 12.dp
private val FooterTop = 10.dp
private val SheetBottom = 20.dp

/** Two lines of 14sp and 10sp with room to breathe, and six rows of them still clear the navigation bar. */
private val CellHeight = 44.dp

private val TodayRing = 1.5.dp
private val ArrowTapSize = 40.dp
private val ArrowSize = 22.dp

/** The week strip's own dimming for a day that cannot be chosen. */
private const val IdleAlpha = 0.4f
private const val DisabledArrowAlpha = 0.3f
