package com.example.omni.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun OmniHeader(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
) {
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
                contentDescription = "Your profile and settings",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick),
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
}

/** The four tabs the bar shows. `Setting` is reached from the header avatar, not the bar. */
private val NavBarItems = listOf(OmniNavItem.Home, OmniNavItem.Feed, OmniNavItem.Sos, OmniNavItem.Fitness)

/**
 * The floating bar — Figma `Bottom Navbar` (node 124:231 and its siblings), a 380 x 67 dark pill
 * with a 35 corner radius.
 *
 * Figma reports the track's fill through a Linear Dodge blend, which a browser export turns into
 * `mix-blend-plus-lighter` and would render as invisible white on a white page; Figma's own render
 * of the node is a flat #302E2E, so that is what ships.
 *
 * ## Motion: a pill that grows, springs and reflows
 *
 * The bar shows four bare white icons and turns the selected one into a white pill (icon + label).
 * The selected pill is *wider* than an icon, so on every tap the tapped pill **grows** its label out
 * while the previous one **shrinks** back to an icon, and the remaining icons slide to take up the
 * freed space. That growth and reflow is driven by a bouncy [spring] rather than a linear tween — the
 * gentle overshoot is what makes it read as fluid instead of a flat fade — and the whole bar is laid
 * out with [Arrangement.SpaceEvenly] so the redistribution happens every frame as the pill resizes.
 *
 * A bounded [ripple] fires from the tapped pill, echoing the reference's radial pulse.
 *
 * The `Setting` destination is deliberately absent: it lives on the header avatar now, which frees a
 * fourth of the track and gives the pill room to breathe.
 */
@Composable
fun OmniBottomNav(
    selected: OmniNavItem,
    modifier: Modifier = Modifier,
    onSelect: (OmniNavItem) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NavSideGutter)
            .height(NavHeight)
            .clip(RoundedCornerShape(35.dp))
            .background(OmniNavBar)
            .padding(horizontal = NavTrackPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavBarItems.forEach { item ->
            NavCell(
                item = item,
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

/**
 * One tab. Unselected it is a bare white icon; selected it is a white pill whose label springs out of
 * the icon. The pill's width is not fixed — it wraps its content, so [AnimatedVisibility] expanding
 * the label is what grows the pill, and the parent's [Arrangement.SpaceEvenly] reflows the siblings
 * around it on the same spring.
 */
@Composable
private fun NavCell(
    item: OmniNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) OmniNavPill else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navPillColor",
    )
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .height(NavPillHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(pillColor)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = if (selected) NavPillPadding else NavIconPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(if (selected) item.darkIcon else item.lightIcon),
            contentDescription = item.contentDescription,
            modifier = Modifier.size(NavIconSize),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                expandHorizontally(
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                shrinkHorizontally(
                    spring(stiffness = Spring.StiffnessMedium),
                    shrinkTowards = Alignment.Start,
                ),
        ) {
            // Left padding is inside the animated region so it grows with the label, keeping the icon
            // centred while collapsed and giving the label its gap only once it is out.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(NavLabelGap))
                Text(
                    text = item.label,
                    style = HomeType.NavLabel,
                    color = OmniInk,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private const val NavIconSizePx = 24f
private val NavIconSize = NavIconSizePx.dp

/** The pill's height inside the 67 track — the source pill was 59. */
private val NavPillHeight = 59.dp

/** Inner inset at each end of the *selected* pill so the icon and label clear its rounded edge. */
private val NavPillPadding = 18.dp

/** Inset around a bare icon, which doubles as its tap target's breathing room. */
private val NavIconPadding = 10.dp

/** Icon-to-label gap once the pill is open — the source used 6. */
private val NavLabelGap = 8.dp

/** Small inset so the leftmost/rightmost pill never kisses the rounded end of the track. */
private val NavTrackPadding = 6.dp

val OmniNavHeight = 67.dp
private val NavHeight = OmniNavHeight

/** (415 − 380) / 2 — reproduces the design's 380 track inside the 415 artboard. */
private val NavSideGutter = 17.5.dp

/** The bar floats 16 above the system navigation bar. */
val OmniNavBottomGap = 16.dp
