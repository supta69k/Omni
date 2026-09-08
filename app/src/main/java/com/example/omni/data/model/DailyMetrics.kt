package com.example.omni.data.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One day of a user's own numbers — the `users/{uid}/days/{yyyy-MM-dd}` document (BACKEND_PLAN §7).
 *
 * Only what happened is stored here; the *targets* live on the account ([User.waterGoal]), so editing a
 * goal cannot rewrite yesterday. The document is created lazily by the first write of the day, which is
 * why every field has a default and why an absent document is not an error — it is a day with nothing
 * logged yet.
 *
 * Everything the dashboard reads for a day lives in this one document rather than in parallel ones,
 * because the cards are rendered together and Firestore charges per document read.
 *
 * [steps] is the *cross-device* record, not this phone's live count: it is written from the device's own
 * bookkeeping every few hundred steps (see `DeviceStepsRepository`), so it lags behind on the phone doing
 * the walking and is the only source on any other device.
 *
 * [sleepHours] and [fiberGrams] feed the two update cards that have no automatic source. Sleep is typed
 * in by hand (`SleepEntrySheet`); fibre is a roll-up of the day's meals and so reads 0 until Phase 6
 * starts writing it — which is the honest reading of "no food logged", not a placeholder. The Firestore
 * field behind [fiberGrams] is called `fiber` (BACKEND_PLAN §7); the Kotlin name carries its unit because
 * the goal beside it does too ([User.fiberGoal]).
 */
data class DailyMetrics(
    val date: String,
    val waterGlasses: Int = 0,
    val steps: Int = 0,
    val sleepHours: Float = 0f,
    val fiberGrams: Float = 0f,
)

/**
 * Today's document id.
 *
 * [LocalDate.toString] is ISO-8601 (`yyyy-MM-dd`) by contract, not by locale, so this is safe to use as
 * a key on a device set to any calendar or language — which a hand-rolled `SimpleDateFormat` pattern
 * would not be. It is the *device's* midnight, deliberately: a day is where the user is standing.
 */
fun todayKey(): String = LocalDate.now().toString()

/**
 * [todayKey], but it does not go stale.
 *
 * An app left open past midnight would otherwise keep reading and writing yesterday's document — the
 * water card would show yesterday's count and the plus button would add to it. This emits the current
 * key immediately, then sleeps until the next local midnight and emits again, so both the listener and
 * the write target move on their own.
 *
 * Two consumers need this to agree (the read and the write), so the rule lives here rather than in a
 * ViewModel. Sleeping to the boundary rather than polling costs one wake-up a day.
 */
fun todayKeyFlow(): Flow<String> = flow {
    while (true) {
        emit(todayKey())
        delay(millisUntilMidnight())
    }
}

private fun millisUntilMidnight(): Long {
    val now = LocalDateTime.now()
    val midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT)
    // A one-second floor: a boundary landing inside the same millisecond would spin the loop, and a
    // clock the user drags backwards would otherwise produce a negative delay.
    return Duration.between(now, midnight).toMillis().coerceAtLeast(1_000L)
}
