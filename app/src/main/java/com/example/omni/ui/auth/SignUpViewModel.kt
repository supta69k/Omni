package com.example.omni.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.repo.AuthException
import com.example.omni.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignUpUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class SignUpViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun signUp(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        onResult: (email: String, initialCodeSent: Boolean) -> Unit = { _, _ -> },
    ) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = SignUpUiState(errorMessage = "Please fill in all fields.")
            return
        }

        if (password != confirmPassword) {
            _uiState.value = SignUpUiState(errorMessage = "Passwords do not match.")
            return
        }

        if (password.length < 6) {
            _uiState.value = SignUpUiState(errorMessage = "Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SignUpUiState(isLoading = true)
            try {
                authRepository.signUp(name.trim(), email.trim(), password)
                // The account now exists, so the user has to reach the verification screen no
                // matter what — refusing to navigate when the first send failed stranded them
                // between "sign up" (EMAIL_ALREADY_IN_USE) and a verification screen they were
                // never shown. On failure the button there still says "Send verification code".
                val sent = authRepository.sendOtpCode()
                _uiState.value = SignUpUiState(isLoading = false)
                // Navigate to email verification screen with the email
                onResult(email.trim(), sent)
            } catch (e: AuthException) {
                _uiState.value = SignUpUiState(isLoading = false, errorMessage = e.message)
            } catch (e: Exception) {
                _uiState.value = SignUpUiState(isLoading = false, errorMessage = e.localizedMessage ?: "Sign up failed.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
