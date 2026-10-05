package com.example.omni.data.model

/**
 * The AI Food Coach's client-side models — the mirror of the backend's `/ai/coach/daily` response
 * (Phase 13). A coach answer is **guidance, not an estimate**, so it has its own state machine and
 * its own metering surface, separate from the meal-analysis flow it sits beside.
 *
 * The server owns every rule the client is never trusted for: identity (the verified ID token), the
 * week's data (read server-side so the model cannot be fed forged numbers), and the meter (a free
 * user gets one coach session a day; Omni+ is unlimited). The client only ever *renders* what the
 * server decided.
 */

/** One coach session's content — the shape of `response.coach` plus its usage line. */
data class CoachGuidance(
    val summary: String,
    /** The one metric the coach is focusing on this session — drives the sheet's focus chip. */
    val focus: String,
    val tips: List<String>,
    /** True when the week had nothing logged and the server returned starter guidance (no AI call). */
    val starter: Boolean,
    /** Sessions used today (including this one). */
    val used: Int,
    /** Daily cap — `null` means unlimited (Omni+). */
    val limit: Int?,
) {
    val limitReached: Boolean get() = limit != null && used >= limit
}

/** The sheet's state machine — Idle → Loading → Ready / LimitReached / Failed. */
sealed interface CoachState {
    data object Idle : CoachState
    data object Loading : CoachState
    data class Ready(val guidance: CoachGuidance) : CoachState

    /** The free tier's one session is used up — the sheet shows the way in to Omni+. */
    data object LimitReached : CoachState

    data class Failed(val error: CoachError) : CoachState
}

/**
 * Why a coach session could not be produced — each maps to one honest sentence, mirroring the meal
 * analysis errors (§34). [LimitReached] is not here: it is its own [CoachState], because "used up"
 * is a *state the sheet renders* (with the way in to Omni+), not a failure message.
 */
enum class CoachError(val message: String) {
    Unavailable("AI coaching is temporarily unavailable. Please try again in a moment."),
    InvalidResponse("The AI Coach couldn't read your week. Please try again in a moment."),
    Auth("You're signed out. Sign in and try again."),
    Network("Couldn't reach the server. Check your connection and try again."),
    Unknown("Something went wrong with your coaching session. Please try again."),
}
