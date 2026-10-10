package com.example.omni.data.local

import java.time.LocalDate
import kotlin.math.abs

/**
 * Everything the app has to remember between two readings of the step counter.
 *
 * `Sensor.TYPE_STEP_COUNTER` reports **steps since the device last booted**, not steps today, and it
 * resets to zero on every reboot. So the raw number is useless on its own: turning it into "steps
 * today" needs an anchor to subtract and a running total to add to, and both have to survive the
 * process — otherwise every app launch would start the day again at zero.
 *
 * @property bootId when the current boot began, as wall-clock millis (`currentTimeMillis −
 *   elapsedRealtime`). Constant for as long as the device stays up, which is what makes it a boot
 *   identity; a reboot moves it.
 * @property anchorRaw the last raw sensor value that has already been counted into [total].
 * @property total steps counted for [date] so far.
 * @property syncedTotal the value of [total] that has been written to Firestore. The difference is
 *   what still owes a write, which is how the sync policy avoids one document write per step.
 * @property date the ISO day [total] belongs to. An empty string means "nothing recorded yet".
 */
data class StepState(
    val bootId: Long = 0L,
    val anchorRaw: Int = 0,
    val total: Int = 0,
    val syncedTotal: Int = 0,
    val date: String = "",
)

/**
 * Folds one raw sensor reading into the state — the only place the reboot rule lives.
 *
 * Four cases, in this order:
 *
 *  1. **The day changed** (including the very first reading ever): today starts at zero whatever the
 *     sensor says, and the new day owes no writes yet. Callers must flush the outgoing day *before*
 *     calling this, because this is where its total is dropped.
 *  2. **Not yet anchored on this device** ([bootId] still 0): adopt [raw] as the anchor and add
 *     nothing. This is a state that already has today's date but has never seen a sensor reading —
 *     which is exactly what [DeviceStepsRepository] seeds when an account is first read on this phone
 *     (from Firestore, or from empty). Without this case that first reading would fall through to
 *     *Normal* and gain `raw − 0` — the **entire since-boot count**, a device-global number the account
 *     never walked. That is how one account's steps used to bleed into the next on the same phone: the
 *     boot counter is shared, so every freshly-seeded account converged on the same value. Any total the
 *     seed carried (a day already part-walked on another device) is kept — only the anchor is set.
 *  3. **The device rebooted**: the counter restarted at 0, so the anchor has to as well, and the whole
 *     of [raw] is new.
 *  4. **Normal**: add the delta since the last reading.
 *
 * BACKEND_PLAN §11 Phase 4 sketches case 2 as "if the raw value is lower than the stored baseline,
 * assume a reboot and set the baseline to 0". That is tightened here to require **both** a changed
 * [bootId] *and* a raw value below [StepState.anchorRaw], because either signal alone lies:
 *
 *  - `bootId` is derived from the wall clock, so an NTP correction of a few seconds moves it without a
 *    reboot. Trusting it alone would zero the anchor and add the entire boot-long raw count again —
 *    inflating the day by thousands of steps. The tolerance below exists for exactly that drift.
 *  - `raw < anchorRaw` alone is also a sensor glitch on some devices, and re-anchoring on it would
 *    double-count just as badly.
 *
 * The cost of demanding both is an *under*-count in one narrow case: a reboot after very short uptime,
 * where the post-reboot raw count has already passed the old anchor before the app looks again. Losing
 * a few steps is the right side to err on — a step total that jumps by 4,000 for no reason is the bug
 * users would actually notice.
 */
