package com.example.omni.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniHomeGreeting
import com.example.omni.ui.theme.OmniHomeName
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNavBar
import com.example.omni.ui.theme.OmniNavPill

/**
 * The two pieces of chrome every Omni screen shares: the sticky white header and the floating
 * bottom navigation bar.
 *
 * Both were originally private to `HomeScreen`. The community feed, nutrition, SOS and hospital
 * frames redraw them byte-for-byte — the same 36dp avatar and 21 gutter, the same 380 x 67 dark
 * pill — so they live here instead of being copied five times.
 *
 * Neither composable creates a [com.example.omni.ui.DesignFrame] or touches `LocalDensity`: they
 * are components, and they inherit the 415 artboard from whichever screen hosts them.
 */

// ---- Header -------------------------------------------------------------------------------------

/**
 * The sticky white bar — Figma `Top Bar` / `Frame 87`, minus its mock iOS status bar.
 *
 * Its inner column sits at x=21, not the 16 the scrolling content uses, so the avatar is inset
 * further than the cards below it. That is the design's own asymmetry, kept.
 *
 * The white fill is applied *before* [statusBarsPadding] so it paints the status-bar strip too;
 * with the padding first the strip stayed transparent and the content behind showed through it.
 */
@Composable
fun OmniHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OmniBackground)
            .statusBarsPadding()
            .padding(top = HeaderTopGap, bottom = HeaderBottomGap)
            .padding(horizontal = HeaderPadding)
            .heightIn(min = HeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.home_avatar),
                contentDescription = "Your profile",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
            Column {
                Text(
                    text = "Good morning!",
                    style = HomeType.Caption12,
                    color = OmniHomeGreeting,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "Sayed Mahir",
                    style = HomeType.HeaderName,
                    color = OmniHomeName,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_home_search_chat),
                contentDescription = "Messages",
                modifier = Modifier.size(24.dp),
            )
            Image(
                painter = painterResource(R.drawable.ic_home_bell_badge),
                contentDescription = "Notifications",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 24 from the bottom of the mock status bar to the avatar row. */
private val HeaderTopGap = 24.dp

/** The avatar's own height, which drives the row. */
private val HeaderRowHeight = 36.dp

/** The header's gutter — 21 in the source, so the avatar is inset further than the cards. */
private val HeaderPadding = 21.dp

/** 101 − (13 + 11.336 + 24 + 36): what is left below the avatar row inside the header frame. */
private val HeaderBottomGap = 16.664.dp

/** The header's height below the real status bar: 101 − 24.336 (the mock bar we drop). */
val OmniHeaderHeight = 76.664.dp

// ---- Bottom navigation --------------------------------------------------------------------------

/**
 * The five destinations of the bottom bar, in Figma's own order, with the label each one shows when
 * it is the selected pill.
 *
 * Every icon ships twice because the bar inverts: on the dark #302E2E track the icons are white,
 * and inside the white pill the selected one turns #302E2E.
 */
enum class OmniNavItem(val label: String) {
    Home("Home"),
    Feed("Feed"),
    Sos("SOS"),
    Fitness("Fitness"),
    Setting("Setting");

    internal val lightIcon: Int
        get() = when (this) {
            Home -> R.drawable.ic_nav_home_light
            Feed -> R.drawable.ic_home_nav_rss
            Sos -> R.drawable.ic_home_nav_ambulance
            Fitness -> R.drawable.ic_home_nav_shoe
            Setting -> R.drawable.ic_home_nav_settings
        }

    internal val darkIcon: Int
        get() = when (this) {
            Home -> R.drawable.ic_home_nav_home
            Feed -> R.drawable.ic_nav_rss_dark
            Sos -> R.drawable.ic_nav_ambulance_dark
            Fitness -> R.drawable.ic_nav_shoe_dark
            Setting -> R.drawable.ic_nav_settings_dark
        }

    internal val contentDescription: String
        get() = when (this) {
            Home -> "Home"
            Feed -> "Community feed"
            Sos -> "Emergency"
            Fitness -> "Activity"
            Setting -> "Settings"
        }

    /**
     * The icon is not quite vertically centred in the source — each sits one to three px high of
     * the 21.5 a 24 icon would need in a 67 bar — so each carries the design's own offset.
     */
    internal val verticalNudge: Dp
        get() = when (this) {
            Home -> (-2.5).dp        // Figma top 19
            Feed -> (-2.5).dp        // Figma top 19
            Sos -> (-1.5).dp         // Figma top 20
            Fitness -> (-3.5).dp     // Figma top 18
            Setting -> (-2.5).dp     // Figma top 19
        }
}

/**
 * The floating bar — Figma `Bottom Navbar` (node 124:231 and its siblings), a 380 x 67 dark pill
 * with a 35 corner radius.
 *
 * Figma reports the track's fill through a Linear Dodge blend, which a browser export turns into
 * `mix-blend-plus-lighter` and would render as invisible white on a white page; Figma's own render
 * of the node is a flat #302E2E, so that is what ships.
 *
 * The five slots are absolutely positioned in the source and the numbers differ per variant, so the
 * horizontal layout is computed rather than hardcoded — see [navSlotOffsets], which reproduces both
 * variants the design actually contains.
 */
@Composable
fun OmniBottomNav(
    selected: OmniNavItem,
    modifier: Modifier = Modifier,
    onSelect: (OmniNavItem) -> Unit = {},
) {
    val offsets = navSlotOffsets(selected.ordinal)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NavSideGutter)
            .height(NavHeight)
            .clip(RoundedCornerShape(35.dp))
            .background(OmniNavBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniNavItem.entries.forEachIndexed { index, item ->
            Spacer(Modifier.width(offsets[index]))
            if (item == selected) {
                NavPill(item = item, onClick = { onSelect(item) })
            } else {
                Image(
                    painter = painterResource(item.lightIcon),
                    contentDescription = item.contentDescription,
                    modifier = Modifier
                        .offset(y = item.verticalNudge)
                        .size(NavIconSize)
                        .clickable { onSelect(item) },
                )
            }
        }
    }
}

