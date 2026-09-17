package com.example.omni.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.repo.AuthException
import com.example.omni.data.repo.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isResetEmailSent: Boolean = false,
    val isVerifyingCode: Boolean = false,        // OTP verification in progress
    val isVerifyingLink: Boolean = false,       // Link verification in progress
    val isResendingCode: Boolean = false,       // Resending OTP code
    val resendCodeError: String? = null,
    val resendCodeSuccess: String? = null,
    val resendCodeCooldown: Int = 0,
    val codeError: String? = null,
    val linkError: String? = null,
)

class SignInViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private var resendCodeCooldownJob: kotlinx.coroutines.Job? = null

    fun signIn(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter both email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SignInUiState(isLoading = true)
            try {
                authRepository.signIn(email.trim(), password)
                // Check if email is verified after sign in
                if (authRepository.isEmailVerified) {
                    _uiState.value = SignInUiState(isLoading = false)
                    onSuccess()
                } else {
                    _uiState.value = SignInUiState(isLoading = false)
                    // Navigate to verification screen instead of going to Home
                    onSuccess()
                }
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

    /**
     * Verifies a 6-digit OTP code.
     * Returns true if code is valid and email is now verified.
     */
    fun verifyCode(code: String, onResult: (Boolean) -> Unit = {}) {
        if (code.length != 6 || !code.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(codeError = "Please enter a valid 6-digit code.")
            onResult(false)
            return
        }

        _uiState.value = _uiState.value.copy(isVerifyingCode = true, codeError = null)
        viewModelScope.launch {
            try {
                val isValid = authRepository.verifyOtpCode(code)

                if (isValid) {
                    // Reload user to get updated emailVerified status
                    authRepository.reloadCurrentUser()
                    val isVerified = authRepository.isEmailVerified

                    if (isVerified) {
                        _uiState.value = _uiState.value.copy(isVerifyingCode = false)
                        onResult(true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isVerifyingCode = false,
                            codeError = "Verification succeeded but email not marked verified. Please try again."
                        )
                        onResult(false)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isVerifyingCode = false,
                        codeError = "Incorrect or expired code."
                    )
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isVerifyingCode = false,
                    codeError = "Failed to verify code. Please try again."
                )
                onResult(false)
            }
        }
    }

    /**
     * Reloads the current user and checks if email is verified (Firebase link flow).
     */
    fun verifyLink(onResult: (Boolean) -> Unit = {}) {
        _uiState.value = _uiState.value.copy(isVerifyingLink = true, linkError = null)
        viewModelScope.launch {
            try {
                authRepository.reloadCurrentUser()
                val isVerified = authRepository.isEmailVerified
                if (isVerified) {
                    _uiState.value = _uiState.value.copy(isVerifyingLink = false)
                    onResult(true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isVerifyingLink = false,
                        linkError = "Your email isn't verified yet. Please tap the link in your email."
                    )
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isVerifyingLink = false,
                    linkError = "Failed to check verification status. Please try again."
                )
                onResult(false)
            }
        }
    }

    /**
     * Resends the 6-digit OTP code.
     */
    /**
     * Sends a fresh 6-digit OTP code through the backend — the ONLY email trigger on the
     * verification screen. [onResult] reports whether the backend confirmed the send, which is
     * what the screen uses to switch the button from "Send" to "Resend".
     */
    fun resendCode(onResult: (Boolean) -> Unit = {}) {
        val currentEmail = authRepository.currentEmail ?: return onResult(false)
        if (currentEmail.isBlank()) return onResult(false)

        _uiState.value = _uiState.value.copy(
            isResendingCode = true,
            resendCodeError = null,
            resendCodeSuccess = null,
            resendCodeCooldown = 60, // 60 second cooldown
        )

        // Start cooldown countdown
        resendCodeCooldownJob?.cancel()
        resendCodeCooldownJob = viewModelScope.launch {
            var remaining = 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(resendCodeCooldown = remaining)
            }
        }

        viewModelScope.launch {
            try {
                val sent = authRepository.sendOtpCode()
                if (sent) {
                    _uiState.value = _uiState.value.copy(
                        isResendingCode = false,
                        resendCodeSuccess = "New verification code sent."
                    )
                    onResult(true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isResendingCode = false,
                        resendCodeError = "Couldn't send the code. Please try again."
                    )
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isResendingCode = false,
                    resendCodeError = "Failed to send verification code. Please try again."
                )
                onResult(false)
            }
        }
    }

    fun clearErrors() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            resendCodeError = null,
            resendCodeSuccess = null,
            codeError = null,
            linkError = null,
        )
    }

    override fun onCleared() {
        super.onCleared()
        resendCodeCooldownJob?.cancel()
    }
}