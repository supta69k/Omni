package com.example.omni.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniHomeGreeting
import com.example.omni.ui.theme.OmniHomeName
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNavBar
import com.example.omni.ui.theme.OmniNavPill
import com.example.omni.ui.theme.OmniOnInk
import com.exyte.animatednavbar.AnimatedNavigationBar
import com.exyte.animatednavbar.animation.balltrajectory.Parabolic
import com.exyte.animatednavbar.animation.indendshape.Height
import com.exyte.animatednavbar.animation.indendshape.shapeCornerRadius
import androidx.compose.runtime.mutableIntStateOf
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.delay

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
 * Everything the header renders about the signed-in account, as one immutable value.
 *
 * It exists so the four screens that host [OmniHeader] pass **one** parameter instead of four each,
 * and so every default is the literal string Figma draws — which is what keeps `@DevicePreviews`
 * rendering the design without a Firebase session behind it (BACKEND_PLAN §4 rule 2).
 *
 * The unread counts are here rather than in the header's parameter list because they arrive from the
 * same document as the name; they are 0 until the Cloud Functions of Phases 10 and 11 maintain them.
 */
data class OmniHeaderState(
    val userName: String = "Sayed Mahir",
    val photoUrl: String? = null,
    val verified: Boolean = false,
    val unreadMessages: Int = 0,
    val unreadNotifications: Int = 0,
) {
    /**
     * The name the dashboard greets, clamped to one word.
     *
     * The header's own slot has room for a full name; `HomeGreeting`'s is a fixed 248 and would clip a
     * long one. Figma writes "Welcome back Mahir" — the *second* word of its own mock name, i.e. a
     * nickname — so with real data this reads "Welcome back Sayed". That is a deliberate difference
     * from the mock, not a regression: a rule that picks the last word would greet most people by
     * their surname.
     */
    val greetingName: String
        get() = userName.trim().substringBefore(' ').ifEmpty { userName }
}

/**
 * The sticky white bar — Figma `Top Bar` / `Frame 87`, minus its mock iOS status bar.
 *
 * Its inner column sits at x=21, not the 16 the scrolling content uses, so the avatar is inset
 * further than the cards below it. That is the design's own asymmetry, kept.
 *
 * The white fill is applied *before* [statusBarsPadding] so it paints the status-bar strip too;
 * with the padding first the strip stayed transparent and the content behind showed through it.
 *
 * [greeting] defaults to the clock rather than to a literal, so no caller has to re-derive it. Pass it
 * explicitly (`greeting = "Good morning!"`) to pin a preview to the string Figma draws — otherwise a
 * preview rendered after noon honestly says "Good afternoon!".
 */
@Composable
fun OmniHeader(
    modifier: Modifier = Modifier,
    state: OmniHeaderState = OmniHeaderState(),
    greeting: String = rememberTimeOfDayGreeting(),
    onProfileClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OmniBackground)
            .statusBarsPadding()
            .padding(top = HeaderTopGap, bottom = HeaderBottomGap)
            .padding(start = HeaderPadding, end = HeaderActionPadding)
            .heightIn(min = HeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Coil rather than [Image]: `photoUrl` is a Storage download URL once the user has set a
            // photo. All three of `placeholder`/`error`/`fallback` are the bundled asset the design
            // draws, so the 36 circle is filled at every point in the load and the row never resizes —
            // and a preview, which cannot reach the network, still renders the mock.
            AsyncImage(
                model = state.photoUrl,
                contentDescription = "Your profile and settings",
                placeholder = painterResource(R.drawable.home_avatar),
                error = painterResource(R.drawable.home_avatar),
                fallback = painterResource(R.drawable.home_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .pressEffect()
                    .clickable(onClick = onProfileClick),
            )
            // A live display name is not the mock's tidy two words. Bounded and ellipsised so a long
            // one truncates instead of shoving the two trailing icons off the bar: 250 is what the
            // design leaves between the avatar's right edge (65) and the first icon's ink (329).
            Column(modifier = Modifier.widthIn(max = HeaderNameMaxWidth)) {
                Text(
                    text = greeting,
                    style = HomeType.Caption12,
                    color = OmniHomeGreeting,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.userName,
                        style = HomeType.HeaderName,
                        color = OmniHomeName,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.verified) {
                        VerifiedBadge()
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeaderActionGap),
        ) {
            HeaderAction(
                icon = R.drawable.ic_home_search_chat,
                contentDescription = "Messages",
                unread = state.unreadMessages,
                onClick = onMessagesClick,
            )
            HeaderAction(
                icon = R.drawable.ic_home_bell_badge,
                contentDescription = "Notifications",
                unread = state.unreadNotifications,
                onClick = onNotificationsClick,
            )
        }
    }
}

