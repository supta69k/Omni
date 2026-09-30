package com.example.omni.ui.nutrition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealAnalysis
import com.example.omni.data.model.MealAnalysisItem
import com.example.omni.data.model.MealSlot
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.NutritionType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriDaySelected
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniNutriUnit
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface
import kotlinx.coroutines.delay

/**
 * Logging a meal by describing it — the AI flow (Phase 7), the same hand-built sheet vocabulary as
 * [MealEntrySheet] (grab handle, [OmniSheetSurface], no `ModalBottomSheet`) so it reads as one more
 * Omni sheet, not an "AI feature": no gradient, no chat bubble, no robot. It reuses the manual sheet's
 * own field composables ([SheetField], [SlotRow], [LabelledField], [MacroField]) so an edited estimate
 * is entered exactly like a manual meal.
 *
 * The whole flow is one state machine ([AiMealState]) the ViewModel owns: the user describes a meal
 * ([AiMealState.Idle]), it is analyzed ([AiMealState.Analyzing]), then either reviewed and edited
 * ([AiMealState.Review]), asked to clarify ([AiMealState.NeedsClarification]), or failed with a
 * specific message ([AiMealState.Failed]). Nothing here writes: [onConfirm] hands the reviewed [Meal]
 * back to the screen, which saves it through the ordinary food-log path.
 *
 * @param onAnalyze send a description to the backend for estimation.
 * @param onConfirm the reviewed, possibly-edited estimate the user accepted — saved as one meal.
 * @param onDismiss close the sheet without saving; manual logging is always still available behind it.
 */
@Composable
internal fun AiMealSheet(
    visible: Boolean,
    state: AiMealState,
    onAnalyze: (String) -> Unit,
    onConfirm: (Meal) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
                .clip(SheetShape)
                .background(OmniSheetSurface)
                .sheetNoRipple(onClick = {})
                .imePadding()
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

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = OmniMotion.fast<Float>()) + slideInVertically(
                        animationSpec = OmniMotion.fast<IntOffset>(),
                        initialOffsetY = { it / 4 }
                    ) togetherWith fadeOut(animationSpec = OmniMotion.fast<Float>())
                },
                label = "aiMealState"
            ) { targetState ->
                when (targetState) {
                    is AiMealState.Analyzing -> AnalyzingContent()
                    is AiMealState.Review -> ReviewContent(analysis = targetState.analysis, onConfirm = onConfirm)
                    is AiMealState.NeedsClarification ->
                        InputContent(note = targetState.question, noteIsError = false, onAnalyze = onAnalyze)
                    is AiMealState.Failed ->
                        InputContent(note = targetState.error.message, noteIsError = true, onAnalyze = onAnalyze)
                    AiMealState.Idle -> InputContent(note = null, noteIsError = false, onAnalyze = onAnalyze)
                }
            }

            Spacer(Modifier.height(SheetBottom))
        }
    }
}

/**
 * The little fork the food-log "+" now opens: log by describing the meal to the AI, or by filling the
 * form in by hand. Manual is offered first and as the plain, dark, always-there option so the AI path
 * is an addition, never a replacement — and so the whole feature degrades to "just tap Manual" when the
 * backend or the model is down.
 */
@Composable
internal fun LogMethodSheet(
    visible: Boolean,
    onUseAi: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
                .clip(SheetShape)
                .background(OmniSheetSurface)
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
            Text(
                text = "Log a meal",
                style = NutritionType.SectionTitle,
                color = OmniSectionTitle,
                maxLines = 1,
            )

            Spacer(Modifier.height(FieldGap))
            PrimaryButton(label = "Use AI", enabled = true, onClick = onUseAi)

            Spacer(Modifier.height(MacroGap))
            // The secondary, outline-only twin of PrimaryButton — the manual path, never dimmed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .pressEffect()
                    .clip(RoundedCornerShape(ButtonCorner))
                    .background(OmniFieldSurface)
                    .pressEffect()
                    .clickable(onClick = onManual),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Manual",
                    style = NutritionType.FoodName,
                    color = OmniCardInk,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(SheetBottom))
        }
    }
}

/**
 * The describe-your-meal step, reused for the first ask and for a re-ask after a clarification or an
 * error — [note] carries the model's clarification question or the failure's specific sentence, and
 * [noteIsError] only changes its colour. Manual logging stays reachable: dismissing the sheet drops
 * back to the food log with its own "+".
 */
