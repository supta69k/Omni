package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow

/**
 * How far the user has got through the first-aid guides — `users/{uid}/guideProgress/{guideId}`
 * (BACKEND_PLAN §7).
 *
 * The *content* of a guide is not here and never will be: it ships in the APK
 * ([BundledGuideRepository]) so a fresh install works in airplane mode. This is the only
 * per-user half, and it is deliberately tiny — a set of finished step numbers and the percentage
 * derived from it.
 *
 * A separate repository from [MetricsRepository] because the lifetime is different again. A day
 * document is finished when the day ends; a guide's progress belongs to the account and carries
 * forward for as long as it takes to read the guide.
 *
 * **Why `percent` is stored rather than computed on read.** Home's CPR card needs one number for one
 * guide and must not know how many steps that guide has — the step count lives in the bundle, which
 * would make the card's reading change under it on the next content update. Storing both keeps the
 * card's read a single field, and [setCompletedSteps] is the only writer, so the two cannot drift.
 */
interface GuideProgressRepository {
    /**
     * Emits [guideId]'s completion percentage on every change, and 0 while there is no document.
     *
     * For the one card that names one guide. The guide list wants [observeAllPercents] instead — the
     * same read across a dozen documents, on one listener rather than a dozen.
     */
    fun observePercent(uid: String, guideId: String): Flow<Int>

    /**
     * Every guide's percentage by document id, and an empty map while nothing has been read.
     *
     * A guide missing from the map is 0, so callers key into it with a default rather than treating an
     * absent entry as a state of its own — before the first step is ever finished *every* guide is
     * absent, which makes the empty case the normal one.
     */
    fun observeAllPercents(uid: String): Flow<Map<String, Int>>

    /**
     * Emits the 1-based step numbers of [guideId] that are finished, and an empty set while there is no
     * document.
     *
     * A set, not a count: the detail screen ticks individual rows, and steps can be finished out of
     * order — someone who already knows to call an ambulance starts at step 2.
     */
    fun observeCompletedSteps(uid: String, guideId: String): Flow<Set<Int>>

    /**
     * Replaces [guideId]'s finished-step set and the percentage that goes with it.
     *
     * **Why a whole-set write rather than `FieldValue.arrayUnion` or `increment`.** §4 rule 7's
     * increment discipline is about *counters*, where two devices each adding one must add two. This is
     * not a counter — it is set membership, and the percentage is a projection of the set. `arrayUnion`
     * would keep the array atomic but leaves no way to write the matching `percent` in the same
     * operation, and a transaction is worse: Firestore transactions need the network, so using one here
     * would break the single acceptance criterion this whole phase is built around. The caller already
     * holds the current set from [observeCompletedSteps] and the step count from the bundle, so it
     * computes both fields and writes them together. The cost is a lost tick if the same guide is read
     * on two devices at once, on a document only its owner touches.
     *
     * Nothing needs to be applied optimistically: Firestore writes to its local cache and re-fires the
     * listeners before the round-trip, so the row ticks on the same frame as the tap. An offline write
     * does not fail — it queues, and this call simply never returns until the app is back online.
     */
    suspend fun setCompletedSteps(uid: String, guideId: String, completed: Set<Int>, percent: Int)
}

/** The one guide the dashboard names. Its document id, so the card and the guide screens agree. */
const val CprGuideId = "cpr"

/**
 * [completed] as a percentage of [totalSteps], clamped to 0..100 — what [GuideProgressRepository.
 * setCompletedSteps] is handed.
 *
 * Here rather than in a ViewModel because both the writer and any future reader have to agree on it, and
 * because a guide with no steps has to be 0 rather than a division by zero. Only steps in `1..totalSteps`
 * count: a step that has been dropped from the bundle since it was ticked would otherwise leave a guide
 * stuck above 100%.
 */
fun percentOf(completed: Set<Int>, totalSteps: Int): Int {
    if (totalSteps <= 0) return 0
    val counted = completed.count { it in 1..totalSteps }
    return (counted * 100 / totalSteps).coerceIn(0, 100)
}
