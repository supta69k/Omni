package com.example.omni.data.repo

import com.example.omni.data.model.Comment
import com.example.omni.data.model.CommentPageSize
import com.example.omni.data.model.Post
import com.example.omni.data.model.toComment
import com.example.omni.data.model.toFirestoreMap
import com.example.omni.data.model.toPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
    private val auth: FirebaseAuth,
) : FeedRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val backendBaseUrl = "https://omni-jx01.onrender.com"

    /**
     * The scope the snapshot listener's async join runs on. Supervised and IO-dispatched: one post's
     * failed join must not cancel the others, and none of it belongs on the main thread.
     */
    private val joinScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>> = callbackFlow {
        // One cache per subscription: opening the feed re-reads every count, scrolling it does not.
        // See [joinCounts] for why a snapshot after the first almost never needs the network.
        val joined = mutableMapOf<String, Joined>()
        val registration: ListenerRegistration =
            postsQuery(pageSize).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                // The join is async, so the listener hands the snapshot to a scope the flow owns.
                launchJoin(snapshot, uid, joined) { trySend(it) }
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

    /**
     * The Following tab's query: `authorId in [chunk]`, ordered and capped, one listener per chunk.
     *
     * Chunked because `whereIn` accepts at most [WhereInLimit] values — following more people than
     * that is one query too big for Firestore to run, not an error the UI should show. Each chunk is
     * its own listener with its own limit, and the chunks are merged here: deduplicated by id (a post
     * can only be in one chunk, but a re-delivery can), sorted by recency across all of them, and cut
     * to [limit] — so the caller gets [limit] posts, not [limit] per chunk.
     *
     * The index this needs already exists: `in` uses the same composite index as `==`, and
     * `posts(authorId ASC, createdAt DESC)` is in `firestore.indexes.json` for [observeByAuthor].
     *
     * Each chunk keeps its **own** join memo. Sharing one would have each chunk's `retainAll` evict
     * the other chunks' entries on every snapshot, which would re-read every count every time.
     *
     * Emitted as soon as any chunk answers rather than waiting for all of them: the common case is
     * one chunk, and in the rare multi-chunk case a list that fills in beats a blank tab.
     */
    override fun observeByAuthors(uid: String, authorIds: Set<String>, limit: Int): Flow<List<Post>> =
        callbackFlow {
            if (authorIds.isEmpty()) {
                // Following nobody is an answer, not a query. Sent so the tab renders its empty state
                // instead of sitting on whatever the last subscription left behind.
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }

            val chunks = authorIds.toList().chunked(WhereInLimit)
            // The latest page per chunk, and one memo per chunk. Both are touched from the join scope,
            // so every read and write of them is inside `merge`'s lock.
            val pages = MutableList<List<Post>>(chunks.size) { emptyList() }
            val memos = List(chunks.size) { mutableMapOf<String, Joined>() }
            val lock = Any()

            val registrations = chunks.mapIndexed { index, chunk ->
                firestore.collection(Posts)
                    .whereIn("authorId", chunk)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener
                        joinScope.launch {
                            val joinedPosts = try {
                                joinCounts(snapshot, uid, memos[index])
                            } catch (_: Exception) {
                                snapshot.documents.mapNotNull { it.toPost() }
                            }
                            val merged = synchronized(lock) {
                                pages[index] = joinedPosts
                                pages.flatten()
                                    .distinctBy { it.id }
                                    .sortedByDescending { it.createdAt }
                                    .take(limit)
                            }
                            trySend(merged)
                        }
                    }
            }
            awaitClose { registrations.forEach { it.remove() } }
        }

    override suspend fun countByAuthor(authorId: String): Int = try {
        firestore.collection(Posts)
            .whereEqualTo("authorId", authorId)
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
            .toInt()
    } catch (_: Exception) {
        // Offline there is no aggregation to run. -1 says "unknown", which the profile renders as
        // the number of rows it actually has rather than as a confident 0.
        -1
    }

    private fun launchJoin(
        snapshot: QuerySnapshot,
        uid: String,
        joined: MutableMap<String, Joined>,
        onReady: (List<Post>) -> Unit,
    ) {
        joinScope.launch {
            try {
                onReady(joinCounts(snapshot, uid, joined))
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
        return joinCounts(snapshot, uid, mutableMapOf())
    }

    /**
     * Likes and unlikes, as the presence or absence of one document under `posts/{id}/likes/{uid}`.
     *
     * The document's *existence* is the like; it carries nothing but a timestamp. The scheme this
     * replaced had every like document carry a `likers` array meant to hold everyone who had liked,
     * seeded from `post.get("likers")` — a field no post has ever had, because [createPost] does not
     * write one and DEVIATION 2 forbids the client touching a post's counters. So the array only
     * ever held the one uid that wrote it, unliking never removed that uid from anyone else's copy,
     * and the count [joinCounts] read back was "1 if I liked this, 0 otherwise" regardless of how
     * many others had. One document per liker cannot drift: it is either there or it is not.
     */
    override suspend fun toggleLike(uid: String, postId: String) {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Not signed in")
        val request = Request.Builder()
            .url("$backendBaseUrl/posts/$postId/like")
            .header("Authorization", "Bearer $idToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Like failed: ${response.code}")
                }
            }
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
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Not signed in")
        val json = JSONObject().put("body", body.trim())
        val request = Request.Builder()
            .url("$backendBaseUrl/posts/$postId/comments")
            .header("Authorization", "Bearer $idToken")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Comment failed: ${response.code}")
                }
            }
        }
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
     * The per-post join: have *I* liked it, how many have, how many have commented, how many have
     * reposted it — run concurrently across the page, and **only for posts not already joined**.
     *
     * Five questions, five reads, each one a network round trip: membership is a document `get`, and
     * a `count()` aggregation has no cached form at all. A twenty-post page is a hundred round trips,
     * and this used to run them again on *every* snapshot — so scrolling, posting, or anyone else
     * posting re-read the whole page. That is the cost that made the feed feel heavy.
     *
     * [joined] is the subscription's memo: a post keeps the counts it was first joined with. That is
     * sound because of what a `posts` snapshot can and cannot mean — likes, comments and reposts all
     * live in *subcollections*, and writing one does not modify the post document, so a snapshot
     * never fires because a count changed. Re-reading them on every snapshot could therefore only
     * ever pick up another device's change by luck of timing. What it reliably did pick up was the
     * bill. My own actions still move the numbers immediately: the ViewModel applies them optimistically.
     *
     * Each read is guarded on its own so one failure costs one fact rather than the page: offline the
     * membership checks still resolve from cache and each count falls back to the post's stored
     * counter (which DEVIATION 2 keeps at 0 — an honest "not counted" rather than a wrong number).
     */
    private suspend fun joinCounts(
        snapshot: QuerySnapshot,
        uid: String,
        joined: MutableMap<String, Joined>,
    ): List<Post> = coroutineScope {
        val posts = snapshot.documents.mapNotNull { it.toPost() }

        val fresh = posts
            .filter { it.id !in joined }
            .map { post ->
                async {
                    val likedByMe = exists(likeDocument(post.id, uid))
                    val repostedByMe = exists(repostDocument(post.id, uid))
                    post.id to Joined(
                        likedByMe = likedByMe,
                        likeCount = countOf(likesCollection(post.id), post.likeCount),
                        commentCount = countOf(commentsCollection(post.id), post.commentCount),
                        repostCount = countOf(repostsCollection(post.id), post.repostCount),
                        repostedByMe = repostedByMe,
                    )
                }
            }
            .awaitAll()

        joined += fresh
        // The page shrinks as older posts scroll out of the listener's window; the memo shrinks with
        // it, so a long session cannot accumulate entries for posts nothing will ask about again.
        joined.keys.retainAll(posts.map { it.id }.toSet())

        posts.map { post ->
            val counts = joined[post.id] ?: return@map post
            post.copy(
                likedByMe = counts.likedByMe,
                likeCount = counts.likeCount,
                commentCount = counts.commentCount,
                repostCount = counts.repostCount,
                repostedByMe = counts.repostedByMe,
            )
        }
    }

    /** What one post's subcollections said the first time this subscription asked. */
    private data class Joined(
        val likedByMe: Boolean,
        val likeCount: Int,
        val commentCount: Int,
        val repostCount: Int,
        val repostedByMe: Boolean,
    )

    private suspend fun exists(document: com.google.firebase.firestore.DocumentReference): Boolean =
        try {
            document.get().await().exists()
        } catch (_: Exception) {
            false
        }

    /** One server-side `count()`, falling back to [ifUnavailable] when it cannot be run. */
    private suspend fun countOf(collection: CollectionReference, ifUnavailable: Int): Int = try {
        collection.count().get(AggregateSource.SERVER).await().count.toInt()
    } catch (_: Exception) {
        ifUnavailable
    }

    /**
     * The repost, as one batch: the quoting post and the marker that says I made it.
     *
     * Batched rather than written in sequence because the two facts are one fact. Written in
     * sequence, a failure between them leaves either a repost nothing counts or a count with no
     * repost behind it, and neither has a screen that could repair it. The marker's id is my uid, so
     * a second tap writes the same document rather than a second one — but the read below rejects it
     * first, so the quoting post is never duplicated either.
     *
     * Image reposts carry the image: the URL is already public (Cloudinary), so the new post points
     * at the same one and the feed shows the photograph, not a description of it.
     */
    override suspend fun repost(uid: String, postId: String, author: PostAuthor): Boolean {
        val marker = repostDocument(postId, uid)
        if (marker.get().await().exists()) return false

        val original = firestore.collection(Posts).document(postId).get().await().toPost()
            ?: return false

        val quote = Post(
            id = "",
            authorId = uid,
            authorName = author.name,
            authorPhotoUrl = author.photoUrl,
            authorVerified = author.verified,
            authorProfession = author.profession,
            body = "🔁 ${original.authorName}:\n${original.body}",
            imageUrl = original.imageUrl,
        )

        firestore.batch()
            .set(firestore.collection(Posts).document(), quote.toFirestoreMap())
            .set(marker, mapOf("createdAt" to FieldValue.serverTimestamp()))
            .commit()
            .await()
        return true
    }

    private fun postsQuery(pageSize: Int): Query =
        firestore.collection(Posts)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

    private fun likesCollection(postId: String) =
        firestore.collection(Posts).document(postId).collection(Likes)

    private fun commentsCollection(postId: String) =
        firestore.collection(Posts).document(postId).collection(Comments)

    private fun repostsCollection(postId: String) =
        firestore.collection(Posts).document(postId).collection(Reposts)

    private fun likeDocument(postId: String, uid: String) =
        likesCollection(postId).document(uid)

    private fun repostDocument(postId: String, uid: String) =
        repostsCollection(postId).document(uid)

    private companion object {
        const val Posts = "posts"
        const val Likes = "likes"
        const val Comments = "comments"
        const val Reposts = "reposts"

        /** Firestore's own ceiling on the values an `in` filter accepts. Not a number we chose. */
        const val WhereInLimit = 30
    }
}
