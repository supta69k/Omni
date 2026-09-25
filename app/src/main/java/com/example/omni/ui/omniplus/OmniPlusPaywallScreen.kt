package com.example.omni.ui.omniplus

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.revenuecat.EntitlementIds
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardBody
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Omni+ paywall — the subscribe page.
 *
 * An original composition built from the user's reference board: selectable plan cards with a
 * "best value" badge and large prices, a check-marked feature list, a billing info banner, and one
 * unmissable gradient CTA (`OmniAuthHeading` → `OmniSetApply`, Omni's own accent pair — no imported
 * palette). Everything stays on Omni's system: [DesignFrame], colours from `Color.kt`, the type
 * styles, `pressEffect()`, percent-50 pills — no Material3 buttons, no spinners, no emoji, no
 * hardcoded hex.
 *
 * RevenueCat wiring is unchanged: the cards come from `state.availablePackages`, the CTA buys the
 * selected package via `onPurchase(pkg.id)`, `onRestore()` is the text link, and an already-active
 * subscriber sees the success row with "Browse Doctors" instead.
 */
@Composable
fun OmniPlusPaywallScreen(
    state: OmniPlusUiState,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onBrowseDoctors: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPackageId by remember { mutableStateOf<String?>(null) }

    // Pre-select the best-value plan the moment the packages arrive, so the CTA is live immediately.
    LaunchedEffect(state.availablePackages) {
        if (selectedPackageId == null) selectedPackageId = defaultSelection(state.availablePackages)
    }

    val purchasing = state.isPurchasing

    // An active subscriber lands straight on the way in — the offer never renders.
    if (state.successMessage != null && state.subscriptionState is RevenueCatState.Active) {
        SubscribedScreen(
            message = state.successMessage,
            onBrowseDoctors = onBrowseDoctors,
        )
        return
    }

    DesignFrame {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DesignFrameWidth)
                    .background(OmniBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PagePadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                // Close — the page-entry pattern every Omni sub-page uses
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CloseButtonSize)
                            .pressEffect()
                            .clip(RoundedCornerShape(percent = 50))
                            .background(OmniFeedHint.copy(alpha = 0.3f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            style = FeedType.Meta12,
                            color = OmniCardInk,
                        )
                    }
                }

                Spacer(Modifier.height(BadgeTop))

                // Badge pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(OmniSetApply.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Omni+",
                        style = SettingsType.GroupLabel,
                        color = OmniSetApply,
                    )
                }

                Spacer(Modifier.height(HeadlineTop))

                // Headline — accent colour carries the second line
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Your Health,",
                        style = HomeType.HeroTitle,
                        color = OmniCardInk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Verified by Experts.",
                        style = HomeType.HeroTitle,
                        color = OmniAuthHeading,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(SubTop))

                Text(
                    text = "Message licensed doctors, get answers that cite your own health data, and keep every thread in one place.",
                    style = SettingsType.CardBody,
                    color = OmniSetCardBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(Modifier.height(FeaturesTop))

                // Feature list — the check-circle idiom from the pricing references
                Text(
                    text = "Everything in Omni, plus",
                    style = SettingsType.GroupLabel,
                    color = OmniSetRowSubtitle,
                )
                Spacer(Modifier.height(FeatureListTop))
                FeatureCheckRow("Verified doctor consultations")
                Spacer(Modifier.height(FeatureRowGap))
                FeatureCheckRow("Priority support — skip the queue")
                Spacer(Modifier.height(FeatureRowGap))
                FeatureCheckRow("Advanced insights on your health data")
                Spacer(Modifier.height(FeatureRowGap))
                FeatureCheckRow("Exclusive premium content")
                Spacer(Modifier.height(PlansTop))

                // Plans
                Text(
                    text = "Choose a plan",
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                )
                Spacer(Modifier.height(PlanListTop))

                if (state.availablePackages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PlanCardMinHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading plans…",
                            style = SettingsType.RowSubtitle,
                            color = OmniFeedHint,
                        )
                    }
                } else {
                    state.availablePackages.forEach { pkg ->
                        PlanCard(
                            pkg = pkg,
                            selected = selectedPackageId == pkg.id,
                            enabled = !purchasing,
                            onClick = { selectedPackageId = pkg.id },
                        )
                        Spacer(Modifier.height(PlanGap))
                    }
                }

                Spacer(Modifier.height(BannerTop))

                // Billing banner — says what the charge is before the button asks for it
                InfoBanner()

                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        style = SettingsType.RowSubtitle,
                        color = OmniAlertRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(CtaTop))

                // The one unmissable button
                GradientCta(
                    label = if (purchasing) "Processing…" else "Subscribe Now",
                    enabled = selectedPackageId != null && !purchasing,
                    onClick = { selectedPackageId?.let(onPurchase) },
                )

                Spacer(Modifier.height(RestoreTop))

                Text(
                    text = "Restore Purchases",
                    style = SettingsType.RowAction,
                    color = OmniSetApply,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .pressEffect()
                        .clickable(enabled = !state.isRestoring, onClick = onRestore)
                        .padding(8.dp),
                )

                Spacer(Modifier.height(LegalTop))

                Text(
                    text = "Cancel anytime. By subscribing, you agree to our Terms of Service.",
                    style = FeedType.Meta12,
                    color = OmniFeedHint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(ContentBottomGap))
            }
        }
    }
}