/**
 * One of the two trailing header icons, with a touch target and an unread dot.
 *
 * **The box is 40 and the icon is still 24.** Both icons had no `clickable` at all before; wrapping
 * the bare 24 in one would have shipped a 24dp touch target, well under the 48 minimum. The design's
 * own geometry caps how much can be reclaimed without moving any ink: the icons are 17 apart, so a box
 * of 24 + 17 = 41 is the largest that still lets their centres stay where Figma puts them.
 * [HeaderActionGap] and [HeaderActionPadding] are the design's 17 and 21 less the padding each box now
 * carries, which is what keeps the ink at the same x. 48 is unreachable here without moving the icons.
 *
 * The dot is drawn only when there is something unread, so today — with the counters still 0 until
 * Phases 10 and 11 — the header is pixel-identical to the design. Figma draws no badge anywhere, and
 * there is no room on a 24 glyph for a numeral, so this deliberately shows *that* there is something
 * rather than how much; the count belongs on the destination screen. Its ring is the page's own white,
 * so it separates from the bell's purple disc as cleanly as from the white bar.
 */
@Composable
private fun HeaderAction(
    icon: Int,
    contentDescription: String,
    unread: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(HeaderActionSize)
            .pressEffect()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Image(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(HeaderIconSize),
            )
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // `offset` takes negatives where `padding` would throw; the box's 8 of slack
                        // on every side is what stops the overhang being clipped.
                        .offset(x = BadgeOverhang, y = -BadgeOverhang)
                        .size(BadgeSize)
                        .clip(CircleShape)
                        .background(OmniBackground)
                        .padding(BadgeRing)
                        .clip(CircleShape)
                        .background(OmniAlertRed),
                )
            }
        }
    }
}

/**
 * "Good morning!" / "Good afternoon!" / "Good evening!", from the clock.
 *
 * Not stored on the user — it is a function of *now*, and the boundaries are noon and 17:00.
 *
 * It also does not go stale. A plain `remember` would be read once at composition, so an app left open
 * across noon would still say "Good morning!" until something else happened to recompose the header;
 * instead the effect sleeps exactly until the next boundary and wakes to update. Two waits a day, not
 * a ticker.
 */
@Composable
fun rememberTimeOfDayGreeting(): String {
    var greeting by remember { mutableStateOf(greetingFor(LocalTime.now())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            greeting = greetingFor(now)
            delay(millisUntilGreetingChanges(now))
        }
    }

    return greeting
}

/** Noon and 17:00 — the two times of day the greeting changes, in order. */
private val GreetingBoundaries = listOf(LocalTime.NOON, LocalTime.of(17, 0))

private fun greetingFor(now: LocalTime): String = when {
    now < GreetingBoundaries[0] -> "Good morning!"
    now < GreetingBoundaries[1] -> "Good afternoon!"
    else -> "Good evening!"
}

/** How long until the next boundary, or until midnight if the evening is the current bracket. */
private fun millisUntilGreetingChanges(now: LocalTime): Long {
    val next = GreetingBoundaries.firstOrNull { it > now }
    val remaining = if (next != null) {
        Duration.between(now, next)
    } else {
        Duration.between(now, LocalTime.MAX).plusNanos(1)
    }
    // A boundary landing inside the same millisecond would otherwise spin this loop.
    return remaining.toMillis().coerceAtLeast(1_000L)
}

/** 24 from the bottom of the mock status bar to the avatar row. */
private val HeaderTopGap = 24.dp

