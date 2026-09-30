package com.example.omni.data.model

/**
 * The AI meal analysis models — the client-side mirror of the backend's `MealAnalysisResult`
 * (Phase 7). The Android app never talks to Gemini directly: it POSTs a natural-language meal to the
 * Omni Render backend, which calls the model, validates the numbers, and returns this shape. These
 * are estimates the user reviews and edits before anything is written — nothing here is a food log
 * entry until the user confirms it and it collapses into a single [Meal] (see
 * [com.example.omni.ui.nutrition.NutritionViewModel.confirmAiMeal]).
 */

/**
 * One estimated food item in an analyzed meal — shown read-only in the review sheet so the user can
 * see what the total is made of. The macros are grams; [calories] are whole kilocalories, matching
 * the [Meal] model the confirmed result folds into. [fiber] defaults to 0 so an estimate that
 * predates fiber (or a food the model assigns none) parses unchanged.
 */
data class MealAnalysisItem(
    val name: String,
    val quantity: Float,
    val unit: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float = 0f,
)

/** The server-recomputed totals for the whole meal — the sum of [MealAnalysis.items], not the model's own claim. */
data class MealAnalysisTotals(
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
)

/**
 * A parsed, validated AI meal estimate.
 *
 * [estimated] is always true — the backend forces it, and the UI labels the result accordingly. When
 * [needsClarification] is true the description was too vague to estimate: [items]/[totals] are empty
 * and [clarificationQuestion] holds a single short prompt for the user rather than a guess.
 */
data class MealAnalysis(
    val mealName: String,
    val items: List<MealAnalysisItem>,
    val totals: MealAnalysisTotals,
    val estimated: Boolean,
    val needsClarification: Boolean,
    val clarificationQuestion: String?,
) {
    /**
     * Collapses the estimate into the single [Meal] the existing food log stores — the whole point of
     * reusing the log rather than inventing a second schema. [fiber] follows the analyzed totals like
     * every other macro, so an AI estimate now carries its fiber into the log; a meal analyzed before
     * fiber existed simply reads 0 and behaves exactly as before. The macro/calorie arguments default
     * to the analyzed totals but are passed explicitly by the review sheet so an edit is what gets
     * saved — and the sheet passes every field it edits, so an unedited fiber arrives here untouched.
     */
    fun toMeal(
        slot: MealSlot,
        name: String = mealName,
        calories: Int = totals.calories,
        protein: Float = totals.protein,
        carbs: Float = totals.carbs,
        fat: Float = totals.fat,
        fiber: Float = totals.fiber,
    ): Meal = Meal(
        id = "",
        name = name,
        slot = slot,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        loggedAt = 0L,
    )
}

/** The repository's answer: a validated estimate, or a typed failure the UI turns into a specific message. */
sealed interface MealAnalysisResult {
    data class Success(val analysis: MealAnalysis) : MealAnalysisResult
    data class Failure(val error: MealAnalysisError) : MealAnalysisResult
}

/**
 * Why an analysis could not be produced — each maps to one honest, specific sentence rather than a
 * catch-all "check your connection" (§34). [Network] is the *only* connectivity message, and it is
 * shown only for an actual transport failure. Every failure leaves manual logging available.
 */
enum class MealAnalysisError(val message: String) {
    /**
     * Gemini's own quota (429 RESOURCE_EXHAUSTED) or a model outage (backend AI_UNAVAILABLE), and any
     * stray 429. Retryable later. Omni imposes no per-user daily cap, so this is never framed as one.
     */
    Unavailable("AI meal analysis is temporarily unavailable. Please try again later. You can still log this meal manually."),

    /** The model answered but the estimate was unusable (backend AI_INVALID_RESPONSE). */
    InvalidResponse("The AI couldn't estimate this meal. Add a little more detail, or log it manually."),

    /** The description was empty or too long before it ever reached the model (backend INVALID_INPUT). */
    InvalidInput("Please describe your meal in a little more detail."),

    /** No valid session — the token could not be minted or the backend rejected it. */
    Auth("You're signed out. Sign in and try again."),

    /** A genuine transport failure — the one case where "check your connection" is the truthful message. */
    Network("Couldn't reach the server. Check your connection and try again."),

    /** Anything else, including a malformed success body. Manual logging still works. */
    Unknown("Something went wrong analyzing your meal. You can still log it manually."),
}
