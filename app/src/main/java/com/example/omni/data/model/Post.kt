package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * One community post — the `posts/{postId}` document (BACKEND_PLAN §7).
 *
 * The author's identity is denormalised onto the post at write time (`authorId`, `authorName`,
 * `authorPhotoUrl`, `authorVerified`, `authorProfession`), so a feed page costs one query rather than
 * N+1 author lookups. The accepted cost, per the plan: renaming a user leaves old posts under the old
 * name.
 *
 * [likedByMe] and [likeCount] are *not* fields of the document. The Firestore rules deployed with
 * this phase (DEVIATION 2 in `firestore.rules`) forbid the client from writing the counters — they
 * are reserved for the Phase 12 Cloud Functions — so the like state travels beside the post instead:
 * the like document at `posts/{id}/likes/{uid}` carries the post's `likers` array, and the count is
 * the array's size. See [com.example.omni.data.repo.FeedRepository] for how the two are joined.
 */
data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String? = null,
    val authorVerified: Boolean = false,
    val authorProfession: Profession? = null,
    val body: String,
    val imageUrl: String? = null,
    val createdAt: Long = 0L,
    /** Joined in by the repository, not stored on the document. */
    val likedByMe: Boolean = false,
    /** Joined in by the repository: the `likers` array off my own like document, or 0 without one. */
    val likeCount: Int = 0,
    /** Joined in by the repository when the comments sheet is opened; 0 means "not loaded yet". */
    val commentCount: Int = 0,
    /** Joined in by the repository from `posts/{id}/reposts`, the same shape as the likes. */
    val repostCount: Int = 0,
    /** Joined in by the repository: whether `posts/{id}/reposts/{myUid}` exists. */
    val repostedByMe: Boolean = false,
)

/** The map written to `posts/{postId}` on create. Counters start at 0 — the rules require it. */
fun Post.toFirestoreMap(): Map<String, Any?> = mapOf(
    "authorId" to authorId,
    "authorName" to authorName,
    "authorPhotoUrl" to authorPhotoUrl,
    "authorVerified" to authorVerified,
    "authorProfession" to authorProfession?.name,
    "body" to body,
    "imageUrl" to imageUrl,
    "createdAt" to FieldValue.serverTimestamp(),
    "likeCount" to 0,
    "commentCount" to 0,
    "repostCount" to 0,
)

fun DocumentSnapshot.toPost(): Post? {
    if (!exists()) return null
    return Post(
        id = id,
        authorId = getString("authorId").orEmpty(),
        authorName = getString("authorName").orEmpty(),
        // Blank is absent — `""` is non-null and would take every `?:` fallback's photo branch, so
        // an author who cleared their picture made the feed draw an empty image instead of the
        // initial that stands in for one.
        authorPhotoUrl = getString("authorPhotoUrl")?.takeIf { it.isNotBlank() },
        authorVerified = getBoolean("authorVerified") ?: false,
        authorProfession = getString("authorProfession")
            ?.let { name -> Profession.entries.firstOrNull { it.name == name } },
        body = getString("body").orEmpty(),
        imageUrl = getString("imageUrl"),
        createdAt = millisOf("createdAt"),
    )
}

/**
 * One comment — `posts/{postId}/comments/{commentId}` (BACKEND_PLAN §7).
 *
 * Carries its author's name the same way a post does, for the same one-read reason.
 */
data class Comment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: Long = 0L,
)

fun Comment.toFirestoreMap(): Map<String, Any?> = mapOf(
    "authorId" to authorId,
    "authorName" to authorName,
    "body" to body,
    "createdAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toComment(): Comment? {
    if (!exists()) return null
    return Comment(
        id = id,
        authorId = getString("authorId").orEmpty(),
        authorName = getString("authorName").orEmpty(),
        body = getString("body").orEmpty(),
        createdAt = millisOf("createdAt"),
    )
}

/**
 * How many comments one post's live listener carries.
 *
 * Every other collection in the app already caps itself — conversations and notifications at 50,
 * a chat thread at 100 — and this was the one that did not, so a post with a thousand replies meant
 * a thousand documents on the wire and a thousand rows in a sheet that shows four. The newest 50 is
 * what the sheet is for; older ones are readable in the order they were written, which nothing in
 * the design offers a way to reach anyway.
 */
const val CommentPageSize = 50L


/**
 * "3 min ago" — the relative timestamp the design hardcodes on every post, made real.
 *
 * Locale-aware through [android.text.format.DateUtils], which also localises "yesterday"/"days ago"
 * for a Bengali phone. A post whose `createdAt` is still 0 (the server timestamp not yet resolved on
 * a freshly written post) reads as "just now" rather than "51 years ago".
 */
fun relativeTimeOf(createdAtMillis: Long): String {
    if (createdAtMillis <= 0L) return "just now"
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        createdAtMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

/**
 * "1k" — the design's compact count format, for like/comment numbers that can grow past four digits.
 *
 * 999 shows whole (three digits fit the pill); 1,000 and up collapse to "1k", "1.2k", "15k". The
 * design's own examples are "1k", "100" and "50", all of which this reproduces exactly.
 */
fun compactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> {
        val k = count / 1_000f
        val text = if (k % 1f == 0f) "${k.toInt()}" else "%.1f".format(k)
        "${text}k"
    }
    else -> "${count / 1_000}k"
}
