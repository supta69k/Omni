package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow

/**
 * How far the user has got through one first-aid guide — `users/{uid}/guideProgress/{guideId}`
 * (BACKEND_PLAN §7).
 *
 * Only the read exists so far, because only the read is needed: the CPR update card on Home renders this
 * number, and Phase 7 — the guides themselves — is what will start writing it. Until then every guide
 * reads 0, which is the truth rather than a placeholder: nothing in the app can complete a step yet.
 *
 * A separate repository from [MetricsRepository] because the lifetime is different again. A day document
 * is finished when the day ends; a guide's progress belongs to the account and carries forward for as long
 * as it takes to read the guide.
 */
interface GuideProgressRepository {
    /**
     * Emits [guideId]'s completion percentage on every change, and 0 while there is no document.
     *
     * A percentage rather than the step list, because that is all any caller needs today and it is what the
     * document already stores. Phase 7 will add the `completedSteps` read beside it.
     */
    fun observePercent(uid: String, guideId: String): Flow<Int>
}

/** The one guide the dashboard names. Its document id, so the card and Phase 7's writer cannot disagree. */
const val CprGuideId = "cpr"