/** The selected slot — a 113 x 59 white pill holding the icon and the only visible label. */
@Composable
private fun NavPill(item: OmniNavItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(NavPillWidth)
            .height(59.dp)
            .clip(RoundedCornerShape(35.dp))
            .background(OmniNavPill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.offset(x = 1.dp, y = 0.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(item.darkIcon),
                contentDescription = null,
                modifier = Modifier.size(NavIconSize),
            )
            Text(text = item.label, style = HomeType.NavLabel, color = OmniInk, maxLines = 1)
        }
    }
}

/**
 * The gap that precedes each of the five slots, for a given selection.
 *
 * Figma hand-places the slots and the numbers move when the selection does, so rather than hardcode
 * one variant this derives them from the two rules the source obeys:
 *
 *  - an unselected slot sits at its own fixed x ([NavIconX]);
 *  - the pill is right-aligned to the right edge of the slot it replaces, but never starts before
 *    [NavPillLead];
 *  - and nothing may crowd its predecessor closer than [NavMinGap].
 *
 * That reproduces both variants the design contains exactly. With Home selected the slots land on
 * 4 / 155 / 217 / 279 / 341, and with Feed selected on 16 / 66 / 217 / 279 / 341 — Figma's own
 * numbers in both cases. The three remaining selections do not appear in the source; the same rules
 * keep them evenly spaced and non-overlapping, which is the most the design can tell us.
 */
private fun navSlotOffsets(selected: Int): List<Dp> {
    val left = FloatArray(NavIconX.size)
    var earliest = 0f
    NavIconX.indices.forEach { i ->
        val width = if (i == selected) NavPillWidthPx else NavIconSizePx
        val wanted =
            if (i == selected) maxOf(NavPillLeadPx, NavIconX[i] + NavIconSizePx - NavPillWidthPx)
            else NavIconX[i]
        left[i] = maxOf(wanted, earliest)
        earliest = left[i] + width + NavMinGapPx
    }
    return NavIconX.indices.map { i ->
        val previousEnd =
            if (i == 0) 0f
            else left[i - 1] + (if (i - 1 == selected) NavPillWidthPx else NavIconSizePx)
        (left[i] - previousEnd).dp
    }
}

/** Where each 24dp icon sits when it is *not* the selected pill. Figma's numbers. */
private val NavIconX = floatArrayOf(16f, 155f, 217f, 279f, 341f)

/** Figma never lets the pill start closer than this to the left end of the track. */
private const val NavPillLeadPx = 4f

/** The tightest breathing room the design allows between two slots. */
private const val NavMinGapPx = 26f

private const val NavIconSizePx = 24f
private const val NavPillWidthPx = 113f

private val NavIconSize = NavIconSizePx.dp
private val NavPillWidth = NavPillWidthPx.dp

val OmniNavHeight = 67.dp
private val NavHeight = OmniNavHeight

/** (415 − 380) / 2 — reproduces the design's 380 track inside the 415 artboard. */
private val NavSideGutter = 17.5.dp

/** The bar floats 16 above the system navigation bar. */
val OmniNavBottomGap = 16.dp
