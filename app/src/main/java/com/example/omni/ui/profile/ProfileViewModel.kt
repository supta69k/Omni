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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the profile page renders.
 *
 * @property user the profile's owner — `null` while the document is in flight or the account was
 *   deleted, which the screen names rather than filling with a ghost.
 * @property posts their posts, newest first.
 * @property postsCount the label under the avatar; the list's own length, not a server counter.
 * @property followers how many follow *them*. Zero until a Cloud Function maintains it — the honest
 *   reading, exactly like the feed's like counts before Phase 12.
 * @property iFollowThem my follow state for the button.
 * @property isMe whether this profile is mine — hides follow/message and shows the settings link.
 */
data class ProfileUiState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val postsCount: Int = 0,
    val followers: Int = 0,
    val iFollowThem: Boolean = false,
    val isMe: Boolean = false,
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
        profileUid.value = uid
    }

    private val me: Flow<String?> = authRepository.authState
        .map { if (it == com.example.omni.data.model.AuthState.AUTHENTICATED) authRepository.currentUid else null }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val profile: Flow<User?> = profileUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(null)
        } else {
            userRepository.observeUser(uid).catch { cause ->
                Log.w("Omni", "The profile could not be read", cause)
                emit(null)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val posts: Flow<List<Post>> = profileUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            feedRepository.observeByAuthor(uid, ProfilePostLimit).catch { cause ->
                Log.w("Omni", "The profile's posts could not be read", cause)
                emit(emptyList())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val myFollow: Flow<Boolean> =
        combine(me, profileUid) { me, target -> me to target }
            .distinctUntilChanged()
            .flatMapLatest { (me, target) ->
                if (me == null || target == null || me == target) {
                    flowOf(false)
                } else {
                    followRepository.observeFollows(me, target).catch { cause ->
                        Log.w("Omni", "The follow state could not be read", cause)
                        emit(false)
                    }
                }
            }

    val uiState: StateFlow<ProfileUiState> =
        combine(profile, posts, myFollow, me) { user, posts, following, me ->
            ProfileUiState(
                user = user,
                posts = posts,
                postsCount = posts.size,
                // Read off the profile document when a function maintains it; absent reads 0.
                followers = 0,
                iFollowThem = following,
                isMe = user?.uid == me,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = ProfileUiState(),
        )

    /**
     * Follows or unfollows the profile's owner — the button's one action, decided by the state the
     * listener last emitted. Optimistic for the same reason the like is: the button must move on
     * the tap's frame, and the listener corrects it within a round-trip.
     */
    fun toggleFollow() {
        val me = authRepository.currentUid ?: return
        val target = profileUid.value ?: return
        if (me == target) return
        viewModelScope.launch {
            try {
                if (uiState.value.iFollowThem) {
                    followRepository.unfollow(me, target)
                } else {
                    followRepository.follow(me, target)
                }
            } catch (cause: Exception) {
                Log.w("Omni", "Toggling the follow failed", cause)
            }
        }
    }

    private companion object {
        const val ListenerGraceMillis = 5_000L

        /** A profile is a page, not an archive — the newest 20 tell the visitor who this is. */
        const val ProfilePostLimit = 20
    }
}
