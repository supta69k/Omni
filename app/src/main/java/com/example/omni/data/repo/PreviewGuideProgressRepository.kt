package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The preview twin of [FirestoreGuideProgressRepository] — one fixed percentage for every guide.
 *
 * It reports 0, which is both what the design draws on the CPR card and what the running app will report
 * until Phase 7 lands. A preview that showed a half-finished guide would be inventing a state no build can
 * currently reach; the partially-read look is reachable from the composable's own parameter instead.
 */
class PreviewGuideProgressRepository(
    private val percent: Int = 0,
) : GuideProgressRepository {

    override fun observePercent(uid: String, guideId: String): Flow<Int> = flowOf(percent)
}
