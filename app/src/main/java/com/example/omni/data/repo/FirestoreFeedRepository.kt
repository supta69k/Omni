package com.example.omni.data.repo

import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import com.example.omni.data.model.toComment
import com.example.omni.data.model.toFirestoreMap
import com.example.omni.data.model.toPost
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * [FeedRepository] over Firestore.
 *
 * Mapped by hand for the same reason every other repository here is: a console-typed value of the
 * wrong type must degrade to a default, not crash the feed.
 *
 * The first-page listener joins the like state in with one `get()` per post, run concurrently; the
 * join is re-run on every snapshot because a like written from another device must eventually
 * correct the local one. Firestore's local cache makes the common case — my own like, applied
 * optimistically by the ViewModel and then written locally — resolve without a round-trip.
 */
class FirestoreFeedRepository(
    private val firestore: FirebaseFirestore,
) : FeedRepository {

    /**
     * The scope the snapshot listener's async join runs on. Supervised and IO-dispatched: one post's
     * failed join must not cancel the others, and none of it belongs on the main thread.
     */
    private val joinScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>> = callbackFlow {
        val registration: ListenerRegistration =
            postsQuery(pageSize).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                // The join is async, so the listener hands the snapshot to a scope the flow owns.
                launchJoin(snapshot, uid) { trySend(it) }
            }
        awaitClose { registration.remove() }
    }

    private fun launchJoin(
        snapshot: QuerySnapshot,
        uid: String,
        onReady: (List<Post>) -> Unit,
    ) {
        joinScope.launch {
            try {
                onReady(joinLikeState(snapshot, uid))
            } catch (_: Exception) {
                // A failed join still shows the posts; only the like state is missing.
                onReady(snapshot.documents.mapNotNull { it.toPost() })
            }
        }
    }

    override suspend fun loadPage(uid: String, beforeCreatedAt: Long, pageSize: Int): List<Post> {
        val cursor = com.google.firebase.Timestamp(beforeCreatedAt / 1000, 0)
        val snapshot = postsQuery(pageSize)
            .startAfter(cursor)
            .get()
            .await()
        return joinLikeState(snapshot, uid)
    }

    override suspend fun toggleLike(uid: String, postId: String) {
        val likeRef = likeDocument(postId, uid)
        val existing = likeRef.get().await()
        if (existing.exists()) {
            likeRef.delete().await()
            return
        }
        // My like document carries the post's public likers array plus me. Reading the post first
        // costs one read per toggle; in exchange, every reader of any like document gets the count.
        val post = postDocument(postId).get().await()
        val current = (post.get("likers") as? List<*>).orEmpty()
        likeRef.set(
            mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "likers" to FieldValue.arrayUnion(uid, *current.toTypedArray()),
            ),
        ).await()
    }

    override fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = firestore.collection(Posts).document(postId).collection(Comments)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toComment() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addComment(uid: String, postId: String, authorName: String, body: String) {
        val comment = Comment(id = "", authorId = uid, authorName = authorName, body = body.trim())
        firestore.collection(Posts).document(postId).collection(Comments)
            .add(
                mapOf(
                    "authorId" to comment.authorId,
                    "authorName" to comment.authorName,
                    "body" to comment.body,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun createPost(uid: String, author: PostAuthor, body: String, imageUrl: String?) {
        val post = Post(
            id = "",
            authorId = uid,
            authorName = author.name,
            authorPhotoUrl = author.photoUrl,
            authorVerified = author.verified,
            authorProfession = author.profession,
            body = body.trim(),
            imageUrl = imageUrl,
        )
        firestore.collection(Posts)
            .add(post.toFirestoreMap())
            .await()
    }

    /**
     * The per-post join: one `get()` of *my* like document, concurrently across the page.
     *
     * The like document's `likers` array is the count, so the read answers both questions — whether I
     * liked and how many have. A post with no like document under it is unliked by 0 people, which is
     * the normal state of a fresh post.
     */
    private suspend fun joinLikeState(snapshot: QuerySnapshot, uid: String): List<Post> =
        coroutineScope {
            snapshot.documents.map { document ->
                async {
                    val post = document.toPost() ?: return@async null
                    val like = likeDocument(post.id, uid).get().await()
                    val likers = (like.get("likers") as? List<*>).orEmpty()
                    post.copy(
                        likedByMe = like.exists(),
                        likeCount = likers.distinct().size,
                    )
                }
            }.awaitAll().filterNotNull()
        }

    private fun postsQuery(pageSize: Int): Query =
        firestore.collection(Posts)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

    private fun postDocument(postId: String) = firestore.collection(Posts).document(postId)

    private fun likeDocument(postId: String, uid: String) =
        firestore.collection(Posts).document(postId).collection(Likes).document(uid)

    private companion object {
        const val Posts = "posts"
        const val Likes = "likes"
        const val Comments = "comments"
    }
}
