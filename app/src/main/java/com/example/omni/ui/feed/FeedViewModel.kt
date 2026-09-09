package com.example.omni.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
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
            }
        }
    }

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
                canLoadMore = !done && pages.isNotEmpty(),
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
     * The optimistic like: the pill flips and the count moves on the tap's frame, the write follows,
     * and the live listener confirms or corrects it.
     *
     * A write failure rolls the override back — unlike the water card there *was* something
     * optimistically applied, so leaving it would have the pill lie about the server's state.
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
                // The listener's next emission carries the truth; the override has done its job.
                optimistic.value = optimistic.value - postId
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
     * Creates a post as the signed-in user. [author] comes from the hoisted profile in
     * `MainActivity`; the repository writes the uid it is handed as `authorId`, and the rules
     * require that to be the caller's own — so a caller cannot post as someone else.
     */
    fun createPost(author: PostAuthor, body: String, imageUrl: String? = null, onDone: () -> Unit = {}) {
        val uid = authRepository.currentUid ?: run { onDone(); return }
        if (body.isBlank()) run { onDone(); return }
        viewModelScope.launch {
            try {
                feedRepository.createPost(uid, author, body, imageUrl)
            } catch (cause: Exception) {
                Log.w("Omni", "Creating the post failed", cause)
            } finally {
                onDone()
            }
        }
    }

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
