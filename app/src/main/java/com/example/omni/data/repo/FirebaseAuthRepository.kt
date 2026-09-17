package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.UserRole
import com.google.firebase.auth.EmailAuthProvider
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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // 60s, not 15: Render's free tier sleeps between requests and a cold start takes ~25-30s
        // before the first byte. A 15s read timeout made the very first request of a session fail
        // with a raw IOException long before the backend had finished waking up.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // TODO: Configure via BuildConfig or local.properties for production vs emulator
    // For emulator: "http://10.0.2.2:5001/omni-2c987/us-central1"
    // For production: "https://us-central1-omni-2c987.cloudfunctions.net"
    private val backendBaseUrl: String = "https://omni-jx01.onrender.com" // Deployed Render backend

    private val _authState = MutableStateFlow(AuthState.LOADING)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionUid = MutableStateFlow(auth.currentUser?.uid)
    override val sessionUid: StateFlow<String?> = _sessionUid.asStateFlow()

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override val currentEmail: String?
        get() = auth.currentUser?.email

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

    override suspend fun changeEmail(currentPassword: String, newEmail: String) {
        val user = reauthenticated(currentPassword)
        try {
            // `verifyBeforeUpdateEmail`, not `updateEmail`. The latter throws on any project with
            // email-enumeration protection turned on, which is the default for projects created since
            // 2023 — and even where it works it swaps the sign-in address for one nobody has proved
            // they can open, which is a lock-out waiting to happen.
            user.verifyBeforeUpdateEmail(newEmail.trim()).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = reauthenticated(currentPassword)
        try {
            user.updatePassword(newPassword).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
    }

    /**
     * The signed-in user, with a credential Firebase has just re-checked.
     *
     * Both credential changes need this: Firebase rejects them outright on a session older than a few
     * minutes, and doing the check here rather than at each call site means the "wrong password"
     * sentence comes from one place and cannot drift between the two forms.
     */
    private suspend fun reauthenticated(currentPassword: String): FirebaseUser {
        val user = auth.currentUser ?: throw AuthException("You're signed out. Sign in and try again.")
        val email = user.email ?: throw AuthException("This account has no email address to change.")
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        } catch (e: Exception) {
            throw mapped(e)
        }
        return user
    }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun reloadCurrentUser() {
        val user = auth.currentUser ?: throw AuthException("You're signed out. Sign in and try again.")
        try {
            user.reload().await()
        } catch (e: Exception) {
            throw mapped(e)
        }
    }

    override val isEmailVerified: Boolean
        get() = auth.currentUser?.isEmailVerified ?: false

    override suspend fun sendOtpCode(): Boolean {
        val user = auth.currentUser ?: throw AuthException("You're signed out. Sign in and try again.")
        Log.d("OmniAuth", "OTP_SEND_START")
        // Force-refresh, not cache: an ID token is valid for an hour, and `false` hands back the
        // cached one when the session has sat idle longer than that — which the backend then
        // rejects as "Invalid or expired token" and the send dies with no email and no retry.
        val idToken = user.getIdToken(true).await()

        val request = Request.Builder()
            .url("$backendBaseUrl/otp/send")
            .header("Authorization", "Bearer $idToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    Log.d("OmniAuth", "OTP_SEND_HTTP_STATUS=$code")
                    if (response.isSuccessful) {
                        // The backend only answers 2xx after SendGrid has accepted the email,
                        // so this is the moment "code sent" becomes a true statement.
                        Log.d("OmniAuth", "OTP_SEND_SUCCESS")
                        true
                    } else {
                        val body = response.body?.string() ?: ""
                        Log.e("OmniAuth", "OTP_SEND_FAILURE status=$code body=$body")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            // Timeout, refused connection, cold Render that never woke up — all of them look the
            // same from here, and all of them mean no email left the building.
            Log.e("OmniAuth", "OTP_SEND_FAILURE ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    override suspend fun verifyOtpCode(code: String): Boolean {
        val user = auth.currentUser ?: throw AuthException("You're signed out. Sign in and try again.")
        val idToken = user.getIdToken(true).await()

        val json = JSONObject().put("code", code)
        val request = Request.Builder()
            .url("$backendBaseUrl/otp/verify")
            .header("Authorization", "Bearer $idToken")
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    throw AuthException("Verification failed: $body")
                }
                val responseBody = response.body?.string() ?: "{}"
                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getBoolean("verified")
            }
        }
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
