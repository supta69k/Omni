package com.example.omni.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one invariant the rotation fix rests on: a phone renders the artboard at the **same** scale in
 * portrait and in landscape.
 *
 * `DesignFrame` keys its density to the short edge, `min(maxWidth, maxHeight) / 415`. Before the fix it
 * keyed to `maxWidth`, so rotating a 393×851 phone to 851×393 changed the scale from 393/415 (≈0.95) to
 * 851/415 (≈2.05) — a ~2× blow-up of a tall design into a short viewport, the "explosion". These tests
 * pin the short-edge behaviour so that regression cannot return silently: it is invisible in a compile
 * and only shows on a physical rotate, which is exactly the kind of bug this file exists to catch.
 */
class DesignFrameTest {

    @Test
    fun `portrait scales the artboard down to the phone's width`() {
        // A 393-wide phone: one design dp becomes 393/415 physical dp, so the 415 artboard fills it.
        assertEquals(393f / 415f, designFrameScale(maxWidth = 393.dp, maxHeight = 851.dp), Tolerance)
    }

    @Test
    fun `landscape resolves to the same scale as portrait, not the long edge`() {
        val portrait = designFrameScale(maxWidth = 393.dp, maxHeight = 851.dp)
        val landscape = designFrameScale(maxWidth = 851.dp, maxHeight = 393.dp)

        // The whole point: the same physical phone renders identically both ways up. The long edge
        // (851) never drives the scale, so the artboard is letterboxed in landscape rather than blown up.
        assertEquals(portrait, landscape, Tolerance)
        assertEquals(393f / 415f, landscape, Tolerance)
    }

    @Test
    fun `a phone exactly the artboard width scales by one`() {
        assertEquals(1f, designFrameScale(maxWidth = 415.dp, maxHeight = 900.dp), Tolerance)
    }

    @Test
    fun `a tablet short-edge wider than the artboard scales up rather than reflowing`() {
        // 800-wide short edge → the artboard grows and letterboxes; documented, deliberate behaviour.
        assertEquals(800f / 415f, designFrameScale(maxWidth = 1280.dp, maxHeight = 800.dp), Tolerance)
    }

    @Test
    fun `designWindow portrait is not landscape and keeps the 415 artboard width`() {
        val window = designWindow(maxWidth = 393.dp, maxHeight = 851.dp)

        assertEquals(false, window.isLandscape)
        // widthDp = maxWidth / budget = 393 / (393/415) = 415 design-dp, exactly the artboard.
        assertEquals(415f, window.widthDp.value, Tolerance)
        // heightDp = maxHeight / budget, the design-dp tall edge (≈899).
        assertEquals(851f / (393f / 415f), window.heightDp.value, Tolerance)
    }

    @Test
    fun `designWindow landscape flags true and hands the long edge back as width`() {
        val window = designWindow(maxWidth = 851.dp, maxHeight = 393.dp)

        assertEquals(true, window.isLandscape)
        // The short edge (393) still drives the scale, so the long edge widens to ≈899 design-dp —
        // the extra room landscape screens reflow into instead of letterboxing.
        assertEquals(851f / (393f / 415f), window.widthDp.value, Tolerance)
        assertEquals(415f, window.heightDp.value, Tolerance)
    }

    @Test
    fun `designWindow square counts as portrait`() {
        // `maxWidth > maxHeight` is the single source of truth; a square is not landscape.
        assertEquals(false, designWindow(maxWidth = 500.dp, maxHeight = 500.dp).isLandscape)
    }

    private companion object {
        const val Tolerance = 0.0001f
    }
}