/** The avatar's own height, which drives the row. */
private val HeaderRowHeight = 36.dp

private val AvatarSize = 36.dp

/** 329 (the first trailing icon's ink) − 65 (the avatar's right edge plus its 8 gap), less breathing. */
private val HeaderNameMaxWidth = 250.dp

/** The header's gutter — 21 in the source, so the avatar is inset further than the cards. */
private val HeaderPadding = 21.dp

/** The trailing icons' own size, unchanged by the touch target that now surrounds them. */
private val HeaderIconSize = 24.dp

/** 24 of icon plus 8 of reclaimed space on each side — see [HeaderAction]. */
private val HeaderActionSize = 40.dp

/** The design's 17 between the icons, less the 8 + 8 their boxes now contribute: 17 − 16. */
private val HeaderActionGap = 1.dp

/** The design's 21 right gutter, less the 8 the last box carries. */
private val HeaderActionPadding = HeaderPadding - (HeaderActionSize - HeaderIconSize) / 2

/** The unread dot: 10 across, a 1.5 ring of page white, hung 2 outside the icon's corner. */
private val BadgeSize = 10.dp
private val BadgeRing = 1.5.dp
private val BadgeOverhang = 2.dp

/** 101 − (13 + 11.336 + 24 + 36): what is left below the avatar row inside the header frame. */
private val HeaderBottomGap = 16.664.dp

/** The header's height below the real status bar: 101 − 24.336 (the mock bar we drop). */
val OmniHeaderHeight = 76.664.dp

// ---- Bottom navigation --------------------------------------------------------------------------

/**
 * Every destination the shared chrome can ask for, in Figma's own order, with the label each one shows
 * when it is the selected pill.
 *
 * The first five are the bar's own order; [Setting] opens from the header avatar and [Messages] and
 * [Notifications] from the two header icons, so three of the seven are not [NavBarItems] and light no
 * pill. They are still members because they are places the chrome navigates to, and one enum means one
 * `onNavigate` lambda per screen instead of a callback per icon.
 *
 * Each icon is a single-colour stroked vector drawn in white, and the bar inverts it by tint rather
 * than by swapping assets: every one is #FFFFFF on the #302E2E track and #302E2E inside the white
 * pill, which are exactly [OmniNavPill] and [OmniInk]. Figma exported a second, dark copy of all five
 * (`ic_nav_*_dark`); those are redundant now and nothing references them.
 */
enum class OmniNavItem(val label: String) {
    Home("Home"),
    Feed("Feed"),
    Sos("SOS"),
    Fitness("Fitness"),
    Setting("Setting"),
    Messages("Messages"),
    Notifications("Notifications");

    internal val icon: Int
        get() = when (this) {
            Home -> R.drawable.ic_nav_home_light
            Feed -> R.drawable.ic_home_nav_rss
            Sos -> R.drawable.ic_home_nav_ambulance
            Fitness -> R.drawable.ic_home_nav_shoe
            Setting -> R.drawable.ic_home_nav_settings
            // Never rendered by the bar — neither is a tab — but the enum owes every member an icon.
            Messages -> R.drawable.ic_home_search_chat
            Notifications -> R.drawable.ic_home_bell_badge
        }

