package com.example.omni.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Post
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the profile page renders — and, as of the wrong-profile repair, *whose* page it is.
 *
 * @property uid the profile this state describes. Carried in the state rather than implied by it,
 *   because it is the only way the screen — or a reader of a bug report — can tell whether what is
 *   on screen belongs to the person who was tapped. Every other field below is only meaningful
 *   alongside this one.
 * @property user the profile's owner — `null` while the document is in flight ([isLoading]) or the
 *   account does not exist ([notFound]), which the screen names rather than filling with a ghost.
 * @property posts their posts, newest first.
 * @property postsCount how many they have posted — the server's own `count()` over
 *   `posts where authorId == uid`, not [posts]`.size`, which stops at the page limit of 20 and would
 *   under-report anybody prolific. Falls back to the row count when the aggregation cannot be run
 *   (offline there is none), so the label is never a confident wrong number.
 * @property followers how many follow *them* — counted server-side over `users/{uid}/followers`, the
 *   same way the feed counts likes. Not a stored counter: no client may write one (rules DEVIATION 2
 *   is the same argument), so a field would read 0 forever until Phase 12's functions exist.
 * @property iFollowThem my follow state for the button.
 * @property isMe whether this profile is mine — hides follow/message and shows the settings link.
 *   Decided by comparing the two *uids*, never by comparing the loaded document to me: see [uiState].
 * @property isLoading the page's reads have not all answered yet. What separates "they have no
 *   posts" from "we have not been told yet", which the screen used to render identically.
 * @property notFound the document was read and there is nothing there — a deleted account.
 * @property followError why the last follow tap did not take, or `null`. A refused write used to be
 *   logged and nothing else, which is indistinguishable from a button that does nothing. One line
 *   under the button is the difference between "this app has no follow" and "the server refused it".
 */
data class ProfileUiState(
    val uid: String? = null,
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val postsCount: Int = 0,
    val followers: Int = 0,
    val iFollowThem: Boolean = false,
    val isMe: Boolean = false,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val followError: String? = null,
)

