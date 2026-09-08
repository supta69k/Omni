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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniHeroInk
import com.example.omni.ui.theme.OmniHeroPink
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniStatLabel
import com.example.omni.ui.theme.OmniStepsCream
import com.example.omni.ui.theme.OmniWaterTeal
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The three bento tiles at the top of the dashboard — the hero (Figma `Hero section background`,
 * node 149:449) and the hydration / step-count pair (`Goal progress`, nodes 149:252 and 149:289).
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
 * "Emergency first aid guide" — Figma `Hero section background` (node 181:476) + `Hero content`,
 * 383 x 169.
 *
 * The card is the `Subtract` vector: the rounded shape with the notch and hairline already cut into
 * its top edge, clipped at the shape's own 16.5596 radius. The kit illustration is a baked band —
 * the source artwork cover-fit into the 204 x 219 frame at (177, −20) and pre-cropped to the
 * 204 x 169 the card actually shows — so no child here exceeds the tile it lives in. (Drawing the
 * full 204 x 219 image from y = −20 was silently rescaled by the card's height constraint, which
 * is why the band exists; see the note in the tile's source.) The text column is the design's 113
 * box, item-centred in that same overhanging row — height 73 + 4 + 21 + 14 + 34 = 146 inside the
 * image's 219 — which puts its top at −20 + (219 − 146) / 2 = 16.5.
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
            .clip(RoundedCornerShape(16.5596.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.home_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        // The wavy contour lines along the card's foot — 299.884 x 40.018 at (−6, 122), Figma
        // node 181:482's own coordinates.
        Image(
            painter = painterResource(R.drawable.home_hero_waves),
            contentDescription = null,
            modifier = Modifier
                .offset(x = (-6).dp, y = 122.dp)
                .width(299.884.dp)
                .height(40.018.dp),
        )
        // The hairline inside the notch — 32.5 of line plus its round caps, centred at x = 194.25
        // and y = 3, which for the 34.5 x 2 export is (177, 2).
        Image(
            painter = painterResource(R.drawable.home_hero_tab),
            contentDescription = null,
            modifier = Modifier
                .offset(x = 177.dp, y = 2.dp)
                .width(34.5.dp)
                .height(2.dp),
        )
        // The kit illustration is baked to the exact visible band: the 959 x 1024 source cover-fit
        // into 204 x 219 at (177, −20), pre-cropped to the rows the card actually shows — so the
        // tile needs no bleeding child at all (the whole 204 x 219 drawing used to be silently
        // rescaled by the card's own height constraint; a plain in-bounds child cannot be).
        Image(
            painter = painterResource(R.drawable.home_hero_kit),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(x = 177.dp, y = 0.dp)
                .size(204.dp, 169.dp),
        )

        Column(
            modifier = Modifier
                .offset(x = 14.dp, y = 16.5.dp)
                .width(113.dp),
        ) {
            // The title's 113 is Figma's own and safe to keep: it is Plus Jakarta Sans, the real
            // design face, and "Emergency" measures 105 at the design's 20 — three lines, as drawn.
            Text(
                text = "Emergency first aid guide",
                style = HomeType.HeroTitle,
                color = OmniHeroInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "100% offline access",
                style = HomeType.HeroCaption,
                color = OmniInk,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
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
                )
            }
        }
    }
}

/**
 * The hydration tile — Figma `Goal progress`, 166 x 167, radius 9.399.
 *
 * The stripe pattern behind it (`Vector 1`, white at 15%) is the one asset in the file that is not
 * worth reproducing as geometry. Figma builds it from a container expanded past the tile on all four
 * sides — inset(−42.59%, −63.87%, −55.05%, −84.62%), a 412.5 x 330 box centred at (65.8, 93.9) — and
 * then mirrors, rotates by −161.29° and skews the vector inside it by −7.9°. Every one of those
 * numbers is a percentage of the tile, so all five would have to be re-derived by hand for any
 * container that is not exactly 166 x 167.
 *
 * So it ships pre-composited instead: that whole transform stack rasterised into the tile's own frame
 * at 4x (664 x 668 for a 166 x 167 tile). The tile is then a plain full-bleed [Image] with no child
 * positioned outside it and no transform maths of ours to drift.
 *
 * The glasses are Figma's own geometry down to the last number: a 152-wide column centred in the 166
 * tile at y=93, two rows 10 apart, four 26dp glasses 16 apart (4x26 + 3x16 = 152). The block ends at
 * 155, twelve clear of the bottom, and the text above it needs
 * 8 + 12.924 + 6 + 29.373 + 6 + 15.861 = 78.2, so nothing collides. The plus is at Figma's (140, 3).
 *
 * **The goal is 8, not the design's 12** — BACKEND_PLAN §3.1. Figma draws 8 icons, three of them
 * filled, while its copy claims 7 of 12 and "5 glasses left": three numbers telling three stories, so
 * "one tap fills one glass" could not be true of it. With a goal of 8 the mapping is exactly 1:1, no
 * layout changes, and the card is self-consistent. The visible consequence is that the default preview
 * now fills seven glasses rather than the design's three, because seven is what its own text always
 * said.
 *
 * "keep going only 5 glasses left" is the only place the design's own box is not honoured. Measured
 * out of `manrope.ttf` at Figma's 11sp with its −0.055 tracking the sentence wants 146.86 and the
 * source box is 142, so it overflowed the design itself. Dropping the text column's end inset from
 * Figma's 11 to 2 gives it 151 and it stays on one line at Figma's own size. That leaves the live
 * string **4dp of slack**, which is why [waterHint] is shaped the way it is.
 */
