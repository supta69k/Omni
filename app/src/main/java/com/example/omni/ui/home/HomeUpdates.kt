package com.example.omni.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.domain.Status
import com.example.omni.domain.formatAmount
import com.example.omni.domain.progressBarOf
import com.example.omni.domain.progressOf
import com.example.omni.domain.remainingTo
import com.example.omni.domain.statusFor
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniCardSurface
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniFootnoteStrong
import com.example.omni.ui.theme.OmniProgressStops
import com.example.omni.ui.theme.OmniProgressTrack
import com.example.omni.ui.theme.OmniStatusLabel
import com.example.omni.ui.theme.OmniTabInactive

/*
 * The three "Daily updates & Recomindation" cards — Figma `Card container` nodes 149:301, 149:320 and
 * 149:339 inside the dashboard's card stack. The redesign moved them (and renamed the section above
 * them) but kept every internal measurement, so these three are unchanged from the previous frame.
 *
 * Unlike the bento tiles these are genuine stacks, so they are built as columns. Each is 354 wide
 * inside a 15 gutter, and the vertical padding and inner gap differ per card (6/6, 7/5, 5/5) — those
 * three pairs are what make all three cards come out at the heights Figma reports.
 *
 * The progress bars were the one place where absolute Figma coordinates became weights, with each bar's
 * three widths hand-placed from the design. They are now computed from one number per card by
 * `domain/Recommendations.kt` — see [com.example.omni.domain.progressBarOf] for why the design's own
 * measurements gave the rule away, and BACKEND_PLAN §3.3 for the arithmetic. The same holds for the status
 * chips: one threshold rule replaces three hand-picked labels, which **flips two of them** (see below).
 *
 * No text keeps a Figma width, and the reason is narrower than it once looked. Measured out of
 * `manrope.ttf`, the stand-in tracks Satoshi's advances to within a few percent — the fibre footnote
 * comes out at 194.1 against the source's 194 — but "within a few percent" is still over: the fibre
 * heading wants 174.7 of its 172 box and "Action Needed" 81.2 of its 79. Every one of those boxes is
 * a dp or two short, so each row sizes to its contents instead, and where two children compete for
 * one row the fixed one wins and the flexible one takes the remainder.
 *
 * That slack is also what absorbs live data. The three footnotes are the only strings here that grow, and
 * each of them shrinks or holds when it changes: a remainder is at most three characters where the design
 * drew two, and the "goal met" variants are shorter outright than the sentences they replace. The one row
 * that can wrap to a second line is the fibre footnote at 0g, where "Action Needed" is the widest chip and
 * the sentence is at its longest — and that is fine, because nothing here has a fixed height. `UpdateCard`
 * is a `Column` with padding, the footnote rows size to their contents, and the page scrolls, so the card
 * grows by one line rather than clipping. The CPR card already ships that two-line look by design.
 */

/** The shell every update card shares: a #F5F5F5 rounded rect with a 15 gutter. */
@Composable
private fun UpdateCard(
    verticalPadding: Dp,
    innerGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniCardSurface)
            .padding(horizontal = 15.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(innerGap),
        content = content,
    )
}

/**
 * Heading on the left, the reading and its glyph on the right.
 *
 * Figma gives the heading a fixed 172, measured in Satoshi. Manrope needs 174.7 for the longest of the
 * three, so at 172 "Increase Your Daily Fiber" lost its last letters. The heading is weighted with
 * `fill = false` instead: it takes its natural width, [Arrangement.SpaceBetween] still pushes the
 * reading to the right edge, and if it ever does run out of room it shrinks rather than shoving the
 * reading off the card. The row's 25 becomes a minimum for the same reason.
 */
@Composable
private fun UpdateHeader(
    title: String,
    value: String,
    glyph: String?,
    glyphWidth: Dp? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = HomeType.CardTitle,
            color = OmniCardInk,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = HomeType.CardValue,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )
            if (glyph != null) {
                Text(
                    text = glyph,
                    style = HomeType.CardTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                    modifier = if (glyphWidth != null) Modifier.width(glyphWidth) else Modifier,
                )
            }
        }
    }
}

