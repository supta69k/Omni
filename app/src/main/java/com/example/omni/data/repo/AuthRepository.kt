package com.example.omni.data.repo

import com.example.omni.data.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the auth screens and the launch gate need, and nothing else.
 *
 * Firebase lives behind this interface so the previews and the Phase 10 emulator tests can run
 * against [PreviewAuthRepository] instead — and so "user not found" style errors arrive as
 * [AuthException]s with human copy rather than a raw FirebaseException.
 */
interface AuthRepository {
    /** Hot, starts collecting immediately; Firebase's own session persistence backs it. */
    val authState: StateFlow<AuthState>

    /** The signed-in user's uid, or null. Drives the `users/{uid}` paths everywhere else. */
    val currentUid: String?

    /** Creates the account and the `users/{uid}` document (name goes with it in one flow). */
    suspend fun signUp(name: String, email: String, password: String)

    suspend fun signIn(email: String, password: String)

    /** Sends the reset email through Firebase; the UI just confirms it was requested. */
    suspend fun sendPasswordReset(email: String)

    fun signOut()
}

/** A backend call failed; [message] is the user-facing sentence the screens show under a field. */
class AuthException(message: String) : Exception(message)

/** Maps Firebase's error codes to the sentences the auth screens show. */
fun firebaseAuthErrorMessage(code: String): String = when (code) {
    "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
    "ERROR_WRONG_PASSWORD" -> "Wrong password. Try again or reset it."
    "ERROR_USER_NOT_FOUND" -> "No account with that email. Sign up first?"
    "ERROR_USER_MISMATCH" -> "No account with that email. Sign up first?"
    "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account. Sign in instead?"
    "ERROR_WEAK_PASSWORD" -> "Passwords need at least 6 characters."
    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a moment and try again."
    "ERROR_NETWORK_REQUEST_FAILED" -> "No connection. Check your internet and try again."
    else -> "Something went wrong. Please try again."
}
