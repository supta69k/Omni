package com.example.omni.ui.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import com.example.omni.data.model.calculateSleepDuration
import com.example.omni.data.model.formatDuration
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * The detailed sleep entry sheet — bedtime, wake time, quality and an optional note.
 *
 * **Why this is not [com.example.omni.ui.home.SleepEntrySheet].** The dashboard's sheet captures a single
 * `Float` (hours to the nearest half hour), which is all Home's "6.5/8h" card can read. Phase 6's record
 * is richer: two clock times, a quality grade and a note, from which the duration is *derived* rather than
 * stated (adjustment #9). The home sheet cannot express that, so this is a second sheet — but it is built
 * from the same parts (`OmniSheetScaffold`'s scrim and slide, the hospitals sheet's surface/shadow/grab
 * handle, the stepper's grey circles, the post composer's `#F5F5F5` field), so it adds no new vocabulary
 * (`UI_ARCHITECTURE.md` §6 rule 11). The two sheets stay in sync through the repository, which writes the
 * derived hours back to `DailyMetrics.sleepHours` on every save (adjustment #2).
 *
 * Times step in fifteen-minute increments rather than opening a wheel picker: a quarter hour is as fine as
 * anyone reliably reports their own sleep, and a stepper needs no keyboard, no locale-specific separator
 * and no "what does 'abc' mean" rule. The live duration under the two steppers is [calculateSleepDuration]'s
 * own answer, so what the user commits is exactly what gets stored.
 *
 * @param editing the record being corrected, or `null` for a new night — the sheet opens on the existing
 *   times so a save fixes the night rather than starting a second one (adjustment #8).
 * @param onSave the four field values; nothing is written until this fires.
 * @param onDelete offered only when [editing] is non-null — there is nothing to delete on a new entry.
 */
@Composable
internal fun SleepLogSheet(
    visible: Boolean,
    editing: SleepRecord?,
    onDismiss: () -> Unit,
    onSave: (bedtime: String, wakeTime: String, quality: SleepQuality, notes: String?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        // Keyed on the record's identity so re-opening on a different night reseeds the fields, while an
        // edit in progress ignores the document changing underneath it (the reveal disposes the content
        // when hidden, so the next open starts from whatever was really saved).
        SleepSheetBody(
            editing = editing,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun SleepSheetBody(
    editing: SleepRecord?,
    onDismiss: () -> Unit,
    onSave: (bedtime: String, wakeTime: String, quality: SleepQuality, notes: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var bedMinutes by remember(editing) { mutableIntStateOf(minutesOfDay(editing?.bedtime) ?: DefaultBedtime) }
    var wakeMinutes by remember(editing) { mutableIntStateOf(minutesOfDay(editing?.wakeTime) ?: DefaultWakeTime) }
    var quality by remember(editing) { mutableStateOf(editing?.quality ?: SleepQuality.GOOD) }
    var notes by remember(editing) { mutableStateOf(editing?.notes.orEmpty()) }

    val bedtime = clockString(bedMinutes)
    val wakeTime = clockString(wakeMinutes)
    val durationMinutes = calculateSleepDuration(bedtime, wakeTime)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            // Swallow taps on the sheet's own surface so they do not fall through to the scrim.
            .sheetNoRipple(onClick = {})
            .navigationBarsPadding()
            .imePadding()
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
        Text(
            text = if (editing == null) "Log last night" else "Edit this night",
            style = HomeType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Bedtime and wake time, to the nearest fifteen minutes.",
            style = HomeType.CardFootnote,
            color = OmniFootnote,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        Spacer(Modifier.height(TimesTop))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TimeColumnGap),
        ) {
            TimeStepper(
                label = "Bedtime",
                display = displayClock(bedMinutes),
                onDown = { bedMinutes = stepDown(bedMinutes) },
                onUp = { bedMinutes = stepUp(bedMinutes) },
                modifier = Modifier.weight(1f),
            )
            TimeStepper(
                label = "Wake",
                display = displayClock(wakeMinutes),
                onDown = { wakeMinutes = stepDown(wakeMinutes) },
                onUp = { wakeMinutes = stepUp(wakeMinutes) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(DurationTop))
        Text(
            text = if (durationMinutes > 0) "That's ${formatDuration(durationMinutes)} of sleep" else
                "Those times don't add up yet",
            style = HomeType.CardFootnote,
            color = if (durationMinutes > 0) OmniCardInk else OmniAlertRed,
            maxLines = 1,
        )

        Spacer(Modifier.height(QualityTop))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QualityGap),
        ) {
            SleepQuality.entries.forEach { option ->
                QualityChip(
                    quality = option,
                    selected = option == quality,
                    onClick = { quality = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(NotesTop))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NotesHeight)
                .clip(RoundedCornerShape(FieldCorner))
                .background(OmniFieldSurface)
                .padding(horizontal = FieldPadding, vertical = FieldPaddingV),
        ) {
            BasicTextField(
                value = notes,
                onValueChange = { notes = it },
                textStyle = FeedType.PostBody.copy(color = OmniInk),
                cursorBrush = SolidColor(OmniInk),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { inner ->
                    if (notes.isEmpty()) {
                        Text(
                            text = "A note about last night (optional)",
                            style = FeedType.PostBody,
                            color = OmniPlaceholder,
                            maxLines = 2,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SaveTop))
        val canSave = durationMinutes > 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .pressEffect(enabled = canSave)
                .clip(RoundedCornerShape(ButtonCorner))
                .background(if (canSave) OmniInk else OmniFootnote)
                .clickable(enabled = canSave) {
                    onSave(bedtime, wakeTime, quality, notes.trim().ifEmpty { null })
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (editing == null) "Save" else "Update",
                style = HomeType.HeroButton,
                color = OmniOnInk,
                maxLines = 1,
            )
        }

        if (editing != null) {
            Spacer(Modifier.height(DeleteTop))
            Text(
                text = "Delete this night",
                style = HomeType.CardFootnote,
                color = OmniAlertRed,
                maxLines = 1,
                modifier = Modifier.sheetNoRipple(onClick = onDelete),
            )
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

/** One time column: a label, the clock reading, and the two stepper circles under it. */
@Composable
private fun TimeStepper(
    label: String,
    display: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TimeLabelGap),
    ) {
        Text(
            text = label,
            style = HomeType.CardFootnote,
            color = OmniSetGroupLabel,
            maxLines = 1,
        )
        Text(
            text = display,
            style = HomeType.CardValue,
            color = OmniCardInk,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(StepperGap)) {
            StepButton(glyph = "−", onClick = onDown)
            StepButton(glyph = "+", onClick = onUp)
        }
    }
}

/** A grey circle with a glyph — the home sleep sheet's own stepper button, rebuilt here. */
@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(StepButtonSize)
            .pressEffect()
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniFieldSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = HomeType.SectionTitle, color = OmniCardInk, maxLines = 1)
    }
}

/** One quality option — a pill that fills with ink when chosen, grey otherwise. */
@Composable
private fun QualityChip(
    quality: SleepQuality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) OmniInk else OmniFieldSurface,
        animationSpec = OmniMotion.fast(),
        label = "qualityChipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) OmniOnInk else OmniSetRowSubtitle,
        animationSpec = OmniMotion.fast(),
        label = "qualityChipText",
    )
    Box(
        modifier = modifier
            .height(QualityHeight)
            .pressEffect()
            .clip(RoundedCornerShape(QualityCorner))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = qualityLabel(quality),
            style = HomeType.Caption12,
            color = textColor,
            maxLines = 1,
        )
    }
}

// ---- Time helpers -------------------------------------------------------------------------------

/** Parses `"HH:mm"` into minutes-of-day, or `null` if it is absent or malformed. */
private fun minutesOfDay(clock: String?): Int? {
    if (clock == null) return null
    val parts = clock.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Minutes-of-day back to the `"HH:mm"` the record stores. */
private fun clockString(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

/** Minutes-of-day to a human 12-hour reading, e.g. `690 → "11:30 AM"`. */
private fun displayClock(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    val hour24 = m / 60
    val minute = m % 60
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%d:%02d %s".format(hour12, minute, suffix)
}

/** Steps down a quarter hour, wrapping past midnight so the ring is continuous. */
private fun stepDown(minutes: Int): Int = ((minutes - StepMinutes) % 1440 + 1440) % 1440

/** Steps up a quarter hour, wrapping past midnight. */
private fun stepUp(minutes: Int): Int = (minutes + StepMinutes) % 1440

/** "Poor" / "Fair" / "Good" / "Best" — the four grades in the user's own words. */
private fun qualityLabel(quality: SleepQuality): String = when (quality) {
    SleepQuality.POOR -> "Poor"
    SleepQuality.FAIR -> "Fair"
    SleepQuality.GOOD -> "Good"
    SleepQuality.EXCELLENT -> "Best"
}

// ---- Geometry -----------------------------------------------------------------------------------
//
// No Figma frame — the sheet is invented UI (§6 rule 11). The surface, gutter, grab handle and button
// are the home sleep sheet's to the dp; the rest is that sheet's vertical rhythm applied to more fields.

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 22.dp
private val TimesTop = 22.dp
private val TimeColumnGap = 16.dp
private val TimeLabelGap = 8.dp
private val StepperGap = 14.dp
private val DurationTop = 16.dp
private val QualityTop = 20.dp
private val QualityGap = 8.dp
private val NotesTop = 16.dp
private val NotesHeight = 64.dp
private val SaveTop = 22.dp
private val DeleteTop = 14.dp
private val SheetBottom = 20.dp

private val StepButtonSize = 44.dp
private val QualityHeight = 40.dp
private val QualityCorner = 12.dp
private val FieldCorner = 8.dp
private val FieldPadding = 14.dp
private val FieldPaddingV = 12.dp

/** The auth pages' button, to the dp. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** Fifteen minutes — the finest anyone honestly reports their own sleep. */
private const val StepMinutes = 15

/** 11:00 PM and 7:00 AM — the likeliest night, so a new entry opens near the answer. */
private const val DefaultBedtime = 23 * 60
private const val DefaultWakeTime = 7 * 60
