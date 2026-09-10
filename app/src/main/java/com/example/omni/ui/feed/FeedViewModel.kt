package com.example.omni.ui.feed

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.PostAuthor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The feed's segment — Discover shows every post, Following keeps the authors I follow.
 *
 * The follow graph does not exist yet (no phase before 11 builds it), so Following filters
 * client-side against the loaded page with an empty follow set — which shows only my own posts,
 * which is the honest state of "you follow nobody" rather than a lie that aliases Discover
 * (BACKEND_PLAN §7, "The 'Following' tab, honestly").
 */
enum class FeedSegment { Discover, Following }

/** One comment row, flattened for the sheet. */
data class CommentRow(
    val id: String,
    val authorName: String,
    val body: String,
    val timeText: String,
)

/**
 * Everything the feed renders.
 *
 * @property posts the loaded pages in feed order, joined with my like state and folded with the
 *   optimistic like overrides.
 * @property canLoadMore whether a further page exists — `true` until a page comes back short.
 * @property commentsOpenId the post whose comments sheet is up, or `null`.
 * @property comments the open post's comments, newest first.
 */
data class FeedUiState(
    val segment: FeedSegment = FeedSegment.Discover,
    val posts: List<Post> = emptyList(),
    val canLoadMore: Boolean = false,
    val commentsOpenId: String? = null,
    val comments: List<CommentRow> = emptyList(),
)

/**
 * The feed's reads and writes: the live first page, one-shot further pages, the like toggle with its
 * optimistic flip, and the comments sheet.
 *
 * Post creation takes the author identity as a parameter from `MainActivity` — which already holds
 * the hoisted profile listener — rather than opening a second profile listener here (BACKEND_PLAN
 * §4 rule 3: state is handed down as values).
 */
