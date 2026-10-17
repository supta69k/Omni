package com.example.omni.widget

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.omni.data.model.DefaultCalorieGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealSlot
import com.example.omni.data.model.todayKey
import com.example.omni.di.AppContainer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of data for the Omni Water Tracker widget.
 */
data class WaterWidgetData(
    val glasses: Int = 0,
    val goal: Int = DefaultWaterGoal,
    val date: String = todayKey(),
    val isAuthenticated: Boolean = true,
)

/**
 * Snapshot of data for the Omni Food Log widget.
 */
data class FoodWidgetData(
    val calories: Int = 0,
    val calorieGoal: Int = DefaultCalorieGoal,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val recentMeals: List<Meal> = emptyList(),
    val date: String = todayKey(),
    val isAuthenticated: Boolean = true,
)

/**
 * Atomic in-memory debounce gate to prevent duplicate widget callback execution
 * when a user rapidly taps "+ Add a glass" or "+ Log" from the home screen.
 */
object WidgetActionDebounce {
    private val lastActionTimes = ConcurrentHashMap<String, Long>()
    private const val DEBOUNCE_WINDOW_MS = 600L

    fun canExecute(actionKey: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastActionTimes[actionKey] ?: 0L
        if (now - last < DEBOUNCE_WINDOW_MS) {
            return false
        }
        lastActionTimes[actionKey] = now
        return true
    }
}

/**
 * Authoritative, lightweight data cache for Omni home-screen widgets.
 *
 * Provides immediate synchronous reads from SharedPreferences so home-screen
 * widgets render without delay or blank states, while maintaining strict session
 * isolation (guaranteeing one user's health numbers never bleed into another user's widget).
 */
object OmniWidgetStore {
    private const val PREFS_NAME = "omni_widget_store"
    private const val KEY_UID = "widget_uid"
    private const val KEY_DATE = "widget_date"
    private const val KEY_WATER_GLASSES = "water_glasses"
    private const val KEY_WATER_GOAL = "water_goal"
    private const val KEY_CALORIES = "food_calories"
    private const val KEY_CALORIE_GOAL = "food_calorie_goal"
    private const val KEY_PROTEIN = "food_protein"
    private const val KEY_CARBS = "food_carbs"
    private const val KEY_FAT = "food_fat"
    private const val KEY_RECENT_MEALS = "recent_meals_json"

    private const val MAX_RECENT_MEALS = 4

    /**
     * Loads the current water widget data.
     * Checks authentication and day rollover, synchronizing with the repository.
     */
    suspend fun getWaterData(context: Context): WaterWidgetData {
        AppContainer.ensureInit(context)
        val container = AppContainer.current
        val currentUid = container.authRepository.currentUid
            ?: return WaterWidgetData(isAuthenticated = false)

        val today = todayKey()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUid = prefs.getString(KEY_UID, null)
        val cachedDate = prefs.getString(KEY_DATE, null)

        val cachedGlasses = if (cachedUid == currentUid && cachedDate == today) {
            prefs.getInt(KEY_WATER_GLASSES, 0)
        } else 0
        val cachedGoal = if (cachedUid == currentUid) {
            prefs.getInt(KEY_WATER_GOAL, DefaultWaterGoal)
        } else DefaultWaterGoal

        // Query fresh metrics with a short timeout to catch any cloud/local updates
        val freshMetrics = runCatching {
            withTimeoutOrNull(1500L) {
                container.metricsRepository.observeDay(currentUid, today).firstOrNull()
            }
        }.getOrNull()

        val finalGlasses = freshMetrics?.waterGlasses ?: cachedGlasses
        val finalGoal = cachedGoal.coerceAtLeast(1)

        // Update cache
        saveWaterData(
            context = context,
            uid = currentUid,
            date = today,
            glasses = finalGlasses,
            goal = finalGoal,
        )

        return WaterWidgetData(
            glasses = finalGlasses,
            goal = finalGoal,
            date = today,
            isAuthenticated = true,
        )
    }

