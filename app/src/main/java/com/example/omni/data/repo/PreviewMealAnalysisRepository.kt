package com.example.omni.data.repo

import com.example.omni.data.model.MealAnalysis
import com.example.omni.data.model.MealAnalysisItem
import com.example.omni.data.model.MealAnalysisResult
import com.example.omni.data.model.MealAnalysisTotals

/**
 * A no-network stand-in for [MealAnalysisRepository] used by `@Preview`s and the preview [AppContainer].
 * It never touches Gemini or the backend — it just returns a fixed, plausible estimate so the review
 * sheet has something to render. Tests use their own fakes (see the nutrition ViewModel tests) rather
 * than this one.
 */
class PreviewMealAnalysisRepository : MealAnalysisRepository {
    override suspend fun analyzeMeal(mealText: String): MealAnalysisResult =
        MealAnalysisResult.Success(
            MealAnalysis(
                mealName = "Eggs, bread and peanut butter",
                items = listOf(
                    MealAnalysisItem("Egg", 2f, "large", 144, 12.6f, 0.8f, 9.6f, 0f),
                    MealAnalysisItem("Bread", 2f, "slice", 160, 6f, 30f, 2f, 3.6f),
                    MealAnalysisItem("Peanut butter", 1f, "tbsp", 190, 8f, 6f, 16f, 0.9f),
                ),
                totals = MealAnalysisTotals(calories = 494, protein = 26.6f, carbs = 36.8f, fat = 27.6f, fiber = 4.5f),
                estimated = true,
                needsClarification = false,
                clarificationQuestion = null,
            ),
        )
}
