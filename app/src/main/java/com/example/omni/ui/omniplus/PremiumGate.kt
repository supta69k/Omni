package com.example.omni.ui.omniplus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.revenuecat.EntitlementIds
import com.example.omni.data.revenuecat.PreviewRevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatRepository
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardBody
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.SettingsType

/**
 * Shows [content] only to an Omni+ subscriber, and otherwise shows the way in.
 *
 * **This frame does not exist in Figma** (§6 rule 11), so it is the settings page's healthcare card
 * with its own words: the #F5F5F5 tray at radius 13, that page's 24dp `ic_set_doctor` glyph, its
 * centred [SettingsType.CardTitle] / `CardBody` pair, and the [OmniSetApply] pill it ends with. The
 * previous version navigated away *during composition* and drew nothing, which is both a side effect
 * in the wrong place and a blank screen on the way past; this one is an ordinary page that waits for
 * a tap.
 *
 * The three RevenueCat states each get their own sentence rather than collapsing into one, because
 * "still asking the store" and "the store said no" are different situations for the person reading:
 * one resolves itself, the other needs the button.
 */
@Composable
fun PremiumGate(
    revenueCatRepository: RevenueCatRepository,
    onNavigateToPaywall: () -> Unit,
    content: @Composable () -> Unit,
) {
    val subscriptionState by revenueCatRepository.subscriptionState.collectAsState()

    when (val current = subscriptionState) {
        is RevenueCatState.Active -> content()

        // Still asking the store. No spinner: Omni's loading states are a sentence (see
        // `MessagesScreen`, `GoalsScreen`), and a gate that flashes a spinner then a pitch reads worse
        // than one that says what it is doing.
        is RevenueCatState.Loading -> GatePage(
            title = "Checking your subscription",
            body = "One moment — Omni is confirming your Omni+ access with the store.",
            action = null,
            onAction = {},
        )

        is RevenueCatState.Error -> GatePage(
            title = "Couldn't check your subscription",
            body = current.message.ifBlank {
                "Omni couldn't reach the store. Check your connection and try again."
            },
            action = "See Omni+",
            onAction = onNavigateToPaywall,
            tone = GateTone.Error,
        )

        else -> GatePage(
            title = "Part of Omni+",
            body = "Verified doctor consultations are an Omni+ feature. Subscribe to message a " +
                "licensed doctor and keep the thread in Messages.",
            action = "See Omni+",
            onAction = onNavigateToPaywall,
        )
    }
}

/** Which colour the body copy carries — plain, or the alert red the auth pages use for a failure. */
private enum class GateTone { Plain, Error }

/**
 * The gate's one layout: the healthcare card, centred in the page, with an optional pill under it.
 *
 * The pill is omitted rather than disabled while loading, because a control that does nothing is worse
 * than no control (§6 rule 10) — and the loading state resolves on its own.
 */
@Composable
private fun GatePage(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    tone: GateTone = GateTone.Plain,
) {
    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .padding(horizontal = PagePadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = DesignFrameWidth)
                    .clip(RoundedCornerShape(CardCorner))
                    .background(OmniSetCardSurface)
                    .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(GlyphSize)
                        .clip(CircleShape)
                        .background(OmniSetApply.copy(alpha = GlyphWash)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_doctor),
                        contentDescription = null,
                        modifier = Modifier.size(GlyphInner),
                    )
                }

                Spacer(Modifier.height(TitleTop))

                Text(
                    text = title,
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(BodyTop))

                Text(
                    text = body,
                    style = SettingsType.CardBody,
                    color = if (tone == GateTone.Error) OmniAlertRed else OmniSetCardBody,
                    textAlign = TextAlign.Center,
                )

                if (action != null) {
                    Spacer(Modifier.height(ActionTop))

                    Box(
                        modifier = Modifier
                            .pressEffect()
                            .clip(RoundedCornerShape(PillCorner))
                            .background(OmniSetApply)
                            .clickable(onClick = onAction)
                            .padding(horizontal = PillPaddingH, vertical = PillPaddingV),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = action,
                            style = SettingsType.CardButton,
                            color = OmniOnInk,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// The settings page's healthcare card, to the dp: radius 13 on #F5F5F5, a 24dp glyph, and the 23dp
// "Apply for Verification" pill. The paddings are that card's own 22 / 83 / 169 rhythm expressed as
// gaps, since this card sizes to its text rather than to a fixed 225.

private val PagePadding = 16.dp
private val CardCorner = 13.dp
private val CardPaddingH = 24.dp
private val CardPaddingV = 28.dp
private val GlyphSize = 52.dp
private val GlyphInner = 24.dp
private const val GlyphWash = 0.12f
private val TitleTop = 16.dp
private val BodyTop = 8.dp
private val ActionTop = 20.dp
private val PillCorner = 23.dp
private val PillPaddingH = 28.dp
private val PillPaddingV = 10.dp

// ---- Previews -----------------------------------------------------------------------------------

/** Active: the gate passes through — the gated content renders. */
@DevicePreviews
@Composable
private fun PremiumGatePreview() {
    OmniTheme {
        PremiumGate(
            revenueCatRepository = PreviewRevenueCatRepository(
                RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true)),
            ),
            onNavigateToPaywall = {},
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Omni+ content renders here")
            }
        }
    }
}

/** Inactive: the gate page itself — the healthcare-card idiom with the See Omni+ pill. */
@DevicePreviews
@Composable
private fun PremiumGateInactivePreview() {
    OmniTheme {
        PremiumGate(
            revenueCatRepository = PreviewRevenueCatRepository(),
            onNavigateToPaywall = {},
        ) { }
    }
}
