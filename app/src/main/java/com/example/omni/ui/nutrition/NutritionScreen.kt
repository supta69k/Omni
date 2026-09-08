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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.NutritionType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriChipCarbs
import com.example.omni.ui.theme.OmniNutriChipFat
import com.example.omni.ui.theme.OmniNutriChipProtein
import com.example.omni.ui.theme.OmniNutriDayIdle
import com.example.omni.ui.theme.OmniNutriDaySelected
import com.example.omni.ui.theme.OmniNutriDayShadow
import com.example.omni.ui.theme.OmniNutriDaySurface
import com.example.omni.ui.theme.OmniNutriFoodName
import com.example.omni.ui.theme.OmniNutriGaugeLabel
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniNutriUnit
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniTheme

/**
 * The nutrition log — Figma frame `iPhone 14 & 15 Pro - 35` (node 151:465).
 *
 * The artboard is 415 x 993 and, like the dashboard and the feed, it is one scrolling page with two
 * viewport-anchored pieces: the white [OmniHeader] on top and the floating [OmniBottomNav] pinned
 * above the system navigation bar.
 *
 * The vertical map of the page, in frame coordinates, is:
 *
 * | y        | node                                              | h       |
 * |----------|---------------------------------------------------|---------|
 * | 0        | `Status Bar` — the mock iOS bar plus the greeting  | 101     |
 * | 108      | `Day container`, x=18, w=379                       | 54      |
 * | 203.7246 | `Progress card`, x=47, w=321                        | 176.55  |
 * | 211      | `Weight measurement` x=30 / `Foot measurement` x=316 | 17      |
 * | 420      | `Nutrient and activity container`, full width        | 154     |
 * | 582      | `Navigation indicator`, x=199, w=16                  | 5.3333  |
 * | 628      | `Food Log` x=18 / the plus x=377                     | 24      |
 * | 689      | first `Food log entry`, x=18, w=383.021              | 47      |
 * | 759      | second entry                                        | 47      |
 * | 820      | third entry                                         | 47      |
 *
 * Every gap constant at the foot of this file is that table's arithmetic, shifted by the 24.336 mock
 * status bar the real device supplies for us.
 *
 * The lavender wash (`Ellipse 12`) is a 254 x 341 ellipse under a 200 blur, which Figma inflates to
 * 640 x 727 and the frame then crops to 415 x 696.1 from y=0 — exactly the exported bitmap. It lives
 * *inside* the scrolling column rather than behind it, because in the source it is a child of the
 * page and scrolls with the arc it sits behind.
 */
@Composable
fun NutritionScreen(
    header: OmniHeaderState = OmniHeaderState(),
    onNavigate: (OmniNavItem) -> Unit = {},
    onLogFood: () -> Unit = {},
) {
    // "W 04" in the design.
    var selectedDay by remember { mutableIntStateOf(3) }
    val pagerState = rememberPagerState(pageCount = { RingPages.size })

    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                // The wash is shorter than the content, so the content is what sizes this box.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.nutrition_glow),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = GlowTop)
                            .fillMaxWidth()
                            .height(GlowHeight),
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(HeaderHeight + DayRowGap))

                        DayRow(selected = selectedDay, onSelect = { selectedDay = it })

                        Spacer(Modifier.height(GaugeGap))

                        GoalGauge()

                        Spacer(Modifier.height(RingsGap))

                        RingPager(state = pagerState)

                        Spacer(Modifier.height(DotsGap))

                        PageDots(
                            current = pagerState.currentPage,
                            count = RingPages.size,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )

                        Spacer(Modifier.height(FoodLogGap))

                        FoodLogHeader(onLog = onLogFood)

                        FoodLog.forEach { entry ->
                            Spacer(Modifier.height(entry.gapAbove))
                            FoodLogEntry(entry = entry)
                        }

                        Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
                    }
                }
            }

            OmniHeader(
                state = header,
                onProfileClick = { onNavigate(OmniNavItem.Setting) },
                onMessagesClick = { onNavigate(OmniNavItem.Messages) },
                onNotificationsClick = { onNavigate(OmniNavItem.Notifications) },
            )

            // Figma highlights "Feed" here, but that is a slip: the frame's navbar was duplicated
            // from the feed frame and its pill never re-pointed. `Fitness` is the tab that routes to
            // this screen, so `Fitness` is the tab that lights up.
            OmniBottomNav(
                selected = OmniNavItem.Fitness,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
            )
        }
    }
}

// ---- The week strip -----------------------------------------------------------------------------

/**
 * Figma `Day container` (node 151:515) — seven 34 x 54 chips across 379, which is the full width less
 * the 18 gutters. Seven 34s and six 23.5 gaps come to exactly that, so [Arrangement.SpaceBetween]
 * reproduces the design's spacing while absorbing any sub-pixel rounding at other widths.
 */
