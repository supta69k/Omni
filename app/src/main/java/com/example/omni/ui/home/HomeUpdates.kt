package com.example.omni.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
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
 * The three "Daily Updates" cards — Figma `Frame 82`, `Frame 101` and `Frame 120` inside `Frame 74`.
 *
 * Unlike the bento tiles these are genuine stacks, so they are built as columns. Each is 354 wide
 * inside a 15 gutter, and the vertical padding and inner gap differ per card (6/6, 7/5, 5/5) — those
 * three pairs are what make all three cards come out at the heights Figma reports.
 *
 * The progress bars are the one place where absolute Figma coordinates become weights: the filled
 * segment sits at a measured offset inside the track, and expressing that offset as a weight ratio
 * reproduces the design exactly at 415dp wide while still scaling on other screens.
 *
 * No text keeps a Figma width, and the reason is narrower than it once looked. Measured out of
 * `manrope.ttf`, the stand-in tracks Satoshi's advances to within a few percent — the fibre footnote
 * comes out at 194.1 against the source's 194 — but "within a few percent" is still over: the fibre
 * heading wants 174.7 of its 172 box and "Action Needed" 81.2 of its 79. Every one of those boxes is
 * a dp or two short, so each row sizes to its contents instead, and where two children compete for
 * one row the fixed one wins and the flexible one takes the remainder.
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
 */
@Composable
private fun UpdateProgress(
    trackHeight: Dp,
    lead: Float,
    fill: Float,
    tail: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(OmniProgressTrack),
    ) {
        if (lead > 0f) Spacer(Modifier.weight(lead))
        Box(
            modifier = Modifier
                .weight(fill)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(colorStops = OmniProgressStops.toTypedArray())),
        )
        if (tail > 0f) Spacer(Modifier.weight(tail))
    }
}

/**
 * The 8dp dot and its word — Figma's only indication of how each metric is doing.
 *
 * The words carried Figma's measured widths (31 / 19 / 79) and "Action Needed" — 81.2 in Manrope —
 * lost its tail; they size themselves now and refuse to wrap, so the chip is always whole and the
 * footnote beside it gets whatever is left.
 */
@Composable
private fun StatusChip(dot: Int, label: String) {
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
 * "Increase Your Daily Fiber" — Figma `Frame 82`, 99.548 tall.
 *
 * Its axis row is 346 wide rather than the card's 354, and carries a small marker triangle at
 * x=183.833 that points at the end of the filled segment. Node `1:100`, a second triangle at
 * (198, 62.73), has no image source and renders nothing in Figma's own output, so it is skipped.
 */
@Composable
internal fun FiberCard(modifier: Modifier = Modifier) {
    UpdateCard(verticalPadding = 6.dp, innerGap = 6.dp, modifier = modifier) {
        UpdateHeader(title = "Increase Your Daily Fiber", value = "18/g", glyph = "🍃")

        UpdateProgress(trackHeight = 9.083.dp, lead = 155.5144f, fill = 72.6416f, tail = 125.8440f)

        Row(
            modifier = Modifier.fillMaxWidth().height(19.464.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("0", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
            Spacer(Modifier.weight(175.8331f))
            Image(
                painter = painterResource(R.drawable.ic_home_marker_arrow_sm),
                contentDescription = null,
                modifier = Modifier.width(8.333.dp).height(6.239.dp),
            )
            Spacer(Modifier.weight(135.8339f))
            Text("31 g", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
        }

        // Figma's 16-tall row and 194-wide footnote both needed loosening: the sentence measures
        // 194.1 in Manrope, a tenth over its box, and a 16 row is shorter than the 15.861 line plus
        // its descenders. The footnote takes what the chip leaves and the row sizes to its contents.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildAnnotatedString {
                    append("You’re ")
                    withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("13g") }
                    append(" away from your Goal")
                },
                style = HomeType.CardFootnote,
                color = OmniFootnote,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(R.drawable.ic_home_dot_good, "Good")
        }
    }
}

/**
 * "Optimize Your Sleep" — Figma `Frame 101`, 99.548 tall.
 *
 * The footnote here uses a straight apostrophe where the fibre card uses a curly one. That is the
 * design's own inconsistency and both are reproduced verbatim rather than normalised.
 */
@Composable
internal fun SleepCard(modifier: Modifier = Modifier) {
    UpdateCard(verticalPadding = 7.dp, innerGap = 5.dp, modifier = modifier) {
        UpdateHeader(title = "Optimize Your Sleep", value = "6.5/h", glyph = "🌙", glyphWidth = 16.dp)

        UpdateProgress(trackHeight = 9.083.dp, lead = 196.4393f, fill = 72.6416f, tail = 84.9191f)

        Row(
            modifier = Modifier.fillMaxWidth().height(20.464.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("0", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
            Spacer(Modifier.weight(215f))
            Image(
                painter = painterResource(R.drawable.ic_home_marker_arrow),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.weight(95f))
            Text("8/h", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
            Spacer(Modifier.weight(8f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildAnnotatedString {
                    append("You're ")
                    withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("1.5h") }
                    append(" away from your Goal")
                },
                style = HomeType.CardFootnoteSleep,
                color = OmniFootnote,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(R.drawable.ic_home_dot_fair, "Fair")
        }
    }
}

/**
 * "Learn Basic CPR Today" — Figma `Frame 120`, 114.131 tall.
 *
 * The odd one out: nothing has been done yet, so the bar starts at 0 and there is no marker on the
 * axis, and its footnote is the only one that wraps to two lines — hence the taller 34 row and the
 * card's extra 14.583 of height.
 */
@Composable
internal fun CprCard(modifier: Modifier = Modifier) {
    UpdateCard(verticalPadding = 5.dp, innerGap = 5.dp, modifier = modifier) {
        UpdateHeader(title = "Learn Basic CPR Today", value = "0%", glyph = null)

        UpdateProgress(trackHeight = 10.667.dp, lead = 0f, fill = 72.6416f, tail = 281.3584f)

        Row(
            modifier = Modifier.fillMaxWidth().height(19.464.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
            Text("100%", style = HomeType.AxisLabel, color = OmniTabInactive, maxLines = 1)
        }

        // "Action Needed" is the widest chip of the three, so this is the row where the footnote has
        // least to work with — 260 of the 353 inner width. The sentence needs 334.7 on one line, so it
        // still breaks into the two the design shows, just at a later word than Figma's 206 box picks.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildAnnotatedString {
                    append("Be prepared for an ")
                    withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("emergency.") }
                    append(" Read the ")
                    withStyle(SpanStyle(color = OmniFootnoteStrong)) { append("2-min") }
                    append(" guide.")
                },
                style = HomeType.CardFootnoteWrapped,
                color = OmniFootnote,
                maxLines = 3,
                modifier = Modifier.weight(1f, fill = false),
            )
            StatusChip(R.drawable.ic_home_dot_action, "Action Needed")
        }
    }
}
