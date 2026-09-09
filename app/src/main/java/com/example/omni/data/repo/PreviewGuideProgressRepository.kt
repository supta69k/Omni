package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The preview twin of [FirestoreGuideProgressRepository] — one in-memory map, no Firebase.
 *
 * It starts empty, which is both what the design draws on the CPR card and what a fresh install reads. A
 * preview that opened on a half-finished guide would be inventing a state the app has to earn; the
 * partially-read look is reachable from the composables' own parameters instead.
 *
 * It *is* mutable, though, and that is the point: it is the seam the tests use, and it makes the guide
 * screens tick their own rows in an interactive preview rather than looking broken.
 */
class PreviewGuideProgressRepository(
    initial: Map<String, Set<Int>> = emptyMap(),
    private val stepsPerGuide: Int = 4,
) : GuideProgressRepository {

    private val completed = MutableStateFlow(initial)

    override fun observePercent(uid: String, guideId: String): Flow<Int> =
        completed.map { percentOf(it[guideId].orEmpty(), stepsPerGuide) }

    override fun observeAllPercents(uid: String): Flow<Map<String, Int>> =
        completed.map { all -> all.mapValues { (_, steps) -> percentOf(steps, stepsPerGuide) } }

    override fun observeCompletedSteps(uid: String, guideId: String): Flow<Set<Int>> =
        completed.map { it[guideId].orEmpty() }

    /** [percent] is ignored — this twin derives it, so a preview cannot desynchronise the two. */
    override suspend fun setCompletedSteps(
        uid: String,
        guideId: String,
        completed: Set<Int>,
        percent: Int,
    ) {
        this.completed.value = this.completed.value + (guideId to completed)
    }
}
