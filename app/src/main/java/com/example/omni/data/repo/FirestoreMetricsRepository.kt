package com.example.omni.data.repo

import com.example.omni.data.model.DailyMetrics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [MetricsRepository] over Firestore.
 *
 * The write is a **merge-set carrying an increment**, which is what makes the plus button feel instant
 * and work offline at the same time:
 *
 *  - `increment` on a merge-set creates the document when it is missing, so there is no read-then-write
 *    race and no separate "create today's document" step (BACKEND_PLAN §11 Phase 3).
 *  - Firestore applies the increment to its **local cache first** and fires the snapshot listener before
 *    the network round-trip, so the card re-renders on the same frame as the tap. In airplane mode the
 *    write simply queues and replays — persistence is on by default, so there is no sync queue to build
 *    (§4 rule 6).
 *
 * Mapped by hand rather than with `toObject`, for the same reason [FirestoreUserRepository] is: a
 * console-typed value of the wrong type takes the whole screen down otherwise. Counts come back as
 * `Long` and an `increment` can leave a `Double`, so everything is read through [Number].
 */
class FirestoreMetricsRepository(
    private val firestore: FirebaseFirestore,
) : MetricsRepository {

    override fun observeDay(uid: String, date: String): Flow<DailyMetrics?> = callbackFlow {
        val registration = dayDocument(uid, date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toDailyMetrics(date))
        }
        awaitClose { registration.remove() }
    }

    /**
     * The month as one range query over document ids.
     *
     * Ranged on [FieldPath.documentId] rather than on the `date` field, even though every write in this
     * app sets `date`: the id is the one thing a day document cannot be missing, so a document written by
     * an older build or typed into the console still lands in the right month. `"$month-01".."$month-32"`
     * brackets the whole month lexicographically — ids are fixed-width `yyyy-MM-dd`, so `-32` sorts after
     * every real day and no month needs to know how long it is.
     */
    override fun observeMonth(uid: String, month: String): Flow<Map<String, DailyMetrics>> = callbackFlow {
        val registration = firestore.collection(Users).document(uid).collection(Days)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), "$month-01")
            .whereLessThanOrEqualTo(FieldPath.documentId(), "$month-32")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val days = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toDailyMetrics(document.id)?.let { document.id to it }
                }
                trySend(days.toMap())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addGlass(uid: String, date: String) {
        dayDocument(uid, date)
            .set(
                mapOf(
                    // Written even though it is also the document id: a query across days is otherwise
                    // unable to order or filter by date.
                    "date" to date,
                    "waterGlasses" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun setSteps(uid: String, date: String, steps: Int) {
        dayDocument(uid, date)
            .set(
                mapOf(
                    "date" to date,
                    // A plain value, not an increment — see [MetricsRepository.setSteps] for why the two
                    // writes on this document deliberately disagree about that.
                    "steps" to steps.coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun setSleepHours(uid: String, date: String, hours: Float) {
        dayDocument(uid, date)
            .set(
                mapOf(
                    "date" to date,
                    "sleepHours" to hours.coerceIn(0f, MaxSleepHours),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun dayDocument(uid: String, date: String) =
        firestore.collection(Users).document(uid).collection(Days).document(date)

    private companion object {
        const val Users = "users"
        const val Days = "days"

        /** A night cannot be longer than the day it is written to; the sheet already stops well short. */
        const val MaxSleepHours = 24f

        /** Grams. Far past anything edible, and short enough that the footnote stays on two lines. */
        const val MaxFiberGrams = 999f

        /** The three macros' shared ceiling; the same layout-slack reasoning as [MaxFiberGrams]. */
        const val MaxMacroGrams = 2_000f

        fun DocumentSnapshot.toDailyMetrics(date: String): DailyMetrics? {
            if (!exists()) return null
            return DailyMetrics(
                date = getString("date") ?: date,
                // Negative is impossible through this repository, but a console edit could write one and
                // the card's arithmetic assumes it cannot.
                waterGlasses = (get("waterGlasses") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                steps = (get("steps") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                sleepHours = float("sleepHours", MaxSleepHours),
                // Named `fiber` in Firestore (BACKEND_PLAN §7) but `fiberGrams` in Kotlin, where the unit
                // has to be visible next to a goal of the same name. Phase 6's meal roll-up writes it.
                fiberGrams = float("fiber", MaxFiberGrams),
                // The meal roll-up's other four sums (Phase 6). Nutrition re-sums its live meal list for
                // its own screen, so these exist for consistency between devices and for any future
                // reader that wants the day without its meals.
                calories = (get("calories") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                protein = float("protein", MaxMacroGrams),
                carbs = float("carbs", MaxMacroGrams),
                fat = float("fat", MaxMacroGrams),
            )
        }

        /**
         * One numeric field, clamped into `0f..max`.
         *
         * `NaN` and `Infinity` are both storable in Firestore and both would poison every arithmetic step
         * downstream — a `NaN` weight silently collapses a progress bar — so a non-finite value is treated
         * as an absent one. The upper clamp is a layout guarantee, not a data one: these numbers are printed
         * into boxes with a dp or two of slack (`UI_ARCHITECTURE.md` §6 rule 8).
         */
        fun DocumentSnapshot.float(key: String, max: Float): Float =
            (get(key) as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, max) ?: 0f
    }
}