/** The subscribed state — the whole page becomes the welcome and the way in to the doctors. */
@Composable
private fun SubscribedScreen(
    message: String,
    onBrowseDoctors: () -> Unit,
) {
    DesignFrame {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DesignFrameWidth)
                    .background(OmniBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(OmniSetApply.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_auth_check),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(OmniSetApply),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = HomeType.SectionTitle,
                    color = OmniSetApply,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your Omni+ membership is active.",
                    style = SettingsType.CardBody,
                    color = OmniSetCardBody,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PagePadding),
                ) {
                    GradientCta(
                        label = "Browse Doctors",
                        enabled = true,
                        onClick = onBrowseDoctors,
                    )
                }
            }
        }
    }
}

/** One feature row — check in an accent wash, then the claim. */
@Composable
private fun FeatureCheckRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeatureIconGap),
    ) {
        Box(
            modifier = Modifier
                .size(FeatureIconSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniSetApply.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_auth_check),
                contentDescription = null,
                modifier = Modifier.size(FeatureIconInner),
                colorFilter = ColorFilter.tint(OmniSetApply),
            )
        }
        Text(
            text = text,
            style = SettingsType.RowTitle,
            color = OmniSetRowTitle,
        )
    }
}

/**
 * One selectable plan — the reference cards' anatomy: name and billing note on the left, the big
 * price on the right, a "BEST VALUE" pill on the yearly plan, and a 2dp accent border when
 * selected.
 */
@Composable
private fun PlanCard(
    pkg: PackageOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect()
            .clip(RoundedCornerShape(PlanRadius))
            .background(if (selected) OmniSetApply.copy(alpha = 0.08f) else OmniSetCardSurface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) OmniSetApply else OmniFeedHint.copy(alpha = 0.4f),
                shape = RoundedCornerShape(PlanRadius),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = pkg.name,
                    style = SettingsType.RowTitle,
                    color = OmniSetRowTitle,
                )
                if (isBestValue(pkg)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(OmniSetApply)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "BEST VALUE",
                            style = SettingsType.GroupLabel,
                            color = OmniOnInk,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = billedNote(pkg),
                style = FeedType.Meta12,
                color = OmniSetRowSubtitle,
            )
        }
        if (pkg.price.isNotBlank()) {
            Text(
                text = pkg.price,
                style = HomeType.CardValue,
                color = OmniCardInk,
            )
        }
    }
}

