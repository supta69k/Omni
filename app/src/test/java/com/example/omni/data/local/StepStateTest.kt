package com.example.omni.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The step counter's reboot rule (BACKEND_PLAN §11 Phase 4).
 *
 * `Sensor.TYPE_STEP_COUNTER` reports steps *since boot*, so every number the app shows is derived
 * from an anchor and a running total. The two failure modes are both silent and both awful — a day
 * that resets itself, and a day that inflates by thousands — and neither is visible in a compile or
 * in a five-minute walk around a room. They are visible here.
 */
class StepStateTest {

    // ---- the ordinary case ---------------------------------------------------------------------

    @Test
    fun `the first reading of a day starts at zero whatever the sensor says`() {
        val state = StepState().reconcile(raw = 4_200, bootId = Boot, date = Today)

        assertEquals(0, state.total)
        assertEquals(4_200, state.anchorRaw)
        assertEquals(Today, state.date)
        assertEquals(0, state.syncedTotal)
    }

    @Test
    fun `later readings add the delta since the last one`() {
        val state = StepState()
            .reconcile(raw = 4_200, bootId = Boot, date = Today)
            .reconcile(raw = 4_250, bootId = Boot, date = Today)
            .reconcile(raw = 4_400, bootId = Boot, date = Today)

        assertEquals(200, state.total)
        assertEquals(4_400, state.anchorRaw)
    }

    /** Standing still re-reports the same value; nothing changes, which is what lets the caller skip a write. */
    @Test
    fun `a repeated reading changes nothing`() {
        val walked = StepState()
            .reconcile(raw = 4_200, bootId = Boot, date = Today)
            .reconcile(raw = 4_400, bootId = Boot, date = Today)

        assertEquals(walked, walked.reconcile(raw = 4_400, bootId = Boot, date = Today))
    }

    // ---- the day boundary ----------------------------------------------------------------------

    @Test
    fun `a live rollover attributes the since-last-reading tail to the new day without double counting`() {
        val yesterday = StepState()
            .reconcile(raw = 4_200, bootId = Boot, date = Today)
            .reconcile(raw = 12_000, bootId = Boot, date = Today)
            .copy(syncedTotal = 7_800)

        val today = yesterday.reconcile(raw = 12_050, bootId = Boot, date = Tomorrow)

        // The 50 steps walked since yesterday's last reading cannot be split across midnight, so
        // they land on the new day. The anchorRaw updates to the reading raw (12_050) so subsequent
        // readings calculate the delta without double counting.
        assertEquals(50, today.total)
        assertEquals(0, today.syncedTotal)
        assertEquals(12_050, today.anchorRaw)
        assertEquals(Tomorrow, today.date)

        // Walking continues from 12_050: 100 new steps (from 12_050 to 12_150) add on top of the
        // 50 already counted, giving exactly 150 steps total today.
        val walked = today.reconcile(raw = 12_150, bootId = Boot, date = Tomorrow)
        assertEquals(150, walked.total)
        assertEquals(12_150, walked.anchorRaw)
    }

    // ---- account isolation on a shared device --------------------------------------------------
    //
    // The pedometer counts since boot and is shared by every account on the phone. When a new account
    // is read for the first time, `DeviceStepsRepository` seeds a state that already carries today's
    // date (from Firestore, or empty) but has never been anchored — bootId 0. The first sensor reading
    // must adopt that raw value as the anchor and add nothing; the old bug fell through to the normal
    // delta and credited the entire since-boot count to the new account, so every account on the phone
    // converged on the same number.

    @Test
    fun `a seeded same-day account anchors at the sensor and does not inherit the boot count`() {
        // What a fresh account looks like the instant before its first reading: today's date, nothing
        // walked, never anchored (bootId 0).
        val seeded = StepState(date = Today)

        // The device's shared since-boot counter is deep into five figures because a previous account
        // walked all day. The new account must not be handed those steps.
        val after = seeded.reconcile(raw = 5_100, bootId = Boot, date = Today)

        assertEquals(0, after.total)
        assertEquals(5_100, after.anchorRaw)
        assertEquals(Boot, after.bootId)
    }