@Composable
private fun InputContent(note: String?, noteIsError: Boolean, onAnalyze: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column {
        Spacer(Modifier.height(HeadingTop))
        Text(
            text = "Log a meal with AI",
            style = NutritionType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )
        Spacer(Modifier.height(SubtitleTop))
        Text(
            text = "Describe what you ate and we'll estimate the nutrition.",
            style = NutritionType.FoodCalories,
            color = OmniNutriMacroLabel,
        )

        if (note != null) {
            Spacer(Modifier.height(NoteTop))
            Text(
                text = note,
                style = NutritionType.FoodCalories,
                color = if (noteIsError) OmniAlertRed else OmniCardInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FieldCorner16))
                    .background(OmniFieldSurface)
                    .padding(horizontal = NotePadding, vertical = NotePaddingV),
            )
        }

        Spacer(Modifier.height(FieldGap))
        FieldLabel("What did you eat?")
        Spacer(Modifier.height(LabelGap))
        DescriptionField(
            value = text,
            onValueChange = { text = it.take(MaxDescriptionLength) },
            placeholder = "2 eggs, 2 slices of bread and peanut butter",
        )

        Spacer(Modifier.height(SaveTop))
        PrimaryButton(
            label = "Analyze meal",
            enabled = text.isNotBlank(),
            onClick = { onAnalyze(text.trim()) },
        )
    }
}

/** The in-flight step — the spinner and the spec's own "Analyzing your meal…" line, nothing else. */
@Composable
private fun AnalyzingContent() {
    Column {
        Spacer(Modifier.height(HeadingTop))
        Text(
            text = "Log a meal with AI",
            style = NutritionType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )
        Spacer(Modifier.height(AnalyzingGap))
        CircularProgressIndicator(color = OmniInk, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(AnalyzingGap))
        Text(
            text = "Analyzing your meal...",
            style = NutritionType.FoodName,
            color = OmniNutriUnit,
            maxLines = 1,
        )
        Spacer(Modifier.height(AnalyzingGap))
    }
}

/**
 * The review step: the estimate, clearly labelled as one, with the fields the user may correct before
 * it is saved (name, meal, calories, protein/carbs/fat — the ones the spec allows) plus the read-only
 * item breakdown so the total is not a black box. "Add to Food Log" hands the edited [Meal] back; it is
 * the first and only moment anything is written.
 */
@Composable
private fun ReviewContent(analysis: MealAnalysis, onConfirm: (Meal) -> Unit) {
    var name by remember(analysis) { mutableStateOf(analysis.mealName) }
    var slot by remember(analysis) { mutableStateOf(MealSlot.BREAKFAST) }
    var calories by remember(analysis) {
        mutableStateOf(analysis.totals.calories.takeIf { it > 0 }?.toString().orEmpty())
    }
    var protein by remember(analysis) { mutableStateOf(analysis.totals.protein.gramsText()) }
    var carbs by remember(analysis) { mutableStateOf(analysis.totals.carbs.gramsText()) }
    var fat by remember(analysis) { mutableStateOf(analysis.totals.fat.gramsText()) }
    var fiber by remember(analysis) { mutableStateOf(analysis.totals.fiber.gramsText()) }

    Column {
        Spacer(Modifier.height(HeadingTop))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Estimated nutrition",
                style = NutritionType.SectionTitle,
                color = OmniSectionTitle,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(OmniFieldSurface)
                    .padding(horizontal = BadgePadding, vertical = BadgePaddingV),
            ) {
                Text(
                    text = "AI estimate",
                    style = NutritionType.FoodChip,
                    color = OmniNutriUnit,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.height(FieldGap))
        FieldLabel("Meal name")
        Spacer(Modifier.height(LabelGap))
        SheetField(value = name, onValueChange = { name = it.take(MaxNameLength) }, placeholder = "Meal name", numeric = false)

        Spacer(Modifier.height(FieldGap))
        SlotRow(selected = slot, onSelect = { slot = it })

        Spacer(Modifier.height(FieldGap))
        LabelledField(label = "Calories", value = calories, unit = "cal") { calories = it }

        Spacer(Modifier.height(FieldGap))
        Row(horizontalArrangement = Arrangement.spacedBy(MacroGap), modifier = Modifier.fillMaxWidth()) {
            MacroField(label = "Protein", value = protein, modifier = Modifier.weight(1f)) { protein = it }
            MacroField(label = "Carbs", value = carbs, modifier = Modifier.weight(1f)) { carbs = it }
            MacroField(label = "Fat", value = fat, modifier = Modifier.weight(1f)) { fat = it }
        }

        Spacer(Modifier.height(FieldGap))
        // Fiber gets its own full-width row, exactly as the manual sheet's does: three macros plus a
        // fourth would squeeze all four, and an editable estimate must offer the same fields a
        // hand-typed meal has — the confirmed value flows into the log through [MealAnalysis.toMeal].
        LabelledField(label = "Fiber", value = fiber, unit = "g") { fiber = it }

        if (analysis.items.isNotEmpty()) {
            Spacer(Modifier.height(FieldGap))
            FieldLabel("What we counted")
            Spacer(Modifier.height(LabelGap))
            Column(modifier = Modifier.fillMaxWidth()) {
                analysis.items.forEachIndexed { index, item ->
                    ItemBreakdownRow(item, index)
                }
            }
        }

        Spacer(Modifier.height(NoteTop))
        Text(
            text = "An estimate to help you log faster — not medical or dietary advice.",
            style = NutritionType.FoodChip,
            color = OmniNutriMacroLabel,
        )

        Spacer(Modifier.height(SaveTop))
        PrimaryButton(
            label = "Add to Food Log",
            enabled = name.isNotBlank(),
            onClick = {
                onConfirm(
                    analysis.toMeal(
                        slot = slot,
                        name = name.trim(),
                        calories = numberOrZero(calories).toInt().coerceIn(0, MaxCalories),
                        protein = numberOrZero(protein).coerceIn(0f, MaxGrams),
                        carbs = numberOrZero(carbs).coerceIn(0f, MaxGrams),
                        fat = numberOrZero(fat).coerceIn(0f, MaxGrams),
                        fiber = numberOrZero(fiber).coerceIn(0f, MaxGrams),
                    ),
                )
            },
        )
    }
}

