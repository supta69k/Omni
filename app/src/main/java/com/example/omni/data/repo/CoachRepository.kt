package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.CoachError
import com.example.omni.data.model.CoachGuidance
import com.example.omni.data.model.CoachState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The AI Food Coach's transport — HTTPS to the Omni Render backend's `POST /ai/coach/daily`,
 * mirroring [RenderMealAnalysisRepository] exactly: the same OkHttp timeouts (60s read, because
 * Render's free tier cold-starts), the same force-refreshed Firebase ID token in a `Bearer` header,
 * and the same single 401 retry for a cold instance whose first token check fails on its side.
 *
 * The client is never trusted for any coach rule: the week's data is read server-side, the meter is
 * a server-side counter, and the entitlement is a server-side RevenueCat read. The client only
 * supplies `today` — its own device-local date — which picks the window's end (presentation, never
 * authorization). Every failure maps onto a typed [CoachError] or the distinct [CoachState.LimitReached].
 */
interface CoachRepository {
    suspend fun requestDailyGuidance(today: String): CoachResult
}

/** The repository's answer: guidance, the day's limit reached, or a typed failure. */
sealed interface CoachResult {
    data class Success(val guidance: CoachGuidance) : CoachResult
    data object LimitReached : CoachResult
    data class Failure(val error: CoachError) : CoachResult
}

class RenderCoachRepository(
    private val auth: FirebaseAuth,
    private val baseUrl: String = "https://omni-jx01.onrender.com",
) : CoachRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // 60s to survive a Render cold start, exactly as FirebaseAuthRepository does for OTP.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun requestDailyGuidance(today: String): CoachResult {
        val user = auth.currentUser ?: return CoachResult.Failure(CoachError.Auth)

        return withContext(Dispatchers.IO) {
            try {
                var token = runCatching { user.getIdToken(true).await().token }.getOrNull()
                    ?: return@withContext CoachResult.Failure(CoachError.Auth)

                var outcome = attempt(today, token)
                // A cold Render instance's first token check can fail on its side (see OTP). Retry
                // once with a freshly refreshed token before believing a 401.
                if (outcome is CoachResult.Failure && outcome.error == CoachError.Auth) {
                    Log.w("OmniCoach", "COACH_RETRY_401 after 3s")
                    delay(3000)
                    token = runCatching { user.getIdToken(true).await().token }.getOrNull() ?: token
                    outcome = attempt(today, token)
                }
                outcome
            } catch (e: IOException) {
                // Timeout, refused connection, a cold Render that never woke — all mean "no answer".
                Log.w("OmniCoach", "COACH_NETWORK ${e.javaClass.simpleName}")
                CoachResult.Failure(CoachError.Network)
            } catch (e: Exception) {
                Log.w("OmniCoach", "COACH_ERROR ${e.javaClass.simpleName}")
                CoachResult.Failure(CoachError.Unknown)
            }
        }
    }

    private suspend fun attempt(today: String, token: String): CoachResult {
        val body = JSONObject().put("today", today)
        val request = Request.Builder()
            .url("$baseUrl/ai/coach/daily")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val raw = response.body?.string().orEmpty()
            Log.d("OmniCoach", "COACH_HTTP_STATUS=$code")

            when {
                response.isSuccessful -> parseSuccess(raw)
                // The free tier's one session is used up — a state the sheet renders (with the way
                // in to Omni+), not a failure. Mapped before errorFor so it can never degrade.
                code == 429 -> CoachResult.LimitReached
                code == 401 -> CoachResult.Failure(CoachError.Auth)
                else -> CoachResult.Failure(errorFor(code, raw))
            }
        }
        return response
    }

    /** Maps a non-2xx status (and the backend's `code`, when present) to a typed failure. */
    private fun errorFor(status: Int, raw: String): CoachError {
        val backendCode = runCatching { JSONObject(raw).optString("code") }.getOrNull()
        return when (backendCode) {
            "AI_UNAVAILABLE" -> CoachError.Unavailable
            "AI_INVALID_RESPONSE" -> CoachError.InvalidResponse
            "AUTH_REQUIRED" -> CoachError.Auth
            else -> when (status) {
                503 -> CoachError.Unavailable
                422 -> CoachError.InvalidResponse
                else -> CoachError.Unknown
            }
        }
    }

    /** Parses `{ success, starter, coach, usage }`; a malformed body is [CoachError.Unknown]. */
    private fun parseSuccess(raw: String): CoachResult {
        val guidance = runCatching {
            val root = JSONObject(raw)
            val coach = root.getJSONObject("coach")
            val usage = root.optJSONObject("usage")
            CoachGuidance(
                summary = coach.optString("summary"),
                focus = coach.optString("focus", "balance"),
                tips = buildList {
                    val tips = coach.optJSONArray("tips")
                    if (tips != null) {
                        for (i in 0 until tips.length()) {
                            tips.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                },
                starter = root.optBoolean("starter", false),
                used = usage?.optInt("used", 0) ?: 0,
                // `limit: null` is the server's "unlimited" (Omni+); absent is treated the same way.
                limit = usage?.let { if (it.isNull("limit")) null else it.optInt("limit") },
            )
        }.getOrNull() ?: return CoachResult.Failure(CoachError.Unknown)

        if (guidance.summary.isBlank() || guidance.tips.isEmpty()) {
            return CoachResult.Failure(CoachError.InvalidResponse)
        }
        return CoachResult.Success(guidance)
    }
}
