package com.example.omni.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniHeroInk
import com.example.omni.ui.theme.OmniHeroPink
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniStatLabel
import com.example.omni.ui.theme.OmniStepsCream
import com.example.omni.ui.theme.OmniWaterTeal

/*
 * The three bento tiles at the top of the dashboard — Figma `Frame 53` (the hero) and `Frame 52`
 * (the hydration / step-count pair).
 *
 * None of the three uses auto-layout in Figma: every child is absolutely positioned and several
 * deliberately bleed past the tile's edge, relying on the clip. So each tile is a clipped [Box].
 *
 * Those coordinates *are* copied verbatim, and they are only meaningful because [HomeScreen] lays the
 * whole page out inside a 415dp design frame (see `DesignFrame`). At that width the gutters leave the
 * bento 383, the weighted pair comes out at exactly 166 and 207, and every number below is the
 * design's own — 92 really is 92. Without that frame a 393dp phone gives the tiles 361 and every
 * absolute x in here is 5.7% too far right.
 *
 * The one thing the frame cannot fix is the face: Satoshi is not open-licensed so Manrope stands in
 * for it. Measured out of `manrope.ttf` it is within a few percent of Satoshi's advances rather than
 * the fifth this file once claimed, but it is *slightly* wider on most strings, so Figma's text boxes
 * are a couple of dp too tight. Text is therefore either sized to itself or given a box a little
 * wider than the design's, never the design's exact width.
 */

/**
 * "Emergency first aid guide" — Figma `Frame 53`, 383 x 169, radius 12.056.
 *
 * Three overlapping layers. The swirl (`Vector 2`) is given in percentages that resolve to a
 * 299.5 x 39.79 rect at (−7.01, 130.06) — it bleeds 7 off the left and 0.845 off the bottom, both
 * clipped. The kit illustration is 204 x 218.3 at x=182 in the source; its export is already clipped
 * to the tile, so the 201 x 169 drawable goes flush against the right edge. The text sits at x=19.
 *
 * The title's 123 is Figma's own and safe to keep: it is Plus Jakarta Sans, the real design face, and
 * "Emergency" measures 110.3 there — anything under 128 gives the design's three lines.
 */
@Composable
internal fun FirstAidCard(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(RoundedCornerShape(12.056.dp))
            .background(OmniHeroPink),
    ) {
        Image(
            painter = painterResource(R.drawable.home_hero_swirl),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-7.01).dp, y = 0.845.dp)
                .width(299.5.dp)
                .height(39.79.dp),
        )
        Image(
            painter = painterResource(R.drawable.home_hero_kit),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(201.dp)
                .height(HeroHeight),
        )

        // The hero's type is all Plus Jakarta Sans, which is the real Figma face rather than a
        // stand-in, so these three keep their exact Figma widths and wrap exactly as the design does.
        Column(modifier = Modifier.padding(start = 19.dp, top = 8.6116.dp)) {
            Text(
                text = "Emergency first aid guide",
                style = HomeType.HeroTitle,
                color = OmniHeroInk,
                modifier = Modifier.width(123.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 19.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "100% offline access",
                style = HomeType.HeroCaption,
                color = OmniInk,
                maxLines = 1,
                softWrap = false,
            )
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmniHeroInk)
                    .clickable(onClick = onExplore),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Explore!",
                    style = HomeType.HeroButton,
                    color = OmniHeroPink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(x = 0.5.dp),
                )
            }
        }
    }
}

/**
 * The hydration tile — Figma `Frame 28`, 166 x 159, radius 9.399.
 *
 * The swirl behind it (`Vector 3`) is drawn with negative coordinates in the source; the exported
 * drawable is already the tile's own viewport, so it simply fills the tile.
 *
 * `Frame 95`, the glasses, is Figma's own geometry down to the last number: a 152-wide column
 * centred in the 166 tile at y=92, two rows 10 apart, four 26dp glasses 16 apart (4x26 + 3x16 = 152).
 * Seven of twelve are filled, which is what the copy says, so the rows are literal rather than
 * generated — three filled then one empty, then four empty. The block ends at 154, five clear of the
 * bottom, and the text above it needs 8 + 12.924 + 6 + 29.373 + 6 + 15.861 = 78.2, so nothing
 * collides. The plus is at Figma's (140, 3).
 *
 * "keep going only 5 glasses left" is the only place the design's own box is not honoured. Measured
 * out of `manrope.ttf` at Figma's 11sp with its −0.055 tracking the sentence wants 146.86 and the
 * source box is 142, so it overflowed the design itself. Dropping the text column's end inset from
 * Figma's 11 to 2 gives it 151 and it stays on one line at Figma's own size.
 */