@Composable
private fun DayRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Days.forEachIndexed { index, day ->
            DayChip(
                day = day,
                selected = index == selected,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun DayChip(day: Day, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DayChipRadius)
    val box = Modifier
        .width(DayChipWidth)
        .height(DayChipHeight)

    // Figma's `0 7 6.2 rgba(141,132,249,.25)` is an offset drop shadow. Compose's single elevation
    // cannot separate offset from blur, and 5 is the closest match to the two together.
    val surface = if (selected) {
        box
            .shadow(
                elevation = DayChipElevation,
                shape = shape,
                ambientColor = OmniNutriDayShadow,
                spotColor = OmniNutriDayShadow,
            )
            .clip(shape)
            .background(OmniNutriDaySelected)
    } else {
        box
            .clip(shape)
            .background(OmniNutriDaySurface)
    }

    Box(
        modifier = surface.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (selected) OmniOnInk else OmniNutriDayIdle
        Column(
            // The source centres the pair on 16.5 of the 34, not 17.
            modifier = Modifier.offset(x = DayLabelNudge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.initial,
                style = NutritionType.DayWeekday,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = day.number,
                style = NutritionType.DayNumber,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ---- The arc gauge ------------------------------------------------------------------------------

/**
 * Figma `Progress card` (node 151:467) — the 321-wide arc, its centred readout, and the weight and
 * height that flank it at the artboard's own x rather than the card's.
 */
@Composable
private fun GoalGauge() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GaugeHeight),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(ArcWidth)
                .height(GaugeHeight),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nutri_arc_track),
                contentDescription = null,
                modifier = Modifier
                    .width(ArcTrackWidth)
                    .height(ArcTrackHeight),
            )
            Image(
                painter = painterResource(R.drawable.ic_nutri_arc_progress),
                contentDescription = null,
                // Figma wraps `Ellipse 19` in `-scale-y-100 rotate-180`, a net horizontal flip. That
                // is what puts the gradient's lavender end at the top right and leaves the grey stub
                // low on the right; the export is the unmirrored geometry, so the flip happens here.
                modifier = Modifier
                    .width(ArcProgressWidth)
                    .height(ArcProgressHeight)
                    .scale(scaleX = -1f, scaleY = 1f),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = GaugeReadoutTop)
                    .width(GaugeReadoutWidth),
                verticalArrangement = Arrangement.spacedBy(GaugeReadoutGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "80%",
                    style = NutritionType.GaugePercent,
                    color = OmniInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = GoalSentence,
                    style = NutritionType.GaugeSentence,
                    color = OmniNutriGaugeLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Measurement(
            value = "59",
            unit = "kg",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = WeightX, y = MeasurementTop),
        )
        Measurement(
            value = "5.7",
            unit = "foot",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = HeightX, y = MeasurementTop),
        )
    }
}

/** "59/kg" and "5.7/foot" — three runs that step down in weight and then in size. */
@Composable
private fun Measurement(value: String, unit: String, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append(value)
            withStyle(NutritionType.StatSeparator.toSpanStyle()) { append("/") }
            withStyle(NutritionType.StatUnit.toSpanStyle().copy(color = OmniNutriUnit)) {
                append(unit)
            }
        },
        style = NutritionType.StatValue,
        color = OmniCardInk,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.width(MeasurementWidth),
    )
}

/**
 * "You're 20% away to hit your daily goal." — five runs in the source: the figure turns pure black,
 * the "%" and " away to hit" step up to Medium, and the rest stays #3E3C3C Regular.
 */
private val GoalSentence = buildAnnotatedString {
    append("You’re")
    withStyle(SpanStyle(color = OmniCardInk)) { append(" 20") }
    withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) { append("%") }
    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(" away to hit") }
    append(" your daily goal.")
}

// ---- The macro and activity rings ---------------------------------------------------------------

/**
 * Figma `Nutrient and activity container` (node 151:480) — two pages of three rings that the design
 * lays out side by side off-canvas: `Main card` at x=32 and `Activity cards` at x=451.645, both 350
 * wide, which is where the page spacing comes from.
 */
@Composable
private fun RingPager(state: PagerState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RingBlockHeight),
    ) {
        HorizontalPager(
            state = state,
            modifier = Modifier
                .padding(start = RingCardX, top = RingCardY)
                .width(RingCardWidth)
                .height(RingCardHeight),
            pageSpacing = RingPageSpacing,
        ) { page ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RingGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingPages[page].forEach { RingGauge(ring = it) }
            }
        }
    }
}

