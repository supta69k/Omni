package com.example.omni.ui.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import com.example.omni.data.model.formatDuration
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The sleep page — one night's detailed record, the week around it, and the month's history (Phase 6).
 *
 * **There is no Figma frame for this page**, so `UI_ARCHITECTURE.md` §6 rule 11 governs it: every part is
 * one the design already owns. The back row is the settings sub-pages' (a mirrored `ic_set_arrow_right` in a
 * 40 hit target); the detail and insight cards are the nutrition/settings #F5F5F5 tray at radius 8; the
 * progress bar is the goal card's fill on the same tray; the history rows are the saved-emergencies rows;
 * the type is [HomeType]/[SettingsType] and every colour is from `Color.kt`. Nothing here is invented.
 *
 * **No bottom bar and no rail, in either orientation** (§3a). Sleep is reached from another page (the Home
 * sleep card / Settings), so — like [com.example.omni.ui.settings.SavedEmergenciesScreen] and the guide
 * detail — its only way out is the back arrow and the system back. It is therefore a plain scrolling
 * `Column` inside the single [DesignFrame], not an `OmniTabScaffold`.
 *
 * Stateless by construction: it renders a [SleepUiState] and forwards taps. `MainActivity` owns the
 * [SleepViewModel] and the crossfade in and out, so this composable is the same in a `@Preview` as on a
 * device — which is what lets the previews below stand in for it.
 */
@Composable
fun SleepScreen(
    state: SleepUiState,
    onBack: () -> Unit = {},
    onSelectDate: (String) -> Unit = {},
    onBrowseMonth: (String) -> Unit = {},
    onAddSleep: () -> Unit = {},
    onEditSleep: (SleepRecord) -> Unit = {},
    onSaveSleep: (date: String, bedtime: String, wakeTime: String, quality: SleepQuality, notes: String?) -> Unit = { _, _, _, _, _ -> },
    onConfirmDelete: (String) -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onDeleteSleep: (String) -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onDismissError: () -> Unit = {},
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
                Text(
                    text = "The nights you log here feed the sleep ring on your dashboard — record " +
                        "when you went to bed and when you woke, and Omni works out the rest.",
                    style = HomeType.CardFootnoteWrapped,
                    color = OmniSetRowSubtitle,
                )

                Spacer(Modifier.height(SectionTop))
                NightCard(
                    selectedDate = state.selectedDate,
                    record = state.selectedRecord,
                    onPrevDay = { shiftDay(state.selectedDate, -1)?.let(onSelectDate) },
                    onNextDay = { shiftDay(state.selectedDate, +1)?.let(onSelectDate) },
                    canGoNext = state.selectedDate < today(),
                    onLog = onAddSleep,
                    onEdit = { state.selectedRecord?.let(onEditSleep) },
                    onDelete = { onConfirmDelete(state.selectedDate) },
                )

                Spacer(Modifier.height(SectionTop))
                InsightsCard(
                    weeklyAverageMinutes = state.weeklyAverageMinutes,
                    record = state.selectedRecord,
                    goalMinutes = state.goalMinutes,
                )

                Spacer(Modifier.height(SectionTop))
                HistorySection(
                    visibleMonth = state.visibleMonth,
                    records = state.monthRecords,
                    selectedDate = state.selectedDate,
                    onPrevMonth = { onBrowseMonth(previousMonth(state.visibleMonth)) },
                    onNextMonth = { onBrowseMonth(nextMonth(state.visibleMonth)) },
                    onSelectNight = { onSelectDate(it.date) },
                )

                Spacer(Modifier.height(ContentBottomGap))
            }

            // Error slot and both sheets sit above the scroll so they cover the page, not scroll with it.
            state.error?.let { message ->
                ErrorBanner(message = message, onDismiss = onDismissError)
            }

            SleepLogSheet(
                visible = state.editing,
                editing = state.editingRecord,
                onDismiss = onDismissSheet,
                onSave = { bedtime, wakeTime, quality, notes ->
                    onSaveSleep(state.selectedDate, bedtime, wakeTime, quality, notes)
                },
                onDelete = { onConfirmDelete(state.selectedDate) },
            )

            DeleteConfirmSheet(
                date = state.confirmingDelete,
                onConfirm = { onDeleteSleep(it) },
                onCancel = onCancelDelete,
            )
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
        Text(text = "Sleep", style = HomeType.SectionTitle, color = OmniCardInk, maxLines = 1)
    }
}