    internal val contentDescription: String
        get() = when (this) {
            Home -> "Home"
            Feed -> "Community feed"
            Sos -> "Emergency"
            Fitness -> "Activity"
            Setting -> "Settings"
            Messages -> "Messages"
            Notifications -> "Notifications"
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
 * out with [Arrangement.SpaceBetween] so the redistribution happens every frame as the pill resizes.
 *
 * A bounded [ripple] fires from the tapped pill, echoing the reference's radial pulse.
 *
 * ## Geometry: 4 / 55 / 55 / 55 / 26, measured to the icons
 *
 * The source (node 178:26, and identically 177:17 and the bar inside 149:221) is not evenly
 * distributed. Its pill hugs the left edge and the last icon stops well short of the right one: 4 in,
 * then 55 between each cell, then 26 out. A [Arrangement.SpaceBetween] row is what reproduces those
 * positions — `SpaceEvenly` never could, its equal gaps having to average 4, 55 and 26.
 *
 * But Figma only ever draws this bar with `Home` selected, and a literal `start = 4` is only right in
 * that one state. The 4 is the gap to the *pill*, which carries 19 of its own padding, so what the
 * design actually places 23 from the left edge is the **icon**. Hard-code the 4 and the moment another
 * tab is picked the bare `Home` icon sits 4 from the edge — jammed against it, while the pill at the
 * far end keeps its 26 of air. That asymmetry is what the last tab exposes worst.
 *
 * So both track insets are measured to the icon ([NavIconInsetStart] / [NavIconInsetEnd]) and give
 * back the pill's own padding whenever the edge cell *is* the pill:
 *
 * | selected  | start           | end             | first icon | last ink |
 * |-----------|-----------------|-----------------|------------|----------|
 * | `Home`    | 23 − 19 = **4** | **26**          | 23         | 354      |
 * | `Feed`    | **23**          | **26**          | 23         | 354      |
 * | `Sos`     | **23**          | **26**          | 23         | 354      |
 * | `Fitness` | **23**          | 26 − 17 = **9** | 23         | 354      |
 *
 * `Home` still lands on the design's literal 4 / 55 / 55 / 55 / 26 (pill 113 + three 24 icons = 185
 * of the 350 inner width, leaving three 55 gaps), and every other state keeps the same 23 and 354 of
 * icon ink, so the bar stays balanced whichever tab is lit.
 *
 * The inset and the pill's padding share [NavShapeSpring] on purpose: their sum is 23 at the start
 * edge and 26 at the end in *every* state, and because a spring's normalised response is
 * amplitude-independent, one shared spec makes the two animations cancel frame for frame. The outer
 * icons therefore hold still through the switch while the pill and the reflow are what move.
 *
 * The `Setting` destination is deliberately absent: it lives on the header avatar now, which frees a
 * fourth of the track and gives the pill room to breathe.
 */
private var globalLastNavIndex = 0

@Composable
fun OmniBottomNav(
    selected: OmniNavItem,
    modifier: Modifier = Modifier,
    onSelect: (OmniNavItem) -> Unit = {},
) {
    val targetIndex = NavBarItems.indexOf(selected).coerceAtLeast(0)
    var animatedIndex by remember { mutableIntStateOf(globalLastNavIndex) }

    LaunchedEffect(targetIndex) {
        animatedIndex = targetIndex
        globalLastNavIndex = targetIndex
    }

    AnimatedNavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = OmniNavBottomGap)
            .padding(horizontal = NavSideGutter)
            .height(NavHeight),
        selectedIndex = animatedIndex,
        barColor = OmniNavBar, // #302E2E dark navbar wrapper
        ballColor = Color(0xFF302E2E), // #302E2E ball
        cornerRadius = shapeCornerRadius(35.dp),
        ballAnimation = Parabolic(tween(NavAnimMillis, easing = FastOutSlowInEasing)),
        indentAnimation = Height(tween(NavAnimMillis, easing = FastOutSlowInEasing)),
    ) {
        NavBarItems.forEach { item ->
            NavIconButton(
                item = item,
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

/**
 * One bottom-bar tab. Bare icon only (no capsule) — Figma node 177:17.
 *
 * White 24×24dp icon on the dark bar, with circular ripple and press effect.
 */
@Composable
private fun NavIconButton(
    item: OmniNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(item.icon),
            contentDescription = item.contentDescription,
            colorFilter = ColorFilter.tint(OmniOnInk),
            modifier = Modifier.size(NavIconSize),
        )
    }
}

/**
 * One tab. Unselected it is a bare white icon; selected it is a white pill whose label springs out of
 * the icon. The pill's width is not fixed — it wraps its content, so [AnimatedVisibility] expanding
 * the label is what grows the pill, and the parent's [Arrangement.SpaceBetween] reflows the siblings
 * around it on the same spring.
 *
 * A bare cell is exactly the 24 icon inside the 59-high row: no side padding, because the source
 * measures the gaps between the icon boxes themselves (55 between neighbours), and any padding here
 * would push the icons inward off their measured slots.
 *
 * The pill's 19 / 17 is therefore animated, not switched. Flipping it on selection moved the icon 19
 * in a single frame while the label beside it took ~400ms to spring out, which is the hitch that made
 * the switch feel broken: the icon arrived, then the pill caught up around it. On [NavShapeSpring] the
 * padding and the parent's track inset are one motion instead (see [OmniBottomNav]).
 *
 * The icon's colour is animated for the same reason, and on the *same* spec as the pill behind it
 * ([NavTintSpring]). Swapping to a second, dark drawable the instant `selected` flipped left a white
 * icon sitting on a pill that was still half white — the icon disappeared for a few frames on the way
 * in and again on the way out. Tinting one drawable keeps the icon exactly as far from its backdrop
 * at every point in the fade as it is at either end.
 */
@Composable
private fun NavCell(
    item: OmniNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) OmniNavPill else Color.Transparent,
        animationSpec = NavTintSpring,
        label = "navPillColor",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) OmniInk else OmniNavPill,
        animationSpec = NavTintSpring,
        label = "navIconColor",
    )
    val pillStart by animateDpAsState(
        targetValue = if (selected) NavPillPaddingStart else 0.dp,
        animationSpec = NavShapeSpring,
        label = "navPillStart",
    )
    val pillEnd by animateDpAsState(
        targetValue = if (selected) NavPillPaddingEnd else 0.dp,
        animationSpec = NavShapeSpring,
        label = "navPillEnd",
    )
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .height(NavPillHeight)
            .pressEffect()
            .clip(RoundedCornerShape(percent = 50))
            .background(pillColor)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(start = pillStart.coerceAtLeast(0.dp), end = pillEnd.coerceAtLeast(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(item.icon),
            contentDescription = item.contentDescription,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(NavIconSize),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                expandHorizontally(NavLabelEnterSpring, expandFrom = Alignment.Start),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                shrinkHorizontally(NavLabelExitSpring, shrinkTowards = Alignment.Start),
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

private const val NavAnimMillis = 280

private const val NavIconSizePx = 24f
private val NavIconSize = NavIconSizePx.dp

/** The pill's height inside the 67 track — the source pill was 59. */
private val NavPillHeight = 59.dp

/** 19 before the icon and 17 after the label inside the pill (node 178:26: 19 + 24 + 6 + 47 + 17). */
private val NavPillPaddingStart = 19.dp
private val NavPillPaddingEnd = 17.dp

/** Icon-to-label gap once the pill is open — the source used 6. */
private val NavLabelGap = 6.dp

val OmniNavHeight = 67.dp
private val NavHeight = OmniNavHeight

/** (415 − 380) / 2 — reproduces the design's 380 track inside the 415 artboard. */
private val NavSideGutter = 17.5.dp

/**
 * The track's own insets, measured to the **icon** rather than to the cell.
 *
 * The source puts its pill 4 from the left edge, and the pill carries 19 of internal padding, so the
 * first icon's ink starts at 23; 26 is the design's own margin from the last icon to the right edge.
 * Both are constants of the *design*, true in every state, which is why [OmniBottomNav] derives its
 * paddings from them instead of hard-coding the 4 that only holds while `Home` is lit.
 */
private val NavIconInsetStart = 23.dp
private val NavIconInsetEnd = 26.dp

/**
 * The spec for every animated [Dp] in the bar — the pill's own 19 / 17 and the track insets that give
 * it back. One shared spec is the point: a spring's normalised response does not depend on amplitude,
 * so `4 → 23` and `19 → 0` stay a constant 23 the whole way across and the outer icons never budge.
 *
 * Non-bouncy, so it also cannot undershoot into the negative [Dp] that [padding] rejects. The bounce
 * the eye reads comes from [NavLabelEnterSpring] and the reflow it drives, which is 53 of travel
 * against these 36.
 */
private val NavShapeSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = 0.1.dp,
)

/**
 * The label's growth and collapse. Same [Spring.StiffnessLow] in both directions so the pill losing
 * its label and the pill gaining one trade width at the same tempo — mismatched stiffnesses made the
 * total content width dip mid-switch, which pumped all three gaps open and shut. Only the damping
 * differs: the incoming label overshoots a little (this is the bounce), the outgoing one must not,
 * because an undershooting [shrinkHorizontally] would hand a negative size to layout.
 */
private val NavLabelEnterSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow,
)
private val NavLabelExitSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)

