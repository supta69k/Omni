package com.example.omni.data.repo

import com.example.omni.data.model.Meal
import kotlinx.coroutines.flow.Flow

/**
 * The day's food log — `users/{uid}/days/{date}/meals/{mealId}` (BACKEND_PLAN §7).
 *
 * A subcollection of the day rather than fields on it, because a day holds an unbounded number of meals
 * and a document that grew an array per meal would be rewritten in full on every edit.
 *
 * The **roll-up is this repository's job**, not a caller's: every write here recomputes the day's
 * `calories`/`protein`/`carbs`/`fat`/`fiber` from the surviving meals and merges them onto the day
 * document. That is what connects a logged meal to Home's fibre card, which reads the roll-up and knows
 * nothing about meals (BACKEND_PLAN §11 Phase 6, "recompute the day's rollup from its meals on every
 * change"). Doing it here means the two screens cannot disagree about what the day contains, and a caller
 * cannot forget.
 */
interface MealRepository {

    /**
     * Every meal logged on [date], oldest first, re-emitted on any change.
     *
     * An empty list for a day with nothing logged — not an error and not `null`, since that is the state
     * every day starts in.
     */
    fun observeMeals(uid: String, date: String): Flow<List<Meal>>

    /**
     * Writes [meal] to [date] and recomputes the day's roll-up.
     *
     * Creates or replaces: a [Meal.id] that already exists is an **edit**, and a blank one is a new meal
     * whose id Firestore allocates. One method rather than `add`/`update` because the entry sheet cannot
     * tell the difference — it either opened on a row or it did not — and a caller that picked the wrong
     * one would silently duplicate a meal.
     */
    suspend fun saveMeal(uid: String, date: String, meal: Meal)

    /** Removes one meal from [date] and recomputes the day's roll-up. */
    suspend fun deleteMeal(uid: String, date: String, mealId: String)
}
