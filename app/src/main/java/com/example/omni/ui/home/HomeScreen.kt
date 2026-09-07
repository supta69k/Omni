package com.example.omni.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniHomeWelcome
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniTabActiveSurface
import com.example.omni.ui.theme.OmniTabInactive
import com.example.omni.ui.theme.OmniTabShadow
import com.example.omni.ui.theme.OmniTabTrack
import com.example.omni.ui.theme.OmniTheme

/**
 * The dashboard — Figma frame `iPhone 14 & 15 Pro - 22` (node 1:4).
 *
 * The artboard is 415 x 1171: taller than a phone, because it is a scrolling page. Three things
 * live in page coordinates in Figma that have to become viewport-anchored here:
 *
 *  - `Frame 87`, the white header, is marked sticky in the source, so it is an overlay and the
 *    content scrolls underneath it.
 *  - `Frame 76`, the dark pill of a bottom bar, is drawn at y=944 in page space but is plainly a
 *    floating bar; it is pinned above the system navigation bar instead.
 *  - the mock iOS status bar inside the header (`Frame 29`, a 9:41 clock and a Dynamic Island
 *    battery) is replaced by the real one via [statusBarsPadding], exactly as the onboarding and
 *    auth screens already do. Everything below it keeps the design's own spacing: the 24 gap from
 *    the status bar to the avatar row, and the 16.664 below the row, are Figma's numbers.
 *
 * Every other measurement is the Figma value verbatim, fractions included, and [DesignFrame] is what
 * makes that literal rather than approximate — see its own note.
 */
@Composable
fun HomeScreen(
    onExploreFirstAid: () -> Unit = {},
    onAddGlass: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(1) }

    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = ScreenPadding),
            ) {
                Spacer(Modifier.height(HeaderHeight + GreetingGap))
                HomeGreeting()

                Spacer(Modifier.height(BentoGap))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FirstAidCard(onExplore = onExploreFirstAid)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WaterCard(onAdd = onAddGlass, modifier = Modifier.weight(WaterCardWeight))
                        StepsCard(modifier = Modifier.weight(StepsCardWeight))
                    }
                }

                Spacer(Modifier.height(SectionGap))

                DailyUpdatesHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                )

                Spacer(Modifier.height(UpdatesGap))

                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FiberCard()
                    SleepCard()
                    CprCard()
                }

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }

            OmniHeader(onProfileClick = { onNavigate(OmniNavItem.Setting) })

            OmniBottomNav(
                selected = OmniNavItem.Home,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
            )
        }
    }
}

/** "Welcome back Mahir" plus the waving hand — Figma `Frame 96`, a 272-wide row with no gap. */
@Composable
private fun HomeGreeting() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Welcome back Mahir",
            style = HomeType.Welcome,
            color = OmniHomeWelcome,
            maxLines = 1,
            softWrap = false,
        )
        Image(
            painter = painterResource(R.drawable.ic_home_hand_wave),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
/**
 * "Daily Updates" and the segmented control under it — Figma `Frame 54`.
 *
 * The three slots carry Figma's own widths (71 / 141 / 59), which is only safe because the track is
 * genuinely 383 inside [DesignFrame] and because Manrope turns out to fit them: "Wellness" measures
 * 67.4 of its 71 and "Activity" 56.1 of its 59. With the design's 21 inset, [Arrangement.SpaceBetween]
 * then puts (383 − 42 − 271) / 2 = 35 between them, which is the design's gap exactly.
 *
 * "Recommended" is Plus Jakarta Sans Medium 16 — the real Figma face — and measures 118.07 inside the
 * pill's 141, matching the 118 the source reports for it.
 *
 * Only the "Recommended" state is drawn in the design; the other two reuse the same white pill,
 * which is the one thing here that is extrapolated rather than measured.
 */
@Composable
private fun DailyUpdatesHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(19.dp)) {
        Text(
            text = "Daily Updates",
            style = HomeType.SectionTitle,
            color = OmniSectionTitle,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(OmniTabTrack)
                .padding(horizontal = TabTrackInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabLabels.forEachIndexed { index, label ->
                TabSlot(
                    label = label,
                    width = TabSlotWidths[index],
                    selected = index == selectedTab,
                    onClick = { onTabSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun TabSlot(
    label: String,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Box(
            modifier = Modifier
                .width(width)
                .height(33.dp)
                // Figma's `0 0 6.6 2 rgba(190,190,190,.25)` is a spread glow, which Compose's
                // single-elevation shadow cannot express; 3dp is the closest visual match.
                .shadow(3.dp, RoundedCornerShape(30.dp), ambientColor = OmniTabShadow, spotColor = OmniTabShadow)
                .clip(RoundedCornerShape(30.dp))
                .background(OmniTabActiveSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = HomeType.TabActive,
                color = OmniCardInk,
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = label,
            style = HomeType.TabIdle,
            color = OmniTabInactive,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(width)
                .clickable(onClick = onClick),
        )
    }
}

private val TabLabels = listOf("Wellness", "Recommended", "Activity")

/** Figma's own slot widths; 71 + 141 + 59 + 2 x 35 gaps + 2 x 21 inset = the 383 track. */
private val TabSlotWidths = listOf(71.dp, 141.dp, 59.dp)

/** The design's inner row is 341 inside a 383 track, i.e. 21 a side. */
private val TabTrackInset = 21.dp
// ---- Geometry ----------------------------------------------------------------------------------
//
// Every value below is derived from frame `iPhone 14 & 15 Pro - 22`, which is 415 x 1171. The
// vertical map of the page is: header 0..101, greeting at y=122 (h 24), the bento block at y=189
// (h 338), "Daily Updates" at y=559.617 (h 81.225), the update cards at y=670 (h 361.226), and the
// nav bar at y=944 (h 67). Each gap constant is that map's arithmetic, written out so the source of
// the number is checkable.

/** The scrolling content's gutter. Figma's three sibling blocks sit at 16 / 16.8958 / 16; the
 *  sub-dp drift on the middle one is a Figma rounding artefact and is normalised to a single 16. */
private val ScreenPadding = 16.dp

/** The header's height below the real status bar: 101 − 24.336 (the mock bar we drop). */
private val HeaderHeight = OmniHeaderHeight

/** 122 − 101 */
private val GreetingGap = 21.dp

/** 189 − (122 + 24) */
private val BentoGap = 43.dp

/** 559.617 − 527 */
private val SectionGap = 32.617.dp

/** 670 − 640.842 */
private val UpdatesGap = 29.158.dp

/** Breathing room so the last card can scroll clear of the floating bar. */
private val ContentBottomGap = 24.dp

/** The bento row is 166 + 10 + 207 = 383; weights keep that ratio at any width. */
private const val WaterCardWeight = 166f
private const val StepsCardWeight = 207f

@DevicePreviews
@Composable
private fun HomeScreenPreview() {
    OmniTheme { HomeScreen() }
}
