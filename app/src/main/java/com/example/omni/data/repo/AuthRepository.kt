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

    /**
     * *Who* is signed in, as a flow — `null` while nobody is, or before Firebase has answered.
     *
     * This exists because [authState] cannot answer the question and every screen was asking it of
     * it anyway. The pattern was `authState.map { if (it == AUTHENTICATED) currentUid else null }`,
     * and it has two holes that only appear when one account replaces another:
     *
     *  - [authState] is an *enum*. Signing out of A and into B reads `AUTHENTICATED` at both ends,
     *    so a `distinctUntilChanged` downstream can erase the entire transition and the `map` never
     *    re-runs. Every read stays keyed to A's uid while B is looking at the screen.
     *  - [currentUid] inside that `map` is an imperative read, not a dependency. Even when the state
     *    does change, what the lambda samples is whatever Firebase holds at that instant — which
     *    during a sign-out/sign-in pair is a coin toss.
     *
     * Keyed on the uid itself, both disappear: a new account is a new value, so every flow built on
     * it restarts, and there is no instant at which one session's answer can land in another's UI.
     *
     * Still distinct from [currentUid], which stays the right thing for an *action* — a tap reads
     * the session as it is when the finger lands.
     */
    val sessionUid: StateFlow<String?>

    /** The signed-in user's uid, or null. Drives the `users/{uid}` paths everywhere else. */
    val currentUid: String?

    /**
     * The signed-in account's email address, or null.
     *
     * Read from Firebase Auth rather than from the `users/{uid}` document on purpose: Auth's copy is the
     * one that signs you in, and the two can legitimately disagree for as long as it takes a pending
     * email change to be confirmed from the inbox. The security page reauthenticates against *this* one.
     */
    val currentEmail: String?

    /** Creates the account and the `users/{uid}` document (name goes with it in one flow). */
    suspend fun signUp(name: String, email: String, password: String)

    suspend fun signIn(email: String, password: String)

    /** Sends the reset email through Firebase; the UI just confirms it was requested. */
    suspend fun sendPasswordReset(email: String)

    /**
     * Starts a change of sign-in address: reauthenticates with [currentPassword], then asks Firebase to
     * send a confirmation link to [newEmail].
     *
     * It **starts** the change rather than completing it. Firebase's own method here is
     * `verifyBeforeUpdateEmail`, which does not touch the account until the link in that message is
     * opened — and that is the right behaviour to expose: an address nobody has proved they can read is
     * an address that would lock the account out. The caller's job is therefore to say "check your
     * inbox", not "done".
     *
     * The password is required because Firebase refuses a credential change on a session that has been
     * signed in for a while, and asking for it up front turns "this operation requires recent
     * authentication" into a field on the form rather than an error after the fact.
     */
    suspend fun changeEmail(currentPassword: String, newEmail: String)

    /** Reauthenticates with [currentPassword], then replaces it with [newPassword]. Takes effect at once. */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    fun signOut()

    /**
     * Re-fetches the signed-in user from Firebase so [isEmailVerified] reflects the server.
     *
     * Required, not optional: `FirebaseUser` is a local snapshot taken at sign-in, and opening the
     * link happens in a browser this process never sees. Without a reload the flag stays `false`
     * forever no matter how many times the user verifies.
     */
    suspend fun reloadCurrentUser()

    /**
     * Whether Firebase Auth considers the signed-in account's email proven — the **only** source of
     * truth for it.
     *
     * A plain read of the current snapshot rather than a flow, and never mirrored into Firestore or
     * DataStore: a verified flag this app could write is a verified flag this app could be tricked
     * into writing. Pair it with [reloadCurrentUser] to ask the server; `false` when nobody is
     * signed in.
     */
    val isEmailVerified: Boolean

    /**
     * Sends a 6-digit OTP verification code to the signed-in account's email.
     *
     * This calls the secure backend which generates a cryptographically secure code,
     * stores it with expiration (10 min), rate limiting, and one-time use enforcement.
     * The backend sends the code via email.
     *
     * Returns true if the email was sent successfully, false otherwise.
     */
    suspend fun sendOtpCode(): Boolean

    /**
     * Verifies a 6-digit OTP code against the secure backend.
     *
     * The backend validates the code, checks expiration, rate limiting, and one-time use.
     * If valid, the backend marks the Firebase user's email as verified.
     * Returns true if verification succeeded.
     */
    suspend fun verifyOtpCode(code: String): Boolean
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
    // The three the account pages can produce that the sign-in pages cannot. `INVALID_CREDENTIAL` is
    // what a modern project returns instead of `WRONG_PASSWORD` once email-enumeration protection is on,
    // and on these pages it can only mean the current password was wrong — there is no "or the account
    // doesn't exist" branch when the account is the one already signed in.
    "ERROR_INVALID_CREDENTIAL" -> "Wrong password. Try again or reset it."
    "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign out and back in, then try again."
    "ERROR_OPERATION_NOT_ALLOWED" -> "Your account can't change this here. Contact support."
    "ERROR_NETWORK_REQUEST_FAILED" -> "No connection. Check your internet and try again."
    else -> "Something went wrong. Please try again."
}
