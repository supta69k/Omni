package com.example.omni.ui.firstaid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.RawRes
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.data.model.Guide
import com.example.omni.data.model.GuideSeverity
import com.example.omni.data.model.GuideStep
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.theme.BodyFont
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.PlusJakartaSans

/**
 * The animated first-aid guide — Figma's redesign of the plain tick-box [GuideDetailScreen], used by
 * the emergency guides that have a hero animation: CPR (276-6239), Choking (304-867) and Severe
 * Bleeding (304-1025). Every one of those frames is the same layout with a different hero and different
 * copy, so they are one screen here, parameterised by [heroRes]; the router picks the guide's GIF.
 *
 * The content is still the [Guide] the repository holds — title, intro, numbered steps and warnings are
 * read from `state.guide`, never hard-coded — so the words on screen and the guide document cannot
 * drift apart. The two things this screen adds over the generic one are the looping hero animation (a
 * bundled GIF, played by Coil's GIF decoder — see `OmniApplication`) and the larger editorial type. The
 * red "Call an ambulance" card is [WarningCard], reused verbatim from the detail screen so the two
 * pages cannot render that card differently.
 *
 * No tick boxes and no "% read": the redesign is a thing you read in an emergency, not a checklist, so
 * it takes no [OpenGuideState.completed]/`onToggleStep` — progress is simply not part of this page.
 */
@Composable
fun AnimatedGuideScreen(
    state: OpenGuideState,
    @RawRes heroRes: Int,
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            Spacer(Modifier.height(HeaderTopGap))

            // Just the arrow, no title row: the design puts the name in the body below the hero. The
            // 40dp tap target is inset so the 24dp glyph lands on Figma's x=21.
            Box(
                modifier = Modifier
                    .padding(start = BackRowInset)
                    .size(BackButtonSize)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_set_arrow_right),
                    contentDescription = "Back to the guides",
                    modifier = Modifier
                        .size(24.dp)
                        .scale(scaleX = -1f, scaleY = 1f),
                )
            }

            Spacer(Modifier.height(HeroTopGap))

            // The hero animation — full-bleed, the design's 416:310 aspect. It is a GIF, decoded and
            // looped by the GIF decoder registered on the app's singleton ImageLoader; without that it
            // would show only its first frame.
            AsyncImage(
                model = heroRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HeroAspect),
            )

            Spacer(Modifier.height(HeroToBodyGap))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BodyPadding),
                verticalArrangement = Arrangement.spacedBy(BlockGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
                    Text(
                        text = state.guide.title,
                        style = TextStyle(
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            lineHeight = 24.sp,
                            letterSpacing = (-0.48).sp,
                        ),
                        color = OmniAuthHeading,
                    )
                    Text(
                        text = state.guide.intro,
                        style = TextStyle(
                            fontFamily = BodyFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.36).sp,
                        ),
                        color = CprIntroInk,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(StepGap)) {
                    state.guide.steps.forEach { step ->
                        StepBlock(step)
                    }
                }
            }

            Spacer(Modifier.height(WarningGap))

            Box(modifier = Modifier.padding(horizontal = BodyPadding)) {
                // The same red "Call an ambulance" card as the plain guide screen, kept at the bottom.
                WarningCard(warnings = state.guide.warnings)
            }

            Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
        }
    }
}

/** One numbered step — a medium title over a regular body, both left-aligned, no tick box. */
@Composable
private fun StepBlock(step: GuideStep) {
    Column(verticalArrangement = Arrangement.spacedBy(StepTitleGap)) {
        Text(
            text = "${step.order}. ${step.title}",
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.38).sp,
            ),
            color = OmniInk,
        )
        Text(
            text = step.detail,
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.16).sp,
            ),
            color = CprStepBodyInk,
        )
    }
}

// ---- Geometry (Figma 276-6239 / 304-867 / 304-1025) --------------------------------------------

private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
/** 13 + (40−24)/2 lands the 24dp arrow glyph on the design's x=21. */
private val BackRowInset = 13.dp
private val HeroTopGap = 8.dp
private const val HeroAspect = 416f / 310f
private val HeroToBodyGap = 28.dp
/** (415 − 372)/2 ≈ 22 — the design's body inset either side of the full-bleed hero. */
private val BodyPadding = 22.dp
private val BlockGap = 29.dp
private val HeadingGap = 18.dp
private val StepGap = 24.dp
private val StepTitleGap = 6.dp
private val WarningGap = 29.dp
private val ContentBottomGap = 24.dp

/** #787878 — the intro grey, the design's own value. */
private val CprIntroInk = Color(0xFF787878)

/** #444444 — the step-body grey. */
private val CprStepBodyInk = Color(0xFF444444)

@DevicePreviews
@Composable
private fun AnimatedGuideScreenPreview() {
    OmniTheme {
        AnimatedGuideScreen(
            heroRes = R.raw.cpr_hero,
            state = OpenGuideState(
                guide = Guide(
                    id = "cpr",
                    title = "CPR",
                    category = "Cardiac",
                    severity = GuideSeverity.Emergency,
                    intro = "Cardiopulmonary resuscitation keeps blood flowing to the brain and heart of " +
                        "a person whose heart has stopped.",
                    steps = listOf(
                        GuideStep(1, "Check and call", "Check the person is unresponsive and not " +
                            "breathing normally. Send someone to call an ambulance, or call yourself."),
                        GuideStep(2, "Chest compressions", "Push hard and fast in the centre of the " +
                            "chest: 5–6 cm down, at 100–120 per minute."),
                    ),
                    warnings = listOf("Call an ambulance if the person is unresponsive and not " +
                        "breathing normally."),
                ),
                completed = emptySet(),
                percent = 0,
            ),
        )
    }
}