/**
 * The white track and its gradient segment.
 *
 * [lead], [fill] and [tail] are the Figma widths in the 354-wide track, passed as weights so their
 * ratio survives any screen width. The gradient is the same four stops on all three cards; Figma
 * angles it at 89.587° (89.648° on the CPR card), which is horizontal to within half a degree.
 *
 * **Phase 16 motion:** the fill width is animated via [animateFloatAsState] using the measured track
 * width from [BoxWithConstraints], so progress changes glide smoothly rather than jumping.
 */
@Composable
private fun UpdateProgress(
    trackHeight: Dp,
    lead: Float,
    fill: Float,
    tail: Float,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(OmniProgressTrack),
    ) {
        val trackPx = with(density) { maxWidth.toPx() }
        val puckPx = trackPx * fill / (lead + fill + tail)
        val leadPx = trackPx * lead / (lead + fill + tail)
        val fillWidthPx = leadPx + puckPx

        Box(
            modifier = Modifier
                .fillMaxWidth(fillWidthPx / trackPx)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(colorStops = OmniProgressStops.toTypedArray())),
        )
    }
}

/**
 * Same bar as [UpdateProgress] but with smooth progress animation.
 *
 * Pass [progress] as a float in 0..1. The fill glides to its new position whenever the value changes,
 * using [OmniMotion.progress] timing (600ms, standard easing).
 */
@Composable
private fun AnimatedUpdateProgress(
    trackHeight: Dp,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = OmniMotion.progress(),
        label = "updateProgress",
    )
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(OmniProgressTrack),
    ) {
        val trackPx = with(density) { maxWidth.toPx() }
        val puckPx = TrackPx * PuckRatio
        val travelPx = trackPx - puckPx
        val leadPx = animatedProgress * travelPx
        val fillWidthPx = leadPx + puckPx

        Box(
            modifier = Modifier
                .fillMaxWidth(fillWidthPx / trackPx)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(colorStops = OmniProgressStops.toTypedArray())),
        )
    }
}

/** Design constants for the animated progress bar. */
private const val TrackPx = 354f
private const val PuckRatio = 72.6416f / 354f

/**
 * The 8dp dot and its word — Figma's only indication of how each metric is doing.
 *
 * The words carried Figma's measured widths (31 / 19 / 79) and "Action Needed" — 81.2 in Manrope —
 * lost its tail; they size themselves now and refuse to wrap, so the chip is always whole and the
 * footnote beside it gets whatever is left.
 *
 * Which of the three shows is [com.example.omni.domain.statusFor]'s decision, not the card's, which is why
 * this takes a [Status] rather than a drawable and a string. The design's own pairings could not survive
 * that: it labels 58% fibre "Good" and 81% sleep "Fair", so one rule inevitably swaps them.
 */
@Composable
private fun StatusChip(status: Status) {
    val dot = when (status) {
        Status.Good -> R.drawable.ic_home_dot_good
        Status.Fair -> R.drawable.ic_home_dot_fair
        Status.ActionNeeded -> R.drawable.ic_home_dot_action
    }
    val label = when (status) {
        Status.Good -> "Good"
        Status.Fair -> "Fair"
        Status.ActionNeeded -> "Action Needed"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(dot),
            contentDescription = null,
            modifier = Modifier.size(8.dp),
        )
        Text(
            text = label,
            style = HomeType.Caption12,
            color = OmniStatusLabel,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The axis under a progress bar: `0` at one end, the goal at the other, and the marker triangle in between.
 *
 * The marker used to be placed by two weighted spacers carrying Figma's own gap widths, which worked only
 * because those gaps had the axis labels' widths already subtracted out of them — and the goal label's
 * width is exactly what stops being a constant once the goal is a parameter. So the row measures itself
 * instead: [BoxWithConstraints] hands back the real width, and the triangle is offset to
 * `markerFraction` of it. Because both this row and the bar above it are `fillMaxWidth`, the marker now
 * lands on the puck's centre *exactly* rather than to within the label-width fudge the design carried.
 *
 * @param marker `null` on a card the design gives no triangle. Only the CPR card, whose axis is a plain
 *   0–100% scale — the bar itself is the position there, and inventing a triangle for it would be adding a
 *   component the source does not draw.
 * @param endInset the sleep card's own 8 of trailing space after its label. Zero on the other two.
 */
@Composable
private fun UpdateAxis(
    height: Dp,
    endLabel: String,
    marker: AxisMarker? = null,
    endInset: Dp = 0.dp,
    startLabel: String = "0",
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(startLabel, style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)

        if (marker != null) {
            Image(
                painter = painterResource(marker.drawable),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = maxWidth * marker.fraction - marker.width / 2)
                    .width(marker.width)
                    .height(marker.height),
            )
        }

        Text(
            text = endLabel,
            style = HomeType.AxisLabel,
            color = OmniTabInactive,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = endInset),
        )
    }
}