    @Test
    fun `a seeded account keeps its cross-device total but not the boot count`() {
        // This account already logged 800 steps today on another phone; the seed carries that total.
        val seeded = StepState(date = Today, total = 800, syncedTotal = 800)

        val anchored = seeded.reconcile(raw = 5_100, bootId = Boot, date = Today)
        assertEquals(800, anchored.total)      // the other device's steps survive
        assertEquals(5_100, anchored.anchorRaw) // and this device anchors here, adding nothing

        // Walking 40 steps on this device now accrues on top of the 800, not on top of the boot count.
        val walked = anchored.reconcile(raw = 5_140, bootId = Boot, date = Today)
        assertEquals(840, walked.total)
    }

    @Test
    fun `two accounts seeded from the same shared counter stay independent`() {
        // Both read the same physical sensor value in turn; neither may pick up the other's total.
        val a = StepState(date = Today, total = 5_000, syncedTotal = 5_000)
            .reconcile(raw = 9_000, bootId = Boot, date = Today)
        val b = StepState(date = Today) // fresh account, nothing logged anywhere
            .reconcile(raw = 9_000, bootId = Boot, date = Today)

        assertEquals(5_000, a.total)
        assertEquals(0, b.total)
    }

    // ---- the reboot rule -----------------------------------------------------------------------

    /**
     * Both signals, or it is not a reboot. A raw value below the anchor *and* a boot id that has moved
     * past the drift tolerance — re-anchoring on either alone double-counts a whole boot's steps.
     */
    @Test
    fun `a reboot re-anchors and counts the whole raw value`() {
        val before = StepState()
            .reconcile(raw = 9_000, bootId = Boot, date = Today)
            .reconcile(raw = 9_500, bootId = Boot, date = Today)

        val after = before.reconcile(raw = 120, bootId = Boot + 60_000L, date = Today)

        assertEquals(620, after.total)   // 500 walked before the reboot, 120 since
        assertEquals(120, after.anchorRaw)
    }

    /**
     * The inflation bug the tightened rule exists to prevent: `bootId` is derived from the wall clock,
     * so an NTP correction moves it without a reboot. Trusting it alone would zero the anchor and add
     * the entire boot-long raw count a second time.
     */
    @Test
    fun `a clock correction with no reboot does not add the whole count again`() {
        val before = StepState()
            .reconcile(raw = 9_000, bootId = Boot, date = Today)
            .reconcile(raw = 9_500, bootId = Boot, date = Today)

        val after = before.reconcile(raw = 9_600, bootId = Boot + 60_000L, date = Today)

        assertEquals(600, after.total)
    }

    /** Ten seconds of jitter between two clock reads is not a reboot either. */
    @Test
    fun `boot id drift inside the tolerance is not a reboot`() {
        val before = StepState()
            .reconcile(raw = 9_000, bootId = Boot, date = Today)
            .reconcile(raw = 9_500, bootId = Boot, date = Today)

        val after = before.reconcile(raw = 100, bootId = Boot + 5_000L, date = Today)

        // No reboot detected, so the backwards reading contributes nothing rather than wrapping
        // negative — the day holds at what was actually walked.
        assertEquals(500, after.total)
    }

    /** A sensor glitch on a device that has stayed up must not re-anchor either. */
    @Test
    fun `a backwards reading with no clock move contributes nothing`() {
        val before = StepState()
            .reconcile(raw = 9_000, bootId = Boot, date = Today)
            .reconcile(raw = 9_500, bootId = Boot, date = Today)

        assertEquals(500, before.reconcile(raw = 8_000, bootId = Boot, date = Today).total)
    }

    // ---- the two readers -----------------------------------------------------------------------