/**
 * One user's public page — the Facebook shape the brief asked for: their posts, a follow button,
 * and a message button that opens the existing chat.
 *
 * The uid arrives as a constructor-time seed ([setProfileUid]) rather than a parameter, because
 * `MainActivity` creates the ViewModel once per navigation and the destination is set on entry —
 * the same one-ViewModel-owns-its-open-page pattern the guides and messages screens use.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val followRepository: FollowRepository,
) : ViewModel() {

    private val profileUid = MutableStateFlow<String?>(null)

    /** Told by `MainActivity` on entering the screen; re-telling re-keys every read below. */
    fun setProfileUid(uid: String?) {
        if (profileUid.value == uid) return
        // The refusal belonged to the page we are leaving. Carrying it over would put the last
        // person's error under the next person's follow button.
        followError.value = null
        profileUid.value = uid
    }

    private val me: StateFlow<String?> = authRepository.sessionUid

    /** Set when a follow write is refused, cleared on the next tap. See [toggleFollow]. */
    private val followError = MutableStateFlow<String?>(null)

    /**
     * Bumped once a follow write has been acknowledged by the server — which is what re-runs the
     * count below. See [followerCountOf] for why the follow *listener* cannot be the trigger.
     */
    private val followerRefresh = MutableStateFlow(0)

    /**
     * The page, rebuilt from scratch whenever *who is being viewed* or *who is viewing* changes.
     *
     * This shape is the wrong-profile fix, and the reason it is a [flatMapLatest] over the pair
     * rather than a flat five-way `combine` is worth spelling out. A `combine` retains each source's
     * last value: re-keying the four reads left the previously combined value standing until every
     * one of them had answered again, so tapping from my profile to someone else's rendered *my*
     * name, *my* photo and *my* posts under their uid for as long as the reads took. Wrapping the
     * combine in a `flatMapLatest` makes that impossible structurally — a new key cancels the old
     * inner combine, and the first thing the new one emits is its own seeded loading state.
     *
     * It closes the cross-account leak for the same reason: sign-out changes [me], which cancels
     * every read keyed to the old session, so a reply that was already in flight for account A
     * cannot be folded into a state that account B is looking at.
     *
     * [ProfileUiState.isMe] is decided from the two keys, not from `user?.uid == me`. The old test
     * was `false` for the whole load window, because `user` is null there — so my own profile spent
     * every open briefly offering to follow and message myself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> =
        combine(profileUid, me) { target, viewer -> target to viewer }
            .distinctUntilChanged()
            .flatMapLatest { (target, viewer) -> page(target, viewer) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
                initialValue = ProfileUiState(),
            )

    /** One profile's reads, combined. Everything here is keyed to [target] and nothing outlives it. */
    private fun page(target: String?, viewer: String?): Flow<ProfileUiState> {
        if (target == null) return flowOf(ProfileUiState(isLoading = false))
        val isMe = viewer != null && viewer == target
        return combine(
            profileOf(target),
            postsOf(target),
            followStateOf(target, viewer),
            followerCountOf(target),
            combine(postCountOf(target), followError) { count, error -> count to error },
        ) { fetched, rows, following, followers, (count, error) ->
            ProfileUiState(
                uid = target,
                user = fetched?.value,
                posts = rows.orEmpty(),
                // -1 is the aggregation's "unknown"; the rows we hold are then the honest answer.
                postsCount = if (count >= 0) count else rows.orEmpty().size,
                followers = followers,
                iFollowThem = following,
                isMe = isMe,
                isLoading = fetched == null || rows == null,
                notFound = fetched != null && fetched.value == null,
                followError = error,
            )
        }
    }

    /**
     * A read that has answered, wrapping an answer that may itself be nothing.
     *
     * `null` and `Fetched(null)` are different facts — "we have not been told yet" and "there is no
     * such account" — and the profile renders them differently. A bare `User?` cannot tell them
     * apart, which is how a page mid-load came to look exactly like a deleted one.
     */
    private data class Fetched<T>(val value: T)

    private fun profileOf(target: String): Flow<Fetched<User?>?> =
        userRepository.observeUser(target)
            // The element type is widened here rather than inferred: `onStart`'s null is the
            // "no answer yet" element, and without the explicit parameters it would be checked
            // against the upstream's non-null type instead.
            .map<User?, Fetched<User?>?> { Fetched(it) }
            .catch { cause ->
                Log.w("Omni", "The profile $target could not be read", cause)
                emit(Fetched(null))
            }
            .onStart { emit(null) }

    private fun postsOf(target: String): Flow<List<Post>?> =
        feedRepository.observeByAuthor(target, ProfilePostLimit)
            .map<List<Post>, List<Post>?> { it }
            .catch { cause ->
                Log.w("Omni", "The posts of $target could not be read", cause)
                emit(emptyList())
            }
            .onStart { emit(null) }

    private fun followStateOf(target: String, viewer: String?): Flow<Boolean> =
        if (viewer == null || viewer == target) {
            flowOf(false)
        } else {
            followRepository.observeFollows(viewer, target)
                .catch { cause ->
                    Log.w("Omni", "The follow state could not be read", cause)
                    emit(false)
                }
                .onStart { emit(false) }
        }

    /**
     * How many follow this profile, re-counted after each of my own follow writes lands.
     *
     * A one-shot read, because `count()` is an aggregation and aggregations cannot be observed. The
     * obvious trigger — the follow listener — is the wrong one: Firestore answers a local write from
     * the cache immediately, so it fires *before* the batch reaches the server and the count would
     * come back one behind, every time, and stay there until the page was reopened. The write
     * completing is the only moment the server's own answer has changed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun followerCountOf(target: String): Flow<Int> =
        followerRefresh
            .flatMapLatest { flow { emit(followRepository.followerCount(target)) } }
            .onStart { emit(0) }

    /** The post count, once. See [ProfileUiState.postsCount] for why it is not `posts.size`. */
    private fun postCountOf(target: String): Flow<Int> =
        flow { emit(feedRepository.countByAuthor(target)) }
            .catch { cause ->
                Log.w("Omni", "The post count of $target could not be read", cause)
                emit(-1)
            }
            .onStart { emit(-1) }

    /**
     * Follows or unfollows the profile's owner — the button's one action, decided by the state the
     * listener last emitted.
     *
     * No optimistic override, unlike the like: the write lands in `users/{me}/following`, which is
     * the very collection [FollowRepository.observeFollowing] is listening to, so Firestore's latency
     * compensation re-emits it from the local cache within a frame and the button moves on its own.
     * (The like needs one because its write lands in a *subcollection* the feed's listener does not
     * cover, so nothing re-fires.) A rejected write is rolled back the same way, which is exactly why
     * it needs [ProfileUiState.followError]: without a message the button would silently return to
     * where it was and read as broken.
     *
     * The follower count is not optimistic either: it is re-read once the write is acknowledged, so
     * it moves a round-trip after the button does rather than being guessed and then corrected.
     *
     * The target is taken from the state rather than from [profileUid], and re-checked after the
     * write: the state is what the button was drawn from, and if the page changed under the tap the
     * refusal must not be reported against whoever is on screen now.
     */
    fun toggleFollow() {
        val snapshot = uiState.value
        val target = snapshot.uid ?: return
        val me = authRepository.currentUid ?: return
        if (me == target) return
        followError.value = null
        viewModelScope.launch {
            try {
                if (snapshot.iFollowThem) {
                    followRepository.unfollow(me, target)
                } else {
                    followRepository.follow(me, target)
                }
                followerRefresh.value++
            } catch (cause: Exception) {
                Log.w("Omni", "Toggling the follow on $target failed", cause)
                if (profileUid.value == target) {
                    followError.value = "That didn't go through — ${cause.reason()}"
                }
            }
        }
    }

    /**
     * The failure's own words, or a fallback when it has none — the same rule the composer uses.
     *
     * Firestore's own message names the cause ("PERMISSION_DENIED: Missing or insufficient
     * permissions"), and a person reading that back in a bug report is worth more than a fixed
     * "check your connection" that is wrong whenever the cause is a rule rather than a router.
     */
    private fun Exception.reason(): String =
        message?.takeIf { it.isNotBlank() } ?: "check your connection and try again"

    private companion object {
        const val ListenerGraceMillis = 5_000L

        /** A profile is a page, not an archive — the newest 20 tell the visitor who this is. */
        const val ProfilePostLimit = 20
    }
}
