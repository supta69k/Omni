package com.example.omni.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AppNotification
import com.example.omni.data.model.AuthState
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the notifications page renders.
 *
 * @property loading true only until the listener's first emission. An empty list afterwards means the
 *   inbox is genuinely empty, which is a different sentence on screen.
 * @property unread counted here rather than taken from the header's flow, so the "Mark all read" link
 *   and the list it acts on can never disagree by a frame.
 */
data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
) {
    val unread: Int get() = items.count { !it.read }
    val hasUnread: Boolean get() = unread > 0
}

/**
 * The bell's inbox — `notifications/{uid}/items` (BACKEND_PLAN §11 Phase 10).
 *
 * Read-mostly: the only writes it makes are the two that mark items seen. Items themselves arrive
 * either from a Cloud Function (Phase 12) or from the app writing into its own inbox — today that is
 * the SOS screen recording that an alert was saved.
 */
class NotificationsViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key the read restarts on. */
    private val uid: Flow<String?> = authRepository.authState
        .map { if (it == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NotificationsUiState> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                notificationRepository.observe(uid).catch { cause ->
                    Log.w("Omni", "notifications/$uid/items could not be read", cause)
                    emit(emptyList())
                }
            }
        }
        .map { items -> NotificationsUiState(items = items, loading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = NotificationsUiState(),
        )

    /**
     * Marks one item seen.
     *
     * Fired by opening the row, not by a separate control: an unread dot the user has to dismiss by
     * hand is bookkeeping the design never asked for. Already-read items short-circuit so scrolling
     * past an old notification does not write to Firestore.
     */
    fun markRead(notificationId: String) {
        if (uiState.value.items.none { it.id == notificationId && !it.read }) return
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                notificationRepository.markRead(uid, notificationId)
            } catch (cause: Exception) {
                Log.w("Omni", "Marking notification $notificationId read failed", cause)
            }
        }
    }

    /** The header link. Guarded so the batch is never built for an inbox with nothing unread in it. */
    fun markAllRead() {
        if (!uiState.value.hasUnread) return
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                notificationRepository.markAllRead(uid)
            } catch (cause: Exception) {
                Log.w("Omni", "Marking all notifications read failed", cause)
            }
        }
    }

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L
    }
}
