package com.example.omni.ui.feed

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.PostAuthor
import com.example.omni.data.repo.UserRepository
import com.example.omni.data.repo.UserSearchLimit
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
 * Following is its **own query**, not a filter over the Discover page: the listener runs
 * `authorId in following` ordered by recency, so following one quiet person among a hundred noisy
 * strangers shows that person's posts rather than an empty tab. (BACKEND_PLAN §7's original "filter
 * the loaded page client-side" note is what that replaced — it answered "which of the newest twenty
 * posts on the app are by someone I follow", which is a different question.)
 *
 * **My own posts are not in it.** An earlier build queried `following ∪ me`, arguing that a tab which
 * hides what you just wrote reads as a lost post. On a real account that turned out to be the wrong
 * trade: the tab fills with your own posts and stops meaning what its name says. Following is other
 * people; Discover and my own profile are where my posts live. Following nobody is therefore an empty
 * tab, which is the honest state of a new account rather than a second Discover.
 */
enum class FeedSegment { Discover, Following }

/** One comment row, flattened for the sheet. */
data class CommentRow(
    val id: String,
    /** Who wrote it — carried so the row's avatar can open their page, as the feed's does. */
    val authorId: String,
    val authorName: String,
    /** Resolved live from [authorId] through the author cache, not stored on the comment. */
    val authorPhotoUrl: String? = null,
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
 * @property myUid the signed-in account, carried so a row can tell my own post from someone else's
 *   without asking the auth repository during composition.
 * @property following the uids I follow, live from `users/{myUid}/following` — the *same* set the
 *   Following segment filters on, so the pill on a row and the tab above it can never disagree.
 */
data class FeedUiState(
    val segment: FeedSegment = FeedSegment.Discover,
    val posts: List<Post> = emptyList(),
    val canLoadMore: Boolean = false,
    val commentsOpenId: String? = null,
    val comments: List<CommentRow> = emptyList(),
    val myUid: String? = null,
    val following: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
)

/**
 * Below this, a prefix match is everyone.
 *
 * One letter would return the first [com.example.omni.data.repo.UserSearchLimit] accounts
 * alphabetically, which looks like a broken search rather than a broad one. Declared beside the
 * state rather than inside the ViewModel because the panel needs it too: "we have not searched yet"
 * and "nobody by that name" are different answers, and only this number tells them apart.
 */
const val MinSearchQuery = 2

/**
 * The search field above the feed, and what it found.
 *
 * [query] is what is in the field — kept verbatim, untrimmed, because it is the field's own value and
 * trimming it under the cursor would move the caret. Everything else describes the *answer*, and the
 * states the panel can be in are told apart here rather than guessed at from an empty list: closed
 * ([query] blank), too short to ask, searching, empty-handed, or broken ([error]).
 */
data class FeedSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<com.example.omni.data.model.User> = emptyList(),
    val error: String? = null,
) {
    /** Whether the results panel is up at all — the field having something in it is the whole rule. */
    val isOpen: Boolean get() = query.isNotBlank()

    /** The field has something in it, but not yet enough to have asked anybody. */
    val isTooShort: Boolean get() = query.trim().length < MinSearchQuery
}

