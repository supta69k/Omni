package com.example.omni.ui.nutrition

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.DayNutrition
import com.example.omni.data.model.DefaultCalorieGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.data.model.Meal
import com.example.omni.domain.formatAmount
import com.example.omni.domain.progressOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniTabScaffold
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.NutritionType
import com.example.omni.ui.theme.OmniArcTrackGrey
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniHeroPink
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
import com.example.omni.ui.theme.OmniStepsCream
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.OmniWaterTeal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

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
    week: List<DayChipState> = emptyList(),
    selectedIndex: Int = 0,
    /** The month label shown above the week strip; tapping opens the month picker. */
    selectedMonth: YearMonth = YearMonth.now(),
    /** Whether the month picker is open. Hoisted, because the month query behind it is the ViewModel's. */
    monthPickerOpen: Boolean = false,
    /** The month the picker is showing, which follows its arrows rather than the selected day. */
    pickerMonth: YearMonth = YearMonth.now(),
    /** Calories logged per day of [pickerMonth]; a day with nothing logged is absent, not zero. */
    monthCalories: Map<LocalDate, Int> = emptyMap(),
    onOpenMonthPicker: () -> Unit = {},
    onDismissMonthPicker: () -> Unit = {},
    onBrowseMonth: (YearMonth) -> Unit = {},
    /** A day chosen from the calendar, which may be outside the seven chips on screen. */
    onSelectDate: (LocalDate) -> Unit = {},
    meals: List<Meal> = emptyList(),
    nutrition: DayNutrition = DayNutrition(),
    steps: Int = 0,
    stepsGoal: Int = DefaultStepsGoal,
    glasses: Int = 0,
    waterGoal: Int = DefaultWaterGoal,
    sleepHours: Float = 0f,
    sleepGoal: Float = DefaultSleepGoal,
    calorieGoal: Int = DefaultCalorieGoal,
    onSelectDay: (DayChipState) -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
    onAddMeal: (Meal) -> Unit = {},
    onEditMeal: (Meal) -> Unit = {},
    onDeleteMeal: (String) -> Unit = {},
) {
    // The log-food sheet is the one thing on this screen the design does not contain. Both the plus and a
    // tap on a row open it; the plus means "new" and the row means "edit". Whether the current open is
    // add or edit is the shape of [editingMeal]: `null`. A blank id is add, a filled one is edit, and
    // that is all [MealEntrySheet] needs to tell the two apart.
    var isAdding by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<Meal?>(null) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val ringPages = remember(meals, nutrition, steps, stepsGoal, glasses, waterGoal, sleepHours, sleepGoal) {
        listOf(
            macroRings(nutrition),
            activityRings(steps, stepsGoal, glasses, waterGoal, sleepHours, sleepGoal),
        )
    }

    DesignFrame {
        OmniTabScaffold(
            selected = OmniNavItem.Fitness,
            onNavigate = onNavigate,
        ) {
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

                        // Month label — tappable to open the month picker modal. The design has no
                        // explicit frame for this, so we borrow the settings row title style and centre it.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding)
                                .clickable(onClick = onOpenMonthPicker),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                    " ${selectedMonth.year}",
                                style = NutritionType.FoodName,
                                color = OmniInk,
                                maxLines = 1,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        DayRow(week = week, selected = selectedIndex, onSelect = onSelectDay)

                        Spacer(Modifier.height(GaugeGap))

                        GoalGauge(calories = nutrition.calories, calorieGoal = calorieGoal)

                        Spacer(Modifier.height(RingsGap))

                        RingPager(state = pagerState, pages = ringPages)

                        Spacer(Modifier.height(DotsGap))

                        PageDots(
                            current = pagerState.currentPage,
                            count = ringPages.size,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )

                        Spacer(Modifier.height(FoodLogGap))

                        FoodLogHeader(onLog = { isAdding = true })

                        if (meals.isEmpty()) {
                            Spacer(Modifier.height(EmptyGap))
                            Text(
                                text = "No meals logged yet.",
                                style = NutritionType.FoodCalories,
                                color = OmniNutriMacroLabel,
                                maxLines = 1,
                                softWrap = false,
                                // The column has no horizontal padding — every child sets its own — so
                                // without this the line started at the screen edge while "Food Log"
                                // above it started at 18. The empty state has to hang off the same
                                // margin as the section it is empty *of*.
                                modifier = Modifier.padding(start = ScreenPadding),
                            )
                        } else {
                            meals.forEach { meal ->
                                Spacer(Modifier.height(EntryGap))
                                FoodLogEntry(
                                    meal = meal,
                                    onClick = { editingMeal = meal },
                                )
                            }
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

            MealEntrySheet(
                // In "add" mode there is no row behind the sheet, so it receives nothing to edit; the
                // blank id is the signal a save means *new*, and the filled id means *update*.
                visible = isAdding || editingMeal != null,
                editing = if (isAdding) null else editingMeal,
                onDismiss = { isAdding = false; editingMeal = null },
                onSave = { meal ->
                    if (isAdding) {
                        onAddMeal(meal)
                    } else {
                        onEditMeal(meal)
                    }
                    isAdding = false
                    editingMeal = null
                },
                onDelete = { mealId ->
                    onDeleteMeal(mealId)
                    isAdding = false
                    editingMeal = null
                },
            )

            // Last child of the box, above the meal sheet in z-order but never open at the same time:
            // the month label is behind the meal sheet's scrim while that one is up.
            MonthPickerSheet(
                visible = monthPickerOpen,
                month = pickerMonth,
                selected = week.getOrNull(selectedIndex)?.date ?: LocalDate.now(),
                calories = monthCalories,
                onDismiss = onDismissMonthPicker,
                onBrowseMonth = onBrowseMonth,
                onSelectDate = onSelectDate,
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
private fun DayRow(week: List<DayChipState>, selected: Int, onSelect: (DayChipState) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        week.forEachIndexed { index, day ->
            DayChip(
                day = day,
                selected = index == selected,
                enabled = day.selectable,
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayChip(day: DayChipState, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
        modifier = surface.clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ink = when {
            selected -> OmniOnInk
            enabled -> OmniNutriDayIdle
            else -> OmniNutriDayIdle.copy(alpha = IdleAlpha)
        }
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
 *
 * The arc is drawn, not a static drawable: [percent] drives how much of the sweep is filled, so the
 * gauge now shows the real fraction the way the readout always has. The geometry is the exported
 * asset's own — a 312-wide sweep on a 321 viewport whose ends drop to rounded caps at the baseline —
 * reproduced with [drawArc] rather than the SVG path, because a path cannot be partially filled
 * without hand-deriving a new one per fraction.
 *
 * The design's gradient runs along the stroke (`8D84F9 → F1BBDC → FEEBA9 → AFDFDF`, the palette's
 * own four accents), here rebuilt as a linear brush across the sweep's bounding box. Figma's export
 * drew the same gradient mirrored; the sweep is drawn left-to-right here, which lands the lavender
 * at the start — matching the render, not the export's coordinate system (the same call the flipped
 * static image used to make).
 */
@Composable
private fun GoalGauge(calories: Int = 0, calorieGoal: Int = DefaultCalorieGoal) {
    val percent = progressOf(calories.toFloat(), calorieGoal.toFloat())

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
            ArcGauge(
                progress = percent,
                modifier = Modifier
                    .width(ArcWidth)
                    .height(GaugeHeight),
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
                    text = "${(percent * 100f).roundToInt()}%",
                    style = NutritionType.GaugePercent,
                    color = OmniInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = goalSentence((percent * 100f).roundToInt()),
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
 * The gauge itself — a half-circle in the exported asset's own proportions.
 *
 * The two drawables it replaces were a 321-wide track and a 314.881-wide gradient arc, both flat
 * assets that could only ever show one value. The [Canvas] version draws the same visual in three
 * parts: the grey track across the full sweep, the gradient arc clipped to [progress] of it, and
 * round caps — because the export's ends are rounded (a 16.5-cap on a 16-wide stroke, i.e. a stroke
 * so nearly circular the cap reads as a dot), which `StrokeCap.Round` reproduces exactly.
 *
 * The sweep is drawn `180°..360°` (left to right across the top), which is where the mirrored export
 * landed its gradient's lavender end — the render, not the export's own coordinate system.
 */
@Composable
private fun ArcGauge(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = ArcStrokeWidth.toPx(), cap = StrokeCap.Round)

        // The arc's own box: inset by half the stroke so the caps' bulge stays inside the 321
        // viewport, exactly where the export puts its own edge.
        val inset = ArcStrokeWidth.toPx() / 2f
        val arcBox = Size(size.width - inset * 2f, (size.height - inset) * 2f)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = ArcTrackColor,
            startAngle = ArcSweepStart,
            sweepAngle = ArcSweepTotal,
            useCenter = false,
            topLeft = topLeft,
            size = arcBox,
            style = stroke,
        )

        if (progress > 0f) {
            drawArc(
                brush = Brush.linearGradient(
                    colors = ArcGradientColors,
                    start = Offset(inset, size.height / 2f),
                    end = Offset(size.width - inset, size.height / 2f),
                ),
                startAngle = ArcSweepStart,
                sweepAngle = ArcSweepTotal * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcBox,
                style = stroke,
            )
        }
    }
}

/**
 * "You're 20% away to hit your daily goal." — five runs in the source: the figure turns pure black,
 * the "%" and " away to hit" step up to Medium, and the rest stays #3E3C3C Regular.
 *
 * The figure is the readout's complement, not its opposite. At "80%" the sentence says 20% away; the
 * two always add to 100, so when the goal is reached the readout says 100% and the sentence flips to the
 * one line the design has no vocabulary for — "[percent]% of your daily goal." — rather than inventing a
 * "0% away to hit" that reads like a second number.
 */
private fun goalSentence(percent: Int): AnnotatedString {
    val remaining = (100 - percent).coerceAtLeast(0)
    return buildAnnotatedString {
        if (percent >= 100) {
            append("You’ve reached your daily goal.")
            return@buildAnnotatedString
        }
        append("You’re")
        withStyle(SpanStyle(color = OmniCardInk)) { append(" $remaining") }
        withStyle(SpanStyle(color = OmniCardInk, fontWeight = FontWeight.Medium)) { append("%") }
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(" away to hit") }
        append(" your daily goal.")
    }
}

// ---- The macro and activity rings ---------------------------------------------------------------

/**
 * Figma `Nutrient and activity container` (node 151:480) — two pages of three rings that the design
 * lays out side by side off-canvas: `Main card` at x=32 and `Activity cards` at x=451.645, both 350
 * wide, which is where the page spacing comes from.
 */
@Composable
private fun RingPager(state: PagerState, pages: List<List<Ring>>) {
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
                pages[page].forEach { RingGauge(ring = it) }
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
 *
 * A row is now a real [Meal] rather than a mock, so it is tappable — but only to *open the edit sheet*,
 * which is the one thing a tap can do (`UI_ARCHITECTURE.md` §6 rule 10). The sheet is what owns edit
 * and delete; this row asks the screen for nothing beyond showing it.
 */
@Composable
private fun FoodLogEntry(meal: Meal, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = ScreenPadding)
            .width(EntryWidth)
            .height(EntryHeight)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = meal.name,
                style = NutritionType.FoodName,
                color = OmniNutriFoodName,
                maxLines = 1,
                softWrap = false,
                // A name can outgrow the box now that it is user-typed, so this is where the clamp
                // lives rather than inside the entry sheet.
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CaloriesGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${meal.calories}cal",
                    style = NutritionType.FoodCalories,
                    color = OmniNutriUnit,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                    MacroChip(amount = grams(meal.protein), fill = OmniNutriChipProtein)
                    MacroChip(amount = grams(meal.carbs), fill = OmniNutriChipCarbs)
                    MacroChip(amount = grams(meal.fat), fill = OmniNutriChipFat)
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

/** `12f` → `"12g"`, `12.5f` → `"12.5g"` — the chips' one-decimal shorthand. */
private fun grams(value: Float): String = formatAmount(value) + "g"

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

/** The first page, driven by the selected day's meal macros. The ring assets do not fill; only the figures move. */
private fun macroRings(nutrition: DayNutrition): List<Ring> = listOf(
    Ring(R.drawable.ic_nutri_ring_protein, 100.994.dp, 99.464.dp, formatAmount(nutrition.protein), "g", "Protein"),
    Ring(R.drawable.ic_nutri_ring_carbs, 99.889.dp, 100.604.dp, formatAmount(nutrition.carbs), "g", "Carbs"),
    Ring(R.drawable.ic_nutri_ring_fat, 100.868.dp, 99.647.dp, formatAmount(nutrition.fat), "g", "Fat"),
)

/**
 * The second page — the day's own readings against its goals.
 *
 * The water figure is written against the *user's* goal rather than Figma's literal `"7/12gla"`.
 * The double-digit "12" is the design's hardcoded mock (every 415 frame ships the same fake), and
 * HOME's water card already reads against `DefaultWaterGoal`/the user's goal — so the same day would
 * show "7/12" here and "7/8" on Home if this kept the mock. It is the `§3.1` water-goal fix, applied
 * to the one screen that never got it.
 */
private fun activityRings(
    steps: Int,
    stepsGoal: Int,
    glasses: Int,
    waterGoal: Int,
    sleepHours: Float,
    sleepGoal: Float,
): List<Ring> = listOf(
    Ring(
        R.drawable.ic_nutri_ring_steps, 100.994.dp, 99.464.dp,
        thousands(steps) + "/" + thousands(stepsGoal), label = "Steps",
    ),
    Ring(
        R.drawable.ic_nutri_ring_water, 99.889.dp, 100.604.dp,
        "$glasses/${waterGoal}gla", label = "water",
    ),
    Ring(
        R.drawable.ic_nutri_ring_sleep, 100.868.dp, 99.647.dp,
        formatAmount(sleepHours) + "/" + formatAmount(sleepGoal) + "h", label = "Sleep",
    ),
)

/** `5600` → `"5.6"`, for the steps ring's `"5.6/10k"` — one decimal, no units of its own. */
private fun thousands(count: Int): String {
    val tenths = (count / 100f).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.US, "%.1f", tenths / 10f)
}

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

/** `Progress card`'s own height, which is a touch more than the arc inside it. */
private val GaugeHeight = 176.55.dp

/** x=47 in a 415 frame, so the card is exactly centred. */
private val ArcWidth = 321.dp

/**
 * The stroke the exported arcs carry: their ~16.5dp caps on a sweep that spans 312 of the 321 —
 * i.e. the same stroke the caps' own roundness implies.
 */
private val ArcStrokeWidth = 16.dp

/** Left across the top, ending at the right baseline. */
private const val ArcSweepStart = 180f
private const val ArcSweepTotal = 180f

/** The export's track grey, aliased so the palette keeps its one-name-per-colour rule. */
private val ArcTrackColor = OmniArcTrackGrey

/** The export's own gradient stops, in sweep order — every one an existing palette accent. */
private val ArcGradientColors = listOf(
    OmniAuthHeading,  // #8D84F9 — lavender
    OmniHeroPink,     // #F1BBDC — pink
    OmniStepsCream,   // #FEEBA9 — cream
    OmniWaterTeal,    // #AFDFDF — teal
)

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

/** 14 — the column's own smallest gap between rows, used between every pair of real rows. */
private val EntryGap = 14.dp

/** The empty state's top gap, so the "No meals yet" line clears the section title. */
private val EmptyGap = 16.dp

/** Future days still get drawn but greyed, so the strip is not full of holes before midnight. */
private val IdleAlpha = 0.3f

/** Clearance below the last row so the floating bar never covers it. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun NutritionScreenPreview() {
    OmniTheme {
        NutritionScreen(
            week = previewWeek(),
            selectedIndex = 3,
            meals = previewMeals(),
            nutrition = DayNutrition(calories = 1_601, protein = 30f, carbs = 23f, fat = 17f, fiber = 18f),
            steps = 5_600,
            glasses = 7,
            sleepHours = 6.5f,
        )
    }
}

/**
 * The strip with "W 04" selected, matching the design's mock. [DayChipState] is a ViewModel type, so the
 * preview builds one per day: today's weekday letter off the real calendar, the seven dates the week
 * strip would show.
 */
private fun previewWeek(): List<DayChipState> {
    val today = java.time.LocalDate.now()
    return (0..6).map { offset ->
        val date = java.time.LocalDate.of(today.year, today.month, today.dayOfMonth).minusDays((6 - offset).toLong())
        DayChipState(
            date = date,
            initial = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                .take(1).uppercase(Locale.getDefault()),
            number = "%02d".format(Locale.US, date.dayOfMonth),
            selectable = !date.isAfter(today),
        )
    }
}

private fun previewMeals(): List<Meal> = listOf(
    Meal(id = "one", name = "Eggs & Toast", calories = 404, protein = 12f, carbs = 15f, fat = 35f, loggedAt = 1L),
    Meal(id = "two", name = "Rice,fish and vegis", calories = 404, protein = 12f, carbs = 15f, fat = 35f, loggedAt = 2L),
    Meal(id = "three", name = "Rice,fish and vegis", calories = 404, protein = 12f, carbs = 15f, fat = 35f, loggedAt = 3L),
)