/**
 * The selected night — a date pager, then either the record or an empty prompt, then the one commitment
 * button (Log when the night is blank, Edit when it is not) and a delete link when there is something to
 * delete. One record per date: editing corrects the night, it never adds a second (adjustment #8).
 */
@Composable
private fun NightCard(
    selectedDate: String,
    record: SleepRecord?,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    canGoNext: Boolean,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(OmniSetCardSurface)
            .padding(CardPadding),
    ) {
        // Date pager — ‹ prev / label / next ›. Next is inert on today: a future night cannot be logged.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerArrow(glyph = "‹", enabled = true, onClick = onPrevDay)
            Text(
                text = longDate(selectedDate),
                style = HomeType.CardTitle,
                color = OmniSetRowTitle,
                maxLines = 1,
            )
            PagerArrow(glyph = "›", enabled = canGoNext, onClick = onNextDay)
        }

        Spacer(Modifier.height(NightBodyTop))

        if (record == null) {
            Text(
                text = "Nothing logged for this night yet.",
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatDuration(record.durationMinutes),
                    style = HomeType.StepsValue,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(NightMetaTop))
            Text(
                text = "${clock12(record.bedtime)}  →  ${clock12(record.wakeTime)}   ·   " +
                    qualityLabel(record.quality),
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            if (!record.notes.isNullOrBlank()) {
                Spacer(Modifier.height(NightNoteTop))
                Text(
                    text = record.notes!!,
                    style = HomeType.CardFootnoteWrapped,
                    color = OmniSetRowSubtitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(NightButtonTop))
        PrimaryButton(
            label = if (record == null) "Log this night" else "Edit this night",
            onClick = if (record == null) onLog else onEdit,
        )

        if (record != null) {
            Spacer(Modifier.height(DeleteLinkTop))
            Text(
                text = "Delete this night",
                style = HomeType.CardFootnote,
                color = OmniAlertRed,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressEffect()
                    .clickable(onClick = onDelete),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The week's summary — the seven-night average and how the selected night measured against the goal.
 *
 * The average skips unlogged nights ([weeklyAverage]), so a partly-recorded week reports the nights that
 * were slept rather than reading four blank nights as a crisis.
 */
@Composable
private fun InsightsCard(
    weeklyAverageMinutes: Int,
    record: SleepRecord?,
    goalMinutes: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(OmniSetCardSurface)
            .padding(CardPadding),
    ) {
        Text(
            text = "This week",
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
        )

        Spacer(Modifier.height(InsightRowTop))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nightly average",
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
            )
            Text(
                text = if (weeklyAverageMinutes > 0) formatDuration(weeklyAverageMinutes) else "—",
                style = HomeType.CardTitle,
                color = OmniCardInk,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(InsightRowTop))
        val percent = goalPercent(record?.durationMinutes ?: 0, goalMinutes)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Goal for this night",
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
            )
            Text(
                text = "$percent%  of  ${formatDuration(goalMinutes)}",
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(BarTop))
        // The goal card's fill: a full-width track, an ink fill across the covered fraction.
        val animatedPercent by animateFloatAsState(
            targetValue = percent / 100f,
            animationSpec = OmniMotion.progress(),
            label = "sleepGoalProgress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniFieldSurface),
        ) {
            if (animatedPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPercent)
                        .height(BarHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(OmniInk),
                )
            }
        }
    }
}

/**
 * The month's logged nights, newest first, under a month pager.
 *
 * The list draws only from the month map the screen already holds; paging the month is what fetches another
 * (`onBrowseMonth`). Tapping a night selects it, scrolling the detail card above to that date.
 */
@Composable
private fun HistorySection(
    visibleMonth: String,
    records: Map<String, SleepRecord>,
    selectedDate: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectNight: (SleepRecord) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerArrow(glyph = "‹", enabled = true, onClick = onPrevMonth)
            Text(
                text = longMonth(visibleMonth),
                style = HomeType.SectionTitle,
                color = OmniSectionTitle,
                maxLines = 1,
            )
            // Next month is always allowed: the ViewModel's read is what refuses a month with no nights,
            // and a user paging into the future simply finds it empty and pages back.
            PagerArrow(glyph = "›", enabled = true, onClick = onNextMonth)
        }

        Spacer(Modifier.height(HistoryListTop))

        val nights = sortedNights(records)
        if (nights.isEmpty()) {
            Text(
                text = "No nights logged this month.",
                style = HomeType.CardFootnote,
                color = OmniSetRowSubtitle,
                maxLines = 1,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(HistoryRowGap)) {
                nights.forEach { night ->
                    HistoryRow(
                        record = night,
                        selected = night.date == selectedDate,
                        onClick = { onSelectNight(night) },
                    )
                }
            }
        }
    }
}