    @Test
    fun `steps read zero on any day but the stored one`() {
        val state = StepState(total = 4_000, date = Today)

        assertEquals(4_000, state.stepsOn(Today))
        assertEquals(0, state.stepsOn(Tomorrow))
        assertEquals(0, StepState().stepsOn(Today))
    }

    @Test
    fun `what is owed is the gap between the total and what was written`() {
        assertEquals(250, StepState(total = 1_250, syncedTotal = 1_000).pendingSync())
        assertEquals(0, StepState(total = 1_000, syncedTotal = 1_000).pendingSync())
    }

    /** A remote total ahead of ours owes nothing — never a negative write threshold. */
    @Test
    fun `a synced total ahead of the local one owes nothing`() {
        assertEquals(0, StepState(total = 900, syncedTotal = 1_000).pendingSync())
    }

    // ---- the overnight rollover ----------------------------------------------------------------
    //
    // The process is dead while the day rolls over — every morning, on any phone that kills
    // background apps. The since-boot counter keeps climbing the whole time; the question is what
    // the new day's seed does with the anchor. Re-anchoring at the first reading (the old behaviour)
    // threw away every step taken while the app was closed, which is why mornings used to start at
    // zero and only count again after the app had been opened once.

    @Test
    fun `an immediate rollover on the same boot carries the anchor so the closed-app morning counts`() {
        val yesterday = StepState()
            .reconcile(raw = 4_200, bootId = Boot, date = Today)
            .reconcile(raw = 12_000, bootId = Boot, date = Today) // last reading yesterday evening

        // Firestore has nothing for the new day yet.
        val seeded = yesterday.seedForRollover(today = Tomorrow, serverSteps = 0, nowBootId = Boot)

        assertEquals(Tomorrow, seeded.date)
        assertEquals(0, seeded.total)
        assertEquals(12_000, seeded.anchorRaw) // the old anchor survives the rollover
        assertEquals(Boot, seeded.bootId)

        // The first reading of the morning: everything walked since yesterday evening lands at once.
        val morning = seeded.reconcile(raw = 14_300, bootId = Boot, date = Tomorrow)
        assertEquals(2_300, morning.total)
    }

    @Test
    fun `a rollover more than one day later re-anchors rather than crediting a week of walking`() {
        val last = StepState(date = Today, bootId = Boot, anchorRaw = 12_000, total = 7_800, syncedTotal = 7_800)

        val nextWeek = last.seedForRollover(today = "2026-09-17", serverSteps = 0, nowBootId = Boot)

        // A fresh state: the next reading anchors wherever it lands, and the gap stays a gap.
        assertEquals(0, nextWeek.total)
        assertEquals(0L, nextWeek.bootId)
        assertEquals(0, nextWeek.anchorRaw)
    }

    @Test
    fun `a rollover across a reboot cannot carry the anchor`() {
        val last = StepState(date = Today, bootId = Boot, anchorRaw = 12_000, total = 7_800, syncedTotal = 7_800)

        val seeded = last.seedForRollover(today = Tomorrow, serverSteps = 0, nowBootId = Boot + 60_000L)

        // The counter restarted at zero; there is no continuity to carry, and the next reading
        // anchors fresh. Steps between the reboot and the next open are unrecoverable without a
        // foreground service — the accepted trade.
        assertEquals(0L, seeded.bootId)
        assertEquals(0, seeded.total)
    }

    @Test
    fun `a rollover keeps a cross-device total and drops yesterday's local one`() {
        // Another device synced 300 steps for the new day while this one was closed; yesterday's
        // local total belongs to yesterday's date and must not leak into it.
        val last = StepState(date = Today, bootId = Boot, anchorRaw = 12_000, total = 7_800, syncedTotal = 7_500)

        val seeded = last.seedForRollover(today = Tomorrow, serverSteps = 300, nowBootId = Boot)

        assertEquals(300, seeded.total)
        assertEquals(300, seeded.syncedTotal)
        assertEquals(12_000, seeded.anchorRaw) // but the anchor still carries
    }

