package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.local.PreferencesStore
import com.example.omni.data.local.StepCounterSource
import com.example.omni.data.local.StepState
import com.example.omni.data.local.currentBootId
import com.example.omni.data.local.pendingSync
import com.example.omni.data.local.reconcile
import com.example.omni.data.local.stepsOn
import com.example.omni.data.model.todayKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * [StepsRepository] over the hardware counter, with DataStore as its memory and Firestore as its record.
 *
 * Three things are true at once here, and they are what the code is shaped around:
 *
 *  - **The sensor counts since boot**, so the day's total is derived, not read. [StepState] holds the
 *    anchor and the running total; [reconcile] is the whole rule.
 *  - **DataStore is the truth for the session.** It is written on every reading that changes anything, so
 *    a process death costs nothing. Firestore is a *record*, deliberately behind: it exists so a second
 *    device (or a fresh install) can see today's number, not so this one can read its own count back.
 *  - **Steps are cheap to produce and expensive to store.** A walk is thousands of readings; a document
 *    write per reading would be thousands of writes. So Firestore is written at most every
 *    [SyncThresholdSteps] steps, plus once when a day ends and once when a session starts holding anything
 *    unsynced (BACKEND_PLAN §11 Phase 4).
 *
 * The honest limitation, stated in the plan and worth repeating: without a foreground service the app only
 * *observes* while it is alive. The sensor keeps counting regardless, so reopening the app catches up on
 * everything walked in between — but steps taken between the last reading and a reboot are gone, because
 * the counter they were in has been reset and nobody read it. That is the accepted trade for not running a
 * permanent notification.
 */
class DeviceStepsRepository(
    private val source: StepCounterSource,
    private val preferences: PreferencesStore,
    private val metrics: MetricsRepository,
) : StepsRepository {

    override val isAvailable: Boolean get() = source.isAvailable

    override fun observeTodaySteps(uid: String?): Flow<Int> = flow {
        if (uid == null) {
            emit(0)
            return@flow
        }

        var state = preferences.stepState(uid).first()
        val startedOn = todayKey()

        // Seed from Firestore if this is the first reading for [uid] on this device, or if the stored day
        // has ended. A fresh install or account switch starts with the server's number rather than zero.
        if (state.date.isEmpty() || state.date != startedOn) {
            val serverSteps = try {
                metrics.observeDay(uid, startedOn).first()?.steps ?: 0
            } catch (cause: Exception) {
                Log.w("Omni", "Could not seed steps for $uid on $startedOn", cause)
                0
            }
            // Keep the anchor and boot id if the day is still current; reset them if the day changed.
            state = if (state.date == startedOn) {
                state.copy(total = serverSteps.coerceAtLeast(state.total), syncedTotal = serverSteps)
            } else {
                StepState(date = startedOn, total = serverSteps, syncedTotal = serverSteps)
            }
            preferences.setStepState(uid, state)
        }

        // Whatever the last session left unwritten goes out now. When the stored day has since ended this
        // is the date-rollover write, and it is the reason the app can be closed at 23:59 and still have
        // yesterday's total on the server.
        state = flush(uid, state)
        emit(state.stepsOn(startedOn))

        source.rawCounts().collect { raw ->
            val date = todayKey()

            // Before reconciling, not after: reconcile is where the outgoing day's total is dropped.
            if (date != state.date) state = flush(uid, state)

            val next = state.reconcile(raw, currentBootId(), date)
            // The counter re-reports the same value while standing still; nothing to store or emit.
            if (next == state) return@collect

            state = next
            preferences.setStepState(uid, state)
            if (state.pendingSync() >= SyncThresholdSteps) state = flush(uid, state)
            emit(state.total)
        }
    }

    /**
     * Publishes [StepState.total] to `users/{uid}/days/{date}` if it owes a write.
     *
     * A failure is logged and swallowed: the total stays pending and the next threshold crossing tries
     * again, which is exactly the behaviour wanted for a metric nobody is waiting on. An *offline* write
     * does not fail at all — Firestore queues it — so reaching the catch means the rules said no.
     */
    private suspend fun flush(uid: String, state: StepState): StepState {
        if (state.date.isEmpty() || state.pendingSync() == 0) return state
        return try {
            metrics.setSteps(uid, state.date, state.total)
            state.copy(syncedTotal = state.total).also { preferences.setStepState(uid, it) }
        } catch (cause: Exception) {
            Log.w("Omni", "Syncing ${state.total} steps for ${state.date} failed", cause)
            state
        }
    }

    private companion object {
        /**
         * Roughly three minutes of walking between document writes.
         *
         * The plan's own figure. It bounds the write cost of a long walk to a handful of documents, and
         * the cost of the delay is that another device can be up to 249 steps behind — which is nothing,
         * given it is showing a number from a phone in someone else's pocket.
         */
        const val SyncThresholdSteps = 250
    }
}
