package com.example.omni.ui.omniplus

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.omni.data.model.Message
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MessageRepository
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
            myUid = authRepository.currentUid ?: return@launch
            val conversationId = "consultation_${myUid}_${doctorUid ?: "unknown"}"
            _uiState.value = _uiState.value.copy(
                selfUid = myUid ?: "",
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
        fun factory(
            messageRepository: MessageRepository,
            authRepository: AuthRepository,
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
                return DoctorConsultationViewModel(messageRepository, authRepository, handle) as T
            }
        }
    }
}
