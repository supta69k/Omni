package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The preview twin of [DeviceStepsRepository] — a fixed count, no sensor, no DataStore.
 *
 * Compose previews and unit tests run without an `Application`, so they get this rather than something
 * that would go looking for a `SensorManager`. It reports [isAvailable] as true because the interesting
 * preview is the one where tracking works; the refused-permission state is reached through the card's own
 * parameter instead.
 */
class PreviewStepsRepository(
    private val steps: Int = PreviewSteps,
) : StepsRepository {

    override val isAvailable: Boolean = true

    override fun observeTodaySteps(uid: String?): Flow<Int> = flowOf(steps)

    private companion object {
        /** The step card's own mock number. */
        const val PreviewSteps = 5_600
    }
}
