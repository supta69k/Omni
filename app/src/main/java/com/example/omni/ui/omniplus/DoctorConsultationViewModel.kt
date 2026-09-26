package com.example.omni.ui.omniplus

import android.util.Log
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.omni.data.model.Message
import com.example.omni.data.model.conversationIdOf
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoctorConsultationUiState(
    val selfUid: String = "",
    val conversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val otherName: String = "",
    val otherPhotoUrl: String? = null,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val inputText: String = "",
)

class DoctorConsultationViewModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val doctorUid: String? = savedStateHandle.get<String>("doctorUid")
    private val doctorName: String? = savedStateHandle.get<String>("doctorName")
    private val doctorPhotoUrl: String? = savedStateHandle.get<String>("doctorPhotoUrl")

    private var myUid: String? = null
    private val _uiState = MutableStateFlow(DoctorConsultationUiState(
        otherName = doctorName ?: "Doctor",
        otherPhotoUrl = doctorPhotoUrl,
    ))
    val uiState: StateFlow<DoctorConsultationUiState> = _uiState.asStateFlow()

    /** Server truth for this thread; the displayed list is this plus any [pending] optimistic echoes. */
    private var serverMessages: List<Message> = emptyList()

    /**
     * Optimistic outgoing messages — the consultation chat is written by the backend over HTTP just
     * like a normal thread, so without this the sender's own message would not appear until the
     * round-trip returned (the sender-missing-message bug). Reconciled by (senderId, text) against the
     * server copy in [pruneDelivered], so a message is one bubble and never a duplicate. Mirrors
     * [com.example.omni.ui.messages.MessagesViewModel].
     */
    private val pending = mutableListOf<Message>()
    private var pendingSeq = 0L

    init {
        viewModelScope.launch {
            val uid = authRepository.currentUid ?: return@launch
            myUid = uid
            val doctorUidValue = doctorUid
            val doctorDisplayName = doctorName ?: "Doctor"

            // The thread's identity has to exist before anything reads it. Every read rule on a
            // conversation keys on `participants`, and the backend's message handler merges only
            // summary fields — so a consultation opened without this write is a thread neither side
            // can read, and the doctor's Patients list (participants array-contains) never sees it.
            // [MessageRepository.openConversation] is idempotent: the id is derived from the two
            // uids and the write is a merge of the identity fields only.
            val self = runCatching { userRepository.getUser(uid) }.getOrNull()
            val otherUid = doctorUidValue ?: "unknown"
            // The canonical id, identical to what the normal messaging flow computes for the same two
            // people (sorted uids). This is the whole fix for the split-conversation bug: the old
            // `consultation_{patient}_{doctor}` id gave the directory chat a different identity from the
            // New Message chat, so the same pair had two histories. Now every entry point resolves here.
            val conversationId = conversationIdOf(uid, otherUid)
            try {
                messageRepository.openConversation(
                    selfUid = uid,
                    selfName = self?.name.orEmpty().ifBlank { "Omni member" },
                    selfPhotoUrl = self?.photoUrl,
                    otherUid = otherUid,
                    otherName = doctorDisplayName,
                    otherPhotoUrl = doctorPhotoUrl,
                    // Flags the one canonical thread as a consultation for the doctor's Patients tab —
                    // it does not fork a separate thread.
                    consultation = true,
                )
            } catch (cause: Exception) {
                // Offline still succeeds — Firestore queues the write. A rules rejection here would
                // mean the thread cannot exist at all, so the page says so instead of rendering a
                // message box whose writes have nowhere to go.
                Log.w(TAG, "Opening the consultation thread failed", cause)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                selfUid = uid,
                conversationId = conversationId,
                isLoading = false,
            )

            messageRepository.observeMessages(conversationId).collect { messages ->
                serverMessages = messages
                renderMessages(isLoading = false)
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun send(text: String) {
        val conversationId = _uiState.value.conversationId ?: return
        val uid = myUid ?: return
        val body = text.trim()
        if (body.isEmpty()) return

        // Optimistic echo: the bubble appears now, stamped with the authenticated uid so it sits on
        // the sender's side and matches its server twin for reconciliation.
        val echo = Message(
            id = "pending-${pendingSeq++}",
            senderId = uid,
            text = body,
            createdAt = System.currentTimeMillis(),
        )
        pending += echo
        _uiState.value = _uiState.value.copy(inputText = "", isSending = true)
        renderMessages()

        viewModelScope.launch {
            try {
                messageRepository.send(
                    conversationId = conversationId,
                    senderId = uid,
                    recipientId = doctorUid ?: "unknown",
                    text = body,
                    imageUrl = null,
                )
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                // The write did not land; drop the echo so the thread does not imply it did.
                pending.removeAll { it.id == echo.id }
                _uiState.value = _uiState.value.copy(isSending = false)
                renderMessages()
            }
        }
    }

    /** Recomputes the visible list: server messages, then any optimistic echo not yet delivered. */
    private fun renderMessages(isLoading: Boolean? = null) {
        pruneDelivered()
        val visible = if (pending.isEmpty()) serverMessages else serverMessages + pending
        _uiState.value = _uiState.value.copy(
            messages = visible,
            isLoading = isLoading ?: _uiState.value.isLoading,
        )
    }

    /** Drops any optimistic echo whose server twin has arrived, matched by (senderId, text) and counted. */
    private fun pruneDelivered() {
        if (pending.isEmpty()) return
        val counts = HashMap<Pair<String, String>, Int>()
        serverMessages.forEach { m -> counts[m.senderId to m.text] = (counts[m.senderId to m.text] ?: 0) + 1 }
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val echo = iterator.next()
            val key = echo.senderId to echo.text
            val n = counts[key] ?: 0
            if (n > 0) {
                counts[key] = n - 1
                iterator.remove()
            }
        }
    }

    companion object {
        private const val TAG = "OmniConsult"

        fun factory(
            messageRepository: MessageRepository,
            authRepository: AuthRepository,
            userRepository: UserRepository,
            owner: SavedStateRegistryOwner,
            defaultDoctorUid: String?,
            defaultDoctorName: String?,
            defaultDoctorPhotoUrl: String? = null,
        ): AbstractSavedStateViewModelFactory = object : AbstractSavedStateViewModelFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle,
            ): T {
                handle["doctorUid"] = defaultDoctorUid
                handle["doctorName"] = defaultDoctorName
                handle["doctorPhotoUrl"] = defaultDoctorPhotoUrl
                return DoctorConsultationViewModel(messageRepository, authRepository, userRepository, handle) as T
            }
        }
    }
}