@Composable
internal fun WaterCard(
    glasses: Int = 7,
    goal: Int = DefaultWaterGoal,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drinking past the goal keeps counting — the number is the truth — but the band only has 8 icons,
    // so the *display* saturates. At the default goal of 8 this is the identity function.
    val filledIcons = ((glasses.toFloat() / goal.coerceAtLeast(1)) * GlassIcons)
        .roundToInt()
        .coerceIn(0, GlassIcons)

    Box(
        modifier = modifier
            .height(StatCardHeight)
            .clip(RoundedCornerShape(9.399.dp))
            .background(OmniWaterTeal),
    ) {
        // The stripes arrive pre-composited: the rotated vector rasterised into the tile's own
        // 166 x 167 frame at Figma's offset, so the tile is a plain full-bleed image with nothing
        // positioned outside it.
        Image(
            painter = painterResource(R.drawable.home_water_stripes),
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
                    // The leading space is Figma's, and it is what keeps the count off the tile edge.
                    append(" $glasses")
                    withStyle(HomeType.WaterUnit.toSpanStyle()) { append("/$goal glasses") }
                },
                style = HomeType.WaterValue,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = waterHint(glasses = glasses, goal = goal),
                style = HomeType.WaterHint,
                color = OmniStatLabel,
                maxLines = 1,
                softWrap = false,
            )
        }

        // The glass band — a 152-wide column centred in the tile at y=93, which is 7 a side.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 93.dp)
                .width(GlassBandWidth),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WaterGlassRow(filled = filledIcons.coerceAtMost(GlassesPerRow))
            WaterGlassRow(filled = (filledIcons - GlassesPerRow).coerceIn(0, GlassesPerRow))
        }

        Image(
            painter = painterResource(R.drawable.home_water_plus),
            contentDescription = "Add a glass of water",
            modifier = Modifier
                .offset(x = 140.dp, y = 3.dp)
                .size(24.dp)
                .clickable(onClick = onAdd),
        )
    }
}

/**
 * The hint line, which has 4dp of slack and therefore dictates its own wording.
 *
 * Every variant is at most as wide as the design's own "keep going only 5 glasses left":
 *
 *  - nothing left to drink → "goal reached, nice work", which is shorter outright
 *  - one glass left → the singular, so it does not read "1 glasses"
 *  - ten or more left → "only" is dropped, which buys back five characters — more than the extra digit
 *    costs. Only reachable if a user sets a goal above 9, which nothing writes today, but the string is
 *    what would clip and a clipped glyph is worse than a slightly terser sentence.
 */
private fun waterHint(glasses: Int, goal: Int) = buildAnnotatedString {
    val remaining = (goal - glasses).coerceAtLeast(0)
    if (remaining == 0) {
        append("goal reached, nice work")
        return@buildAnnotatedString
    }
    append(if (remaining < 10) "keep going only " else "keep going ")
    withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) {
        append("$remaining ")
    }
    append(if (remaining == 1) "glass left" else "glasses left")
}

