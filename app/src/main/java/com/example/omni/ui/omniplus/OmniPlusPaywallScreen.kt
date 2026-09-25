package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardBody
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Omni+ paywall / landing screen.
 *
 * Matches Omni's existing premium-look screens (onboarding, auth): the badge pill, the hero headline
 * with accent colour on the second line, the feature rows, and the CTA button. RevenueCat
 * functionality is unchanged — `state.availablePackages`, `onPurchase`, `onRestore` are wired exactly
 * as before. No `Scaffold`, `Button`, `OutlinedButton`, `CircularProgressIndicator`, emoji, or
 * hardcoded hex. All colours from `Color.kt`. All typography from the type system.
 */
@Composable
fun OmniPlusPaywallScreen(
    state: OmniPlusUiState,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onBrowseDoctors: () -> Unit,
    onDismiss: () -> Unit,
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
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PagePadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                // Top row: close button on the right (matches Omni's page-entry pattern)
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
                        .clip(RoundedCornerShape(50))
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

                // Headline
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
                        text = "Verified by Experts",
                        style = HomeType.HeroTitle,
                        color = OmniAuthHeading,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(SubTop))

                Text(
                    text = "Verified doctor consultations, priority support, and exclusive health insights — all in one subscription.",
                    style = SettingsType.CardBody,
                    color = OmniSetCardBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(Modifier.height(BenefitsTop))

                // Feature rows
                BenefitRow(
                    icon = R.drawable.ic_set_doctor,
                    title = "Verified Doctor Consultation",
                    description = "Text-based consultations with licensed doctors",
                )
                Spacer(Modifier.height(BenefitRowGap))
                BenefitRow(
                    icon = R.drawable.ic_set_arrow_right,
                    title = "Priority Support",
                    description = "Skip the queue with dedicated support",
                )
                Spacer(Modifier.height(BenefitRowGap))
                BenefitRow(
                    icon = R.drawable.ic_hosp_star,
                    title = "Advanced Insights",
                    description = "Deep-dive analytics for your health data",
                )
                Spacer(Modifier.height(BenefitRowGap))
                BenefitRow(
                    icon = R.drawable.ic_auth_check,
                    title = "Exclusive Content",
                    description = "Premium guides and personalised recommendations",
                )

                Spacer(Modifier.height(PackagesTop))

                // Error message
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = SettingsType.RowSubtitle,
                        color = OmniAlertRed,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                // Success: active subscriber
                if (state.successMessage != null && state.subscriptionState is RevenueCatState.Active) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "✓ ${state.successMessage}",
                            style = HomeType.SectionTitle,
                            color = OmniSetApply,
                        )
                        Spacer(Modifier.height(16.dp))
                        PackageButton(
                            label = "Browse Doctors",
                            isLoading = false,
                            onClick = onBrowseDoctors,
                        )
                    }
                    Spacer(Modifier.height(ContentBottomGap))
                    return@Column
                }

                // Subscription packages
                if (state.availablePackages.isNotEmpty()) {
                    state.availablePackages.forEach { pkg ->
                        PackageButton(
                            label = pkg.name,
                            sub = pkg.price.ifBlank { pkg.id },
                            isLoading = state.isPurchasing && state.purchasePackageId == pkg.id,
                            onClick = { onPurchase(pkg.id) },
                        )
                        Spacer(Modifier.height(PackageGap))
                    }
                } else {
                    // Loading state — plain text, matching Omni's loading states
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading packages…",
                            style = SettingsType.RowSubtitle,
                            color = OmniFeedHint,
                        )
                    }
                }

                Spacer(Modifier.height(RestoreTop))

                // Restore purchases — Omni text-link style
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
                )

                Spacer(Modifier.height(ContentBottomGap))
            }
        }
    }
}

/**
 * One benefit row — icon in a circle, title + description.
 * Matches Omni's feature-row idiom used in onboarding and nutrition entry.
 */
@Composable
private fun BenefitRow(
    icon: Int,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BenefitIconGap),
    ) {
        Box(
            modifier = Modifier
                .size(BenefitIconSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniLavender.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(BenefitIconInner),
            )
        }
        Column {
            Text(
                text = title,
                style = SettingsType.RowTitle,
                color = OmniSetRowTitle,
            )
            Text(
                text = description,
                style = SettingsType.RowSubtitle,
                color = OmniSetRowSubtitle,
            )
        }
    }
}

/**
 * One package CTA — matches Omni's auth button and healthcare card CTA idiom.
 * `OmniSetCardSurface` tray, `OmniSetApply` pill, `SettingsType.CardButton` text.
 */
@Composable
private fun PackageButton(
    label: String,
    sub: String? = null,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PackageRadius))
            .background(OmniSetCardSurface)
            .pressEffect()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = PackagePaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = SettingsType.CardButton,
                color = OmniOnInk,
            )
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    style = FeedType.Meta12,
                    color = OmniOnInk.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// From Omni's onboarding, auth, and healthcare card:
//   PagePadding        16.dp  — gutter
//   HeaderTopGap      12.dp  — below status bar
//   CloseButtonSize   36.dp  — circular close button
//   BadgeTop          24.dp  — below top row
//   HeadlineTop       24.dp  — below badge
//   SubTop            16.dp  — below headline
//   BenefitsTop       32.dp  — below sub
//   BenefitRowGap     16.dp  — between benefit rows
//   BenefitIconSize   44.dp  — icon circle
//   BenefitIconInner  20.dp  — icon inside circle
//   BenefitIconGap    12.dp  — icon to text gap
//   PackagesTop       32.dp  — below benefits
//   PackageGap        12.dp  — between packages
//   PackageRadius     16.dp  — card corner radius
//   PackagePaddingV   14.dp  — package vertical padding
//   RestoreTop       16.dp  — below CTA
//   LegalTop          12.dp  — below restore
//   ContentBottomGap  24.dp  — safe area breathing room

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val CloseButtonSize = 36.dp
private val BadgeTop = 24.dp
private val HeadlineTop = 24.dp
private val SubTop = 16.dp
private val BenefitsTop = 32.dp
private val BenefitRowGap = 16.dp
private val BenefitIconSize = 44.dp
private val BenefitIconInner = 20.dp
private val BenefitIconGap = 12.dp
private val PackagesTop = 32.dp
private val PackageGap = 12.dp
private val PackageRadius = 16.dp
private val PackagePaddingV = 14.dp
private val RestoreTop = 16.dp
private val LegalTop = 12.dp
private val ContentBottomGap = 24.dp
