package com.example.omni.ui.omniplus

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.R
import com.example.omni.data.revenuecat.EntitlementIds
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.theme.BodyFont
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.PlusJakartaSans

// Design-specific values from Figma node 238-23 that have no Color.kt token yet. Everything that
// does have a token (OmniInk #302E2E, OmniAuthHeading #8D84F9, OmniSetApply #B184E1) is used
// directly; these five are the gradient's own stops and the card's surfaces, verbatim from the file.
private val BadgeGradient = listOf(
    Color(0xFF8D84F9),
    Color(0xFFAE96EF),
    Color(0xFFF1BBDC),
    Color(0xFFFEEBA9),
    Color(0xFFAFDFDF),
)
private val CardBorderGradient = listOf(
    Color(0xFFF1BBDC),
    Color(0xFF8D84F9),
    Color(0xFFB184E1),
    Color(0x45B3E1E1), // rgba(179, 225, 225, 0.27) — the border fades out below the halfway point
)
private val OmniPlusCardSurface = Color(0xFFFDF6FA)
private val OmniPlusNoteInk = Color(0xFF444444)
private val OmniPlusRestoreInk = Color(0xFF5A5A5A)

/**
 * Omni+ paywall — Figma node 238-23 (PROJECT_OMNI_BACKUP), built to the dp.
 *
 * The frame is a gradient-bordered card: two plan tiles under a "best value" ribbon, the feature
 * panel with double-check marks, and a single dark Subscribe button. The RevenueCat wiring is
 * unchanged — the tiles render `state.availablePackages` (two shown, as designed), the button buys
 * the selected package via `onPurchase(pkg.id)`, `onRestore()` is the text link, and an
 * already-active subscriber sees the success page ([SubscribedScreen]). The design has no close
 * control, so the system back gesture dismisses via [BackHandler].
 */
