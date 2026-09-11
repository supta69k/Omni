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
    fun `a new day starts over and owes no writes`() {
        val yesterday = StepState()
            .reconcile(raw = 4_200, bootId = Boot, date = Today)
            .reconcile(raw = 12_000, bootId = Boot, date = Today)
            .copy(syncedTotal = 7_800)

        val today = yesterday.reconcile(raw = 12_050, bootId = Boot, date = Tomorrow)

        assertEquals(0, today.total)
        assertEquals(0, today.syncedTotal)
        assertEquals(12_050, today.anchorRaw)
        assertEquals(Tomorrow, today.date)
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

    private companion object {
        const val Boot = 1_757_000_000_000L
        const val Today = "2026-09-10"
        const val Tomorrow = "2026-09-11"
    }
}
