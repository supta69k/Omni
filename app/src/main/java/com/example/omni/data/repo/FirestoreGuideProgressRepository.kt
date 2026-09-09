package com.example.omni.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [GuideProgressRepository] over Firestore.
 *
 * A missing document is 0 — not an error — everywhere in here. Until a step is finished for the first
 * time *every* guide is missing, so the absent case is the normal one and the cards have to render it
 * without complaining.
 *
 * Every value is read through [Number] and clamped, like the rest of this layer: `percent` is written as
 * an integer but comes back as `Long`, and a value outside 0..100 would put a progress puck off the end
 * of its track. Step numbers get the same treatment for a different reason — the detail screen looks
 * steps up *by* number, so a `0` or a `-3` from the console would simply never match a row.
 */
class FirestoreGuideProgressRepository(
    private val firestore: FirebaseFirestore,
) : GuideProgressRepository {

    override fun observePercent(uid: String, guideId: String): Flow<Int> = callbackFlow {
        val registration = doc(uid, guideId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(percentIn(snapshot?.get(Percent)))
        }
        awaitClose { registration.remove() }
    }

    override fun observeAllPercents(uid: String): Flow<Map<String, Int>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().associate { it.id to percentIn(it.get(Percent)) })
        }
        awaitClose { registration.remove() }
    }

    override fun observeCompletedSteps(uid: String, guideId: String): Flow<Set<Int>> = callbackFlow {
        val registration = doc(uid, guideId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            // Anything that is not a positive whole number is dropped rather than coerced: a step number
            // is an identity, so there is no sensible nearest value to fall back to.
            val steps = (snapshot?.get(CompletedSteps) as? List<*>).orEmpty()
                .mapNotNull { (it as? Number)?.toInt() }
                .filter { it > 0 }
                .toSet()
            trySend(steps)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setCompletedSteps(
        uid: String,
        guideId: String,
        completed: Set<Int>,
        percent: Int,
    ) {
        doc(uid, guideId)
            .set(
                mapOf(
                    // Sorted so the console shows something readable, and so writing the same set twice
                    // produces the same document rather than a reordered one.
                    CompletedSteps to completed.filter { it > 0 }.sorted(),
                    Percent to percent.coerceIn(0, 100),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                // Merge, so the document keeps any field a later phase adds beside these three.
                SetOptions.merge(),
            )
            .await()
    }

    private fun collection(uid: String) =
        firestore.collection(Users).document(uid).collection(GuideProgress)

    private fun doc(uid: String, guideId: String) = collection(uid).document(guideId)

    private companion object {
        const val Users = "users"
        const val GuideProgress = "guideProgress"
        const val Percent = "percent"
        const val CompletedSteps = "completedSteps"

        fun percentIn(value: Any?): Int = (value as? Number)?.toInt()?.coerceIn(0, 100) ?: 0
    }
}