/** One night in the history list — the saved-emergencies row: a date, a duration, a quality grade. */
@Composable
private fun HistoryRow(
    record: SleepRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect()
            .clip(RoundedCornerShape(RowCorner))
            .background(if (selected) OmniInk else OmniSetCardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediumDate(record.date),
                style = SettingsType.RowTitle,
                color = if (selected) OmniOnInk else OmniSetRowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${clock12(record.bedtime)} → ${clock12(record.wakeTime)}",
                style = SettingsType.RowSubtitle,
                color = if (selected) OmniFieldSurface else OmniSetRowSubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(record.durationMinutes),
                style = SettingsType.RowTitle,
                color = if (selected) OmniOnInk else OmniSetRowTitle,
                maxLines = 1,
            )
            Text(
                text = qualityLabel(record.quality),
                style = SettingsType.RowSubtitle,
                color = if (selected) OmniFieldSurface else OmniSetRowSubtitle,
                maxLines = 1,
            )
        }
    }
}

/**
 * The delete confirmation — a small hand-built sheet, so a destructive action is never one stray tap.
 *
 * Built on [com.example.omni.ui.components.OmniSheetScaffold] like the entry sheet, not an AlertDialog:
 * dialogs hoist their content into a separate window, outside [DesignFrame]'s density (`UI_ARCHITECTURE.md`
 * §6 rule 11 / the entry sheet's own note).
 */
@Composable
private fun DeleteConfirmSheet(
    date: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    SleepConfirmSheet(
        date = date,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

/** A pager chevron in a fixed hit target, greyed and inert at the range's edge. */
@Composable
private fun PagerArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PagerArrowSize)
            .pressEffect(enabled = enabled)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = HomeType.SectionTitle,
            color = if (enabled) OmniCardInk else OmniFeedHint,
            maxLines = 1,
        )
    }
}

/** The auth pages' 50 x 20 ink commitment button, reused for the card's one action. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .pressEffect()
            .clip(RoundedCornerShape(ButtonCorner))
            .background(OmniInk)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = HomeType.HeroButton, color = OmniOnInk, maxLines = 1)
    }
}

/** The auth pages' error slot: one red line, tap to dismiss, pinned to the top of the page. */
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = PagePadding),
    ) {
        Spacer(Modifier.height(HeaderTopGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressEffect()
                .clip(RoundedCornerShape(RowCorner))
                .background(OmniAlertRed)
                .clickable(onClick = onDismiss)
                .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        ) {
            Text(text = message, style = HomeType.CardFootnoteWrapped, color = OmniOnInk)
        }
    }
}

// ---- Quality + date helpers ---------------------------------------------------------------------

/** "Poor" / "Fair" / "Good" / "Best" — the four grades in the user's own words. */
private fun qualityLabel(quality: SleepQuality): String = when (quality) {
    SleepQuality.POOR -> "Poor"
    SleepQuality.FAIR -> "Fair"
    SleepQuality.GOOD -> "Good"
    SleepQuality.EXCELLENT -> "Best"
}

