package com.example.omni.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.theme.OmniCream
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniPeriwinkle
import com.example.omni.ui.theme.OmniPink

/**
 * One onboarding slide.
 *
 * [titleLead] is rendered in the ink colour and [titleAccent] in [accentColor]; splitting the
 * headline this way mirrors how the Figma text node is composed of coloured spans.
 */
data class OnboardingPage(
    val titleLead: String,
    val titleAccent: String,
    val accentColor: Color,
    val body: String,
    @DrawableRes val illustration: Int,
    /**
     * Side length the square artwork is drawn at, straight from the Figma `Illustration` node.
     *
     * Slide 1's node is 296.795dp; slides 2 and 3 are 383dp. The bitmaps behind them are the 1024px
     * originals, so this is a pure downscale — nothing is being stretched up.
     */
    val illustrationSize: Dp,
    /** Page 1 uses a smaller, heavier headline than the other two in the source design. */
    val usesCompactTitle: Boolean = false,
    /** Trailing span painted in a second accent — only page 2 uses this. */
    val titleTail: String = "",
    val tailColor: Color = OmniCream,
)

val onboardingPages = listOf(
    OnboardingPage(
        titleLead = "Master your daily ",
        titleAccent = "Rhythm.",
        accentColor = OmniLavender,
        body = "Hydration, sleep, and meals—tracked effortlessly in one beautiful " +
            "space. Build habits that stick without the stress.",
        illustration = R.drawable.onboarding_wellness,
        illustrationSize = 296.795.dp,
        usesCompactTitle = true,
    ),
    OnboardingPage(
        titleLead = "Real experts, zero ",
        titleAccent = "guesswork",
        accentColor = OmniPeriwinkle,
        titleTail = ".",
        body = "Stop Googling symptoms. Get personalized diet plans and medical " +
            "consultations straight from manually verified experts.",
        illustration = R.drawable.onboarding_experts,
        illustrationSize = 383.dp,
    ),
    OnboardingPage(
        titleLead = "Prepared for the ",
        titleAccent = "unexpected.",
        accentColor = OmniPink,
        body = "Master life-saving skills with our 100% offline first-aid guide, and " +
            "instantly map the nearest top-rated hospitals when every second counts.",
        illustration = R.drawable.onboarding_emergency,
        illustrationSize = 383.dp,
    ),
)
