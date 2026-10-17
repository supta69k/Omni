package com.example.omni.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

/**
 * Omni design system colors adapted for Jetpack Glance home-screen widgets.
 *
 * Provides harmonious light and dark mode colors that mirror Omni's Soft-Bento
 * aesthetic while complying with Android widget rendering limitations.
 */
object OmniWidgetTheme {

    // ---- Water Widget Colors ----
    /** Light: Omni's signature Hydration teal #AFDFDF, Dark: Rich deep teal #182B2B */
    val waterBackground = ColorProvider(
        day = Color(0xFFAFDFDF),
        night = Color(0xFF182B2B),
    )

    /** Light: Pure black #000000, Dark: Clean high-contrast cyan-white #E6F6F6 */
    val waterTextPrimary = ColorProvider(
        day = Color(0xFF000000),
        night = Color(0xFFE6F6F6),
    )

    /** Light: #3E3C3C (OmniStatLabel), Dark: #A2C4C4 */
    val waterTextSecondary = ColorProvider(
        day = Color(0xFF3E3C3C),
        night = Color(0xFFA2C4C4),
    )

    /** Action button fill: Light #302E2E (OmniInk), Dark #284444 */
    val waterButtonBackground = ColorProvider(
        day = Color(0xFF302E2E),
        night = Color(0xFF2E5252),
    )

    /** Action button text */
    val waterButtonText = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFFE6F6F6),
    )

    /** Progress track and indicator */
    val waterProgressTrack = ColorProvider(
        day = Color(0x4DFFFFFF),
        night = Color(0x33FFFFFF),
    )

    val waterProgressFill = ColorProvider(
        day = Color(0xFF302E2E),
        night = Color(0xFF6ED8D8),
    )

    // ---- Food Log Widget Colors ----
    /** Light: Card surface #FFFFFF, Dark: #1E1E22 */
    val foodBackground = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF1E1E22),
    )

    /** Outline stroke for Soft-Bento definition */
    val foodCardStroke = ColorProvider(
        day = Color(0xFFEAE7E7),
        night = Color(0xFF2C2C34),
    )

    /** Light: Pure black #000000, Dark: High-contrast white #F5F5F7 */
    val foodTextPrimary = ColorProvider(
        day = Color(0xFF000000),
        night = Color(0xFFF5F5F7),
    )

    /** Secondary label: #5A5A5A / #A0A0AA */
    val foodTextSecondary = ColorProvider(
        day = Color(0xFF5A5A5A),
        night = Color(0xFFA0A0AA),
    )

    /** Tertiary/hint label: #787878 / #858590 */
    val foodTextMuted = ColorProvider(
        day = Color(0xFF787878),
        night = Color(0xFF858590),
    )

    /** Primary action button fill: #302E2E / #8D84F9 (OmniAuthHeading) */
    val foodButtonBackground = ColorProvider(
        day = Color(0xFF302E2E),
        night = Color(0xFF8D84F9),
    )

    val foodButtonText = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFFFFFFFF),
    )

    /** Quick log row pill surface */
    val foodPillBackground = ColorProvider(
        day = Color(0xFFF5F5F5),
        night = Color(0xFF27272D),
    )

    /** Macro badges */
    val proteinBadge = ColorProvider(
        day = Color(0xFFF996D3),
        night = Color(0xFFD874B2),
    )

    val carbsBadge = ColorProvider(
        day = Color(0xFFB184E1),
        night = Color(0xFF986EC8),
    )

    val fatBadge = ColorProvider(
        day = Color(0xFF8D84F9),
        night = Color(0xFF746CE0),
    )

    val foodProgressTrack = ColorProvider(
        day = Color(0xFFEAE7E7),
        night = Color(0xFF2C2C34),
    )

    val foodProgressFill = ColorProvider(
        day = Color(0xFF8D84F9),
        night = Color(0xFF8D84F9),
    )
}