/** Intermediate result of combining the first five flows before adding [isRefreshing]. */
private data class Quint(
    val seg: FeedSegment,
    val posts: List<Post>,
    val canLoadMore: Boolean,
    val openId: String?,
    val rows: List<CommentRow>,
    val me: String?,
    val follows: Set<String>,
    val people: Map<String, com.example.omni.data.model.User>,
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
    private val followRepository: FollowRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val uid: StateFlow<String?> = authRepository.sessionUid

    private val segment = MutableStateFlow(FeedSegment.Discover)

    /** Tracks pull-to-refresh state. */
    private val isRefreshing = MutableStateFlow(false)

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
     * The live comment count per post id, populated by the comments sheet's own listener.
     *
     * The post document carries a structural `commentCount` field that the rules permit on create
     * but never on update — `posts/{id}`'s rule is `request.resource.data.commentCount ==
     * resource.data.commentCount`, which is what makes the counter honest about being server-side.
     * Without Cloud Functions (§9's `onCommentCreated` is Phase 12's) there is no write that can
     * move it, so it stays at zero on every post. The fix is to count from the live listener the
     * comments sheet is paying for anyway: a count we already have is the honest count, and a
     * comment that the sheet has not seen has not been written.
     */
    private val commentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * Authors by uid, resolved once each from `users/{uid}` and then reused.
     *
     * A post carries the name and photo its author had *when it was written* — the denormalisation
     * that makes a feed page one query instead of twenty-one. The cost the plan accepted was that a
     * rename or a new photo never reaches old posts, which in practice is how "my profile picture
     * doesn't show in the feed" happens: the account had no photo when it first posted, and the post
     * still says so.
     *
     * So the identity is resolved from [Post.authorId] and the post's own copy is the fallback. One
     * read per author per session, not per post and not per recomposition — twenty posts by three
     * people cost three reads.
     */
    private val authors = MutableStateFlow<Map<String, com.example.omni.data.model.User>>(emptyMap())

    /** Uids already asked for, so a missing profile is not re-requested on every emission. */
    private val requestedAuthors = mutableSetOf<String>()

    /**
     * The live page for the segment the feed is showing — Discover (everyone's newest) or
     * Following (the people I follow, including me) — **carried with the segment it was read for**.
     *
     * Two real listeners, keyed together rather than held separately. Switching segments re-subscribes
     * the listener rather than swapping in a `combine`-frozen value. Switching the follow set while on
     * Following also re-keys, so unfollowing someone removes their posts in the same frame the pill
     * turns. A segment change clears [olderPages] so Discover's cursor pages do not leak into
     * Following.
     *
     * The segment travels *with* the page because re-subscribing is not the same as clearing. A
     * `combine` retains each source's last value, and [segment] reaches [uiState] through [view] as
     * well — so on the tap, `view` re-emits with `Following` while this flow is still holding
     * Discover's twenty posts, and the combine would put them on screen under the new tab's name
     * until the new query answered. Pairing them lets [uiState] tell "these posts are this tab's"
     * from "these posts are the tab I just left", which is the difference between a blank moment and
     * a wrong one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val firstPage: Flow<Pair<FeedSegment, List<Post>>> =
        combine(uid, segment) { me, seg -> me to seg }
            .distinctUntilChanged()
            .flatMapLatest { (me, seg) ->
                when (seg) {
                    FeedSegment.Discover -> observeDiscover(me)
                    FeedSegment.Following -> observeFollowing(me)
                }.map { page -> seg to page }
            }

    /**
     * The live Discover page. Errors degrade to an empty feed rather than crashing — the screen's
     * empty state is the honest reading of a feed that could not be read.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDiscover(me: String?): Flow<List<Post>> =
        if (me == null) flowOf(emptyList()) else feedRepository.observeFirstPage(me, PageSize)
            .catch { cause ->
                Log.w("Omni", "The feed could not be read", cause)
                emit(emptyList())
            }
            .onEach { page ->
                firstPageWasFull = page.size >= PageSize
                // Switching into Discover invalidates anything loaded under Following.
                olderPages.value = emptyList()
                exhausted.value = false
            }

    /**
     * The live Following page, scoped to **the people I follow and nobody else**.
     *
     * My own uid is deliberately not in the query. It used to be (`.map { it + me }`), on the
     * reasoning that a tab which hides what you just wrote reads as a lost post — but on a device that
     * is not what it looks like: Following fills up with your own posts and stops being the thing it
     * is named after. Discover already carries my posts, and so does my profile. Following is other
     * people.
     *
     * The exclusion is by *omission*, not by filtering a downloaded page: the uid never enters
     * `authorIds`, so Firestore never sends those documents at all (§17).
     *
     * The flow is keyed on the following set — adding or removing a person re-subscribes, so the
     * query the listener runs is always the current one, with no restart needed. [authorIds] is
     * computed once per emission from the latest follow set, and the repository then handles the
     * chunking for `whereIn`.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFollowing(me: String?): Flow<List<Post>> =
        if (me == null) flowOf(emptyList())
        else followRepository.observeFollowing(me)
            // A stray `users/{me}/following/{me}` document would otherwise put my own posts back in
            // through the side door. Cheaper to hold the invariant here than to trust the data.
            .map { it - me }
            .distinctUntilChanged()
            .flatMapLatest { authorIds ->
                if (authorIds.isEmpty()) flowOf(emptyList()) else feedRepository.observeByAuthors(
                    uid = me,
                    authorIds = authorIds,
                    limit = FollowingPageSize,
                )
            }
            .catch { cause ->
                Log.w("Omni", "The following feed could not be read", cause)
                emit(emptyList())
            }
            .onEach { page ->
                // Following is its own query, not a cursor over Discover: there is no Load more.
                firstPageWasFull = false
                olderPages.value = emptyList()
                exhausted.value = false
            }

    /** `true` only while the *first page* was full — a short first page means the feed is exhausted. */
    @Volatile private var firstPageWasFull = false

    /**
     * The open post's comments, live while the sheet is up — carried with the id they belong to.
     *
     * Paired rather than combined separately so the sheet can never draw one post's comments under
     * another post's header: the id and the rows move together or not at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sheet: Flow<Pair<String?, List<CommentRow>>> =
        combine(uid, commentsOpenId) { uid, id -> uid to id }
            .distinctUntilChanged()
            .flatMapLatest { (_, id) ->
                if (id == null) {
                    flowOf(null to emptyList())
                } else {
                    feedRepository.observeComments(id)
                        // Before the `catch`, deliberately: a read that failed must not be counted.
                        // The fallback below emits an empty list so the sheet can draw its empty
                        // state, and letting that reach the counter would tell the pill the post has
                        // no comments when what actually happened is that nobody could read them.
                        .onEach { list ->
                            resolveAuthors(list.map { it.authorId })
                            // The listener is live on the subcollection, so this re-fires the moment
                            // a comment is added — which is what makes the pill's number move on the
                            // same frame the comment appears in the sheet above it.
                            commentCounts.value = commentCounts.value + (id to list.size)
                        }
                        .catch { cause ->
                            Log.w("Omni", "Comments on $id could not be read", cause)
                            emit(emptyList())
                        }
                        .map { list -> id to list.map { it.toRow() } }
                }
            }

    /**
     * The loaded pages, with the optimistic overrides and the known comment counts folded over them
     * by id.
     *
     * Four flows, which the typed [combine] overloads still take — [uiState] is the one that is at
     * its five-flow ceiling, which is why the counts are folded in *here* rather than added there.
     */
    private val loaded: Flow<Pair<FeedSegment, List<Post>>> =
        combine(firstPage, olderPages, optimistic, commentCounts) { (seg, first), older, overrides, counts ->
            seg to (first + older).map { post ->
                val base = overrides[post.id] ?: post
                // Only a count we have actually counted replaces the document's own. A post whose
                // sheet has never been opened keeps whatever the document says rather than being
                // told it has zero comments by a map that has never heard of it.
                val known = counts[post.id]
                if (known == null) base else base.copy(commentCount = known)
            }
        }.onEach { (_, posts) -> resolveAuthors(posts.map { it.authorId }) }

    /**
     * The uids I follow, live — the set the Following segment filters on.
     *
     * A rejected read degrades to the empty set, not to "everyone": a Following tab that quietly
     * aliases Discover is the failure that is hardest to notice. Signed out it is empty too, which is
     * the only truthful answer when there is no "I".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val following: Flow<Set<String>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptySet())
        } else {
            followRepository.observeFollowing(uid).catch { cause ->
                Log.w("Omni", "The follow set for $uid could not be read", cause)
                emit(emptySet())
            }
        }
    }

    /**
     * Who I am, which segment is up, and who I follow — as one value.
     *
     * Tripled rather than passed separately because the typed [combine] overloads stop at five flows
     * and [uiState] already uses all five. The uid belongs *here*, in a flow keyed on the auth state,
     * rather than read imperatively inside the combine lambda: `authRepository.currentUid` there was
     * read at the moment the lambda ran, so a result computed for the account that was signed in
     * when the request started could be folded with the uid of whoever was signed in when it
     * finished. Signing out and back in as someone else is exactly that race.
     */
    private val view: Flow<Triple<String?, FeedSegment, Set<String>>> =
        combine(uid, segment, following) { me, seg, follows -> Triple(me, seg, follows) }

    val uiState: StateFlow<FeedUiState> =
        combine(
            combine(loaded, view, exhausted, sheet, authors) { (pageSeg, pages), (me, seg, follows), done, (openId, rows), people ->
                // Whether the page in hand was read for the tab that is up. On the frame a tab is tapped
                // it is not: `view` has the new segment and the page is still the old tab's. Drawing it
                // would show unrelated users' posts under "Following", which is the one thing §5 forbids
                // — so the list is empty for that frame and fills when the new query answers, which off
                // the local cache is the very next one.
                val forThisTab = pageSeg == seg
                val resolved = if (forThisTab) pages.map { it.withAuthor(people[it.authorId]) } else emptyList()
                Quint(
                    seg = seg,
                    posts = resolved,
                    canLoadMore = forThisTab && !done && resolved.isNotEmpty() && firstPageWasFull &&
                        seg == FeedSegment.Discover,
                    openId = openId,
                    rows = rows,
                    me = me,
                    follows = follows,
                    people = people,
                )
            },
            isRefreshing,
        ) { base, refreshing ->
            FeedUiState(
                segment = base.seg,
                posts = base.posts,
                canLoadMore = base.canLoadMore,
                commentsOpenId = base.openId,
                comments = base.rows.map { row ->
                    row.copy(
                        authorName = base.people[row.authorId]?.name?.ifBlank { null } ?: row.authorName,
                        authorPhotoUrl = base.people[row.authorId]?.photoUrl ?: row.authorPhotoUrl,
                    )
                },
                myUid = base.me,
                following = base.follows,
                isRefreshing = refreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = FeedUiState(),
        )

    /**
     * The post as its author is *now*, falling back to the identity written onto it.
     *
     * Never the signed-in user's profile: the only thing consulted is [Post.authorId]'s own document,
     * so a post by someone else cannot end up wearing my name or my face no matter who is looking.
     */
    private fun Post.withAuthor(author: com.example.omni.data.model.User?): Post {
        if (author == null) return this
        return copy(
            authorName = author.name.ifBlank { authorName },
            authorPhotoUrl = author.photoUrl ?: authorPhotoUrl,
            authorVerified = author.verified,
            authorProfession = author.profession ?: authorProfession,
        )
    }

    /** Fetches any of [uids] not already held or already asked for. One read each, once. */
    private fun resolveAuthors(uids: List<String>) {
        val missing = uids.filter { it.isNotBlank() && requestedAuthors.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { uid ->
                val user = userRepository.getUser(uid)
                if (user != null) authors.value = authors.value + (uid to user)
            }
        }
    }

    fun onSegmentChange(value: FeedSegment) {
        segment.value = value
    }

    /** Follow writes already in flight, so a double tap cannot race itself into a follow/unfollow pair. */
    private val followsInFlight = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Follows or unfollows the *post's author* from the feed row.
     *
     * [authorId] is the target, always — it is [Post.authorId] as the row was drawn from, never
     * `currentUid`, which is the signed-in viewer and is only ever the *subject* of the write. The two
     * are told apart here rather than at the call site: a row hands its own author's uid down and this
     * refuses it when it is mine.
     *
     * **Nothing is faked locally.** The pill's state comes from [following], which is a live listener
     * on `users/{me}/following` — and unlike a like (a subcollection write under `posts`, which cannot
     * re-fire the posts listener) a follow writes into the very collection that flow observes, so the
     * listener reports it and every screen reading the same set moves together. That is also why there
     * is no optimistic override: there is nothing to be optimistic about, the round trip is local.
     *
     * Duplicate follows are impossible by construction — a follow *is* the document
     * `users/{me}/following/{them}`, so writing it twice writes the same document — but a second tap
     * arriving before the first write lands would read the pre-tap set and flip the wrong way. The
     * in-flight guard is for that, not for the database.
     */
    fun toggleFollow(authorId: String, onResult: (String?) -> Unit = {}) {
        val me = authRepository.currentUid
            ?: return onResult("You are signed out. Sign in and try again.")
        if (authorId.isBlank() || authorId == me) return
        if (authorId in followsInFlight.value) return
        val alreadyFollowing = authorId in uiState.value.following
        followsInFlight.value = followsInFlight.value + authorId
        viewModelScope.launch {
            try {
                if (alreadyFollowing) {
                    followRepository.unfollow(me, authorId)
                } else {
                    followRepository.follow(me, authorId)
                }
                onResult(null)
            } catch (cause: Exception) {
                Log.w("Omni", "Following $authorId failed", cause)
                onResult("That didn't go through — ${cause.reason()}")
            } finally {
                followsInFlight.value = followsInFlight.value - authorId
            }
        }
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

    // ---- Searching for people -------------------------------------------------------------------

    /**
     * The search field's own state, deliberately *beside* [uiState] rather than inside it.
     *
     * Two reasons, one mechanical and one about what the screen does. The mechanical one: the typed
     * [combine] overloads stop at five flows and [uiState] uses all five. The real one: a keystroke
     * must not recompose the feed. Folded into [uiState] every letter typed would produce a new
     * `FeedUiState` holding the same twenty posts, and the list would diff them all — which is the
     * "search bar drops characters" the device report described on the map screen.
     */
    private val search = MutableStateFlow(FeedSearchState())

    val searchState: StateFlow<FeedSearchState> = search

    /**
     * The debounce, as a cancellable job rather than a `debounce` operator.
     *
     * Each keystroke cancels the last job before it has finished sleeping, so only the pause at the
     * end of a word ever reaches Firestore — "avoid querying Firestore on every keystroke", stated as
     * code. The same cancellation makes an in-flight query's result unable to land after a newer one.
     */
    private var searchJob: kotlinx.coroutines.Job? = null

    fun onSearchQueryChange(value: String) {
        val term = value.trim()
        searchJob?.cancel()
        // The field is never lagged behind the keyboard: the query is stored on the spot and only the
        // *answer* is debounced.
        search.value = search.value.copy(query = value, error = null)

        if (term.length < MinSearchQuery) {
            search.value = search.value.copy(isSearching = false, results = emptyList())
            return
        }

        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SearchDebounceMillis)
            search.value = search.value.copy(isSearching = true)
            try {
                val found = userRepository.searchByName(term, UserSearchLimit)
                search.value = search.value.copy(results = found, isSearching = false)
            } catch (cause: Exception) {
                Log.w("Omni", "Searching for \"$term\" failed", cause)
                search.value = search.value.copy(
                    results = emptyList(),
                    isSearching = false,
                    error = "Search didn't work — ${cause.reason()}",
                )
            }
        }
    }

    /** Closes the search: the field empties and the results panel goes with it. */
    fun clearSearch() {
        searchJob?.cancel()
        search.value = FeedSearchState()
    }

    fun openComments(postId: String) {
        commentsOpenId.value = postId
    }

    fun closeComments() {
        commentsOpenId.value = null
    }

    /**
     * Posts [body] as a comment on the open post.
     *
     * [onResult] is handed `null` on success and a sentence otherwise, the same shape [createPost]
     * uses — the sheet keeps the draft until the write lands, so a rejected comment is retryable
     * rather than lost. It used to swallow the failure into the log, which is indistinguishable from
     * a comment that simply never appeared.
     *
     * The uid is the signed-in account's, taken here and never from a parameter: the rules require
     * `authorId == request.auth.uid`, and a caller has no way to name someone else.
     */
    fun addComment(body: String, onResult: (String?) -> Unit = {}) {
        val uid = authRepository.currentUid
            ?: return onResult("You are signed out. Sign in and try again.")
        val postId = commentsOpenId.value ?: return onResult("That post is no longer open.")
        if (body.isBlank()) return onResult("Write something first.")
        val name = author.name.ifBlank { "You" }
        viewModelScope.launch {
            try {
                feedRepository.addComment(uid, postId, name, body)
                onResult(null)
            } catch (cause: Exception) {
                Log.w("Omni", "Commenting on $postId failed", cause)
                onResult("Your comment wasn't posted — ${cause.reason()}")
            }
        }
    }

    /**
     * Who I am when I write something here, handed down from the hoisted profile by `MainActivity` —
     * the same pattern as the goals on Home, and the reason this ViewModel opens no second `users`
     * listener (BACKEND_PLAN §4 rule 3).
     *
     * Set on entry and on every profile change, so a rename or a new photo applies to the next
     * comment and the next repost without reopening the feed.
     */
    fun onAuthorIdentity(name: String, photoUrl: String?, verified: Boolean) {
        author = PostAuthor(name = name, photoUrl = photoUrl, verified = verified, profession = null)
    }

    private var author = PostAuthor(name = "", photoUrl = null, verified = false, profession = null)

    /**
     * Reposts [postId] — a new post of my own that quotes the original, plus the marker at
     * `posts/{postId}/reposts/{uid}` that makes it countable and unrepeatable.
     *
     * One per user per post. The repository commits the quoting post and the marker in one batch and
     * refuses a second attempt against the marker, so the pill is a *state* rather than a button that
     * wrote a fresh post on every press — which is what it used to be, with a hardcoded "0" beside
     * it that never moved however many times it was tapped.
     *
     * The flip is optimistic so the tap is acknowledged on its own frame; a failed write rolls it
     * back. The repost is attributed with my whole identity — name, photo and badge — not just my
     * name: it used to pass `photoUrl = null`, so every repost was permanently avatar-less in a feed
     * where my other posts had my face on them.
     */
    fun repost(postId: String) {
        val uid = authRepository.currentUid ?: return
        val current = loadedState().firstOrNull { it.id == postId } ?: return
        if (current.repostedByMe) return
        optimistic.value = optimistic.value + (
            postId to current.copy(
                repostedByMe = true,
                repostCount = current.repostCount + 1,
            )
            )
        viewModelScope.launch {
            try {
                feedRepository.repost(
                    uid = uid,
                    postId = postId,
                    author = author.copy(name = author.name.ifBlank { "You" }),
                )
            } catch (cause: Exception) {
                Log.w("Omni", "Reposting $postId failed", cause)
                optimistic.value = optimistic.value - postId
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
     * Force refresh — shows the pull-to-refresh indicator for 1.2s then dismisses.
     * The feed is already live via Firestore listeners, so this only needs to drive the indicator.
     */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                kotlinx.coroutines.delay(1200)
            } finally {
                isRefreshing.value = false
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
        authorId = authorId,
        authorName = authorName.ifBlank { "Someone" },
        body = body,
        timeText = relativeTimeOf(createdAt),
    )

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L

        /** BACKEND_PLAN's own page size for the feed's `limit(…)`. */
        const val PageSize = 20

        /**
         * The Following tab's page size — bigger than Discover's because Following's filter is the
         * author set, not the whole app, and a follower of three quiet people should see *all three*
         * before any of them scroll past the fold.
         */
        const val FollowingPageSize = 50

        /**
         * How long the field must be still before the query goes out.
         *
         * Long enough that typing a name is one search rather than eight, short enough that the pause
         * is not felt as lag. 300ms is the interval the platform's own search fields settle on.
         */
        const val SearchDebounceMillis = 300L
    }
}
