package com.example.omni.data.repo

import com.example.omni.data.model.AuthState
import com.example.omni.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The real thing: Firebase Auth for the session, Firestore for the `users/{uid}` document.
 *
 * The user document is created inside the sign-up flow itself (register, then set) so a profile is
 * never missing when the signed-in user first lands on a screen. Firestore's offline persistence
 * keeps that first write safe on flaky connections.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(AuthState.LOADING)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionUid = MutableStateFlow(auth.currentUser?.uid)
    override val sessionUid: StateFlow<String?> = _sessionUid.asStateFlow()

    override val currentUid: String?
        get() = auth.currentUser?.uid

    init {
        // FirebaseAuth persists its session across process deaths, so this fires immediately on
        // launch with the restored user — the launch gate never asks the user to sign in twice.
        scope.launch {
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.collect { user ->
                // The uid first: a collector woken by the state change must not be able to read a
                // session that has already been replaced. See AuthRepository.sessionUid.
                _sessionUid.value = user?.uid
                _authState.value = if (user != null) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED
            }
        }
    }

    override suspend fun signUp(name: String, email: String, password: String) {
        val result = try {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
        result.user?.let { writeNewUser(it, name.trim()) }
    }

    override suspend fun signIn(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
    }

    override fun signOut() {
        auth.signOut()
    }

    /**
     * The document schema's root: role starts at USER; `profession`/`verified` are absent until the
     * verification flow (Phase 6) writes them. `createdAt` is server time so the feed can order.
     */
    private suspend fun writeNewUser(user: FirebaseUser, name: String) {
        val profile = mapOf(
            "name" to name,
            "email" to user.email,
            "role" to UserRole.USER.name,
            "verified" to false,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        try {
            firestore.collection("users").document(user.uid).set(profile).await()
        } catch (e: Exception) {
            // The account exists but the profile write failed: sign the user back out rather than
            // landing them in an app that assumes a document is there. Kept short because the auth
            // screens show it in a single-line slot.
            auth.signOut()
            throw AuthException("Profile couldn't be saved. Please try again.")
        }
    }

    private fun mapped(e: Exception): AuthException {
        val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
            ?: (e.cause as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        return AuthException(code?.let { firebaseAuthErrorMessage(it) } ?: "Something went wrong. Please try again.")
    }
}