/**
 * The pill's fill and the icon's tint, deliberately one spec: the icon is only ever legible because
 * it is the inverse of whatever is directly behind it, so the two must not be allowed to drift apart
 * mid-fade. Quicker than [NavShapeSpring] — colour has no distance to travel and lagging the layout
 * makes the pill look like it is catching up with itself.
 */
private val NavTintSpring = spring<Color>(stiffness = Spring.StiffnessMediumLow)

/** The bar floats 16 above the system navigation bar. */
val OmniNavBottomGap = 16.dp

// ---- Adaptive scaffold + navigation rail --------------------------------------------------------

/**
 * The shared chrome wrapper every tab-reachable screen uses to pick its navigation affordance by
 * orientation, so the portrait-vs-landscape switch lives in **one** place instead of in every screen.
 *
 * - **Portrait** overlays today's floating [OmniBottomNav] at the bottom — pixel-identical to before.
 *   The [content] fills the frame and scrolls under the bar exactly as it always has.
 * - **Landscape** puts an [OmniNavRail] on the left edge and gives [content] the remaining width. The
 *   screen's own [OmniHeader] and body then span that content column, starting after the rail.
 *
 * It reads [LocalDesignWindow] (the design-dp orientation signal from [com.example.omni.ui.DesignFrame])
 * rather than any physical-dp source. It is a *component* like [OmniHeader] and [OmniBottomNav]: each
 * screen still owns exactly one [com.example.omni.ui.DesignFrame] and invokes this inside it, so the
 * "one frame per screen" rule (`UI_ARCHITECTURE.md` §2a) holds — the bar is simply chosen here now.
 *
 * [selected] lights the matching rail/bar pill; pass a non-[NavBarItems] member (e.g. a header-opened
 * destination) to show the chrome with nothing lit, as the bar-bearing header pages already do.
 */
