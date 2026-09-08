package com.example.omni.data.repo

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [GuideProgressRepository] over Firestore.
 *
 * A missing document is 0, not an error — before Phase 7 writes anything, *every* guide is missing, so the
 * absent case is the normal one and the card has to render it without complaining.
 *
 * Read through [Number] and clamped like every other console-reachable value in this layer: `percent` is
 * written as an integer but comes back as `Long`, and a value outside 0..100 would put the progress bar's
 * puck off the end of its track.
 */
class FirestoreGuideProgressRepository(
    private val firestore: FirebaseFirestore,
) : GuideProgressRepository {

    override fun observePercent(uid: String, guideId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection(Users).document(uid)
            .collection(GuideProgress).document(guideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend((snapshot?.get("percent") as? Number)?.toInt()?.coerceIn(0, 100) ?: 0)
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val Users = "users"
        const val GuideProgress = "guideProgress"
    }
}
