package com.example.omni.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind the three "Daily updates & Recomindation" cards (BACKEND_PLAN Phase 5 and §3.3).
 *
 * `domain/` is pure Kotlin precisely so it can be exercised here, without a device — and the cases
 * that matter are the ugly ones, because Firestore hands back whatever the console typed. A goal of
 * zero, a negative reading and a `NaN` all have to *mean* something rather than crash a card.
 */
class RecommendationsTest {

    // ---- statusFor -----------------------------------------------------------------------------

    @Test
    fun `status is good from four fifths`() {
        assertEquals(Status.Good, statusFor(0.80f))
        assertEquals(Status.Good, statusFor(0.81f))
        assertEquals(Status.Good, statusFor(1f))
    }

    @Test
    fun `status is fair from two fifths up to the good threshold`() {
        assertEquals(Status.Fair, statusFor(0.40f))
        assertEquals(Status.Fair, statusFor(0.58f))   // the fibre card — see statusFor's KDoc
        assertEquals(Status.Fair, statusFor(0.7999f))
    }

    @Test
    fun `status needs action below two fifths`() {
        assertEquals(Status.ActionNeeded, statusFor(0f))
        assertEquals(Status.ActionNeeded, statusFor(0.3999f))
    }

    /**
     * The documented divergence from Figma, pinned so nobody "fixes" it back.
     *
     * The design pairs 18/31g fibre (58%) with "Good" and 6.5/8h sleep (81%) with "Fair". Those two
     * labels are swapped relative to any consistent threshold; the app adopts the rule and flips them.
     */
    @Test
    fun `the design's own two chips are deliberately flipped`() {
        assertEquals(Status.Fair, statusFor(progressOf(18f, 31f)))
        assertEquals(Status.Good, statusFor(progressOf(6.5f, 8f)))
        assertEquals(Status.ActionNeeded, statusFor(progressOf(0f, 1f)))
    }

    // ---- progressOf ----------------------------------------------------------------------------

    @Test
    fun `progress is the fraction of the goal`() {
        assertEquals(0.5f, progressOf(4f, 8f), Tolerance)
        assertEquals(0.58064f, progressOf(18f, 31f), Tolerance)
    }

    @Test
    fun `progress never exceeds one`() {
        assertEquals(1f, progressOf(12f, 8f), Tolerance)
        assertEquals(1f, progressOf(Float.MAX_VALUE, 8f), Tolerance)
    }

    /** Every one of these would otherwise divide by zero, go negative, or propagate a `NaN` into a bar. */
    @Test
    fun `nonsense readings are no progress rather than a crash`() {
        assertEquals(0f, progressOf(4f, 0f), Tolerance)
        assertEquals(0f, progressOf(4f, -8f), Tolerance)
        assertEquals(0f, progressOf(-4f, 8f), Tolerance)
        assertEquals(0f, progressOf(Float.NaN, 8f), Tolerance)
        assertEquals(0f, progressOf(4f, Float.NaN), Tolerance)
        assertEquals(0f, progressOf(4f, Float.POSITIVE_INFINITY), Tolerance)
    }

    // ---- remainingTo ---------------------------------------------------------------------------

    @Test
    fun `what is still owed is rounded to the tenth the card prints`() {
        assertEquals(13f, remainingTo(18f, 31f), Tolerance)
        assertEquals(1.5f, remainingTo(6.5f, 8f), Tolerance)
    }

    /**
     * The reason the rounding lives in `remainingTo` and not at the call site: 0.04g away must come
     * back as exactly zero, so the footnote can say the goal is met instead of "0g away from your Goal".
     */
    @Test
    fun `a hair under the goal rounds to zero owed`() {
        assertEquals(0f, remainingTo(30.96f, 31f), Tolerance)
    }

    @Test
    fun `past the goal nothing is owed`() {
        assertEquals(0f, remainingTo(40f, 31f), Tolerance)
        assertEquals(0f, remainingTo(Float.NaN, 31f), Tolerance)
        assertEquals(0f, remainingTo(18f, Float.NaN), Tolerance)
    }

    // ---- progressBarOf -------------------------------------------------------------------------

    /**
     * §3.3's giveaway: the gradient segment never changes width, it only slides. The three parts
     * therefore always sum to the track, whatever the progress.
     */
    @Test
    fun `the three parts always sum to the track`() {
        val widths = listOf(0f, 0.25f, 0.58f, 1f).map { progressBarOf(it) }
        val track = widths.first().let { it.lead + it.fill + it.tail }
        widths.forEach { bar ->
            assertEquals(track, bar.lead + bar.fill + bar.tail, Tolerance)
            assertEquals(widths.first().fill, bar.fill, Tolerance)
        }
    }

    /** The CPR card, straight out of §3.3's table: `0 | 72.6416 | 281.3584`. */
    @Test
    fun `the CPR card is reproduced exactly`() {
        val bar = progressBarOf(0f)
        assertEquals(0f, bar.lead, Tolerance)
        assertEquals(72.6416f, bar.fill, Tolerance)
        assertEquals(281.3584f, bar.tail, Tolerance)
    }

    @Test
    fun `a met goal leaves no tail`() {
        val bar = progressBarOf(1f)
        assertEquals(0f, bar.tail, Tolerance)
        assertEquals(281.3584f, bar.lead, Tolerance)
    }

    /**
     * The marker rides the puck's *centre*, which is why it can never reach either edge — 10.3% at
     * zero and 89.7% at one. No clamping keeps the triangle on the card; this does.
     */
    @Test
    fun `the marker stays on the card at both extremes`() {
        assertEquals(0.1026f, progressBarOf(0f).markerFraction, 0.001f)
        assertEquals(0.8974f, progressBarOf(1f).markerFraction, 0.001f)
    }

    @Test
    fun `out of range progress is clamped rather than drawn off the track`() {
        assertEquals(progressBarOf(0f), progressBarOf(-3f))
        assertEquals(progressBarOf(1f), progressBarOf(4f))
        assertEquals(progressBarOf(0f), progressBarOf(Float.NaN))
    }

    // ---- formatAmount --------------------------------------------------------------------------

    @Test
    fun `a whole number never prints a bare decimal`() {
        assertEquals("18", formatAmount(18f))
        assertEquals("8", formatAmount(8.0f))
        assertEquals("0", formatAmount(0f))
    }

    @Test
    fun `one decimal at most`() {
        assertEquals("6.5", formatAmount(6.5f))
        assertEquals("1.5", formatAmount(1.5f))
        assertEquals("18.1", formatAmount(18.14f))
        assertEquals("18.2", formatAmount(18.15f))
    }

    /**
     * A rounding that lands on a whole number must print as one — 7.98 is "8", never "8.0", or it no
     * longer fits the box the design measured for it.
     */
    @Test
    fun `a value that rounds up to a whole number prints whole`() {
        assertEquals("8", formatAmount(7.98f))
    }

    @Test
    fun `a non-finite amount is zero rather than the word NaN`() {
        assertEquals("0", formatAmount(Float.NaN))
        assertEquals("0", formatAmount(Float.POSITIVE_INFINITY))
    }

    /**
     * The decimal separator is a full stop on every phone, whatever its locale — the boxes are
     * measured for "6.5 h", and a comma-decimal locale would otherwise stop matching the axis labels
     * printed above the value.
     */
    @Test
    fun `the decimal separator does not follow the phone's locale`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertTrue(formatAmount(6.5f) == "6.5")
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    private companion object {
        /** Floats compared to within a hundredth of a dp — closer than any screen can draw. */
        const val Tolerance = 0.01f
    }
}