@Composable
fun OmniTabScaffold(
    selected: OmniNavItem,
    modifier: Modifier = Modifier,
    onNavigate: (OmniNavItem) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val window = LocalDesignWindow.current
    if (window.isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            OmniNavRail(
                selected = selected,
                onSelect = onNavigate,
                modifier = Modifier
                    .fillMaxHeight()
                    .systemBarsPadding()
                    .padding(start = OmniNavBottomGap, end = RailContentGap),
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            OmniBottomNav(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
            )
        }
    }
}

/**
 * The landscape navigation rail — the [OmniBottomNav] pill stood on its end.
 *
 * It reuses the bar's exact vocabulary: the same [NavBarItems], the same dark [OmniNavBar] track, and
 * the same [NavCell] whose white pill springs its label out on selection. Laid out as a centred [Column]
 * instead of a [Row], so the four tabs stack and the selected one grows its label to the right of its
 * icon, unselected ones staying bare 24 icons aligned to the same start edge. The rail width is fixed
 * ([RailWidth]) so the track itself does not resize as the pill animates.
 */
@Composable
fun OmniNavRail(
    selected: OmniNavItem,
    modifier: Modifier = Modifier,
    onSelect: (OmniNavItem) -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .width(RailWidth)
                .clip(RoundedCornerShape(35.dp))
                .background(OmniNavBar)
                .padding(horizontal = RailIconInset, vertical = RailVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(RailItemGap),
            horizontalAlignment = Alignment.Start,
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
}

/** Fixed rail width — wide enough for the widest expanded pill so the track never resizes. */
private val RailWidth = 140.dp

/** The rail track's own inset to its icons, mirroring the bar's 23 icon inset. */
private val RailIconInset = 19.dp

/** Top/bottom breathing room inside the rail track. */
private val RailVerticalPadding = 20.dp

/** Vertical gap between the four stacked tabs. */
private val RailItemGap = 14.dp

/** Gap between the rail and the content column in landscape. */
private val RailContentGap = 8.dp