/** Today, as the same `yyyy-MM-dd` key the ViewModel compares against. */
private fun today(): String = LocalDate.now().toString()

/** Shifts a `yyyy-MM-dd` key by [days], or `null` if it will not parse. */
private fun shiftDay(date: String, days: Int): String? =
    runCatching { LocalDate.parse(date).plusDays(days.toLong()).toString() }.getOrNull()

/** `"2025-01-15" → "Wed, 15 Jan"` — the detail card's date. */
private fun longDate(date: String): String {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val day = parsed.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val month = parsed.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$day, ${parsed.dayOfMonth} $month"
}

/** `"2025-01-15" → "Wed 15"` — the history row's leading date. */
private fun mediumDate(date: String): String {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val day = parsed.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$day ${parsed.dayOfMonth}"
}

/** `"2025-01" → "January 2025"` — the history pager's month. */
private fun longMonth(month: String): String {
    val parsed = runCatching { LocalDate.parse("$month-01") }.getOrNull() ?: return month
    val name = parsed.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$name ${parsed.year}"
}

/** `"23:00" → "11:00 PM"`, or the raw string if it will not parse. */
private fun clock12(clock: String): String {
    val parts = clock.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()
    val m = parts.getOrNull(1)?.toIntOrNull()
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return clock
    val suffix = if (h < 12) "AM" else "PM"
    val hour12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(hour12, m, suffix)
}

// ---- Geometry -----------------------------------------------------------------------------------
//
// No Figma frame — every number is borrowed (§6 rule 11): the gutter, back row and button are the settings
// sub-pages', the cards are the nutrition/settings #F5F5F5 tray, and the rhythm is the settings page's own.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val IntroTop = 18.dp
private val SectionTop = 20.dp

private val CardCorner = 14.dp
private val CardPadding = 16.dp
private val NightBodyTop = 16.dp
private val NightMetaTop = 8.dp
private val NightNoteTop = 8.dp
private val NightButtonTop = 18.dp
private val DeleteLinkTop = 12.dp

private val InsightRowTop = 12.dp
private val BarTop = 14.dp
private val BarHeight = 8.dp

private val HistoryListTop = 14.dp
private val HistoryRowGap = 10.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp

private val PagerArrowSize = 40.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Clears the gesture area — there is no bottom bar on this page. */
private val ContentBottomGap = 32.dp

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewNight = SleepRecord(
    date = "2025-01-15",
    bedtime = "23:00",
    wakeTime = "07:00",
    durationMinutes = 480,
    quality = SleepQuality.GOOD,
    notes = "Slept through, woke before the alarm.",
)

private val PreviewState = SleepUiState(
    selectedDate = "2025-01-15",
    selectedRecord = PreviewNight,
    monthRecords = mapOf(
        "2025-01-15" to PreviewNight,
        "2025-01-14" to PreviewNight.copy(
            date = "2025-01-14",
            bedtime = "00:30",
            wakeTime = "07:15",
            durationMinutes = 405,
            quality = SleepQuality.FAIR,
            notes = null,
        ),
        "2025-01-12" to PreviewNight.copy(
            date = "2025-01-12",
            bedtime = "22:30",
            wakeTime = "06:45",
            durationMinutes = 495,
            quality = SleepQuality.EXCELLENT,
            notes = null,
        ),
    ),
    visibleMonth = "2025-01",
    weeklyAverageMinutes = 460,
    goalMinutes = 480,
)

@DevicePreviews
@Composable
private fun SleepScreenPreview() {
    OmniTheme { SleepScreen(state = PreviewState) }
}

@DevicePreviews
@Composable
private fun SleepScreenEmptyPreview() {
    OmniTheme {
        SleepScreen(
            state = SleepUiState(
                selectedDate = "2025-01-15",
                visibleMonth = "2025-01",
                goalMinutes = 480,
            ),
        )
    }
}
