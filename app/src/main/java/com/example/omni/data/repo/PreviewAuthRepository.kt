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

    override suspend fun signUp(name: String, email: String, password: String) {
        require("@" in email) { "That email address doesn't look right." }
        require(password.length >= 6) { "Passwords need at least 6 characters." }
        enter("preview-$email")
    }

    override suspend fun signIn(email: String, password: String) {
        if (password.isEmpty()) throw AuthException("Wrong password. Try again or reset it.")
        enter("preview-$email")
    }

    override suspend fun sendPasswordReset(email: String) {
        require("@" in email) { "That email address doesn't look right." }
    }

    override fun signOut() {
        _sessionUid.value = null
        _authState.value = AuthState.UNAUTHENTICATED
    }

    /** The uid before the state, matching the real repository's ordering. */
    private fun enter(uid: String) {
        _sessionUid.value = uid
        _authState.value = AuthState.AUTHENTICATED
    }
}
