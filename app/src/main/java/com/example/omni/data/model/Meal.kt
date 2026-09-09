package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate

/**
 * One logged meal — a `users/{uid}/days/{yyyy-MM-dd}/meals/{mealId}` document (BACKEND_PLAN §7).
 *
 * [fiber] is the field the whole Phase 6/5 chain exists for: the day document's `fiber` roll-up is
 * what Home's fibre card reads, and meals are its only source (BACKEND_PLAN §3.5).
 *
 * [slot] is recorded and orders the log, but the Figma food log is a flat list with no section
 * headers — so the slot appears in the edit sheet, not as a heading on the page (UI_ARCHITECTURE.md
 * §6 rule 9: the design is followed where it speaks, and the divergence is recorded where it
 * doesn't).
 */
data class Meal(
    val id: String,
    val name: String,
    val slot: MealSlot = MealSlot.BREAKFAST,
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
    val loggedAt: Long = 0L,
) {
    /** Orders the day's log; a meal without a timestamp sorts last, never first. */
    val sortKey: Long get() = if (loggedAt > 0) loggedAt else Long.MAX_VALUE
}

/** The four eating occasions the log sheet offers, and the Firestore strings they map to. */
enum class MealSlot(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun from(raw: String?): MealSlot =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BREAKFAST
    }
}

/** The day document's roll-up of every meal logged into it — the numbers the gauge and rings read. */
data class DayNutrition(
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
)

fun List<Meal>.toDayNutrition(): DayNutrition = DayNutrition(
    calories = sumOf { it.calories },
    protein = sumOfGrams { it.protein },
    carbs = sumOfGrams { it.carbs },
    fat = sumOfGrams { it.fat },
    fiber = sumOfGrams { it.fiber },
)

/**
 * Sums a gram field over the day's meals.
 *
 * Accumulated in [Double] and narrowed once, not added up in [Float]: five meals at 12.3g each drift by
 * enough in single precision to print "61.500004" in a readout, and the day document stores doubles anyway.
 * Written out because `sumOf` has no `Float` overload — asking for one picks the `Int`/`UInt` pair and fails
 * to resolve.
 */
private inline fun List<Meal>.sumOfGrams(select: (Meal) -> Float): Float {
    var total = 0.0
    for (meal in this) total += select(meal).toDouble()
    return total.toFloat()
}

/** The Firestore map for a meal document; [id] is the document id, never a field. */
fun Meal.toFirestoreMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "slot" to slot.name,
    "calories" to calories,
    "protein" to protein.toDouble(),
    "carbs" to carbs.toDouble(),
    "fat" to fat.toDouble(),
    "fiber" to fiber.toDouble(),
    "loggedAt" to loggedAt,
)

fun DocumentSnapshot.toMeal(): Meal? {
    if (!exists()) return null
    return Meal(
        id = id,
        name = getString("name").orEmpty(),
        slot = MealSlot.from(getString("slot")),
        calories = (get("calories") as? Number)?.toInt() ?: 0,
        protein = number("protein"),
        carbs = number("carbs"),
        fat = number("fat"),
        fiber = number("fiber"),
        loggedAt = (get("loggedAt") as? Number)?.toLong() ?: 0L,
    )
}

private fun DocumentSnapshot.number(key: String): Float =
    (get(key) as? Number)?.toDouble()?.toFloat()?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f

/** The day keys a week strip ending on [today] shows — the seven days up to and including it. */
fun weekEndingOn(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }
