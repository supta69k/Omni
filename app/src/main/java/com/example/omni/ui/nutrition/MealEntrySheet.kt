package com.example.omni.ui.nutrition

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealSlot
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
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

/**
 * Logging or editing one meal — the second sheet the design does not contain.
 *
 * Figma draws the food log's plus button and three finished rows, but nothing in between: no form, no
 * keyboard, no way to correct a row. So this is invented UI in the same shape as
 * [com.example.omni.ui.home.SleepEntrySheet] and for the same reasons (`UI_ARCHITECTURE.md` §6 rule 11) —
 * the hospitals sheet's surface, shadow, grab handle and reveal timing, hand-built rather than
 * `ModalBottomSheet`, which would hoist its content out of `DesignFrame` and invalidate every measurement
 * on the page.
 *
 * Unlike the sleep sheet this one needs real text input: a name is a name, and five numbers with a stepper
 * each would be an absurd amount of tapping. So it uses [BasicTextField] with a numeric keyboard and parses
 * defensively — `"12.5"`, `"12,5"`, `""` and `"abc"` all have to mean something. The parse rules live in
 * [numberOrZero] and are the only place in the app that reads a user-typed number.
 *
 * @param editing the row that was tapped, or `null` for the plus button. That one parameter is the whole
 *   difference between "log a meal" and "edit a meal" — the sheet fills itself from it, keeps its
 *   [Meal.id] and [Meal.loggedAt] so a save lands back in the same row, and shows a delete.
 */
@Composable
internal fun MealEntrySheet(
    visible: Boolean,
    editing: Meal?,
    onDismiss: () -> Unit,
    onSave: (Meal) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared scrim + slide envelope (ui/components/OmniSheetScaffold).
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        MealSheet(
            editing = editing,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun MealSheet(
    editing: Meal?,
    onDismiss: () -> Unit,
    onSave: (Meal) -> Unit,
    onDelete: (String) -> Unit,
) {
    // Keyed on the meal's id so tapping a different row while the sheet is open refills the fields, and so
    // an edit in progress is not overwritten by the day's listener re-emitting the same meal.
    val key = editing?.id.orEmpty()
    var name by remember(key) { mutableStateOf(editing?.name.orEmpty()) }
    var slot by remember(key) { mutableStateOf(editing?.slot ?: MealSlot.BREAKFAST) }
    var calories by remember(key) { mutableStateOf(editing?.calories?.takeIf { it > 0 }?.toString().orEmpty()) }
    var protein by remember(key) { mutableStateOf(editing.gramsText { it.protein }) }
    var carbs by remember(key) { mutableStateOf(editing.gramsText { it.carbs }) }
    var fat by remember(key) { mutableStateOf(editing.gramsText { it.fat }) }
    var fiber by remember(key) { mutableStateOf(editing.gramsText { it.fiber }) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            .sheetNoRipple(onClick = {})
            // The keyboard covers the lower half of the screen, and this sheet is the lower half of the
            // screen. Without this the Save button is behind it and the fibre field is unreachable.
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

        Spacer(Modifier.height(HeadingTop))
        Text(
            text = if (editing == null) "Log a meal" else "Edit this meal",
            style = NutritionType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )

        Spacer(Modifier.height(FieldGap))
        SheetField(
            value = name,
            onValueChange = { name = it.take(MaxNameLength) },
            placeholder = "What did you eat?",
            numeric = false,
        )

        Spacer(Modifier.height(FieldGap))
        SlotRow(selected = slot, onSelect = { slot = it })

        Spacer(Modifier.height(FieldGap))
        LabelledField(label = "Calories", value = calories, unit = "cal") { calories = it }

        Spacer(Modifier.height(FieldGap))
        Row(horizontalArrangement = Arrangement.spacedBy(MacroGap)) {
            MacroField(label = "Protein", value = protein, modifier = Modifier.weight(1f)) { protein = it }
            MacroField(label = "Carbs", value = carbs, modifier = Modifier.weight(1f)) { carbs = it }
            MacroField(label = "Fat", value = fat, modifier = Modifier.weight(1f)) { fat = it }
        }

        Spacer(Modifier.height(FieldGap))
        // Given its own row rather than a fourth column: it is the field the whole phase exists for (Home's
        // fibre card has no other source), and three macros plus a fourth would squeeze all four.
        LabelledField(label = "Fiber", value = fiber, unit = "g") { fiber = it }

        Spacer(Modifier.height(SaveTop))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .pressEffect(enabled = name.isNotBlank())
                .clip(RoundedCornerShape(ButtonCorner))
                .background(OmniInk)
                // A meal with no name is not a log entry, so the button is inert until there is one. Every
                // number may legitimately be blank — "an apple" with nothing else filled in is a real thing
                // to record — and blank reads as 0.
                .clickable(enabled = name.isNotBlank()) {
                    onSave(
                        Meal(
                            id = editing?.id.orEmpty(),
                            name = name.trim(),
                            slot = slot,
                            calories = numberOrZero(calories).toInt().coerceIn(0, MaxCalories),
                            protein = numberOrZero(protein).coerceIn(0f, MaxGrams),
                            carbs = numberOrZero(carbs).coerceIn(0f, MaxGrams),
                            fat = numberOrZero(fat).coerceIn(0f, MaxGrams),
                            fiber = numberOrZero(fiber).coerceIn(0f, MaxGrams),
                            loggedAt = editing?.loggedAt ?: 0L,
                        )
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                style = NutritionType.FoodName,
                color = if (name.isNotBlank()) OmniOnInk else OmniNutriMacroLabel,
                maxLines = 1,
            )
        }

        if (editing != null) {
            Spacer(Modifier.height(DeleteTop))
            Text(
                text = "Delete this meal",
                style = NutritionType.FoodCalories,
                color = OmniAlertRed,
                maxLines = 1,
                modifier = Modifier.sheetNoRipple { onDelete(editing.id) },
            )
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

/** The four eating occasions as pills. A fixed set of four, so a row of chips beats a dropdown. */
@Composable
internal fun SlotRow(selected: MealSlot, onSelect: (MealSlot) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SlotGap),
    ) {
        MealSlot.entries.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SlotHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) OmniNutriDaySelected else OmniFieldSurface)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = NutritionType.FoodChip,
                    color = if (active) OmniOnInk else OmniNutriUnit,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A caption over a numeric field, for the two that stand alone. */
@Composable
internal fun LabelledField(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(LabelGap))
        SheetField(value = value, onValueChange = onValueChange, placeholder = "0 $unit", numeric = true)
    }
}

/** The same, sized to share a row with two siblings. */
@Composable
internal fun MacroField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(LabelGap))
        SheetField(value = value, onValueChange = onValueChange, placeholder = "0 g", numeric = true)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = NutritionType.FoodCalories, color = OmniNutriMacroLabel, maxLines = 1)
}

