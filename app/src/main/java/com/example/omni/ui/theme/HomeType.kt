package com.example.omni.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Text styles for the home dashboard — Figma frame `iPhone 14 & 15 Pro - 22` (node 1:4).
 *
 * The frame uses about twenty distinct combinations of family, weight, size and leading, far more
 * than Material's fifteen typography slots hold, so they live here as named constants instead of
 * being squeezed into [Typography]. Colour is deliberately not baked in: every value comes from
 * `Color.kt` and is applied at the call site, the same way the auth screens do it.
 *
 * Sizes, line heights and letter spacings are Figma's own numbers, fractions included. Figma
 * reports tracking in px for a specific size; keeping the fraction is what makes a 21sp number and
 * its 12sp unit suffix sit on the same baseline run.
 */
object HomeType {

    // ---- Header (Figma `Frame 87`) ----

    /** "Good morning!", and the "Good"/"Fair"/"Action Needed" status chips. */
    val Caption12 = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.861.sp,
        letterSpacing = (-0.06).sp,
    )

    /** "Sayed Mahir" */
    val HeaderName = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Welcome back Mahir" — the greeting that scrolls with the content. */
    val Welcome = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.24).sp,
    )
    // ---- Emergency first-aid hero card (Figma `Frame 53`) ----

    /** "Emergency first aid guide" */
    val HeroTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.63).sp,
    )

    /** "100% offline access" — the only Light weight anywhere in the app. */
    val HeroCaption = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.12).sp,
    )

    /** "Explore!" */
    val HeroButton = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.14).sp,
    )

    // ---- Hydration and step-count cards (Figma `Frame 52`) ----
    // The two cards are typographically the same idea with slightly different leadings, which is
    // why each one gets its own trio rather than sharing.

    /** "Water today" */
    val WaterLabel = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 12.924.sp,
        letterSpacing = (-0.06).sp,
    )
    /** " 7" in "7/12 glasses" */
    val WaterValue = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 29.373.sp,
        letterSpacing = (-0.188).sp,
    )

    /** "/12 glasses" */
    val WaterUnit = WaterValue.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp)

    /**
     * "keep going only 5 glasses left" — Figma's own 11sp.
     *
     * This one is worth a note because it was briefly 10. Measured straight out of `manrope.ttf` with
     * the `wght` deltas applied the sentence wants 146.86 at 11sp, and the source's box is 142 — so it
     * overflows the *design's* box, not just the phone's. The fix belongs in the layout, not here:
     * the tile is now laid out at its true 166 (see `DesignFrame`) and the text column's end inset
     * drops from 11 to 2, which leaves 151 and the line fits at the design's size.
     */
    val WaterHint = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.861.sp,
        letterSpacing = (-0.055).sp,
    )

    /** "Today's steps" */
    val StepsLabel = WaterLabel.copy(lineHeight = 14.648.sp)

    /** "5,600" */
    val StepsValue = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 33.29.sp,
        letterSpacing = (-0.2131).sp,
    )

    /** "/10,000" */
    val StepsUnit = StepsValue.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp)

    /**
     * "You're 78% towards your daily goal,just keep moving" — spelled out rather than copied from
     * [WaterHint] so the two stay independent; this one is meant to wrap, and in the 110 the tile
     * leaves it Manrope breaks it into the same three lines the design shows.
     */
    val StepsHint = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.551.sp,
        letterSpacing = (-0.055).sp,
    )
    // ---- "Daily Updates" section (Figma `Frame 54`) ----

    val SectionTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
    )

    /** "Wellness" / "Activity" — the unselected tabs, which use the body face. */
    val TabIdle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Recommended" — the selected tab switches to the display face. */
    val TabActive = TabIdle.copy(fontFamily = PlusJakartaSans)

    // ---- Update cards (Figma `Frame 74`) ----

    /** Card headings, and the leaf/moon emoji that sit beside the values. */
    val CardTitle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.32).sp,
    )

    /** "18/g" / "6.5/h" / "0%" */
    val CardValue = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.36).sp,
    )

    /** The "0" and "31 g" ends of each progress axis. */
    val AxisLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.1).sp,
    )
    /** "You're 13g away from your Goal" on the fibre card. */
    val CardFootnote = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 15.861.sp,
        letterSpacing = (-0.14).sp,
    )

    /** The sleep card's version of the same sentence, tracked one hundredth wider in the source. */
    val CardFootnoteSleep = CardFootnote.copy(letterSpacing = (-0.16).sp)

    /** The CPR card's sentence, which wraps to two lines and so has its own leading. */
    val CardFootnoteWrapped = CardFootnote.copy(lineHeight = 17.sp)

    // ---- Bottom navigation (Figma `Frame 76`) ----

    /** "Home" — the only labelled nav item. */
    val NavLabel = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.208).sp,
    )
}
