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
     *
     * **Collected only by `OmniTrackingService`.** The listener, the reconcile and the DataStore
     * writes are one pipeline with one owner; anything else that wants the number reads
     * [observeLocalSteps] instead, or two reconcilers would race on the same state.
     */
    fun observeTodaySteps(uid: String?): Flow<Int>

    /**
     * Today's step total **as this device has persisted it** — without registering the sensor
     * listener.
     *
     * This is the read side of the pipeline [observeTodaySteps] owns: it reflects every reading the
     * pipeline has already folded into its state and nothing since. The dashboard collects this
     * rather than the pipeline itself, so there is exactly one sensor listener and one reconciler
     * per process — the tracking service's — however many screens want the number.
     */
    fun observeLocalSteps(uid: String?): Flow<Int>
}
