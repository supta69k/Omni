package com.example.omni.data.repo

import com.example.omni.data.model.DailyMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * The in-memory twin of [FirestoreMetricsRepository], matching [PreviewUserRepository]'s role.
 *
 * It starts at the count the Figma card draws, so a preview that binds live data renders the same thing
 * as one that uses the composable's defaults. Its `addGlass` really does increment, which makes the plus
 * button's behaviour visible in an interactive preview without a Firebase project behind it.
 */
class PreviewMetricsRepository(
    initial: Int = PreviewGlasses,
) : MetricsRepository {

    private val glasses = MutableStateFlow(initial)
    private val steps = MutableStateFlow(PreviewSteps)
    private val sleepHours = MutableStateFlow(PreviewSleepHours)

    override fun observeDay(uid: String, date: String): Flow<DailyMetrics?> =
        combine(glasses, steps, sleepHours) { water, walked, slept ->
            DailyMetrics(
                date = date,
                waterGlasses = water,
                steps = walked,
                sleepHours = slept,
                // Deliberately 0: nothing writes fibre until Phase 6, so a preview that showed grams here
                // would be advertising a number the running app cannot produce.
                fiberGrams = 0f,
            )
        }

    override suspend fun addGlass(uid: String, date: String) {
        glasses.update { it + 1 }
    }

    override suspend fun setSteps(uid: String, date: String, steps: Int) {
        this.steps.value = steps
    }

    override suspend fun setSleepHours(uid: String, date: String, hours: Float) {
        sleepHours.value = hours
    }

    private companion object {
        /** What the design draws — see BACKEND_PLAN §3.1. */
        const val PreviewGlasses = 7

        /** The step card's own mock number, so a bound preview matches an unbound one. */
        const val PreviewSteps = 5_600

        /** Likewise the sleep card's. Its setter really does update, so the entry sheet works in a preview. */
        const val PreviewSleepHours = 6.5f
    }
}
