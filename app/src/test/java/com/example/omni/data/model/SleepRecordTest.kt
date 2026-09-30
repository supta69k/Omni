package com.example.omni.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one function that turns two clock times into a length of sleep, and its display companion.
 *
 * [calculateSleepDuration] is the single reusable, overnight-safe calculation the whole feature routes
 * through (adjustment #9): the ViewModel computes the stored `durationMinutes` with it, so a bug here would
 * be a wrong number on the dashboard, in the history and in the weekly average at once. The three worked
 * cases from the brief are pinned as tests so they cannot regress, and the "never negative" guarantee is
 * checked directly because a negative duration would underflow every progress bar it reaches.
 */
class SleepDurationTest {

    // ---- Adjustment #9's three worked cases ------------------------------------------------------

    /** The ordinary night: to bed before midnight, up after it. */
    @Test
    fun `eleven pm to seven am is eight hours`() {
        assertEquals(480, calculateSleepDuration("23:00", "07:00"))
    }

    /** The short nap that still crosses midnight — the case a naive `wake - bed` gets wrong. */
    @Test
    fun `half past eleven to half past midnight is one hour`() {
        assertEquals(60, calculateSleepDuration("23:30", "00:30"))
    }

    /** Both times after midnight, same morning — no wrap at all, and it must not add a spurious day. */
    @Test
    fun `one am to eight am is seven hours`() {
        assertEquals(420, calculateSleepDuration("01:00", "08:00"))
    }

    // ---- The never-negative guarantee ------------------------------------------------------------

    /** Wake earlier on the clock than bed is a full-day rest, not a negative length. */
    @Test
    fun `wake earlier on the clock than bed wraps to a positive length`() {
        // 22:00 → 21:00 is 23 hours, the brief's day-long-rest edge case — and crucially still positive.
        val minutes = calculateSleepDuration("22:00", "21:00")
        assertEquals(1_380, minutes)
        assertTrue("A duration can never be negative", minutes >= 0)
    }

    /** Identical times are zero minutes, not a 24-hour reading. */
    @Test
    fun `the same time twice is zero`() {
        assertEquals(0, calculateSleepDuration("07:00", "07:00"))
    }

    /** A malformed string is 0 rather than a thrown exception the save path would have to catch. */
    @Test
    fun `a malformed time is zero, not a crash`() {
        assertEquals(0, calculateSleepDuration("not-a-time", "07:00"))
        assertEquals(0, calculateSleepDuration("23:00", "99:99"))
        assertEquals(0, calculateSleepDuration("", ""))
    }

    /** Midnight-to-midnight is a whole day, and every branch stays non-negative. */
    @Test
    fun `no pair of valid times ever yields a negative`() {
        val samples = listOf("00:00", "06:30", "12:00", "18:45", "23:59")
        for (bed in samples) {
            for (wake in samples) {
                assertTrue(
                    "$bed -> $wake went negative",
                    calculateSleepDuration(bed, wake) >= 0,
                )
            }
        }
    }
}

/** The dashboard's "8h 0m" rendering — always both units, even when one is zero. */
class SleepFormatDurationTest {

    @Test
    fun `a round eight hours shows its zero minutes`() {
        assertEquals("8h 0m", formatDuration(480))
    }

    @Test
    fun `an hour and a half keeps both parts`() {
        assertEquals("1h 30m", formatDuration(90))
    }

    @Test
    fun `under an hour still leads with a zero hour`() {
        assertEquals("0h 45m", formatDuration(45))
    }

    @Test
    fun `zero is zero of both`() {
        assertEquals("0h 0m", formatDuration(0))
    }
}