fun StepState.reconcile(raw: Int, bootId: Long, date: String): StepState {
    if (date != this.date) {
        // Day rollover. When the anchor still describes this boot, carry it rather than re-anchoring:
        // the counter's gain since the last reading belongs to the new day — the midnight boundary
        // inside that delta cannot be split without per-step timestamps — and re-anchoring here is
        // what threw overnight steps away. A reboot or a never-anchored state starts the day fresh.
        if (this.date.isNotEmpty() && sameBootAs(bootId) && isNextCalendarDay(this.date, date)) {
            return copy(
                date = date,
                anchorRaw = raw,
                total = (raw - anchorRaw).coerceAtLeast(0),
                syncedTotal = 0,
            )
        }
        return StepState(bootId = bootId, anchorRaw = raw, total = 0, syncedTotal = 0, date = date)
    }

    // Case 2: seeded for today but never anchored on this device. Take raw as the anchor and gain
    // nothing, keeping whatever total the seed carried. This is the account-isolation fix — see the
    // KDoc. A real bootId is `currentTimeMillis − elapsedRealtime`, so 0 can only be the seed's default.
    if (this.bootId == 0L) {
        return copy(bootId = bootId, anchorRaw = raw)
    }

    val rebooted = abs(bootId - this.bootId) > BootDriftToleranceMillis && raw < anchorRaw
    val anchor = if (rebooted) 0 else anchorRaw

    // Still clamped: a backwards reading with no reboot detected contributes nothing rather than
    // wrapping negative.
    val gained = (raw - anchor).coerceAtLeast(0)
    return copy(bootId = bootId, anchorRaw = raw, total = total + gained)
}

/** Steps recorded for [date], or 0 when the stored day is a different one. */
fun StepState.stepsOn(date: String): Int = if (this.date == date) total else 0

/** How much [total] owes Firestore. Never negative — a remote total ahead of ours owes nothing. */
fun StepState.pendingSync(): Int = (total - syncedTotal).coerceAtLeast(0)

/**
 * The state a session starts with when the stored day is over and Firestore's number for the new
 * day ([serverSteps]) has been read.
 *
 * The case that matters is the **immediate rollover on the same boot** — the normal morning, on any
 * phone that kills background apps. The counter has kept climbing the whole time the process was
 * dead: the tail of yesterday after its final sync, plus everything walked this morning. With no
 * per-step timestamps that delta cannot be split, and it is owed to the *new* day. Carrying the
 * anchor forward credits it on the first reading; re-anchoring at the first reading — the old
 * behaviour — threw all of it away, which is why mornings used to start at zero and only count
 * again after the app had been opened once.
 *
 * The anchor is carried only when all three hold: the state was actually anchored ([bootId] not
 * 0), the anchor belongs to the **current boot** (a reboot wiped the counter's continuity —
 * steps walked between a reboot and the next open are unrecoverable without a foreground
 * service), and the stored day is the **immediately previous** calendar day. A longer gap cannot
 * be split honestly: a week of walking credited to one morning is worse than a gap.
 */
fun StepState.seedForRollover(today: String, serverSteps: Int, nowBootId: Long): StepState {
    val carriesAnchor = sameBootAs(nowBootId) && isNextCalendarDay(date, today)
    return if (carriesAnchor) {
        copy(date = today, total = serverSteps, syncedTotal = serverSteps)
    } else {
        StepState(date = today, total = serverSteps, syncedTotal = serverSteps)
    }
}

/** True when this state's anchor was taken on the boot [bootId] describes (and exists at all). */
fun StepState.sameBootAs(bootId: Long): Boolean =
    this.bootId != 0L && abs(this.bootId - bootId) <= BootDriftToleranceMillis

/** True when [later] is exactly one calendar day after [earlier]; both are ISO yyyy-MM-dd. */
private fun isNextCalendarDay(earlier: String, later: String): Boolean = runCatching {
    LocalDate.parse(later).toEpochDay() - LocalDate.parse(earlier).toEpochDay() == 1L
}.getOrDefault(false)

/**
 * How far the derived boot timestamp may wander before it counts as a different boot.
 *
 * `currentTimeMillis − elapsedRealtime` is stable to the millisecond within a boot, so any slack at all
 * is enough for jitter between the two clock reads; ten seconds also absorbs a small NTP correction.
 * A user who manually changes the device clock by more than that, on a device that has also rebooted,
 * is the only way to fool this.
 */
private const val BootDriftToleranceMillis = 10_000L
