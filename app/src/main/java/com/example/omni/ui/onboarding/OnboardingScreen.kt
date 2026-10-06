package com.example.omni.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniBody
import com.example.omni.ui.theme.OmniIndicatorActive
import com.example.omni.ui.theme.OmniIndicatorInactive
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniMuted
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniTheme
import kotlinx.coroutines.launch

/**
 * Three-slide onboarding carousel.
 *
 * The Figma frame lays its three slides out side by side at absolute x offsets (0 / 415 / 830)
 * inside a clipped container — that is how a static design expresses a pager. Here it becomes a
 * real [HorizontalPager] so the slides are swipeable, and the layout is built from weights rather
 * than the source's absolute y coordinates so it survives on screens that are not 415x920.
 *
 * The page indicator and the bottom call-to-action live outside the pager: in the design they are
 * repeated identically on every slide, which means they are chrome, not page content.
 *
 * Landscape is a **deliberately accepted degradation**, not a bug. The weighted layout squeezes and the
 * illustration crops (saved from a hard clip by [clipToBounds]) in the short landscape viewport, because
 * a real fix would mean giving the pager a fixed page height and a root scroll — which changes the
 * portrait layout and breaks the pixel-identity the whole `DesignFrame` artboard depends on
 * (`UI_ARCHITECTURE.md` §3). Onboarding is shown once and landscape-during-onboarding is rare, so the
 * crop is the right trade against a refactor that risks every other screen's portrait fidelity.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(29.dp))

            PageIndicator(
                pageCount = onboardingPages.size,
                currentPage = pagerState.currentPage,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                OnboardingPageContent(onboardingPages[index])
            }

            BottomActions(
                isLastPage = isLastPage,
                onNext = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                onSignIn = onSignIn,
            )
        }
    }
}

/**
 * A single slide: the blurred glow, then the artwork, then the copy — the Figma layer order.
 *
 * In the source every slide is built from the same three-layer stack: `Ellipse 12` (a gradient
 * ellipse under a 121px Gaussian blur), the square `Illustration` image node, and the text. The
 * glow is shipped as a bitmap because Compose's `Modifier.blur` needs API 31 and minSdk here is 26 —
 * [R.drawable.onboarding_glow] is Figma's own render of that ellipse cropped to the slide, so it is
 * the design's blur rather than an approximation of it.
 *
 * Both layers hang off the slide's centre with the same small nudge the design applies, so they stay
 * registered with each other at any screen size. `clipToBounds` stands in for the slide frame's
 * overflow-clip.
 */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Image(
            painter = painterResource(R.drawable.onboarding_glow),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        )

        Image(
            painter = painterResource(page.illustration),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = IllustrationOffsetX, y = IllustrationOffsetY)
                .size(page.illustrationSize),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(39.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = OmniInk)) { append(page.titleLead) }
                    withStyle(SpanStyle(color = page.accentColor)) { append(page.titleAccent) }
                    if (page.titleTail.isNotEmpty()) {
                        withStyle(SpanStyle(color = page.tailColor)) { append(page.titleTail) }
                    }
                },
                style = if (page.usesCompactTitle) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(293.dp),
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = OmniBody,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The glow and the illustration are both centred on the slide, offset by these amounts — the design
 * expresses them as `left: calc(50% - 2.1px)` and `top: calc(50% + 19.9px)`, identically on all
 * three slides, so they are shared constants rather than per-page values.
 */
private val IllustrationOffsetX = (-2.1).dp
private val IllustrationOffsetY = 19.9.dp

private val ActiveDotWidth = 54.dp

/**
 * In the design the three indicator marks are 54 / 11 / 6 px wide and the 54px pill sits at the
 * active slide's position, so the two inactive marks keep their sizes in index order. Reproducing
 * that rule (rather than three fixed dots) is what makes the pill appear to slide between pages.
 */
private val InactiveDotWidths = listOf(11.dp, 6.dp)

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var inactiveIndex = 0
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val targetWidth = if (isActive) {
                ActiveDotWidth
            } else {
                val next = InactiveDotWidths[inactiveIndex.coerceAtMost(InactiveDotWidths.lastIndex)]
                inactiveIndex++
                next
            }
            val width by animateDpAsState(targetValue = targetWidth, animationSpec = OmniMotion.fast(), label = "indicatorWidth")

            Box(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isActive) OmniIndicatorActive else OmniIndicatorInactive),
            )
        }
    }
}

@Composable
private fun BottomActions(
    isLastPage: Boolean,
    onNext: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OmniInk,
                contentColor = OmniOnInk,
            ),
        ) {
            // The Figma button node stacks both labels in a clipped frame — that is the design's
            // way of showing the label swaps on the final slide.
            Text(
                text = if (isLastPage) "Start My Journey" else "Next",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Already a omni member?",
                style = MaterialTheme.typography.bodyMedium,
                color = OmniMuted,
            )
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.bodyMedium,
                color = OmniInk,
                modifier = Modifier.clickable(onClick = onSignIn),
            )
        }

        Spacer(Modifier.height(14.dp))
    }
}

@DevicePreviews
@Composable
private fun OnboardingScreenPreview() {
    OmniTheme {
        OnboardingScreen()
    }
}
