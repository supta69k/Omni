package com.example.omni.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import com.example.omni.MainActivity
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealSlot
import com.example.omni.data.model.todayKey
import com.example.omni.di.AppContainer

/**
 * Handles 1-tap quick logging of a recent or favorite meal directly from the widget.
 *
 * Uses the existing meal's exact nutritional values and persists it for today
 * using the authoritative [MealRepository.saveMeal] implementation.
 */
class QuickLogMealActionCallback : ActionCallback {

    companion object {
        val KeyMealName = ActionParameters.Key<String>("meal_name")
        val KeyMealCalories = ActionParameters.Key<Int>("meal_calories")
        val KeyMealProtein = ActionParameters.Key<Float>("meal_protein")
        val KeyMealCarbs = ActionParameters.Key<Float>("meal_carbs")
        val KeyMealFat = ActionParameters.Key<Float>("meal_fat")
        val KeyMealFiber = ActionParameters.Key<Float>("meal_fiber")
        val KeyMealSlot = ActionParameters.Key<String>("meal_slot")

        fun parametersFor(meal: Meal): ActionParameters = actionParametersOf(
            KeyMealName to meal.name,
            KeyMealCalories to meal.calories,
            KeyMealProtein to meal.protein,
            KeyMealCarbs to meal.carbs,
            KeyMealFat to meal.fat,
            KeyMealFiber to meal.fiber,
            KeyMealSlot to meal.slot.name,
        )
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val name = parameters[KeyMealName]?.trim().orEmpty()
        if (name.isBlank()) return

        // Prevent accidental rapid taps
        if (!WidgetActionDebounce.canExecute("food_quick_log_$name")) {
            return
        }

        val appContext = context.applicationContext
        AppContainer.ensureInit(appContext)
        val container = AppContainer.current
        val uid = container.authRepository.currentUid

        if (uid == null) {
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appContext.startActivity(intent)
            return
        }

        try {
            val calories = parameters[KeyMealCalories] ?: 0
            val protein = parameters[KeyMealProtein] ?: 0f
            val carbs = parameters[KeyMealCarbs] ?: 0f
            val fat = parameters[KeyMealFat] ?: 0f
            val fiber = parameters[KeyMealFiber] ?: 0f
            val slotRaw = parameters[KeyMealSlot]
            val slot = MealSlot.from(slotRaw)

            val newMeal = Meal(
                id = "", // Blank id so Firestore generates a fresh unique document for this meal
                name = name,
                slot = slot,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                fiber = fiber,
                loggedAt = System.currentTimeMillis(),
            )

            val today = todayKey()
            // Authoritative save and day nutrition roll-up recomputation
            container.mealRepository.saveMeal(uid, today, newMeal)

            // Update recent meals store
            OmniWidgetStore.addRecentMeal(appContext, uid, newMeal)

            // Refresh this widget and any others
            OmniFoodWidget().update(appContext, glanceId)
            OmniWidgetUpdater.updateFoodWidgets(appContext)
        } catch (e: Exception) {
            Log.w("OmniWidget", "Failed to quick-log meal from widget", e)
        }
    }
}
