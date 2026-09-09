package com.example.omni.data.repo

import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The in-memory twin of [FirestoreMealRepository] — the design's own three rows, and writes that work.
 *
 * Keyed by date so switching days in a preview behaves like the real thing: the seeded log belongs to
 * whichever day the preview opens on, and every other day of the week is empty, which is exactly what a
 * fresh account looks like.
 *
 * The macro numbers are Figma's ("404cal", 12/15/35g, three times over). The **fibre** figures are not in
 * the design at all — no mock row carries one, because the field did not exist until Phase 6 — so they are
 * invented at plausible values whose sum lands near the fibre card's own 18g mock, which keeps the two
 * screens telling one story in a preview.
 */
class PreviewMealRepository : MealRepository {

    private val byDate = MutableStateFlow<Map<String, List<Meal>>>(emptyMap())

    /** `null` until the first read, so the seed lands on whatever day the caller actually asks for. */
    private var seededDate: String? = null

    override fun observeMeals(uid: String, date: String): Flow<List<Meal>> {
        if (seededDate == null) {
            seededDate = date
            byDate.update { it + (date to Seed) }
        }
        return byDate.map { days -> days[date].orEmpty().sortedBy { it.sortKey } }
    }

    override suspend fun saveMeal(uid: String, date: String, meal: Meal) {
        byDate.update { days ->
            val existing = days[date].orEmpty()
            val stored = if (meal.id.isBlank()) meal.copy(id = "preview-${existing.size + 1}") else meal
            val replaced = existing.map { if (it.id == stored.id) stored else it }
            days + (date to if (replaced.any { it.id == stored.id }) replaced else existing + stored)
        }
    }

    override suspend fun deleteMeal(uid: String, date: String, mealId: String) {
        byDate.update { days -> days + (date to days[date].orEmpty().filterNot { it.id == mealId }) }
    }

    private companion object {
        val Seed = listOf(
            Meal("preview-1", "Eggs & Toast", MealSlot.BREAKFAST, 404, 12f, 15f, 35f, 4f, 1L),
            Meal("preview-2", "Rice,fish and vegis", MealSlot.LUNCH, 404, 12f, 15f, 35f, 7f, 2L),
            Meal("preview-3", "Rice,fish and vegis", MealSlot.DINNER, 404, 12f, 15f, 35f, 7f, 3L),
        )
    }
}