/** The billing note under a plan's name — derived from the package id, never invented. */
private fun billedNote(pkg: PackageOption): String {
    val id = "${pkg.id} ${pkg.name}".lowercase()
    return when {
        "year" in id || "annual" in id -> "Billed yearly · cancel anytime"
        "month" in id -> "Billed monthly · cancel anytime"
        else -> "Cancel anytime, hassle-free"
    }
}

/** Yearly/annual plans are the paywall's default pick; otherwise whatever is first on offer. */
private fun defaultSelection(packages: List<PackageOption>): String? {
    val yearly = packages.firstOrNull { isBestValue(it) }
    return yearly?.id ?: packages.firstOrNull()?.id
}

private fun isBestValue(pkg: PackageOption): Boolean {
    val id = "${pkg.id} ${pkg.name}".lowercase()
    return "year" in id || "annual" in id
}

/** The "you will be billed…" banner from the checkout reference, in Omni's accent wash. */
@Composable
private fun InfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmniSetApply.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniSetApply.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                style = SettingsType.GroupLabel,
                color = OmniSetApply,
            )
        }
        Text(
            text = "Billed to your Google account. Cancel anytime, hassle-free.",
            style = FeedType.Meta12,
            color = OmniSetRowSubtitle,
        )
    }
}

/**
 * The CTA — a full-width pill in Omni's accent gradient. This is the page's one loud element:
 * everything above it is quiet, so the button needs no decoration to win the eye.
 */
@Composable
private fun GradientCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .pressEffect()
            .clip(RoundedCornerShape(CtaRadius))
            .background(Brush.horizontalGradient(listOf(OmniAuthHeading, OmniSetApply)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = CtaPaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SettingsType.CardButton,
            color = OmniOnInk,
        )
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// Page gutter, close button and pill rhythm from the previous paywall; card/CTA geometry from the
// references: 20dp card radius, 2dp selected border, a 56dp-tall CTA at 20dp radius.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val CloseButtonSize = 36.dp
private val BadgeTop = 20.dp
private val HeadlineTop = 20.dp
private val SubTop = 12.dp
private val FeaturesTop = 28.dp
private val FeatureListTop = 12.dp
private val FeatureRowGap = 12.dp
private val FeatureIconSize = 26.dp
private val FeatureIconInner = 14.dp
private val FeatureIconGap = 10.dp
private val PlansTop = 28.dp
private val PlanListTop = 12.dp
private val PlanGap = 10.dp
private val PlanRadius = 20.dp
private val PlanCardMinHeight = 64.dp
private val BannerTop = 16.dp
private val CtaTop = 16.dp
private val CtaRadius = 20.dp
private val CtaPaddingV = 16.dp
private val RestoreTop = 12.dp
private val LegalTop = 8.dp
private val ContentBottomGap = 24.dp

// ---- Previews ----------------------------------------------------------------------------------

private val PreviewPackages = listOf(
    PackageOption(id = "omni_plus_monthly", name = "Omni+ Monthly", price = "$4.99"),
    PackageOption(id = "omni_plus_yearly", name = "Omni+ Yearly", price = "$39.99"),
)

private val PreviewPaywallState = OmniPlusUiState(
    subscriptionState = RevenueCatState.Inactive,
    availablePackages = PreviewPackages,
)

@DevicePreviews
@Composable
private fun OmniPlusPaywallPreview() {
    OmniTheme {
        OmniPlusPaywallScreen(
            state = PreviewPaywallState,
            onPurchase = {},
            onRestore = {},
            onBrowseDoctors = {},
            onDismiss = {},
        )
    }
}

/** The subscribed state: the offer is replaced by the success row and the Browse Doctors CTA. */
@DevicePreviews
@Composable
private fun OmniPlusPaywallSubscribedPreview() {
    OmniTheme {
        OmniPlusPaywallScreen(
            state = OmniPlusUiState(
                subscriptionState = RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true)),
                successMessage = "Welcome to Omni+!",
            ),
            onPurchase = {},
            onRestore = {},
            onBrowseDoctors = {},
            onDismiss = {},
        )
    }
}
