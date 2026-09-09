package com.example.omni.data.repo

import com.example.omni.data.model.Meal
import com.example.omni.data.model.toDayNutrition
import com.example.omni.data.model.toFirestoreMap
import com.example.omni.data.model.toMeal
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [MealRepository] over Firestore.
 *
 * **The roll-up is recomputed, not incremented,** which is the one place this class deliberately disagrees
 * with the water card next door. A glass is an event that can only be added, so `addGlass` increments; a
 * day's macros are a *sum over a set*, and the set can shrink. An edit that lowered a meal's protein or a
 * delete would both need a compensating negative increment, computed from a value the caller would have to
 * have read first — which is the read-modify-write race §4 rule 7 exists to avoid, just spelled differently.
 * Re-summing the surviving meals has no such race: whatever the list says is what the day is.
 *
 * The recompute costs one extra query per write. That is the right trade at this scale — a day holds a
 * handful of meals, the query is served from the local cache in the common case, and correctness after a
 * delete is worth more than a read.
 *
 * Offline, both halves queue: the meal write and the roll-up merge are ordinary writes, applied to the
 * local cache immediately (so the screen updates on the same frame) and replayed in order when the network
 * returns (§4 rule 6). The query in between reads the cache, so the sum is computed from the same list the
 * user is looking at.
 */
class FirestoreMealRepository(
    private val firestore: FirebaseFirestore,
) : MealRepository {

    override fun observeMeals(uid: String, date: String): Flow<List<Meal>> = callbackFlow {
        val registration = mealsCollection(uid, date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            // Ordered here rather than with `orderBy("loggedAt")`: a document written by an older build (or
            // by hand) can lack the field entirely, and a Firestore `orderBy` silently *omits* those
            // documents instead of sorting them last. A handful of meals sorts for free.
            trySend(snapshot?.documents?.mapNotNull { it.toMeal() }?.sortedBy { it.sortKey }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveMeal(uid: String, date: String, meal: Meal) {
        val meals = mealsCollection(uid, date)
        val document = if (meal.id.isBlank()) meals.document() else meals.document(meal.id)
        // Not a merge: an edit that cleared a field should clear it, and every field the app owns is in
        // the map anyway. `loggedAt` is stamped by the caller, so a re-save keeps the meal in place in the
        // log rather than jumping it to the end.
        document.set(meal.toFirestoreMap()).await()
        recomputeDay(uid, date)
    }

    override suspend fun deleteMeal(uid: String, date: String, mealId: String) {
        if (mealId.isBlank()) return
        mealsCollection(uid, date).document(mealId).delete().await()
        recomputeDay(uid, date)
    }

    /**
     * Re-sums the day's meals onto the day document.
     *
     * Reads the collection back after the write rather than adjusting by a delta — see the class note. The
     * five sums are written even when they are 0, because "every meal deleted" has to be able to return the
     * day to zero; a merge that omitted them would leave yesterday's totals standing.
     */
    private suspend fun recomputeDay(uid: String, date: String) {
        val meals = mealsCollection(uid, date).get().await().documents.mapNotNull { it.toMeal() }
        val total = meals.toDayNutrition()

        firestore.collection(Users).document(uid).collection(Days).document(date)
            .set(
                mapOf(
                    "date" to date,
                    "calories" to total.calories,
                    "protein" to total.protein.toDouble(),
                    "carbs" to total.carbs.toDouble(),
                    "fat" to total.fat.toDouble(),
                    // The field Home's fibre card reads. Named `fiber` in Firestore, `fiberGrams` in
                    // Kotlin — see `DailyMetrics`.
                    "fiber" to total.fiber.toDouble(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                // Merged so the same document keeps its water, steps and sleep, which nothing here owns.
                SetOptions.merge(),
            )
            .await()
    }

    private fun mealsCollection(uid: String, date: String) =
        firestore.collection(Users).document(uid).collection(Days).document(date).collection(Meals)

    private companion object {
        const val Users = "users"
        const val Days = "days"
        const val Meals = "meals"
    }
}