@Composable
private fun RingGauge(ring: Ring) {
    Column(
        modifier = Modifier.width(ring.width),
        verticalArrangement = Arrangement.spacedBy(RingLabelGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(ring.width)
                .height(ring.ringHeight),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(ring.icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = buildAnnotatedString {
                    append(ring.value)
                    if (ring.unit != null) {
                        withStyle(NutritionType.RingUnit.toSpanStyle()) { append(ring.unit) }
                    }
                },
                style = NutritionType.RingValue,
                color = OmniInk,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = ring.label,
            style = NutritionType.MacroLabel,
            color = OmniNutriMacroLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Figma `Navigation indicator` (node 151:473) — a filled 5.333 dot for the page you are on and a
 * 4.267 outline for the other, 6.4 apart, which comes to 16 wide whichever page is active.
 *
 * The design bakes this as a flat asset. Drawn here instead so it follows the pager.
 */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(ActiveDot),
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            if (index == current) {
                Box(
                    modifier = Modifier
                        .size(ActiveDot)
                        .clip(CircleShape)
                        .background(OmniInk),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(IdleDot)
                        .border(IdleDotStroke, OmniCardInk, CircleShape),
                )
            }
        }
    }
}

// ---- The food log -------------------------------------------------------------------------------

/**
 * "Food Log" and the plus. The plus ends at 401, so its gutter is 14 rather than the 18 the title
 * uses; both children hang from the same top edge, and the taller plus sets the row's 24.
 */
@Composable
private fun FoodLogHeader(onLog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = PlusPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Food Log",
            style = NutritionType.SectionTitle,
            color = OmniCardInk,
            maxLines = 1,
            softWrap = false,
        )
        Image(
            painter = painterResource(R.drawable.ic_nutri_plus),
            contentDescription = "Log a meal",
            modifier = Modifier
                .size(PlusSize)
                .clickable(onClick = onLog),
        )
    }
}

/**
 * Figma `Food log entry` (nodes 151:608, 151:582, 151:595) — a name, the calories, three macro chips
 * and a hairline. Fixing the row at its 47 and hanging the hairline off the bottom keeps the gaps
 * between rows at the design's own literal numbers.
 */
@Composable
private fun FoodLogEntry(entry: FoodEntry) {
    Box(
        modifier = Modifier
            .padding(start = ScreenPadding)
            .width(EntryWidth)
            .height(EntryHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.name,
                style = NutritionType.FoodName,
                color = OmniNutriFoodName,
                maxLines = 1,
                softWrap = false,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CaloriesGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.calories,
                    style = NutritionType.FoodCalories,
                    color = OmniNutriUnit,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                    MacroChip(amount = entry.protein, fill = OmniNutriChipProtein)
                    MacroChip(amount = entry.carbs, fill = OmniNutriChipCarbs)
                    MacroChip(amount = entry.fat, fill = OmniNutriChipFat)
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_nutri_divider),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(EntryRuleHeight),
        )
    }
}

