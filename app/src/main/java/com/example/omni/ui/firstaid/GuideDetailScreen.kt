package com.example.omni.ui.firstaid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Guide
import com.example.omni.data.model.GuideSeverity
import com.example.omni.data.model.GuideStep
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.AdaptiveColumnGrid
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniCardSurface
import com.example.omni.ui.theme.OmniFootnote
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSosTrack
import com.example.omni.ui.theme.OmniTheme

/**
 * One guide, open — the step list, its tick boxes and the "call an ambulance if…" warnings
 * (Phase 7, BACKEND_PLAN §11).
 *
 * Same invented-UI rule as [GuidesScreen]: the header is the dashboard's section-title scale, each
 * step is an update card, the tick is the auth screen's own checked mark, and the warnings sit in a
 * card of the SOS track's red with white text — the one true red in the app, spent on the thing that
 * most deserves it.
 *
 * No bottom bar: this is a page *inside* first aid, reached from the list, and the back button plus
 * the system back both return there. Drawing the bar here would light no pill and offer a jump to a
 * tab from a screen the tabs do not know about.
 */
@Composable
fun GuideDetailScreen(
    state: OpenGuideState,
    onToggleStep: (Int) -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = ScreenPadding),
        ) {
            Spacer(Modifier.height(HeaderTopGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BackRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(BackButtonSize)
                        .clip(RoundedCornerShape(percent = 50))
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

                // Ellipsised rather than clipped: "Severe Bleeding (Arterial)" is longer than the
                // row, and a title cut mid-letter hides that anything was dropped
                // (UI_ARCHITECTURE.md §6 rule 8).
                Text(
                    text = state.guide.title,
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(PercentGap))

            Text(
                text = "${state.percent}% read",
                style = HomeType.Caption12,
                color = OmniFootnote,
                maxLines = 1,
            )

            Spacer(Modifier.height(IntroGap))

            Text(
                text = state.guide.intro,
                style = HomeType.CardFootnoteWrapped,
                color = OmniInk,
            )

            Spacer(Modifier.height(StepsGap))

            // The numbered steps. Portrait stacks them, byte-identical to the plain `Column` this
            // was; landscape deals them round-robin into two columns so the wide content pane
            // carries two steps abreast instead of one at the left gutter. The intro paragraph and
            // warning card above and below stay single-column — they're not lists, and forcing them
            // into the grid would only break the way they read.
            AdaptiveColumnGrid(
                items = state.guide.steps,
                verticalSpacing = StepGap,
                horizontalSpacing = StepGap,
            ) { step ->
                StepCard(
                    step = step,
                    done = step.order in state.completed,
                    onToggle = { onToggleStep(step.order) },
                )
            }

            Spacer(Modifier.height(WarningGap))

            WarningCard(warnings = state.guide.warnings)

            Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
        }
    }
}

/**
 * One numbered step: a tick box on the left, the step's title and detail on the right.
 *
 * The tick is the auth screen's checked mark (`ic_auth_check`) inside the update card's grey, flipping
 * to the ink colour when the step is done — the same inversion the bottom bar's selected pill makes.
 */
@Composable
private fun StepCard(step: GuideStep, done: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniCardSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = StepPadding, vertical = StepVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(StepGapInside),
        verticalAlignment = Alignment.Top,
    ) {
        TickBox(done = done)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StepTitleGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${step.order}. ${step.title}",
                    style = HomeType.CardTitle,
                    color = if (done) OmniFootnote else OmniCardInk,
                    maxLines = 2,
                )
            }
            Text(
                text = step.detail,
                style = HomeType.CardFootnoteWrapped,
                color = if (done) OmniFootnote else OmniInk,
            )
        }
    }
}

/** 24 square: grey with a faint outline when open, filled ink with the white check when done. */
@Composable
private fun TickBox(done: Boolean) {
    Box(
        modifier = Modifier
            .size(TickSize)
            .clip(CircleShape)
            .background(if (done) OmniInk else OmniBackground)
            .border(1.dp, TickIdleRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Image(
                painter = painterResource(R.drawable.ic_auth_check),
                contentDescription = null,
                modifier = Modifier.size(TickGlyph),
            )
        }
    }
}

/**
 * The red card at the foot — every guide's "call an ambulance if…" sentences, which the intro of an
 * Emergency guide also alludes to. White text on the SOS red, because the palette has no other red
 * and this is the one place the app is allowed to shout.
 */
@Composable
private fun WarningCard(warnings: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniSosTrack)
            .padding(horizontal = WarningPadding, vertical = WarningVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(WarningGapInside),
    ) {
        Text(
            text = "Call an ambulance",
            style = HomeType.CardTitle,
            color = OmniOnInk,
            maxLines = 1,
        )
        warnings.forEach { line ->
            Text(
                text = line,
                style = HomeType.CardFootnoteWrapped,
                color = OmniOnInk,
            )
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------

private val ScreenPadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val PercentGap = 12.dp
private val IntroGap = 8.dp
private val StepsGap = 20.dp
private val StepGap = 10.dp
private val StepPadding = 15.dp
private val StepVerticalPadding = 14.dp
private val StepGapInside = 12.dp
private val StepTitleGap = 4.dp
private val TickSize = 24.dp
private val TickGlyph = 16.dp
private val WarningGap = 24.dp
private val WarningPadding = 15.dp
private val WarningVerticalPadding = 14.dp
private val WarningGapInside = 6.dp

/** The ring an unticked box carries: the footnote grey at 40%, the palette's own alpha idiom. */
private val TickIdleRing = OmniFootnote.copy(alpha = 0.4f)

/** The list screen's own clearance, reused so both pages scroll to the same depth. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun GuideDetailScreenPreview() {
    OmniTheme {
        GuideDetailScreen(
            state = OpenGuideState(
                guide = Guide(
                    id = "cpr",
                    title = "CPR",
                    category = "Cardiac",
                    severity = GuideSeverity.Emergency,
                    intro = "Cardiopulmonary resuscitation keeps blood flowing to the brain and heart.",
                    steps = listOf(
                        GuideStep(1, "Check and call", "Check the person is unresponsive and not breathing normally."),
                        GuideStep(2, "Chest compressions", "Push hard and fast in the centre of the chest."),
                    ),
                    warnings = listOf("Call an ambulance if the person is unresponsive and not breathing normally."),
                ),
                completed = setOf(1),
                percent = 50,
            ),
        )
    }
}