/**
 * One input on the grey field surface the auth pages already use.
 *
 * [BasicTextField] rather than Material's `TextField`, which brings its own 56dp minimum, its own label
 * animation and a container this design has no vocabulary for. The numeric variant filters as it types, so
 * a stray letter never reaches the parse — the parse still guards, since a paste can carry anything.
 */
@Composable
internal fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(RoundedCornerShape(FieldCorner))
            .background(OmniFieldSurface)
            .padding(horizontal = FieldPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = NutritionType.FoodName,
                color = OmniNutriMacroLabel,
                maxLines = 1,
                softWrap = false,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValueChange(if (numeric) typed.filter(::isNumberChar) else typed) },
            textStyle = TextStyle(
                fontFamily = NutritionType.FoodName.fontFamily,
                fontWeight = NutritionType.FoodName.fontWeight,
                fontSize = NutritionType.FoodName.fontSize,
                color = OmniCardInk,
                textAlign = TextAlign.Start,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Digits and the two decimal separators a locale might produce. Nothing else can be typed into a number. */
private fun isNumberChar(character: Char): Boolean = character.isDigit() || character == '.' || character == ','

/**
 * A typed number, or 0 for anything that is not one.
 *
 * The comma is normalised first: a phone set to most of Europe puts one on its decimal key, and
 * [String.toFloatOrNull] would read `"12,5"` as nothing at all. A blank field means 0 rather than an error,
 * because most meals will have fields nobody bothers to fill in — that is a real answer, not a mistake.
 */
internal fun numberOrZero(raw: String): Float {
    val normalised = raw.replace(',', '.')
    return normalised.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 0f
}

/** A stored gram value as editable text: blank for 0, so the placeholder shows instead of a literal "0.0". */
private inline fun Meal?.gramsText(select: (Meal) -> Float): String {
    val grams = this?.let(select) ?: 0f
    if (grams <= 0f) return ""
    return if (grams % 1f == 0f) grams.toInt().toString() else grams.toString()
}

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The hospitals sheet's gutter and handle inset, as in `SleepEntrySheet`. */
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp

private val HeadingTop = 20.dp
private val FieldGap = 14.dp
private val LabelGap = 5.dp
private val MacroGap = 10.dp
private val SlotGap = 8.dp
private val SlotHeight = 32.dp
private val SaveTop = 22.dp
private val DeleteTop = 14.dp
private val SheetBottom = 20.dp

/** The auth pages' own field and button metrics — the same components in a different room. */
private val FieldHeight = 52.dp
private val FieldCorner = 35.dp
private val FieldPadding = 18.dp
private val ButtonHeight = 50.dp
private val ButtonCorner = 20.dp

/** The food log row shows one line at 16sp in 240dp; past this a name would ellipsise on the page. */
private const val MaxNameLength = 60

/** Ceilings that keep a row's "404cal" and its three 33-wide chips from outgrowing their boxes. */
private const val MaxCalories = 99_999
private const val MaxGrams = 2_000f
