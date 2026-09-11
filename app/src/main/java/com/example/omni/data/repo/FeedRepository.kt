package com.example.omni.data.repo

import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import kotlinx.coroutines.flow.Flow

/**
 * The community feed — `posts` and its `likes`/`comments` subcollections (BACKEND_PLAN §7).
 *
 * ## How counts work without the counters
 *
 * The deployed rules (DEVIATION 2 in `firestore.rules`) forbid the client from writing
 * `likeCount`/`commentCount` on a post — those belong to the Phase 12 Cloud Functions, which do not
 * exist yet. The feed therefore keeps its own count beside the state it is allowed to write:
 *
 *  - **Liking** writes/deletes `posts/{id}/likes/{uid}` carrying the post's whole `likers` array
 *    (`arrayUnion`/`arrayRemove` of uids, applied by every liker to their own like document). The
 *    count is that array's size, so every client that reads my like document — which the feed
 *    already does to know *whether* I liked — gets the number for free, and it stays consistent
 *    because every liker applies the same union to the same array.
 *  - **Commenting** simply writes a comment document; the sheet loads the subcollection and the count
 *    is the list's length. The `commentCount` field on the post stays 0 until Phase 12's trigger
 *    exists, which is the honest reading of "no function has counted them yet" — the pill shows the
 *    real number the moment the sheet has the list.
 *
 * When the functions land, both call sites switch to reading the fields and this note shrinks.
 */
interface FeedRepository {

    /**
     * The first page of the feed, live — a snapshot listener, so a post composed anywhere lands
     * without a refresh (BACKEND_PLAN §11 Phase 8: listener for page one only).
     *
     * Each post arrives joined with `likedByMe`/`likeCount` from *my* like document under it — one
     * extra read per post, which is the price of not being allowed to hold the count on the post
     * itself. The join is per-post because the like state is per-user: a shared feed document cannot
     * carry it.
     */
    fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>>

    /**
     * One author's posts, newest first, live — the profile screen's list.
     *
     * Like [observeFirstPage] but filtered to `authorId == [authorId]` and without the like-state
     * join: the profile is a reading surface, and a like from there is a feed action the feed
     * performs. Capped for the same reason the feed pages — a profile is a page, not a scroll
     * that grows without bound.
     */
    fun observeByAuthor(authorId: String, limit: Int): Flow<List<Post>>

    /**
     * The posts of *several* authors, newest first, live — the Following tab.
     *
     * A real query rather than a filter over the Discover page. Filtering in memory answers "which of
     * the twenty newest posts on the app are by someone I follow", which is a different question from
     * "what have the people I follow posted": follow one quiet person among a hundred noisy strangers
     * and the honest answer is their posts, while the filtered page is empty. So the authors go into
     * the query and the limit applies to *their* posts.
     *
     * [authorIds] is expected to include the viewer's own uid when their posts belong in the result —
     * this method has no opinion about that; it returns the authors it is given. An empty set returns
     * an empty list rather than everybody.
     *
     * Joined with my like/repost state exactly as [observeFirstPage] is, so a row in this tab behaves
     * identically to the same row in Discover.
     */
    fun observeByAuthors(uid: String, authorIds: Set<String>, limit: Int): Flow<List<Post>>

    /**
     * How many posts [authorId] has written, server-side.
     *
     * Separate from [observeByAuthor] because that query is capped: a profile with 40 posts would
     * otherwise say "20 posts", and a count that disagrees with the number of rows below it is the
     * kind of wrong that makes a user distrust everything else on the page. One aggregation, billed
     * as a single read.
     */
    suspend fun countByAuthor(authorId: String): Int

    /**
     * One further page, oldest-of-the-loaded as the cursor — a one-shot `get()`, not a listener, so
     * a growing feed does not re-read everything it has already shown.
     */
    suspend fun loadPage(uid: String, beforeCreatedAt: Long, pageSize: Int): List<Post>

    /**
     * Toggles my like on [postId]: writes the like document (carrying the post's current `likers`
     * array plus mine) or deletes it.
     *
     * The repository reads the post's likers before writing, which costs one read per toggle. The
     * write itself is a single `set`/`delete` of my own document, which the rules allow any signed-in
     * user to perform on their own uid.
     */
    suspend fun toggleLike(uid: String, postId: String)

    /**
     * One post's comments, newest first, live while the sheet is open.
     */
    fun observeComments(postId: String): Flow<List<Comment>>

    /**
     * Adds a comment. The count the pill shows comes from [observeComments]'s list length.
     *
     * [authorName] is the commenter's display name, handed down from the hoisted profile by the
     * caller — the repository does not open a profile listener of its own (BACKEND_PLAN §4 rule 3).
     */
    suspend fun addComment(uid: String, postId: String, authorName: String, body: String)

    /**
     * Creates a post with the signed-in author's identity denormalised onto it.
     *
     * [author] is the profile to copy onto the post, not a parameter the caller picks freely: the
     * repository writes the uid it is handed as `authorId`, and the rules require that to be the
     * caller's own uid.
     */
    suspend fun createPost(uid: String, author: PostAuthor, body: String, imageUrl: String?)

    /**
     * Reposts [postId] once, as a quoting post of my own plus a marker at
     * `posts/{postId}/reposts/{uid}`.
     *
     * Returns `false` when the marker already exists — one repost per user per post, which is what
     * makes the pill a toggleable *state* rather than a button that writes a new post every time it
     * is pressed. The marker and the quoting post are committed in one [com.google.firebase.firestore.WriteBatch],
     * so the two can never disagree: there is no path that leaves a repost counted but not written,
     * or written but not counted.
     *
     * Not undoable, deliberately. Deleting the marker would leave the quoting post behind — it is a
     * post like any other, in other people's feeds — and a count that says 0 beside a repost someone
     * is currently reading is worse than a pill that does not un-press.
     */
    suspend fun repost(uid: String, postId: String, author: PostAuthor): Boolean
}

/** The identity stamped onto a post at create time — the profile, flattened to what the feed shows. */
data class PostAuthor(
    val name: String,
    val photoUrl: String?,
    val verified: Boolean,
    val profession: com.example.omni.data.model.Profession?,
)