    @Test
    fun `a never-anchored state seeds without inventing a boot id`() {
        val seeded = StepState(date = Today).seedForRollover(today = Tomorrow, serverSteps = 0, nowBootId = Boot)

        assertEquals(0, seeded.total)
        assertEquals(0L, seeded.bootId) // the next reading anchors wherever it lands, adding nothing
    }

    // ---- additional accuracy & pipeline regressions --------------------------------------------

    @Test
    fun `a live rollover more than one day later re-anchors rather than crediting a week of walking`() {
        val last = StepState(date = Today, bootId = Boot, anchorRaw = 12_000, total = 7_800, syncedTotal = 7_800)
        val nextWeek = last.reconcile(raw = 15_000, bootId = Boot, date = "2026-09-17")

        // More than one day later: anchors fresh on the new day with 0 steps
        assertEquals(0, nextWeek.total)
        assertEquals(15_000, nextWeek.anchorRaw)
        assertEquals("2026-09-17", nextWeek.date)
    }

    @Test
    fun `increasing cumulative sensor count produces correct daily delta across multiple readings`() {
        var state = StepState().reconcile(raw = 1_000, bootId = Boot, date = Today)
        assertEquals(0, state.total)

        state = state.reconcile(raw = 1_100, bootId = Boot, date = Today)
        assertEquals(100, state.total)

        state = state.reconcile(raw = 1_250, bootId = Boot, date = Today)
        assertEquals(250, state.total)

        state = state.reconcile(raw = 2_000, bootId = Boot, date = Today)
        assertEquals(1_000, state.total)
    }

    @Test
    fun `reopening the app and receiving same sensor reading does not double count`() {
        val state = StepState(date = Today, bootId = Boot, anchorRaw = 5_000, total = 3_000, syncedTotal = 3_000)
        // Reopen app, sensor emits the same raw count (5_000)
        val reopened = state.reconcile(raw = 5_000, bootId = Boot, date = Today)

        assertEquals(3_000, reopened.total)
        assertEquals(5_000, reopened.anchorRaw)
    }

    @Test
    fun `device reboot counter reset does not produce negative count and preserves daily total`() {
        val beforeReboot = StepState(date = Today, bootId = Boot, anchorRaw = 10_000, total = 4_000, syncedTotal = 4_000)
        val newBoot = Boot + 3_600_000L // 1 hour later boot

        // Pedometer counter reset to 15 steps after reboot
        val afterReboot = beforeReboot.reconcile(raw = 15, bootId = newBoot, date = Today)

        assertEquals(4_015, afterReboot.total)
        assertEquals(15, afterReboot.anchorRaw)
        assertEquals(newBoot, afterReboot.bootId)
    }

    @Test
    fun `account switching preserves isolation without leaking step history`() {
        // User A was walking on this device
        val userA = StepState(date = Today, bootId = Boot, anchorRaw = 10_000, total = 5_000, syncedTotal = 5_000)

        // User B signs in on the same device with 0 steps on server
        val userBInitial = StepState(date = Today) // unanchored
        // Device hardware counter is still at 10_000
        val userBAnchored = userBInitial.reconcile(raw = 10_000, bootId = Boot, date = Today)

        assertEquals(0, userBAnchored.total)
        assertEquals(10_000, userBAnchored.anchorRaw)

        // User B walks 200 steps (raw becomes 10_200)
        val userBWalked = userBAnchored.reconcile(raw = 10_200, bootId = Boot, date = Today)
        assertEquals(200, userBWalked.total)

        // User A's total remains unaffected
        assertEquals(5_000, userA.total)
    }

    private companion object {
        const val Boot = 1_757_000_000_000L
        const val Today = "2026-09-10"
        const val Tomorrow = "2026-09-11"
    }
}