/** One read-only line of the estimate's breakdown: what it is, how much, and its calories. */
@Composable
private fun ItemBreakdownRow(item: MealAnalysisItem, index: Int) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(OmniMotion.staggerDelay(index).toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = OmniMotion.fast<Float>()) + slideInVertically(
            animationSpec = OmniMotion.fast<IntOffset>(),
            initialOffsetY = { it / 4 }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ItemRowGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(item.name)
                    val qty = item.quantity.gramsText()
                    if (qty.isNotEmpty()) append(" · $qty ${item.unit}")
                },
                style = NutritionType.FoodCalories,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "${item.calories} cal",
                style = NutritionType.FoodCalories,
                color = OmniNutriUnit,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The dark pill button shared by both steps — same metrics as the manual sheet's Save. */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .pressEffect()
            .clip(RoundedCornerShape(ButtonCorner))
            .background(OmniInk)
            .pressEffect(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NutritionType.FoodName,
            color = if (enabled) OmniOnInk else OmniNutriMacroLabel,
            maxLines = 1,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = NutritionType.FoodCalories,
        color = OmniNutriMacroLabel,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The meal-description input — like [SheetField] but multi-line and taller, because a sentence is not a
 * calorie count. Capped at [MaxDescriptionLength] to match the backend's own request-size limit so an
 * over-long paste is stopped here rather than rejected after a round-trip.
 */
@Composable
private fun DescriptionField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DescriptionMinHeight)
            .clip(RoundedCornerShape(FieldCorner16))
            .background(OmniFieldSurface)
            .padding(horizontal = FieldPadding, vertical = DescriptionPaddingV),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = NutritionType.FoodName,
                color = OmniNutriMacroLabel,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = NutritionType.FoodName.fontFamily,
                fontWeight = NutritionType.FoodName.fontWeight,
                fontSize = NutritionType.FoodName.fontSize,
                color = OmniCardInk,
                textAlign = TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
            ),
            singleLine = false,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField -> innerTextField() },
        )
    }
}

/** A number as compact editable text: blank for 0, whole numbers without a trailing ".0". */
private fun Float.gramsText(): String {
    if (this <= 0f) return ""
    return if (this % 1f == 0f) toInt().toString() else toString()
}

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 20.dp
private val SubtitleTop = 6.dp
private val NoteTop = 12.dp
private val NotePadding = 14.dp
private val NotePaddingV = 12.dp
private val FieldGap = 14.dp
private val LabelGap = 5.dp
private val MacroGap = 10.dp
private val SaveTop = 22.dp
private val SheetBottom = 20.dp
private val AnalyzingGap = 16.dp
private val ItemRowGap = 4.dp
private val BadgePadding = 10.dp
private val BadgePaddingV = 4.dp

private val FieldPadding = 18.dp
private val FieldCorner16 = 16.dp
private val DescriptionMinHeight = 84.dp
private val DescriptionPaddingV = 14.dp
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

private const val MaxNameLength = 60
private const val MaxCalories = 99_999
private const val MaxGrams = 2_000f

/** The backend caps a request at ~500 chars; stop an over-long description here, before the round-trip. */
private const val MaxDescriptionLength = 500