    /**
     * Synchronously persists water metrics to the widget store.
     */
    fun saveWaterData(
        context: Context,
        uid: String,
        date: String,
        glasses: Int,
        goal: Int,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_DATE, date)
            .putInt(KEY_WATER_GLASSES, glasses)
            .putInt(KEY_WATER_GOAL, goal)
            .apply()
    }

    /**
     * Loads the current food log widget data, including recent meals for quick logging.
     */
    suspend fun getFoodData(context: Context): FoodWidgetData {
        AppContainer.ensureInit(context)
        val container = AppContainer.current
        val currentUid = container.authRepository.currentUid
            ?: return FoodWidgetData(isAuthenticated = false)

        val today = todayKey()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUid = prefs.getString(KEY_UID, null)
        val cachedDate = prefs.getString(KEY_DATE, null)

        val cachedCalories = if (cachedUid == currentUid && cachedDate == today) {
            prefs.getInt(KEY_CALORIES, 0)
        } else 0
        val cachedGoal = if (cachedUid == currentUid) {
            prefs.getInt(KEY_CALORIE_GOAL, DefaultCalorieGoal)
        } else DefaultCalorieGoal
        val cachedProtein = if (cachedUid == currentUid && cachedDate == today) {
            prefs.getFloat(KEY_PROTEIN, 0f)
        } else 0f
        val cachedCarbs = if (cachedUid == currentUid && cachedDate == today) {
            prefs.getFloat(KEY_CARBS, 0f)
        } else 0f
        val cachedFat = if (cachedUid == currentUid && cachedDate == today) {
            prefs.getFloat(KEY_FAT, 0f)
        } else 0f

        val recentMeals = if (cachedUid == currentUid) {
            getRecentMeals(context, currentUid)
        } else emptyList()

        // Check fresh metrics with short timeout
        val freshMetrics = runCatching {
            withTimeoutOrNull(1500L) {
                container.metricsRepository.observeDay(currentUid, today).firstOrNull()
            }
        }.getOrNull()

        val finalCalories = freshMetrics?.calories ?: cachedCalories
        val finalProtein = freshMetrics?.protein ?: cachedProtein
        val finalCarbs = freshMetrics?.carbs ?: cachedCarbs
        val finalFat = freshMetrics?.fat ?: cachedFat

        saveFoodData(
            context = context,
            uid = currentUid,
            date = today,
            calories = finalCalories,
            calorieGoal = cachedGoal,
            protein = finalProtein,
            carbs = finalCarbs,
            fat = finalFat,
            recentMeals = recentMeals,
        )

        return FoodWidgetData(
            calories = finalCalories,
            calorieGoal = cachedGoal,
            protein = finalProtein,
            carbs = finalCarbs,
            fat = finalFat,
            recentMeals = recentMeals,
            date = today,
            isAuthenticated = true,
        )
    }

    /**
     * Synchronously persists food metrics to the widget store.
     */
    fun saveFoodData(
        context: Context,
        uid: String,
        date: String,
        calories: Int,
        calorieGoal: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        recentMeals: List<Meal> = emptyList(),
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_DATE, date)
            .putInt(KEY_CALORIES, calories)
            .putInt(KEY_CALORIE_GOAL, calorieGoal)
            .putFloat(KEY_PROTEIN, protein)
            .putFloat(KEY_CARBS, carbs)
            .putFloat(KEY_FAT, fat)

        if (recentMeals.isNotEmpty()) {
            val jsonArray = JSONArray()
            recentMeals.take(MAX_RECENT_MEALS).forEach { meal ->
                val obj = JSONObject().apply {
                    put("id", meal.id)
                    put("name", meal.name)
                    put("slot", meal.slot.name)
                    put("calories", meal.calories)
                    put("protein", meal.protein.toDouble())
                    put("carbs", meal.carbs.toDouble())
                    put("fat", meal.fat.toDouble())
                    put("fiber", meal.fiber.toDouble())
                }
                jsonArray.put(obj)
            }
            editor.putString(KEY_RECENT_MEALS, jsonArray.toString())
        }

        editor.apply()
    }

    /**
     * Adds a meal to the recent meals cache, maintaining up to [MAX_RECENT_MEALS] distinct meals.
     */
    fun addRecentMeal(context: Context, uid: String, meal: Meal) {
        if (meal.name.isBlank()) return
        val currentList = getRecentMeals(context, uid).toMutableList()
        // Deduplicate by name (case-insensitive)
        currentList.removeAll { it.name.trim().equals(meal.name.trim(), ignoreCase = true) }
        currentList.add(0, meal)

        val trimmed = currentList.take(MAX_RECENT_MEALS)
        val jsonArray = JSONArray()
        trimmed.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("slot", item.slot.name)
                put("calories", item.calories)
                put("protein", item.protein.toDouble())
                put("carbs", item.carbs.toDouble())
                put("fat", item.fat.toDouble())
                put("fiber", item.fiber.toDouble())
            }
            jsonArray.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_RECENT_MEALS, jsonArray.toString())
            .apply()
    }

    /**
     * Reads cached recent meals for the signed-in user.
     */
    fun getRecentMeals(context: Context, uid: String): List<Meal> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUid = prefs.getString(KEY_UID, null)
        if (cachedUid != uid) return emptyList()

        val jsonStr = prefs.getString(KEY_RECENT_MEALS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val result = mutableListOf<Meal>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    Meal(
                        id = obj.optString("id", ""),
                        name = obj.getString("name"),
                        slot = MealSlot.from(obj.optString("slot")),
                        calories = obj.optInt("calories", 0),
                        protein = obj.optDouble("protein", 0.0).toFloat(),
                        carbs = obj.optDouble("carbs", 0.0).toFloat(),
                        fat = obj.optDouble("fat", 0.0).toFloat(),
                        fiber = obj.optDouble("fiber", 0.0).toFloat(),
                        loggedAt = 0L,
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.w("OmniWidget", "Failed to parse recent meals from cache", e)
            emptyList()
        }
    }
}
