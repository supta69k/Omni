package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.Story
import com.example.omni.data.model.StoryLifetimeMillis
import com.example.omni.data.model.toFirestoreMap
import com.example.omni.data.model.toStory
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Stories — the `stories` collection (BACKEND_PLAN §7's social surface).
 *
 * ## The shape of the read
 *
 * One query for the whole strip: stories ordered by `createdAt`, newest first, filtered to the live
 * 24-hour window on the client. A `whereGreaterThan("expiresAt", now)` would need a composite index
 * with `createdAt` and re-issuing the listener at every midnight-ish rollover; filtering a ~dozen
 * documents locally costs nothing and keeps the rules and index story simple. Stories past their
 * window are invisible to the reader and are removed by their author's next open (see [deleteMine])
 * or a TTL policy later — never by a reader.
 *
 * ## The shape of the write
 *
 * A story is created with its expiry stamped at creation (`now + 24h`), and only its author may
 * write or delete it. The rules mirror exactly that.
 */
interface StoryRepository {

    /**
     * Every live story, newest first — one listener behind the whole strip.
     *
     * Expired stories are filtered on read, so the strip never shows a dead tile even before
     * anything deletes the document.
     */
    fun observeStories(): Flow<List<Story>>

    /**
     * Publishes a story: [imageUrl] comes from Cloudinary (already uploaded by the caller), and the
     * author's identity is denormalised the same way a post's is.
     */
    suspend fun createStory(story: Story)

    /**
     * Deletes one of *my* stories — the viewer's own "delete" affordance on their own story.
     * Returns without writing when the story is not mine; the rules would reject it anyway.
     */
    suspend fun deleteMine(uid: String, storyId: String)
}

class FirestoreStoryRepository(
    private val firestore: FirebaseFirestore,
) : StoryRepository {

    override fun observeStories(): Flow<List<Story>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection(Stories)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(StripLimit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Omni", "The story strip could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                trySend(snapshot?.documents?.mapNotNull { it.toStory() }?.filter { it.isLive(now) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createStory(story: Story) {
        firestore.collection(Stories)
            .add(story.toFirestoreMap())
            .await()
    }

    override suspend fun deleteMine(uid: String, storyId: String) {
        val reference = firestore.collection(Stories).document(storyId)
        val existing = reference.get().await()
        if (existing.getString("authorId") != uid) return
        reference.delete().await()
    }

    private companion object {
        const val Stories = "stories"

        /**
         * The strip's ceiling. A term-project community has a handful of authors; the cap exists so
         * a runaway write loop cannot turn the strip into an unbounded download — the same reasoning
         * as the feed's page size.
         */
        const val StripLimit = 50L
    }
}

/** The expiry a story created now carries. */
fun storyExpiryFromNow(): Long = System.currentTimeMillis() + StoryLifetimeMillis

/** Server-side timestamp for the create map — kept beside the repo so callers never build maps. */
@Suppress("unused")
private fun serverTimestamp(): FieldValue = FieldValue.serverTimestamp()