/**
 * One axis marker: which triangle, how big, and where along the row its centre goes.
 *
 * The two cards that have one use different assets at different sizes (8.333 x 6.239 against a square 10),
 * which is Figma's own inconsistency and is reproduced rather than normalised.
 */
private class AxisMarker(
    val drawable: Int,
    val width: Dp,
    val height: Dp,
    val fraction: Float,
)

/**
 * "Increase Your Daily Fiber" — Figma `Frame 82`, 99.548 tall.
 *
 * Its axis row is 346 wide in the source rather than the card's 354, and carries a small marker triangle at
 * x=183.833. Node `1:100`, a second triangle at (198, 62.73), has no image source and renders nothing in
 * Figma's own output, so it is skipped.
 *
 * [grams] reads 0 in the running app until Phase 6 starts rolling up meals, so the state this card is
 * *usually* in today is the one the design never drew: nothing logged, "Action Needed", and the footnote at
 * its longest. That is deliberate — an empty fibre log is worth saying out loud — and the default keeps the
 * preview on the design's own 18/31g.
 *
 * The chip is the divergence to record (`UI_ARCHITECTURE.md` §6 rule 9): 18/31g is 58%, which the shared
 * threshold rule calls **Fair**, where Figma drew "Good". Since the same rule calls the sleep card's 81%
 * "Good" where Figma drew "Fair", the two labels are simply swapped in the source and no single rule can
 * reproduce both.
 */
@Composable
internal fun FiberCard(
    grams: Float = 18f,
    goalGrams: Float = DefaultFiberGoal,
    modifier: Modifier = Modifier,
) {
    val progress = progressOf(grams, goalGrams)
    val bar = progressBarOf(progress)

    UpdateCard(verticalPadding = 6.dp, innerGap = 6.dp, modifier = modifier) {
        UpdateHeader(
            title = "Increase Your Daily Fiber",
            // Figma's own "18/g", slash included, rather than the "18g" it reads as.
            value = "${formatAmount(grams)}/g",
            glyph = "🍃",
        )

        AnimatedUpdateProgress(trackHeight = 9.083.dp, progress = progress)

        UpdateAxis(
            height = 19.464.dp,
            endLabel = "${formatAmount(goalGrams)} g",
            marker = AxisMarker(
                drawable = R.drawable.ic_home_marker_arrow_sm,
                width = 8.333.dp,
                height = 6.239.dp,
                fraction = bar.markerFraction,
            ),
        )

        // Figma's 16-tall row and 194-wide footnote both needed loosening: the sentence measures
        // 194.1 in Manrope, a tenth over its box, and a 16 row is shorter than the 15.861 line plus
        // its descenders. The footnote takes what the chip leaves and the row sizes to its contents.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = remainderFootnote(
                    remaining = remainingTo(grams, goalGrams),
                    unit = "g",
                    apostrophe = Curly,
                ),
                style = HomeType.CardFootnote,
                color = OmniFootnote,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(statusFor(progress))
        }
    }
}

/**
 * "Optimize Your Sleep" — Figma `Frame 101`, 99.548 tall.
 *
 * The footnote here uses a straight apostrophe where the fibre card uses a curly one. That is the
 * design's own inconsistency and both are reproduced verbatim rather than normalised.
 *
 * The only card with an input behind it: nothing in the app or the phone reports sleep (BACKEND_PLAN §3.5),
 * so [onLog] opens `SleepEntrySheet` and the number is typed. With no entry yet [hours] is 0 and the card
 * says so, which is the honest state rather than the design's mock 6.5.
 *
 * Its chip is the other half of the swap recorded on [FiberCard]: 6.5/8h is 81%, which the shared rule
 * calls **Good**, where Figma drew "Fair".
 */
