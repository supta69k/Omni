package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.MealAnalysis
import com.example.omni.data.model.MealAnalysisError
import com.example.omni.data.model.MealAnalysisItem
import com.example.omni.data.model.MealAnalysisResult
import com.example.omni.data.model.MealAnalysisTotals
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
 * The real analyzer: HTTPS to the Omni Render backend's `POST /ai/meal/analyze`, mirroring the exact
 * transport [FirebaseAuthRepository] already uses for the OTP calls — same OkHttp timeouts (60s read,
 * because Render's free tier cold-starts), the same force-refreshed Firebase ID token in a `Bearer`
 * header, the same single 401 retry for a cold instance whose first token check fails on its side.
 *
 * The Gemini API key is never here. This class only ever sends the user's ID token; the backend
 * derives the UID from the *verified* token (never trusting the client) and holds the key in its own
 * environment. The meal text is personal data, so it is never logged — only that a request happened
 * and how it resolved.
 */
class RenderMealAnalysisRepository(
    private val auth: FirebaseAuth,
    private val baseUrl: String = "https://omni-jx01.onrender.com",
) : MealAnalysisRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // 60s to survive a Render cold start, exactly as FirebaseAuthRepository does for OTP.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun analyzeMeal(mealText: String): MealAnalysisResult {
        val trimmed = mealText.trim()
        if (trimmed.isEmpty()) return failure(MealAnalysisError.InvalidInput)

        val user = auth.currentUser ?: return failure(MealAnalysisError.Auth)

        return withContext(Dispatchers.IO) {
            try {
                var token = runCatching { user.getIdToken(true).await().token }.getOrNull()
                    ?: return@withContext failure(MealAnalysisError.Auth)

                var outcome = attempt(trimmed, token)
                // A cold Render instance's first token check can fail on its side (see OTP). Retry once
                // with a freshly refreshed token before believing a 401.
                if (outcome.retryableAuth) {
                    Log.w("OmniAiMeal", "ANALYZE_RETRY_401 after 3s")
                    delay(3000)
                    token = runCatching { user.getIdToken(true).await().token }.getOrNull() ?: token
                    outcome = attempt(trimmed, token)
                }
                outcome.result
            } catch (e: IOException) {
                // Timeout, refused connection, a cold Render that never woke — all mean "no answer".
                Log.w("OmniAiMeal", "ANALYZE_NETWORK ${e.javaClass.simpleName}")
                failure(MealAnalysisError.Network)
            } catch (e: Exception) {
                Log.w("OmniAiMeal", "ANALYZE_ERROR ${e.javaClass.simpleName}")
                failure(MealAnalysisError.Unknown)
            }
        }
    }

    /** One HTTP attempt, mapped to a result plus whether a 401 is worth one retry. */
    private fun attempt(mealText: String, token: String): Attempt {
        val body = JSONObject().put("mealText", mealText)
        val request = Request.Builder()
            .url("$baseUrl/ai/meal/analyze")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val raw = response.body?.string().orEmpty()
            Log.d("OmniAiMeal", "ANALYZE_HTTP_STATUS=$code len=${mealText.length}")

            if (response.isSuccessful) {
                return Attempt(parseSuccess(raw), retryableAuth = false)
            }
            if (code == 401) {
                // Signal a single retry; the caller decides. After the retry a 401 becomes Auth below.
                return Attempt(failure(MealAnalysisError.Auth), retryableAuth = true)
            }
            return Attempt(failure(errorFor(code, raw)), retryableAuth = false)
        }
    }

    /** Maps a non-2xx status (and the backend's `code`, when present) to a typed error. */
    private fun errorFor(status: Int, raw: String): MealAnalysisError {
        val backendCode = runCatching { JSONObject(raw).optString("code") }.getOrNull()
        return when (backendCode) {
            // Omni no longer imposes a per-user daily cap. A 429 now only means Gemini's own quota is
            // exhausted; treat it (and the retired AI_MEAL_LIMIT_REACHED code, should an old backend
            // build answer during a rollout) as a temporary outage, never an Omni limit.
            "AI_MEAL_LIMIT_REACHED" -> MealAnalysisError.Unavailable
            "AI_UNAVAILABLE" -> MealAnalysisError.Unavailable
            "AI_INVALID_RESPONSE" -> MealAnalysisError.InvalidResponse
            "INVALID_INPUT" -> MealAnalysisError.InvalidInput
            "AUTH_REQUIRED" -> MealAnalysisError.Auth
            else -> when (status) {
                429 -> MealAnalysisError.Unavailable
                503 -> MealAnalysisError.Unavailable
                422 -> MealAnalysisError.InvalidResponse
                400 -> MealAnalysisError.InvalidInput
                else -> MealAnalysisError.Unknown
            }
        }
    }

    /** Parses `{ success, result }`; a malformed body is [MealAnalysisError.Unknown], never a crash. */
    private fun parseSuccess(raw: String): MealAnalysisResult {
        val analysis = runCatching {
            val result = JSONObject(raw).getJSONObject("result")
            val itemsJson = result.optJSONArray("items")
            val items = buildList {
                if (itemsJson != null) {
                    for (i in 0 until itemsJson.length()) {
                        val it = itemsJson.getJSONObject(i)
                        add(
                            MealAnalysisItem(
                                name = it.optString("name"),
                                quantity = it.optDouble("quantity", 0.0).toFloat(),
                                unit = it.optString("unit", "serving"),
                                calories = it.optDouble("calories", 0.0).toInt(),
                                protein = it.optDouble("proteinGrams", 0.0).toFloat(),
                                carbs = it.optDouble("carbsGrams", 0.0).toFloat(),
                                fat = it.optDouble("fatGrams", 0.0).toFloat(),
                                // `opt` with a 0 default, like every field above: a backend build from
                                // before fiber existed simply reads 0 and the meal logs as it always did.
                                fiber = it.optDouble("fiberGrams", 0.0).toFloat(),
                            ),
                        )
                    }
                }
            }
            val totalsJson = result.optJSONObject("totals")
            val totals = MealAnalysisTotals(
                calories = totalsJson?.optDouble("calories", 0.0)?.toInt() ?: 0,
                protein = totalsJson?.optDouble("proteinGrams", 0.0)?.toFloat() ?: 0f,
                carbs = totalsJson?.optDouble("carbsGrams", 0.0)?.toFloat() ?: 0f,
                fat = totalsJson?.optDouble("fatGrams", 0.0)?.toFloat() ?: 0f,
                fiber = totalsJson?.optDouble("fiberGrams", 0.0)?.toFloat() ?: 0f,
            )
            MealAnalysis(
                mealName = result.optString("mealName"),
                items = items,
                totals = totals,
                estimated = result.optBoolean("estimated", true),
                needsClarification = result.optBoolean("needsClarification", false),
                clarificationQuestion = result.optString("clarificationQuestion").takeIf { it.isNotBlank() },
            )
        }.getOrNull() ?: return failure(MealAnalysisError.Unknown)

        // A "success" whose result carries neither items nor a clarification prompt is unusable.
        if (!analysis.needsClarification && analysis.items.isEmpty()) {
            return failure(MealAnalysisError.InvalidResponse)
        }
        return MealAnalysisResult.Success(analysis)
    }

    private fun failure(error: MealAnalysisError): MealAnalysisResult = MealAnalysisResult.Failure(error)

    /** One attempt's outcome: the result, plus whether a 401 here is worth exactly one retry. */
    private class Attempt(val result: MealAnalysisResult, val retryableAuth: Boolean)
}
