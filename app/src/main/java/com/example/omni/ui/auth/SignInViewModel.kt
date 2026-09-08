package com.example.omni.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.repo.AuthException
import com.example.omni.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isResetEmailSent: Boolean = false,
)

class SignInViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter both email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SignInUiState(isLoading = true)
            try {
                authRepository.signIn(email.trim(), password)
                _uiState.value = SignInUiState(isLoading = false)
                onSuccess()
            } catch (e: AuthException) {
                _uiState.value = SignInUiState(isLoading = false, errorMessage = e.message)
            } catch (e: Exception) {
                _uiState.value = SignInUiState(isLoading = false, errorMessage = e.localizedMessage ?: "Sign in failed.")
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter your email to reset password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                authRepository.sendPasswordReset(email.trim())
                _uiState.value = _uiState.value.copy(isLoading = false, isResetEmailSent = true)
            } catch (e: AuthException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to send reset email.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