class FeedViewModel(
    private val authRepository: AuthRepository,
    private val feedRepository: FeedRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val uid: Flow<String?> = authRepository.authState
        .map { if (it == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()

    private val segment = MutableStateFlow(FeedSegment.Discover)

    /** Further pages beyond the listener's first, appended in load order. */
    private val olderPages = MutableStateFlow<List<Post>>(emptyList())

    /** `true` once a page has come back short, which is how "no more pages" is known. */
    private val exhausted = MutableStateFlow(false)

    private val commentsOpenId = MutableStateFlow<String?>(null)

    /**
     * The optimistic like overrides, keyed by post id. Written on tap, cleared when the live
     * listener's next emission already agrees (or on write failure, with the pre-tap state put back).
     */
    private val optimistic = MutableStateFlow<Map<String, Post>>(emptyMap())

    /**
     * The live first page. Errors degrade to an empty feed rather than crashing — the screen's
     * empty state is the honest reading of a feed that could not be read.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val firstPage: Flow<List<Post>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            feedRepository.observeFirstPage(uid, PageSize).catch { cause ->
                Log.w("Omni", "The feed could not be read", cause)
                emit(emptyList())
            }.onEach { page -> firstPageWasFull = page.size >= PageSize }
        }
    }

    /** `true` only while the *first page* was full — a short first page means the feed is exhausted. */
    @Volatile private var firstPageWasFull = false

    /** The open post's comments, live while the sheet is up. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val comments: Flow<List<CommentRow>> =
        combine(uid, commentsOpenId) { uid, id -> uid to id }
            .distinctUntilChanged()
            .flatMapLatest { (_, id) ->
                if (id == null) {
                    flowOf(emptyList())
                } else {
                    feedRepository.observeComments(id)
                        .catch { cause ->
                            Log.w("Omni", "Comments on $id could not be read", cause)
                            emit(emptyList())
                        }
                        .map { list -> list.map { it.toRow() } }
                }
            }

    /** The loaded pages, with the optimistic overrides folded over them by id. */
    private val loaded: Flow<List<Post>> =
        combine(firstPage, olderPages, optimistic) { first, older, overrides ->
            (first + older).map { post -> overrides[post.id] ?: post }
        }

    val uiState: StateFlow<FeedUiState> =
        combine(loaded, segment, exhausted, comments, commentsOpenId) { pages, seg, done, rows, openId ->
            FeedUiState(
                segment = seg,
                posts = when (seg) {
                    FeedSegment.Discover -> pages
                    // Only my own posts until a follow feature exists; see [FeedSegment].
                    FeedSegment.Following -> pages.filter { it.authorId == authRepository.currentUid }
                },
                // "Load more" appears only when a further page is genuinely possible: not exhausted,
                // something to load, and a first page that filled its limit (a 3-post feed showing a
                // Load-more button is a lie about there being more).
                canLoadMore = !done && pages.isNotEmpty() && firstPageWasFull,
                commentsOpenId = openId,
                comments = rows,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = FeedUiState(),
        )

    fun onSegmentChange(value: FeedSegment) {
        segment.value = value
    }

    /**
     * The optimistic like: the pill flips and the count moves on the tap's frame, and the write
     * follows. A failure rolls the override back — unlike the water card there *was* something
     * optimistically applied, so leaving it would have the pill lie about the server's state.
     *
     * **Success keeps the override.** It used to drop it, on the reasoning that the live listener's
     * next emission would carry the truth; it never does. A like is a document in the post's `likes`
     * subcollection, and a subcollection write does not re-fire a snapshot listener registered on
     * the `posts` collection — so no emission arrived, the join never re-ran, and dropping the
     * override rebuilt the row from the unchanged pre-tap snapshot. The pill filled on tap and
     * reverted a moment later, which is the flicker that reads as "liking doesn't stick".
     *
     * The override is the truth for this session: it is `base ± 1` off a count the join read from
     * the server, and the next time the feed re-joins (reopening it, or a new post arriving) the
     * server's own count replaces it.
     */
    fun toggleLike(postId: String) {
        val uid = authRepository.currentUid ?: return
        val current = (loadedState()).firstOrNull { it.id == postId } ?: return
        val flipped = current.copy(
            likedByMe = !current.likedByMe,
            likeCount = (current.likeCount + if (current.likedByMe) -1 else 1).coerceAtLeast(0),
        )
        optimistic.value = optimistic.value + (postId to flipped)
        viewModelScope.launch {
            try {
                feedRepository.toggleLike(uid, postId)
            } catch (cause: Exception) {
                Log.w("Omni", "Toggling the like on $postId failed", cause)
                optimistic.value = optimistic.value - postId
            }
        }
    }

    fun openComments(postId: String) {
        commentsOpenId.value = postId
    }

    fun closeComments() {
        commentsOpenId.value = null
    }

    fun addComment(body: String) {
        val uid = authRepository.currentUid ?: return
        val postId = commentsOpenId.value ?: return
        if (body.isBlank()) return
        val name = authorName.ifBlank { "You" }
        viewModelScope.launch {
            try {
                feedRepository.addComment(uid, postId, name, body)
            } catch (cause: Exception) {
                Log.w("Omni", "Commenting on $postId failed", cause)
            }
        }
    }

    /**
     * My display name, handed down from the hoisted profile by `MainActivity` — the same pattern as
     * the goals on Home. Set on entry and on every profile change, because a rename should apply to
     * the next comment without reopening the feed.
     */
    fun onAuthorName(name: String) {
        authorName = name
    }

    private var authorName = ""

    /**
     * Reposts [postId] — a new post of my own that quotes the original.
     *
     * The rules forbid the client from touching a post's `repostCount` (DEVIATION 2 — counters are
     * the Cloud Functions' job, and those do not exist yet), so the count the pill shows stays 0
     * while the repost itself is real: a quoting post in the feed, attributed to me, crediting the
     * original author by name. When the Phase 12 functions land, the pill can read the counter the
     * trigger maintains — the reference post remains the durable artefact either way.
     *
     * Image reposts carry the image: the URL is already public (Cloudinary), so the new post points
     * at the same one and the feed shows the photograph, not a description of it.
     */
    fun repost(postId: String) {
        val uid = authRepository.currentUid ?: return
        val original = loadedState().firstOrNull { it.id == postId } ?: return
        viewModelScope.launch {
            try {
                feedRepository.createPost(
                    uid = uid,
                    author = PostAuthor(
                        name = authorName.ifBlank { "You" },
                        photoUrl = null,
                        verified = false,
                        profession = null,
                    ),
                    body = "🔁 ${original.authorName}:\n${original.body}",
                    imageUrl = original.imageUrl,
                )
            } catch (cause: Exception) {
                Log.w("Omni", "Reposting $postId failed", cause)
            }
        }
    }

    fun loadMore() {
        val uid = authRepository.currentUid ?: return
        if (loading) return
        val oldest = loadedState().lastOrNull { it.createdAt > 0 }?.createdAt ?: return
        loading = true
        viewModelScope.launch {
            try {
                val page = feedRepository.loadPage(uid, oldest, PageSize)
                olderPages.value = olderPages.value + page
                if (page.size < PageSize) exhausted.value = true
            } catch (cause: Exception) {
                Log.w("Omni", "Loading an older page failed", cause)
            } finally {
                loading = false
            }
        }
    }

    /**
     * Creates a post as the signed-in user, uploading [image] first when one is attached.
     *
     * [author] comes from the hoisted profile in `MainActivity`; the repository writes the uid it is
     * handed as `authorId`, and the rules require that to be the caller's own — so a caller cannot
     * post as someone else.
     *
     * [onResult] is handed `null` on success and a sentence to show otherwise. It used to be a bare
     * `onDone()` called from a `finally`, which navigated back to the feed whether or not the write
     * had worked — survivable while a post was three fields of text, dishonest now that it can carry
     * a multi-megabyte upload over a phone connection that drops.
     *
     * The upload runs before the document is written, deliberately: a post created first and then
     * failed to illustrate would need a second write to repair, and there is no screen that could
     * offer to retry it.
     */
    fun createPost(
        author: PostAuthor,
        body: String,
        image: Uri? = null,
        onResult: (String?) -> Unit = {},
    ) {
        val uid = authRepository.currentUid
            ?: return onResult("You are signed out. Sign in and try again.")
        // An image on its own is a post — the rules cap `body` at 2000 characters but never require
        // one, and a photo with no caption is the ordinary case on every feed this one resembles.
        if (body.isBlank() && image == null) {
            return onResult("Write something or add a photo first.")
        }
        viewModelScope.launch {
            val imageUrl = if (image == null) null else {
                try {
                    mediaRepository.uploadPostImage(uid, image)
                } catch (cause: Exception) {
                    Log.w("Omni", "Uploading the post image failed", cause)
                    return@launch onResult("The photo could not be uploaded — ${cause.reason()}")
                }
            }
            try {
                feedRepository.createPost(uid, author, body, imageUrl)
                onResult(null)
            } catch (cause: Exception) {
                Log.w("Omni", "Creating the post failed", cause)
                onResult("The post could not be published — ${cause.reason()}")
            }
        }
    }

    /**
     * The failure's own words, or a fallback when it has none.
     *
     * [MediaRepository] builds each throw as a readable clause — Cloudinary's own rejection text
     * included — precisely so the composer can show it, and this used to replace all of them with a
     * fixed "check your connection" that was wrong about the cause every time it was shown. A
     * message the user can read back is the difference between a bug report that says "it failed"
     * and one that names the failing step.
     */
    private fun Exception.reason(): String =
        message?.takeIf { it.isNotBlank() } ?: "check your connection and try again"

    private var loading = false

    /** The freshest loaded list without waiting for the sharing to settle. */
    private fun loadedState(): List<Post> = uiState.value.posts.ifEmpty { emptyList() }

    private fun Comment.toRow() = CommentRow(
        id = id,
        authorName = authorName.ifBlank { "Someone" },
        body = body,
        timeText = relativeTimeOf(createdAt),
    )

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L

        /** BACKEND_PLAN's own page size for the feed's `limit(…)`. */
        const val PageSize = 20
    }
}
