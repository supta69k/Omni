package com.example.omni.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {

    /**
     * `null` before the profile arrives and while signed out — which is why the screens keep their
     * Figma defaults for that case rather than rendering an empty name for a frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val user: StateFlow<User?> = authRepository.authState
        .map { state -> if (state == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()
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
        .stateIn(
            scope = viewModelScope,
            // Outlives a rotation, so turning the phone does not re-run the query, but a backgrounded
            // app stops paying for a listener.
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = null,
        )

    private companion object {
        const val ListenerGraceMillis = 5_000L
    }
}