@Composable
private fun WaterGlassRow(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(GlassesPerRow) { index ->
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

/** Two rows of four — the band Figma draws, and the ceiling on what the card can show. */
private const val GlassesPerRow = 4
private const val GlassIcons = GlassesPerRow * 2

/** 4 x 26 glasses with 3 x 16 between them — Figma `Frame 95` is 152 wide for exactly this reason. */
private val GlassBandWidth = 152.dp

/**
 * The step-count tile — Figma `Goal stats container`, 207 x 167, radius 9.377.
 *
 * Four absolutely positioned children, all at the design's own coordinates: the dark footprint rail
 * (`Goal icon`, 30 x 140 at 11, 8), the three texts sharing x=53 at y=19 / 42 / 92, and the runner.
 *
 * The runner (`Object`) keeps its 107 x 150 — aspect 107/150 in the source — centred at y=88 and
 * starting at x=144.06, so 44 of it hangs past the tile's right edge and is clipped away.
 *
 * Figma's y-gaps are not uniform (8.352 between label and value, 16.71 between value and hint), so
 * the text column spaces itself with explicit spacers rather than one arrangement gap.
 *
 * The hint is the one child not given its Figma width. In the design's 97 Manrope breaks it into four
 * lines; the design shows three, and any box from 99 to 123 produces exactly the design's three breaks,
 * so it gets the middle of that window. 53 + 110 = 163 overlaps the runner's transparent left edge by 19,
 * which is the same kind of overlap the design has at 150.
 *
 * **The percentage is computed, and the design's is wrong** — BACKEND_PLAN §3.2. Figma reads "5,600 /
 * 10,000" and then "78%", which is 56%. So the number is derived from the two either side of it and the
 * default preview now says 56, a deliberate divergence from the mock (`UI_ARCHITECTURE.md` §6 item 9).
 * It is not capped: walking past the goal is worth seeing, and "100%" and "134%" are the same width.
 *
 * **The three live strings all fit the box the mock string measured** (§6 item 8). The value line's widest
 * realistic form is `19,999/10,000`, two glyphs more than the design's `5,600/10,000`, on a line with 53dp
 * of clear tile to its right. The hint is the tighter one, and the 99..123 window above is what settles it:
 * a *narrower* box down to 99 does not push a word down either, so at 110 every one of the three lines is
 * carrying at least 11dp — about two glyphs — of trailing slack. A three-digit percentage costs one, and
 * [StepsPermissionHint] is thirteen characters shorter than the design's sentence outright.
 */
@Composable
internal fun StepsCard(
    steps: Int = 5_600,
    goal: Int = DefaultStepsGoal,
    permissionNeeded: Boolean = false,
    onEnableTracking: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(StatCardHeight)
            .clip(RoundedCornerShape(9.377.dp))
            .background(OmniStepsCream)
            // Only tappable when tapping would do something. The whole tile is the target rather than the
            // hint text: at 11sp the words are far too small to aim at, and there is nothing else on the
            // card to hit by accident.
            .then(
                if (permissionNeeded) Modifier.clickable(onClick = onEnableTracking) else Modifier,
            ),
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
                .offset(x = 144.06.dp, y = 13.dp)
                .width(107.dp)
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
                    append(grouped(steps))
                    withStyle(HomeType.StepsUnit.toSpanStyle()) { append("/${grouped(goal)}") }
                },
                style = HomeType.StepsValue,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )

            // 92 − (42 + 33.29)
            Spacer(Modifier.height(16.71.dp))

            Text(
                text = if (permissionNeeded) {
                    AnnotatedString(StepsPermissionHint)
                } else {
                    stepsHint(steps = steps, goal = goal)
                },
                style = HomeType.StepsHint,
                color = OmniStatLabel,
                maxLines = 3,
                modifier = Modifier.width(StepsHintWidth),
            )
        }
    }
}

/**
 * The design's own sentence, with its own missing space after the comma, and a real percentage.
 *
 * Uncapped for anything a walker can reach — passing the goal is worth seeing, and "134%" is the same
 * width as "100%". The ceiling exists only so a nonsense goal typed into the Firebase console (a target of
 * 1 step, say) cannot produce a number wide enough to break the three-line box.
 */
private fun stepsHint(steps: Int, goal: Int) = buildAnnotatedString {
    val percent = (steps.toFloat() / goal.coerceAtLeast(1) * 100).roundToInt().coerceAtMost(MaxHintPercent)
    append("You’re ")
    withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) {
        append("$percent%")
    }
    append(" towards your daily goal,just keep moving")
}

/** Three digits — the widest the hint's slack allows, and ten times the goal is beyond anybody's day. */
private const val MaxHintPercent = 999

/**
 * What the tile says with the permission refused: the truth, and what to do about it.
 *
 * Never a fake number and never a crash (BACKEND_PLAN §11 Phase 4) — the value line keeps showing the goal
 * against whatever another device has synced, which is 0 for most people, and this line explains why.
 */
private const val StepsPermissionHint = "Step tracking is off, tap to turn it on"

/**
 * Thousands separators, pinned to [Locale.US].
 *
 * Not the device locale: the design's separator is a comma, and a phone set to German would render the same
 * number as `5.600` while the goal beside it kept its slash — two different conventions in one line. The
 * strings here are numbers in a fixed-width slot, not prose.
 */
private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)

/** Figma's box is 97, which breaks the sentence into four lines; 99..123 all give the design's three. */
private val StepsHintWidth = 110.dp

/** Figma `Hero section background` is 169 tall. */
internal val HeroHeight = 169.dp

/**
 * Figma's height for both stat tiles in this frame — both grew from 159 to 167 in the redesign, and
 * both tiles' contents still fit inside it.
 *
 * The hydration tile spends 8 + 12.924 + 6 + 29.373 + 6 + 15.861 = 78.2 on text and puts the glass
 * band at 93..155. The step tile's hint starts at 92 and takes 3 x 14.551, ending at 135.7.
 */
internal val StatCardHeight = 167.dp
