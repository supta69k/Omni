package com.example.omni.data.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The follow graph — `users/{uid}/following/{targetUid}` (BACKEND_PLAN §7).
 *
 * A follow is a document whose id *is* the target: it exists or it does not, which makes the whole
 * graph idempotent by construction and race-free without transactions — two devices following the
 * same person write the same document and both succeed.
 *
 * The feed's "Following" segment reads this set and filters the loaded page client-side, exactly as
 * BACKEND_PLAN §7's "The 'Following' tab, honestly" note prescribed.
 */
interface FollowRepository {

    /** The uids I follow, live. Empty when I follow nobody — the honest state of a new account. */
    fun observeFollowing(uid: String): Flow<Set<String>>

    /** Whether [uid] follows [target]. A one-document read, cheaper than collecting the set. */
    fun observeFollows(uid: String, target: String): Flow<Boolean>

    /** Follows [target]. Writing the document is the whole act — no counters, no fan-out. */
    suspend fun follow(uid: String, target: String)

    /** Unfollows [target]: the document's absence *is* unfollowed. */
    suspend fun unfollow(uid: String, target: String)
}

class FirestoreFollowRepository(
    private val firestore: FirebaseFirestore,
) : FollowRepository {

    override fun observeFollowing(uid: String): Flow<Set<String>> = callbackFlow {
        val registration = following(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { it.id }?.toSet().orEmpty())
        }
        awaitClose { registration.remove() }
    }

    override fun observeFollows(uid: String, target: String): Flow<Boolean> =
        observeFollowing(uid).map { target in it }

    override suspend fun follow(uid: String, target: String) {
        following(uid).document(target)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun unfollow(uid: String, target: String) {
        following(uid).document(target).delete().await()
    }

    private fun following(uid: String) =
        firestore.collection("users").document(uid).collection("following")
}