@Composable
internal fun SleepCard(
    hours: Float = 6.5f,
    goalHours: Float = DefaultSleepGoal,
    onLog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val progress = progressOf(hours, goalHours)
    val bar = progressBarOf(progress)

    UpdateCard(
        verticalPadding = 7.dp,
        innerGap = 5.dp,
        // The whole card is the target, not a button inside it: there is no button in the design, and this
        // is the only card whose tap does something, so the tap has to be the card. Unlike `StepsCard` it is
        // always live — logging sleep is never a no-op (§2a rule 4 is about dead taps, not about state).
        modifier = modifier.pressEffect().clickable(onClick = onLog),
    ) {
        UpdateHeader(
            title = "Optimize Your Sleep",
            value = "${formatAmount(hours)}/h",
            glyph = "🌙",
            glyphWidth = 16.dp,
        )

        AnimatedUpdateProgress(trackHeight = 9.083.dp, progress = progress)

        UpdateAxis(
            height = 20.464.dp,
            endLabel = "${formatAmount(goalHours)}/h",
            marker = AxisMarker(
                drawable = R.drawable.ic_home_marker_arrow,
                width = 10.dp,
                height = 10.dp,
                fraction = bar.markerFraction,
            ),
            endInset = 8.dp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = remainderFootnote(
                    remaining = remainingTo(hours, goalHours),
                    unit = "h",
                    apostrophe = Straight,
                ),
                style = HomeType.CardFootnoteSleep,
                color = OmniFootnote,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(statusFor(progress))
        }
    }
}

/**
 * "Learn Basic CPR Today" — Figma `Frame 120`, 114.131 tall.
 *
 * The odd one out: the design draws it at 0%, so there is no marker on its axis, and its footnote is the
 * only one that wraps to two lines — hence the taller 34 row and the card's extra 14.583 of height. A "0%"
 * card still shows a coloured band, because the puck is a fixed width that slides rather than a fill that
 * grows (BACKEND_PLAN §3.3). That is the design's intent, not a bug.
 *
 * [percent] stays 0 until Phase 7 ships the guides, so the two states above 0 are ones no build can reach
 * yet. They exist anyway: the design has copy for "you have not started" only, and a card that kept telling
 * someone to "read the 2-min guide" after they had read it would be worse than a plain sentence.
 */
@Composable
internal fun CprCard(
    title: String = "Learn Basic CPR Today",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    UpdateCard(
        verticalPadding = 12.dp,
        innerGap = 8.dp,
        // Tappable when the dashboard hands in a target guide — the whole card opens it, like SleepCard.
        modifier = if (onClick != null) modifier.pressEffect().clickable(onClick = onClick) else modifier,
    ) {
        Text(
            text = title,
            style = HomeType.CardTitle,
            color = OmniCardInk,
            maxLines = 1,
        )

        // No progress bar or axis here: this card rotates to a different first-aid guide each day, so a
        // percentage read-out would be noise. It is a recommendation, not a tracker.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = cprFootnote(0),
                style = HomeType.CardFootnoteWrapped,
                color = OmniFootnote,
                maxLines = 3,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(Status.ActionNeeded)
        }
    }
}

/**
 * "You’re **13g** away from your Goal", and what it becomes once there is nothing left to owe.
 *
 * Shared by the two cards that count towards a number, because the sentence is the same sentence — only
 * [unit] and the design's inconsistent [apostrophe] differ.
 *
 * The met variant is new copy for a state the design never drew, and it is deliberately **shorter** than
 * the sentence it replaces (25 characters against 30), so the row it lands in cannot overflow a layout that
 * already fits the longer one.
 */
private fun remainderFootnote(remaining: Float, unit: String, apostrophe: Char): AnnotatedString =
    buildAnnotatedString {
        if (remaining <= 0f) {
            append("You${apostrophe}re at your daily Goal")
            return@buildAnnotatedString
        }
        append("You${apostrophe}re ")
        withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("${formatAmount(remaining)}$unit") }
        append(" away from your Goal")
    }

/** The CPR card's sentence, which is three sentences: not started, part way, done. */
private fun cprFootnote(percent: Int): AnnotatedString = buildAnnotatedString {
    when {
        percent <= 0 -> {
            append("Be prepared for an ")
            withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("emergency.") }
            append(" Read the ")
            withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("2-min") }
            append(" guide.")
        }

        percent >= 100 -> {
            append("Guide finished. ")
            withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("You${Curly}re prepared.") }
        }

        else -> {
            append("You${Curly}ve read ")
            withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("$percent%") }
            append(" of the guide.")
        }
    }
}

/** The two apostrophes the design uses, named so a call site reads as a choice rather than a typo. */
private const val Curly = '’'
private const val Straight = '\''

