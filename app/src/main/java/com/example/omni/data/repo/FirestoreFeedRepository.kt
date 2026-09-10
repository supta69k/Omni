package com.example.omni.data.repo

import com.example.omni.data.model.Comment
import com.example.omni.data.model.CommentPageSize
import com.example.omni.data.model.Post
import com.example.omni.data.model.toComment
import com.example.omni.data.model.toFirestoreMap
import com.example.omni.data.model.toPost
import com.google.firebase.firestore.AggregateSource
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

    override fun observeByAuthor(authorId: String, limit: Int): Flow<List<Post>> = callbackFlow {
        val registration = firestore.collection(Posts)
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toPost() }.orEmpty())
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

    /**
     * Likes and unlikes, as the presence or absence of one document under `posts/{id}/likes/{uid}`.
     *
     * The document's *existence* is the like; it carries nothing but a timestamp. The scheme this
     * replaced had every like document carry a `likers` array meant to hold everyone who had liked,
     * seeded from `post.get("likers")` — a field no post has ever had, because [createPost] does not
     * write one and DEVIATION 2 forbids the client touching a post's counters. So the array only
     * ever held the one uid that wrote it, unliking never removed that uid from anyone else's copy,
     * and the count [joinLikeState] read back was "1 if I liked this, 0 otherwise" regardless of how
     * many others had. One document per liker cannot drift: it is either there or it is not.
     */
    override suspend fun toggleLike(uid: String, postId: String) {
        val likeRef = likeDocument(postId, uid)
        if (likeRef.get().await().exists()) {
            likeRef.delete().await()
        } else {
            likeRef.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = firestore.collection(Posts).document(postId).collection(Comments)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            // Newest first, then capped — so the cap keeps the 50 the sheet opens on rather than
            // the 50 oldest. An uncapped listener on a busy post is a download that grows without
            // limit for a sheet that can only show a handful.
            .limit(CommentPageSize)
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
     * The per-post join: have *I* liked it, and how many have — run concurrently across the page.
     *
     * Two questions, two reads. Membership is my own like document; the count is a server-side
     * `count()` aggregation over the subcollection, which bills as a single read no matter how many
     * likers there are and, unlike the array it replaced, is the same number on every device.
     *
     * Each read is guarded on its own so one failure costs one fact rather than the page: an
     * aggregation needs the network (there is no cached `count()`), so offline the membership check
     * still resolves from cache and the count falls back to the post's stored counter.
     */
    private suspend fun joinLikeState(snapshot: QuerySnapshot, uid: String): List<Post> =
        coroutineScope {
            snapshot.documents.map { document ->
                async {
                    val post = document.toPost() ?: return@async null
                    val likedByMe = try {
                        likeDocument(post.id, uid).get().await().exists()
                    } catch (_: Exception) {
                        false
                    }
                    val likeCount = try {
                        likesCollection(post.id).count().get(AggregateSource.SERVER).await()
                            .count.toInt()
                    } catch (_: Exception) {
                        post.likeCount
                    }
                    post.copy(likedByMe = likedByMe, likeCount = likeCount)
                }
            }.awaitAll().filterNotNull()
        }

    private fun postsQuery(pageSize: Int): Query =
        firestore.collection(Posts)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

    private fun likesCollection(postId: String) =
        firestore.collection(Posts).document(postId).collection(Likes)

    private fun likeDocument(postId: String, uid: String) =
        likesCollection(postId).document(uid)

    private companion object {
        const val Posts = "posts"
        const val Likes = "likes"
        const val Comments = "comments"
    }
}