@Composable
fun OmniPlusPaywallScreen(
    state: OmniPlusUiState,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onBrowseDoctors: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var selectedPackageId by remember { mutableStateOf<String?>(null) }

    // Pre-select the best-value plan the moment the packages arrive, so the CTA is live immediately.
    LaunchedEffect(state.availablePackages) {
        if (selectedPackageId == null) selectedPackageId = defaultSelection(state.availablePackages)
    }

    val purchasing = state.isPurchasing

    // An active subscriber lands straight on the way in — the offer never renders.
    if (state.successMessage != null && state.subscriptionState is RevenueCatState.Active) {
        SubscribedScreen(onBrowseDoctors = onBrowseDoctors)
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(38.dp),
            ) {
                Spacer(Modifier.height(88.dp))

                // ---- Hero --------------------------------------------------------------------------------
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(113.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OmniInk),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = "Omni Plus",
                                style = TextStyle(
                                    brush = Brush.verticalGradient(BadgeGradient),
                                    fontFamily = BodyFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    letterSpacing = (-0.131).sp,
                                ),
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_omniplus_sparkles),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = OmniCardInk)) { append("Your health\n") }
                            withStyle(SpanStyle(color = OmniAuthHeading)) { append("Verified Experts") }
                        },
                        style = TextStyle(
                            fontFamily = BodyFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            letterSpacing = (-0.16).sp,
                        ),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "Message licensed doctors, get answers that cite your own health data, " +
                            "and keep every thread in one place.",
                        style = TextStyle(
                            fontFamily = BodyFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            letterSpacing = (-0.07).sp,
                        ),
                        color = OmniCardInk,
                        textAlign = TextAlign.Center,
                    )
                }

                // ---- The gradient-bordered card ----------------------------------------------------------
                Box(
                    modifier = Modifier
                        .width(351.dp)
                        .height(515.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                0.011f to CardBorderGradient[0],
                                0.084f to CardBorderGradient[1],
                                0.138f to CardBorderGradient[2],
                                0.549f to CardBorderGradient[3],
                            ),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 11.dp)
                            .width(337.dp)
                            .height(497.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(OmniPlusCardSurface),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ---- Plans block (243:61) — 294×121 at (38, 6) inside the surface ---------
                            Box(
                                modifier = Modifier
                                    .offset(x = 38.dp, y = 6.dp)
                                    .width(294.dp)
                                    .height(121.dp),
                            ) {
                                if (state.availablePackages.isEmpty()) {
                                    Text(
                                        text = "Loading plans…",
                                        style = TextStyle(
                                            fontFamily = BodyFont,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 14.sp,
                                        ),
                                        color = OmniPlusNoteInk,
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.offset(y = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(27.dp),
                                    ) {
                                        state.availablePackages.take(2).forEach { pkg ->
                                            PlanCard(
                                                pkg = pkg,
                                                selected = selectedPackageId == pkg.id,
                                                onClick = { selectedPackageId = pkg.id },
                                            )
                                        }
                                    }
                                }
                                // The ribbon overhangs the yearly tile's top-right (243:35 at x=235, y=0).
                                if (state.availablePackages.any { isBestValue(it) }) {
                                    BestValueRibbon(modifier = Modifier.offset(x = 235.dp))
                                }
                            }

                            // ---- Features panel (243:37) — 337 wide at y=151; height raised so the
                            // Restore line clears the bottom edge, panel bottom flush with surface (497).
                            Box(
                                modifier = Modifier
                                    .offset(y = 151.dp)
                                    .width(337.dp)
                                    .height(346.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 21.dp,
                                            topEnd = 21.dp,
                                            bottomStart = 7.dp,
                                            bottomEnd = 7.dp,
                                        ),
                                    )
                                    .background(Color.White),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 15.dp, top = 14.dp)
                                        .width(298.dp),
                                    verticalArrangement = Arrangement.spacedBy(43.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.width(266.dp),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                    ) {
                                        Text(
                                            text = "Everything in Omni plus",
                                            style = TextStyle(
                                                fontFamily = PlusJakartaSans,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 16.sp,
                                                lineHeight = 20.sp,
                                                letterSpacing = (-0.16).sp,
                                            ),
                                            color = OmniPlusNoteInk,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FeatureCheckRow("Verified doctor consultations")
                                            FeatureCheckRow("Priority support — skip the queue")
                                            FeatureCheckRow("Advanced insights on your health data")
                                            FeatureCheckRow("Nearby pharmacies on the SOS map")
                                            FeatureCheckRow("Exclusive Ai acess")
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        if (state.error != null) {
                                            Text(
                                                text = state.error,
                                                style = TextStyle(
                                                    fontFamily = BodyFont,
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                ),
                                                color = OmniAlertRed,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(OmniInk)
                                                .clickable(
                                                    enabled = selectedPackageId != null && !purchasing,
                                                    onClick = { selectedPackageId?.let(onPurchase) },
                                                )
                                                .padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = if (purchasing) "Processing…" else "Subscribe Now",
                                                style = TextStyle(
                                                    fontFamily = BodyFont,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    lineHeight = 24.sp,
                                                ),
                                                color = Color.White,
                                            )
                                        }
                                        Text(
                                            text = "Restore Purchases",
                                            style = TextStyle(
                                                fontFamily = BodyFont,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 14.sp,
                                            ),
                                            color = OmniPlusRestoreInk,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !state.isRestoring, onClick = onRestore),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The subscribed state — Figma node 249-390: the gradient-bordered celebration card, centred on the
 * page. Same border gradient and construction as the paywall card (gradient surface, white inner at
 * 8dp), with the party-popper artwork, the welcome title with its lavender sparkles, and the
 * Browse Doctors pill. The card is the design's own 242×251 size, not a full-bleed screen.
 */
@Composable
private fun SubscribedScreen(
    onBrowseDoctors: () -> Unit,
) {
    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(242.dp)
                    .height(251.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.verticalGradient(
                            0.0108f to CardBorderGradient[0],
                            0.084f to CardBorderGradient[1],
                            0.138f to CardBorderGradient[2],
                            0.549f to CardBorderGradient[3],
                        ),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(227.dp)
                        .height(236.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White),
                ) {
                    // Figma 257:100 — the content container: 188dp wide, 201dp tall, top 13.4 (≈ centred).
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 13.dp)
                            .width(188.dp)
                            .height(201.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.omniplus_celebration),
                            contentDescription = "Celebration",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(x = 10.dp)
                                .size(67.dp),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(23.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Welcome to Omni Plus",
                                    style = TextStyle(
                                        fontFamily = BodyFont,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        letterSpacing = (-0.32).sp,
                                    ),
                                    color = OmniInk,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Your omni plus membership is active now",
                                    style = TextStyle(
                                        fontFamily = BodyFont,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 10.sp,
                                        lineHeight = 17.sp,
                                        letterSpacing = (-0.1).sp,
                                    ),
                                    color = SubscribedSubtitleInk,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(37.dp)
                                    .clip(RoundedCornerShape(5.83.dp))
                                    .background(OmniAuthHeading)
                                    .clickable(onClick = onBrowseDoctors),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Browse Doctors",
                                    style = TextStyle(
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.57.sp,
                                        lineHeight = 27.69.sp,
                                        letterSpacing = (-0.1457).sp,
                                    ),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SubscribedSubtitleInk = Color(0xFF5A5A5A)

/**
 * One plan tile — 117×111, a 1dp OmniSetApply border, and the three-line stack from the frame.
 * The selected tile fills white; the unselected one is transparent and shows the card's #FDF6FA
 * surface, which is the design's active/inactive treatment. Tapping selects the package the
 * Subscribe button buys.
 */
@Composable
private fun PlanCard(
    pkg: PackageOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(117.dp)
            .height(111.dp)
            .clip(RoundedCornerShape(8.dp))
            // Figma: the selected tile fills white; the other stays transparent over the #FDF6FA card.
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.dp, OmniSetApply, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = pkg.name,
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.195).sp,
            ),
            color = OmniInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = pkg.price.ifBlank { pkg.name },
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.27).sp,
            ),
            color = OmniCardInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = billedNote(pkg),
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 7.sp,
                lineHeight = 18.sp,
                letterSpacing = (-0.07).sp,
            ),
            color = OmniPlusNoteInk,
            textAlign = TextAlign.Center,
        )
    }
}

/** The "best value" ribbon — sits on the yearly tile's top-right corner, overhanging the card. */
@Composable
private fun BestValueRibbon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(59.dp)
            .height(17.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 4.dp,
                    bottomEnd = 22.dp,
                    bottomStart = 0.dp,
                ),
            )
            .background(OmniAuthHeading),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "best value",
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                lineHeight = 10.sp,
            ),
            color = Color.White,
        )
    }
}

/** One feature row — the double-check mark, then the claim. */
@Composable
private fun FeatureCheckRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_omniplus_check_check),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.14).sp,
            ),
            color = OmniCardInk,
        )
    }
}

/** The billing note under a plan's name — derived from the package id, never invented. */
private fun billedNote(pkg: PackageOption): String {
    val id = "${pkg.id} ${pkg.name}".lowercase()
    return when {
        "year" in id || "annual" in id -> "Billed Yearly · cancel anytime"
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

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewPackages = listOf(
    PackageOption(
        id = "omni_plus_monthly",
        name = "Monthly",
        price = "$4.99",
    ),
    PackageOption(
        id = "omni_plus_yearly",
        name = "Yearly",
        price = "$39.99",
    ),
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

/** The subscribed state: the offer is replaced by the success row and the Browse Doctors button. */
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
