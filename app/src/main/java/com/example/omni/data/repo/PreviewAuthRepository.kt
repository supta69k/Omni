package com.example.omni.data.repo

import com.example.omni.data.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The offline twin of [FirebaseAuthRepository] — used by Compose previews and the unit tests, so
 * neither ever needs a Firebase project or network.
 *
 * Behaves like the real thing: accepts any well-formed call, flips to AUTHENTICATED on
 * signUp/signIn, keeps the state in memory only.
 */
class PreviewAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow(AuthState.UNAUTHENTICATED)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionUid = MutableStateFlow<String?>(null)
    override val sessionUid: StateFlow<String?> = _sessionUid.asStateFlow()

    override val currentUid: String?
        get() = _sessionUid.value

    private var password: String = PreviewPassword

    private val _currentEmail = MutableStateFlow<String?>(null)
    override val currentEmail: String?
        get() = _currentEmail.value

    override suspend fun signUp(name: String, email: String, password: String) {
        require("@" in email) { "That email address doesn't look right." }
        require(password.length >= 6) { "Passwords need at least 6 characters." }
        this.password = password
        enter("preview-$email", email)
    }

    override suspend fun signIn(email: String, password: String) {
        if (password.isEmpty()) throw AuthException("Wrong password. Try again or reset it.")
        this.password = password
        enter("preview-$email", email)
    }

    override suspend fun sendPasswordReset(email: String) {
        require("@" in email) { "That email address doesn't look right." }
    }

    /**
     * Checks the password and stops there — as the real one does, because Firebase only *starts* the
     * change here and finishes it when the link in the confirmation mail is opened. A fake that swapped
     * the address immediately would be the one thing the screen must not claim has happened.
     */
    override suspend fun changeEmail(currentPassword: String, newEmail: String) {
        reauthenticate(currentPassword)
        require("@" in newEmail) { "That email address doesn't look right." }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        reauthenticate(currentPassword)
        if (newPassword.length < 6) throw AuthException("Passwords need at least 6 characters.")
        password = newPassword
    }

    override fun signOut() {
        _sessionUid.value = null
        _currentEmail.value = null
        _authState.value = AuthState.UNAUTHENTICATED
    }

    private fun reauthenticate(currentPassword: String) {
        if (_sessionUid.value == null) throw AuthException("You're signed out. Sign in and try again.")
        if (currentPassword != password) throw AuthException("Wrong password. Try again or reset it.")
    }

    /** The uid before the state, matching the real repository's ordering. */
    private fun enter(uid: String, email: String) {
        _sessionUid.value = uid
        _currentEmail.value = email
        _authState.value = AuthState.AUTHENTICATED
    }

    private companion object {
        /** What a test or preview must pass as the current password when nobody has signed in yet. */
        const val PreviewPassword = "preview"
    }
}
