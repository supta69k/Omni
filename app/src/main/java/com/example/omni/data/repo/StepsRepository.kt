package com.example.omni.data.repo

import kotlinx.coroutines.flow.Flow

/**
 * Today's step count, from this device.
 *
 * Split from [MetricsRepository] even though both end up writing the same Firestore document, because the
 * source is different in kind: everything else the dashboard shows is something the user told the app,
 * while this is a *reading* off hardware that keeps counting whether the app is running or not. The
 * reconciliation that turns "steps since boot" into "steps today" is this repository's whole job.
 */
interface StepsRepository {
    /** False on a device with no pedometer, in which case [observeTodaySteps] only ever emits 0. */
    val isAvailable: Boolean

    /**
     * Emits today's step count, starting with whatever was already recorded before this call.
     *
     * [uid] is nullable because the sensor does not care whether anyone is signed in — counting still
     * works, it simply has nowhere to sync to. Collecting registers the sensor listener, so callers
     * should not collect this while the permission is missing: on API 29+ the OS withholds the events
     * and the flow would sit silent forever.
     */
    fun observeTodaySteps(uid: String?): Flow<Int>
}
