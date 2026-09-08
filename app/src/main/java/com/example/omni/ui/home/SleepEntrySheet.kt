package com.example.omni.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.domain.formatAmount
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniScrim
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface
import kotlin.math.roundToInt

/**
 * How last night's hours get into the app — the one piece of UI here with no Figma node behind it.
 *
 * It exists because sleep is the only metric in the dashboard that nothing can observe: water is tapped,
 * steps come off the pedometer, fibre will come off Phase 6's meals, and sleep has to be *stated*
 * (BACKEND_PLAN §3.5). The design draws the reading but never a way to give it, so this is invented UI, and
 * it is built out of parts the design already owns — the hospitals sheet's surface, its shadow, its grab
 * handle, and the same reveal `SosScreen` uses — so that inventing it adds no new vocabulary.
 *
 * Hand-built rather than Material3's `ModalBottomSheet` for two reasons: that component is still behind an
 * experimental opt-in, and it hoists its content into a separate window, which would leave it outside
 * [com.example.omni.ui.DesignFrame]'s density and undo every measurement on this page.
 *
 * The input is a stepper in half-hours, not a text field. Half an hour is as fine as anyone can honestly
 * report their own sleep; a field would need a keyboard, a decimal separator that changes by locale, and a
 * rule for what "abc" means, and all three would be work spent on precision that is not really there.
 *
 * @param hours what is already recorded for today, so re-opening the sheet corrects rather than restarts.
 * @param onSave the chosen value. Nothing is written until this fires — the stepper is local until then, so
 *   a dismissed sheet leaves the day's document alone.
 */
@Composable
internal fun SleepEntrySheet(
    visible: Boolean,
    hours: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The dim is its own layer so it fades while the sheet slides, and it is what swallows taps meant
        // for the page underneath — including the sleep card itself, which would otherwise re-open this.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetMillis)),
            exit = fadeOut(tween(SheetMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniScrim)
                    .noRipple(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SheetMillis)) { it } + fadeIn(tween(SheetMillis)),
            exit = slideOutVertically(tween(SheetMillis)) { it } + fadeOut(tween(SheetMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SleepSheet(
                initial = hours,
                onDismiss = onDismiss,
                onSave = onSave,
            )
        }
    }
}

/** The sheet proper: handle, heading, the stepper, and the one button that writes anything. */
@Composable
private fun SleepSheet(
    initial: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
) {
    // Read once, on the frame the sheet appears, and thereafter the stepper is the only thing that moves it:
    // a keyless `remember` inside content the reveal disposes when hidden means an edit in progress ignores
    // the day's document changing underneath it, while the next open starts from whatever was really saved.
    // An empty log opens at the goal rather than at zero: eight is both the likeliest answer and the axis
    // label on the card, and starting at 0 would cost sixteen taps to reach it.
    var chosen by remember { mutableFloatStateOf(onGrid(if (initial > 0f) initial else DefaultSleepGoal)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            // A tap on the sheet's own blank surface would otherwise fall through to the scrim behind it
            // and close the sheet mid-edit. This consumes it and does nothing, which is the point.
            .noRipple(onClick = {})
            .navigationBarsPadding()
            .padding(horizontal = SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GrabTop))
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Close",
            modifier = Modifier.width(59.dp).height(5.dp).noRipple(onClick = onDismiss),
        )

        Spacer(Modifier.height(HeadingTop))
        Text(
            text = "How long did you sleep?",
            style = HomeType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Last night, to the nearest half hour.",
            style = HomeType.CardFootnote,
            color = OmniFootnote,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        Spacer(Modifier.height(StepperTop))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StepperGap),
        ) {
            StepButton(glyph = "−", enabled = chosen > SleepMin) {
                chosen = (chosen - SleepStep).coerceAtLeast(SleepMin)
            }

            // The reading and its unit are the card's own pair, so the number someone sets here looks like
            // the number they will read back off the dashboard.
            Row(
                modifier = Modifier.width(ValueWidth),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(formatAmount(chosen), style = HomeType.StepsValue, color = OmniCardInk, maxLines = 1)
                Text(" h", style = HomeType.StepsUnit, color = OmniFootnote, maxLines = 1)
            }

            StepButton(glyph = "+", enabled = chosen < SleepMax) {
                chosen = (chosen + SleepStep).coerceAtMost(SleepMax)
            }
        }

        Spacer(Modifier.height(SaveTop))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .clip(RoundedCornerShape(ButtonCorner))
                .background(OmniInk)
                .clickable { onSave(chosen) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Save", style = HomeType.HeroButton, color = OmniOnInk, maxLines = 1)
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

/**
 * One end of the stepper: a grey circle with a glyph, greyed out at the range's edge.
 *
 * Text glyphs rather than drawables — "−" and "+" are the whole of the artwork, and adding two vector
 * assets for two characters the font already has would be two more files to keep in step with the palette.
 */
@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(StepButtonSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniFieldSurface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = HomeType.SectionTitle,
            color = if (enabled) OmniCardInk else OmniFootnote,
            maxLines = 1,
        )
    }
}

/** Snaps a stored value onto the stepper's half-hour grid, so `+` always lands on a printable number. */
private fun onGrid(hours: Float): Float =
    if (hours.isFinite()) (hours * 2f).roundToInt() / 2f else DefaultSleepGoal

/**
 * A tap with no ink — for the three places here that are hit targets without being buttons: the scrim, the
 * grab handle, and the sheet's own surface swallowing what the scrim would otherwise catch.
 */
@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** Rounded at the top only: the sheet's other three corners are off the bottom of the screen. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The hospitals sheet's own gutter and handle inset, borrowed so the two sheets read as one component. */
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp

private val HeadingTop = 22.dp
private val StepperTop = 22.dp
private val StepperGap = 20.dp
private val SaveTop = 24.dp
private val SheetBottom = 20.dp

private val StepButtonSize = 48.dp

/** Wide enough for "12" plus its unit, so stepping does not shuffle the two buttons sideways. */
private val ValueWidth = 96.dp

/** The auth pages' button, to the dp — this is the same kind of commitment in the same app. */
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

private const val SleepMin = 0f

/** Twelve, the same ceiling `FirestoreUserRepository` puts on the goal. Past it, it is not last night. */
private const val SleepMax = 12f

private const val SleepStep = 0.5f

/** `SosScreen`'s own reveal timing, since this is the same motion. */
private const val SheetMillis = 280