@Composable
private fun MacroChip(amount: String, fill: Color) {
    Box(
        modifier = Modifier
            .width(ChipWidth)
            .height(ChipHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = amount,
            style = NutritionType.FoodChip,
            color = OmniOnInk,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---- Content ------------------------------------------------------------------------------------

private data class Day(val initial: String, val number: String)

private val Days = listOf(
    Day("S", "01"),
    Day("M", "02"),
    Day("T", "03"),
    Day("W", "04"),
    Day("T", "05"),
    Day("F", "06"),
    Day("S", "07"),
)

/**
 * One ring. Figma exports the three at slightly different sizes — the arc caps land in different
 * places, so each SVG's own bounding box differs by about a dp — and the two pages reuse them.
 */
private data class Ring(
    val icon: Int,
    val width: Dp,
    val ringHeight: Dp,
    val value: String,
    /** The lighter, larger unit trailing the value. Only the macro page has one. */
    val unit: String? = null,
    val label: String,
)

private val MacroRings = listOf(
    Ring(R.drawable.ic_nutri_ring_protein, 100.994.dp, 99.464.dp, "30", "g", "Protein"),
    Ring(R.drawable.ic_nutri_ring_carbs, 99.889.dp, 100.604.dp, "23", "g", "Carbs"),
    Ring(R.drawable.ic_nutri_ring_fat, 100.868.dp, 99.647.dp, "17", "g", "Fat"),
)

/** The second page. "water" is lower-case in the source, unlike its two neighbours. */
private val ActivityRings = listOf(
    Ring(R.drawable.ic_nutri_ring_steps, 100.994.dp, 99.464.dp, "5.6/10k", label = "Steps"),
    Ring(R.drawable.ic_nutri_ring_water, 99.889.dp, 100.604.dp, "7/12gla", label = "water"),
    Ring(R.drawable.ic_nutri_ring_sleep, 100.868.dp, 99.647.dp, "6.5/8h", label = "Sleep"),
)

private val RingPages = listOf(MacroRings, ActivityRings)

private data class FoodEntry(
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    /**
     * The gap above this row: 37 below the heading, then 23 and 14. The design's own drift, kept —
     * the rows are not on a rhythm.
     */
    val gapAbove: Dp,
)

private val FoodLog = listOf(
    FoodEntry("Eggs & Toast", "404cal", "12g", "15g", "35g", gapAbove = 37.dp),
    FoodEntry("Rice,fish and vegis", "404cal", "12g", "15g", "35g", gapAbove = 23.dp),
    FoodEntry("Rice,fish and vegis", "404cal", "12g", "15g", "35g", gapAbove = 14.dp),
)

// ---- Geometry -----------------------------------------------------------------------------------

/** The header below the real status bar: 101 − 24.336. */
private val HeaderHeight = OmniHeaderHeight

/** The wash starts at frame y=0, which is 24.336 above where the content column begins. */
private val GlowTop = (-24.336).dp

/** The crop Figma applies to the blurred ellipse: 415 x 696.1, rounded to the exported bitmap. */
private val GlowHeight = 697.dp

/** 18 — the gutter the week strip, the section title and the food rows all share. */
private val ScreenPadding = 18.dp

/** 108 − 101, header to week strip. */
private val DayRowGap = 7.dp

private val DayChipWidth = 34.dp
private val DayChipHeight = 54.dp
private val DayChipRadius = 23.dp
private val DayChipElevation = 5.dp

/** `left-[calc(50%-0.5px)]` on the letter and the date inside a 34 chip. */
private val DayLabelNudge = (-0.5).dp

/** 203.7246 − 162, week strip to arc. */
private val GaugeGap = 41.725.dp

/** `Progress card`'s own height, which is a touch more than either arc. */
private val GaugeHeight = 176.55.dp

/** x=47 in a 415 frame, so the card is exactly centred. */
private val ArcWidth = 321.dp

private val ArcTrackWidth = 321.dp
private val ArcTrackHeight = 176.104.dp
private val ArcProgressWidth = 314.881.dp
private val ArcProgressHeight = 175.624.dp

/** `Goal progress` at y=277.0966 in the frame, 73.372 down inside the card. */
private val GaugeReadoutTop = 73.372.dp
private val GaugeReadoutWidth = 216.675.dp

/** 341.469 − (277.097 + 35), "80%" to the sentence below it. */
private val GaugeReadoutGap = 29.dp

private val MeasurementWidth = 74.dp

/** 211 − 203.7246 — the two measurements sit slightly below the card's top edge. */
private val MeasurementTop = 7.275.dp
private val WeightX = 30.dp
private val HeightX = 316.dp

/** 420 − 380.2746, arc to rings. */
private val RingsGap = 39.725.dp

/** `Nutrient and activity container`, taller than the cards it holds. */
private val RingBlockHeight = 154.dp

private val RingCardWidth = 350.dp
private val RingCardHeight = 129.dp
private val RingCardX = 32.dp
private val RingCardY = 13.dp

/** 451.645 − 350: `Activity cards` begins that far past the end of `Main card`. */
private val RingPageSpacing = 101.645.dp

private val RingGap = 24.dp

/** 120.464 − (99.464 + 13.735) inside the card — ring to its label. */
private val RingLabelGap = 7.285.dp

/** 582 − 574, rings to dots. */
private val DotsGap = 8.dp

private val ActiveDot = 5.333.dp
private val IdleDot = 4.267.dp
private val IdleDotStroke = 0.5.dp

/** 11.733 − 5.333 between the two dots. */
private val DotGap = 6.4.dp

/** 628 − 587.3333, dots to "Food Log". */
private val FoodLogGap = 40.667.dp

private val PlusSize = 24.dp

/** 415 − (377 + 24) — the plus is inset less than everything else on the screen. */
private val PlusPadding = 14.dp

private val EntryWidth = 383.021.dp
private val EntryHeight = 47.dp
private val EntryRuleHeight = 1.dp

/** 340 − (240 + 45 + 6 + 107) resolves to 6 between "404cal" and the first chip. */
private val CaloriesGap = 6.dp

private val ChipWidth = 33.dp
private val ChipHeight = 17.dp
private val ChipRadius = 8.dp
private val ChipGap = 4.dp

/** Clearance below the last row so the floating bar never covers it. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun NutritionScreenPreview() {
    OmniTheme {
        NutritionScreen()
    }
}
