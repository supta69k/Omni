package com.example.omni.data.repo

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The follow graph — `users/{uid}/following/{targetUid}` and its mirror,
 * `users/{targetUid}/followers/{uid}` (BACKEND_PLAN §7).
 *
 * A follow is a document whose id *is* the other person: it exists or it does not, which makes the
 * whole graph idempotent by construction and race-free without transactions — two devices following
 * the same person write the same document and both succeed.
 *
 * **Both halves are written, in one batch.** `following` answers "whose posts belong in my Following
 * tab", which is the question the feed asks; `followers` answers "how many follow *them*", which is
 * the question a profile asks. Neither can answer the other's: counting my followers from `following`
 * alone would mean scanning every user's subcollection. The batch is what keeps the two halves from
 * disagreeing — either both land or neither does.
 *
 * The feed's "Following" segment reads this set and filters the loaded page client-side, exactly as
 * BACKEND_PLAN §7's "The 'Following' tab, honestly" note prescribed.
 */
interface FollowRepository {

    /** The uids I follow, live. Empty when I follow nobody — the honest state of a new account. */
    fun observeFollowing(uid: String): Flow<Set<String>>

    /** Whether [uid] follows [target]. A one-document read, cheaper than collecting the set. */
    fun observeFollows(uid: String, target: String): Flow<Boolean>

    /**
     * How many follow [uid], counted server-side.
     *
     * A one-shot read rather than a listener: `count()` is an aggregation, and aggregations cannot be
     * observed. The profile re-reads it whenever the follow state changes, which covers the only
     * change the visitor can cause themselves; somebody else's follow lands on the next open. Falls
     * back to 0 when the count cannot be run — offline there is no cached aggregation.
     */
    suspend fun followerCount(uid: String): Int

    /** Follows [target] — writes my `following` entry and their `followers` entry together. */
    suspend fun follow(uid: String, target: String)

    /** Unfollows [target]: the documents' absence *is* unfollowed. Both halves go together. */
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

    override suspend fun followerCount(uid: String): Int = try {
        followers(uid).count().get(AggregateSource.SERVER).await().count.toInt()
    } catch (_: Exception) {
        0
    }

    override suspend fun follow(uid: String, target: String) {
        val stamp = mapOf("createdAt" to FieldValue.serverTimestamp())
        firestore.batch()
            .set(following(uid).document(target), stamp)
            .set(followers(target).document(uid), stamp)
            .commit()
            .await()
    }

    override suspend fun unfollow(uid: String, target: String) {
        firestore.batch()
            .delete(following(uid).document(target))
            .delete(followers(target).document(uid))
            .commit()
            .await()
    }

    private fun following(uid: String) =
        firestore.collection("users").document(uid).collection("following")

    private fun followers(uid: String) =
        firestore.collection("users").document(uid).collection("followers")
}
