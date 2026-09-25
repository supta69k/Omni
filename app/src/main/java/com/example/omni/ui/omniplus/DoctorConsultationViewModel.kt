package com.example.omni.ui.omniplus

import android.util.Log
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.omni.data.model.Message
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

    private var myUid: String? = null
    private val _uiState = MutableStateFlow(DoctorConsultationUiState(
        otherName = doctorName ?: "Doctor",
    ))
    val uiState: StateFlow<DoctorConsultationUiState> = _uiState.asStateFlow()

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
            try {
                messageRepository.openConversation(
                    selfUid = uid,
                    selfName = self?.name.orEmpty().ifBlank { "Omni member" },
                    selfPhotoUrl = self?.photoUrl,
                    otherUid = doctorUidValue ?: "unknown",
                    otherName = doctorDisplayName,
                    otherPhotoUrl = null,
                )
            } catch (cause: Exception) {
                // Offline still succeeds — Firestore queues the write. A rules rejection here would
                // mean the thread cannot exist at all, so the page says so instead of rendering a
                // message box whose writes have nowhere to go.
                Log.w(TAG, "Opening the consultation thread failed", cause)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val conversationId = "consultation_${uid}_${doctorUidValue ?: "unknown"}"
            _uiState.value = _uiState.value.copy(
                selfUid = uid,
                conversationId = conversationId,
                isLoading = false,
            )

            messageRepository.observeMessages(conversationId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun send(text: String) {
        val conversationId = _uiState.value.conversationId ?: return
        val uid = myUid ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                messageRepository.send(
                    conversationId = conversationId,
                    senderId = uid,
                    recipientId = doctorUid ?: "unknown",
                    text = text.trim(),
                    imageUrl = null,
                )
                _uiState.value = _uiState.value.copy(inputText = "", isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false)
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
        ): AbstractSavedStateViewModelFactory = object : AbstractSavedStateViewModelFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle,
            ): T {
                handle["doctorUid"] = defaultDoctorUid
                handle["doctorName"] = defaultDoctorName
                return DoctorConsultationViewModel(messageRepository, authRepository, userRepository, handle) as T
            }
        }
    }
}
