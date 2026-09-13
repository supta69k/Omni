package com.example.omni.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the page's two forms is in flight — at most one, because each needs the same password. */
enum class SecurityAction { EMAIL, PASSWORD }

/**
 * The security page's state — two independent forms in one object.
 *
 * Independent because they fail independently: a wrong password typed into the email form must not wipe
 * the half-typed new password below it, and a confirmation notice for one must not sit under the other.
 * So each form carries its own fields, its own failure sentence and its own notice, and [busy] names
 * which one (if either) is currently talking to Firebase.
 *
 * @property email the address Firebase currently signs this account in with — not the profile
 *   document's copy, which can lag behind a change until its confirmation link is opened.
 */
data class SecurityUiState(
    val email: String = "",
    val newEmail: String = "",
    val emailPassword: String = "",
    val emailError: String? = null,
    /** "Check your inbox" — a change that has been *started*, which is all Firebase can promise here. */
    val emailNotice: String? = null,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: String? = null,
    val passwordNotice: String? = null,
    val busy: SecurityAction? = null,
) {
    /**
     * The new address has to be a plausible one *and* different from the current one.
     *
     * The second half matters more than it looks: Firebase accepts `verifyBeforeUpdateEmail` with the
     * address already on the account and sends the mail, so without this the page would cheerfully
     * report a change that changes nothing.
     */
    val canSubmitEmail: Boolean
        get() = busy == null &&
            newEmail.isEmailish() &&
            !newEmail.trim().equals(email.trim(), ignoreCase = true) &&
            emailPassword.isNotEmpty()

    val canSubmitPassword: Boolean
        get() = busy == null &&
            currentPassword.isNotEmpty() &&
            newPassword.length >= MinPasswordLength &&
            newPassword == confirmPassword &&
            newPassword != currentPassword
}

/**
 * Email and password — the page behind Settings' "Security" row.
 *
 * This is the *other* half of "Account Details". The group had two rows both reading "Personal
 * Information", so one of them had nowhere of its own to go; credentials are what it should have been,
 * because they are the one part of an account that does not live in `users/{uid}` at all. Everything here
 * goes through [AuthRepository] and nothing through [com.example.omni.data.repo.UserRepository] — the
 * profile document's `email` field is a copy, reconciled by
 * [com.example.omni.ui.SessionViewModel] once a change has actually landed.
 *
 * Both operations reauthenticate, which is why each form asks for the current password. Firebase refuses
 * a credential change on a session more than a few minutes old, and collecting the password up front
 * turns that refusal into a field on the form rather than an error after the attempt.
 */
class SecurityViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState(email = authRepository.currentEmail.orEmpty()))
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init {
        // Re-read on every session change rather than once at construction: this ViewModel lives on the
        // Activity's store, so one instance can outlive a sign-out — and a page showing the previous
        // account's address would ask for the previous account's password.
        viewModelScope.launch {
            authRepository.sessionUid.collect {
                _uiState.value = SecurityUiState(email = authRepository.currentEmail.orEmpty())
            }
        }
    }

    fun onNewEmailChange(value: String) = _uiState.update {
        it.copy(newEmail = value, emailError = null, emailNotice = null)
    }

    fun onEmailPasswordChange(value: String) = _uiState.update {
        it.copy(emailPassword = value, emailError = null, emailNotice = null)
    }

    fun onCurrentPasswordChange(value: String) = _uiState.update {
        it.copy(currentPassword = value, passwordError = null, passwordNotice = null)
    }

    fun onNewPasswordChange(value: String) = _uiState.update {
        it.copy(newPassword = value, passwordError = null, passwordNotice = null)
    }

    fun onConfirmPasswordChange(value: String) = _uiState.update {
        it.copy(confirmPassword = value, passwordError = null, passwordNotice = null)
    }

    /**
     * Starts the email change and says so — "check your inbox", not "done".
     *
     * The address on the account does not move until the link in that message is opened, so the fields
     * are cleared (the request is spent) but [SecurityUiState.email] is left showing the old address,
     * because that is still the one this account signs in with.
     */
    fun submitEmail() {
        val state = _uiState.value
        if (!state.canSubmitEmail) return
        val target = state.newEmail.trim()

        _uiState.update { it.copy(busy = SecurityAction.EMAIL, emailError = null, emailNotice = null) }
        viewModelScope.launch {
            try {
                authRepository.changeEmail(state.emailPassword, target)
                _uiState.update {
                    it.copy(
                        newEmail = "",
                        emailPassword = "",
                        busy = null,
                        emailNotice = "Confirmation sent to $target. Open the link there to finish the " +
                            "change — until you do, keep signing in with ${it.email}.",
                    )
                }
            } catch (cause: Exception) {
                Log.w("Omni", "The email change could not be started", cause)
                _uiState.update {
                    // The repository's exceptions already carry user-facing sentences; anything else is a
                    // bug rather than something the user can act on, so it gets the generic line.
                    it.copy(busy = null, emailPassword = "", emailError = cause.userMessage())
                }
            }
        }
    }

    /** Changes the password outright — this one *does* take effect immediately. */
    fun submitPassword() {
        val state = _uiState.value
        if (!state.canSubmitPassword) return

        _uiState.update { it.copy(busy = SecurityAction.PASSWORD, passwordError = null, passwordNotice = null) }
        viewModelScope.launch {
            try {
                authRepository.changePassword(state.currentPassword, state.newPassword)
                _uiState.update {
                    it.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                        busy = null,
                        passwordNotice = "Password changed. Use the new one next time you sign in.",
                    )
                }
            } catch (cause: Exception) {
                Log.w("Omni", "The password change failed", cause)
                // Only the current-password box is cleared. Retyping a new password that was never the
                // problem is a punishment for Firebase having rejected a different field.
                _uiState.update {
                    it.copy(busy = null, currentPassword = "", passwordError = cause.userMessage())
                }
            }
        }
    }
}

private fun Exception.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."

/**
 * Enough of an address to be worth sending to Firebase, and no more.
 *
 * Deliberately not a full RFC check: the real verdict comes from the confirmation mail actually arriving,
 * and a stricter pattern here would only reject legitimate addresses this app has no business having an
 * opinion about. This catches the typo that has no `@` at all.
 */
private fun String.isEmailish(): Boolean {
    val trimmed = trim()
    val at = trimmed.indexOf('@')
    return at > 0 && trimmed.indexOf('.', at) > at + 1 && !trimmed.endsWith('.') && ' ' !in trimmed
}

/** Firebase's own floor, and the sentence `firebaseAuthErrorMessage` already quotes for it. */
const val MinPasswordLength = 6
