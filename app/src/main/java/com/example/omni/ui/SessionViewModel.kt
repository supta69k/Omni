package com.example.omni.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MediaRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.NotificationRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.data.repo.unreadTotal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Who is signed in, as one flow the whole app reads.
 *
 * Every screen that draws the shared header needs the same name, photo and unread counts, so this is
 * hoisted once in `MainActivity` (BACKEND_PLAN §4 rule 3) and its value handed down as plain data.
 * One `users/{uid}` snapshot listener serves the entire app rather than one per screen.
 *
 * It is deliberately keyed on the **session**, not on the uid alone: signing out has to drop the
 * listener, or Firestore keeps a live subscription to a document the rules no longer let us read.
 */
class SessionViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val messageRepository: MessageRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key every read below restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * The signed-in uid as state the router can hold.
     *
     * `AuthRepository.currentUid` is a plain getter, so reading it inside a composable samples
     * whatever Firebase happens to hold at that instant and produces no recomposition when that
     * changes. The router used it to decide "is this story mine" and "is this post mine", which
     * made both answers a race: sign out on one frame and the next composition could still be
     * drawing the old session's ownership until something unrelated invalidated it. Collected as
     * state, ownership changes the moment the session does.
     *
     * Passed straight through rather than re-`stateIn`'d: the repository's own flow is already hot
     * and always has a value, so a second stage would only add a subscription and a frame of delay
     * behind an `initialValue` that could disagree with it.
     */
    val signedInUid: StateFlow<String?> = uid

    /**
     * `null` before the profile arrives and while signed out — which is why the screens keep their
     * Figma defaults for that case rather than rendering an empty name for a frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val user: StateFlow<User?> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                // The repository closes this flow on a rules rejection or a dead project, which would
                // otherwise crash the collector. Falling back to `null` keeps the app usable and the
                // log line is what names the cause — a header stuck on "Sayed Mahir" in a real build
                // means this fired.
                userRepository.observeUser(uid).catch { cause ->
                    Log.w("Omni", "users/$uid could not be read; header falls back to defaults", cause)
                    emit(null)
                }
            }
        }
        .onEach { profile -> reconcileEmail(profile) }
        .stateIn(
            scope = viewModelScope,
            // Outlives a rotation, so turning the phone does not re-run the query, but a backgrounded
            // app stops paying for a listener.
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = null,
        )

    /**
     * The bell badge, counted from the inbox itself rather than read off `users/{uid}.unread`.
     *
     * BACKEND_PLAN §7 denormalises this count onto the profile, but the rules forbid a client writing
     * `unread` and the Cloud Functions that would maintain it are Phase 12 — so bound to the profile
     * field the badge could only ever be 0. Counting unread items is the ground truth; the counter can
     * become an optimisation over it later without any screen changing.
     * See [NotificationRepository.observeUnreadCount] for the cap on how high it counts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotifications: StateFlow<Int> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(0)
            } else {
                notificationRepository.observeUnreadCount(uid).catch { cause ->
                    Log.w("Omni", "The unread count for $uid could not be read", cause)
                    emit(0)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = 0,
        )

    /**
     * The message badge — summed across threads, for the bell's exact reason.
     *
     * `users/{uid}.unread.messages` is the denormalised field BACKEND_PLAN §7 binds this to, and it is
     * maintained by the same Phase 12 function that would maintain the bell's; until it exists the field
     * is 0. So the count comes from the thread list itself, where each thread already carries this user's
     * own unread half ([com.example.omni.data.model.Conversation.unread]). One listener serves both this
     * badge and the Messages tab.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadMessages: StateFlow<Int> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(0)
            } else {
                messageRepository.observeConversations(uid).unreadTotal().catch { cause ->
                    Log.w("Omni", "The unread message total for $uid could not be read", cause)
                    emit(0)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = 0,
        )

    /**
     * Copies a confirmed email change from Firebase Auth onto `users/{uid}.email`.
     *
     * Auth owns the address you sign in with; the profile document holds a copy, and the settings row is
     * what reads it. The security page can only *start* a change — Firebase sends a link and the address
     * moves when that link is opened, in a browser, with nothing of this app running — so there is no
     * moment at the write site where both can be set together. This is where they meet again: the first
     * profile snapshot after the change finds the two disagreeing and writes the document across.
     *
     * Only on disagreement, so the ordinary case costs nothing. It can lag by up to an hour in the worst
     * case, because `currentUser.email` is read from the cached token and the change is picked up on the
     * next refresh — Auth is right either way, and the stale copy shows in one place.
     */
    private suspend fun reconcileEmail(profile: User?) {
        val signedIn = authRepository.currentEmail?.takeIf { it.isNotBlank() } ?: return
        if (profile == null || profile.email.equals(signedIn, ignoreCase = true)) return
        try {
            userRepository.updateProfile(profile.uid, mapOf("email" to signedIn))
        } catch (cause: Exception) {
            Log.w("Omni", "users/${profile.uid}.email could not be re-synced to the account", cause)
        }
    }

    private val _photoUpload = MutableStateFlow(PhotoUploadState())

    /** The settings screen's avatar state while a new profile photo is uploading. */
    val photoUpload: StateFlow<PhotoUploadState> = _photoUpload.asStateFlow()

    /**
     * Uploads [image] and points `users/{uid}.photoUrl` at it.
     *
     * The app had fourteen places that *read* a profile photo and none that ever wrote one, so every
     * avatar in it — the header, the feed composer, a post's author row — fell back to the mock
     * `home_avatar` forever. This is the write.
     *
     * Nothing is echoed into local state on success: the `users/{uid}` listener above re-emits the
     * document, [user] changes, and every screen drawing the header follows from the one source. A
     * second copy here is how the header and the settings row start disagreeing.
     *
     * Posts already written keep the `authorPhotoUrl` they were denormalised with — a new photo does
     * not rewrite history, which is why the feed prefers the live photo for the signed-in user's own
     * posts (see `FeedScreen`'s `myPhotoUrl`).
     */
    fun setPhoto(image: Uri) {
        val uid = authRepository.currentUid ?: return
        // A second tap while the first upload is in flight would race two writes to the same field and
        // leave whichever finished last, which from the outside looks like the picker ignoring a choice.
        if (_photoUpload.value.inFlight) return

        _photoUpload.value = PhotoUploadState(inFlight = true)
        viewModelScope.launch {
            try {
                val url = mediaRepository.uploadAvatar(uid, image)
                userRepository.updateProfile(uid, mapOf("photoUrl" to url))
                _photoUpload.value = PhotoUploadState()
            } catch (cause: Exception) {
                Log.w("Omni", "The profile photo for $uid could not be saved", cause)
                // The repository throws readable clauses ("the upload was rejected: …"), so the reason
                // reaches the user instead of a generic failure. See `MediaRepository`.
                _photoUpload.value = PhotoUploadState(
                    error = cause.message?.takeIf { it.isNotBlank() }
                        ?.let { "Your photo could not be saved — $it" }
                        ?: "Your photo could not be saved — check your connection and try again.",
                )
            }
        }
    }

    /** Dismisses the failure line, so the next attempt starts from a clean row. */
    fun clearPhotoError() {
        if (_photoUpload.value.error != null) _photoUpload.value = PhotoUploadState()
    }

    private companion object {
        const val ListenerGraceMillis = 5_000L
    }
}

/**
 * What the settings screen shows while a picked photo is on its way to `users/{uid}.photoUrl`.
 *
 * Two fields rather than a sealed hierarchy: an upload is either running or it is not, and the last
 * one either failed or it did not. There is no third state — success is simply the avatar changing,
 * because the profile listener re-emits and every screen drawing the header follows.
 */
data class PhotoUploadState(
    val inFlight: Boolean = false,
    /** `null` unless the last attempt failed; the sentence is already user-facing. */
    val error: String? = null,
)