@Composable
internal fun WaterCard(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(StatCardHeight)
            .clip(RoundedCornerShape(9.399.dp))
            .background(OmniWaterTeal),
    ) {
        Image(
            painter = painterResource(R.drawable.home_water_swirl),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.padding(start = 13.dp, top = 8.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 26 of end padding keeps the label clear of the add button above it.
            Text(
                text = "Water today",
                style = HomeType.WaterLabel,
                color = OmniStatLabel,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 26.dp),
            )
            Text(
                text = buildAnnotatedString {
                    // The leading space is Figma's, and it is what keeps the "7" off the tile edge.
                    append(" 7")
                    withStyle(HomeType.WaterUnit.toSpanStyle()) { append("/12 glasses") }
                },
                style = HomeType.WaterValue,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = buildAnnotatedString {
                    append("keep going only ")
                    withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) {
                        append("5 ")
                    }
                    append("glasses left")
                },
                style = HomeType.WaterHint,
                color = OmniStatLabel,
                maxLines = 1,
                softWrap = false,
            )
        }

        // `Frame 95` — a 152-wide band centred in the tile at y=92, which is 7 a side.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 92.dp)
                .width(GlassBandWidth),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WaterGlassRow(filled = 3)
            WaterGlassRow(filled = 0)
        }

        Image(
            painter = painterResource(R.drawable.home_plus_glow),
            contentDescription = "Add a glass of water",
            modifier = Modifier
                .offset(x = 140.dp, y = 3.dp)
                .size(24.dp)
                .clickable(onClick = onAdd),
        )
    }
}

@Composable
private fun WaterGlassRow(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { index ->
            Image(
                painter = painterResource(
                    if (index < filled) R.drawable.ic_home_water_glass_fill
                    else R.drawable.ic_home_water_glass,
                ),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** 4 x 26 glasses with 3 x 16 between them — Figma `Frame 95` is 152 wide for exactly this reason. */
private val GlassBandWidth = 152.dp

/**
 * The step-count tile — Figma `Frame 51`, 207 x 159, radius 9.377.
 *
 * Four absolutely positioned children, all at the design's own coordinates: the dark footprint rail
 * (`Frame 15`, 30 x 140 at 11, 8), the three texts sharing x=53 at y=19 / 42 / 92, and the runner.
 *
 * The runner (`Object`) is 107 x 150 at (144, 9), so 44 of it hangs past the tile and is clipped
 * away. The exported drawable is already that clip — 63 x 150 — hence the end alignment.
 *
 * Figma's y-gaps are not uniform (8.352 between label and value, 16.71 between value and hint), so
 * the text column spaces itself with explicit spacers rather than one arrangement gap.
 *
 * The hint is the one child not given its Figma width. In the design's 97 Manrope breaks it into four
 * lines; the design shows three, and any box from 99 to 123 produces exactly the design's three
 * breaks, so it gets the middle of that window. 53 + 110 = 163 overlaps the runner's transparent left
 * edge by 19, which is the same kind of overlap the design has at 150.
 */
@Composable
internal fun StepsCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(StatCardHeight)
            .clip(RoundedCornerShape(9.377.dp))
            .background(OmniStepsCream),
    ) {
        Image(
            painter = painterResource(R.drawable.home_steps_track),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = 11.dp, y = 8.dp)
                .width(30.dp)
                .height(140.dp),
        )

        Image(
            painter = painterResource(R.drawable.home_steps_runner),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = 9.dp)
                .width(63.dp)
                .height(150.dp),
        )

        Column(modifier = Modifier.offset(x = 53.dp, y = 19.dp)) {
            Text(
                text = "Today’s steps",
                style = HomeType.StepsLabel,
                color = OmniStatLabel,
                maxLines = 1,
                softWrap = false,
            )

            // 42 − (19 + 14.648)
            Spacer(Modifier.height(8.352.dp))

            Text(
                text = buildAnnotatedString {
                    append("5,600")
                    withStyle(HomeType.StepsUnit.toSpanStyle()) { append("/10,000") }
                },
                style = HomeType.StepsValue,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )

            // 92 − (42 + 33.29)
            Spacer(Modifier.height(16.71.dp))

            Text(
                text = buildAnnotatedString {
                    append("You’re ")
                    withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) {
                        append("78%")
                    }
                    append(" towards your daily goal,just keep moving")
                },
                style = HomeType.StepsHint,
                color = OmniStatLabel,
                maxLines = 3,
                modifier = Modifier.width(StepsHintWidth),
            )
        }
    }
}

/** Figma's box is 97, which breaks the sentence into four lines; 99..123 all give the design's three. */
private val StepsHintWidth = 110.dp

/** Figma `Frame 53` is 169 tall. */
internal val HeroHeight = 169.dp

/**
 * Figma's height for both stat tiles, and both tiles' contents fit inside it.
 *
 * The hydration tile spends 8 + 12.924 + 6 + 29.373 + 6 + 15.861 = 78.2 on text and puts the glass
 * band at 92..154. The step tile's hint starts at 92 and takes 3 x 14.551, ending at 135.7.
 */
internal val StatCardHeight = 159.dp
