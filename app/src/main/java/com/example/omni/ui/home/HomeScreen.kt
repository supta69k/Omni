package com.example.omni.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.DefaultFiberGoal
import com.example.omni.data.model.DefaultSleepGoal
import com.example.omni.data.model.DefaultStepsGoal
import com.example.omni.data.model.DefaultWaterGoal
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.AdaptiveRow
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniTabScaffold
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniHomeWelcome
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniTheme

/**
 * The dashboard — Figma frame `iPhone 14 & 15 Pro - 34` (node 149:221).
 *
 * The artboard is 415 x 952: taller than a phone, because it is a scrolling page. Three things
 * live in page coordinates in Figma that have to become viewport-anchored here:
 *
 *  - the white header is marked sticky in the source, so it is an overlay and the content scrolls
 *    underneath it.
 *  - the dark pill of a bottom bar is drawn near the foot of the page but is plainly a floating
 *    bar; it is pinned above the system navigation bar instead.
 *  - the mock iOS status bar inside the header (a 9:41 clock and a battery) is replaced by the real
 *    one via [statusBarsPadding], exactly as the onboarding and auth screens already do. Everything
 *    below it keeps the design's own spacing, and [DesignFrame] is what makes the numbers literal
 *    rather than approximate — see its own note.
 *
 * The redesign also retired the Wellness / Recommended / Activity segmented control: the section is
 * now the single title "Daily updates & Recomindation" — spelling Figma's own — followed straight by
 * the card stack.
 *
 * Every value parameter defaults to the number the design draws, so `@DevicePreviews` renders the frame
 * exactly as Figma has it and the running app simply passes real ones in (BACKEND_PLAN §4 rule 2).
 *
 * @param startSleepSheetOpen previews only, the same trick `SosScreen` uses for its hospitals sheet: the
 *   sheet's open state is this screen's own, so a preview needs a way in.
 */
@Composable
fun HomeScreen(
    header: OmniHeaderState = OmniHeaderState(),
    glasses: Int = 7,
    waterGoal: Int = DefaultWaterGoal,
    steps: Int = 5_600,
    stepsGoal: Int = DefaultStepsGoal,
    stepPermissionNeeded: Boolean = false,
    fiberGrams: Float = 18f,
    fiberGoal: Float = DefaultFiberGoal,
    sleepHours: Float = 6.5f,
    sleepGoal: Float = DefaultSleepGoal,
    cprPercent: Int = 0,
    startSleepSheetOpen: Boolean = false,
    onExploreFirstAid: () -> Unit = {},
    onAddGlass: () -> Unit = {},
    onEnableStepTracking: () -> Unit = {},
    onLogSleep: (Float) -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    var sleepSheetOpen by remember { mutableStateOf(startSleepSheetOpen) }

    DesignFrame {
        OmniTabScaffold(
            selected = OmniNavItem.Home,
            onNavigate = onNavigate,
        ) {
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
                    HomeGreeting(name = header.greetingName)

                    Spacer(Modifier.height(BentoGap))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FirstAidCard(onExplore = onExploreFirstAid)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WaterCard(
                                glasses = glasses,
                                goal = waterGoal,
                                onAdd = onAddGlass,
                                modifier = Modifier.weight(WaterCardWeight),
                            )
                            StepsCard(
                                steps = steps,
                                goal = stepsGoal,
                                permissionNeeded = stepPermissionNeeded,
                                onEnableTracking = onEnableStepTracking,
                                modifier = Modifier.weight(StepsCardWeight),
                            )
                        }
                    }

                    Spacer(Modifier.height(SectionGap))

                    Text(
                        text = "Daily updates & Recomindation",
                        style = HomeType.SectionTitle,
                        color = OmniSectionTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(DashboardGap))

                    // The three cards stack in portrait — `AdaptiveRow` collapses to one column when
                    // the design window says portrait, so this branch is the only place the layout
                    // differs. Landscape splits the dashboard into two columns: the day's two
                    // goal readings (fiber, sleep) on the left, the percentile card (cpr) on the
                    // right. The hero block above keeps its portrait shape because it is one tall
                    // card sitting above the day-stat row, not a peer of the cards below.
                    AdaptiveRow(
                        horizontalSpacing = DashboardStackGap,
                    ) { columnIndex, _ ->
                        when (columnIndex) {
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(DashboardStackGap)) {
                                FiberCard(grams = fiberGrams, goalGrams = fiberGoal)
                                SleepCard(
                                    hours = sleepHours,
                                    goalHours = sleepGoal,
                                    onLog = { sleepSheetOpen = true },
                                )
                            }
                            else -> CprCard(percent = cprPercent)
                        }
                    }

                    Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
                }

                OmniHeader(
                    state = header,
                    onProfileClick = { onNavigate(OmniNavItem.Setting) },
                    onMessagesClick = { onNavigate(OmniNavItem.Messages) },
                    onNotificationsClick = { onNavigate(OmniNavItem.Notifications) },
                )
            }
        }

        // Sheet is sibling of scaffold so it overlays bar/rail (KDoc rule 11)
        SleepEntrySheet(
            visible = sleepSheetOpen,
            hours = sleepHours,
            onDismiss = { sleepSheetOpen = false },
            onSave = { hours ->
                sleepSheetOpen = false
                onLogSleep(hours)
            },
        )
    }
}

/**
 * "Welcome back Mahir" plus the waving hand — a 248-wide text and a 24 emoji.
 *
 * The 248 is why [name] arrives already clamped to one word (see
 * [OmniHeaderState.greetingName]): the slot has no room to grow, and `softWrap = false` here would
 * clip a long display name rather than wrap it.
 *
 * An empty [name] is the honest state while the profile document is still in flight, so the line drops
 * to "Welcome back" rather than trailing a space — the greeting still reads, and nothing shifts when
 * the name arrives a frame later.
 */
@Composable
private fun HomeGreeting(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (name.isEmpty()) "Welcome back" else "Welcome back $name",
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

// ---- Geometry ----------------------------------------------------------------------------------
//
// Every value below is derived from frame `iPhone 14 & 15 Pro - 34` (node 149:221), which is
// 415 x 952. The vertical map of the page is: header 0..101, greeting at y=112 (h 24), the bento
// block at y=169 (h 346 — the 169 hero, a 10 gap, and the 167 goal row), "Daily updates &
// Recomindation" at y=546, the card stack 20 below the title's 25 leading, and the nav bar floating
// above the page foot. Each gap constant is that map's arithmetic, written out so the source of the
// number is checkable.

/** The scrolling content's gutter — 16 in the source on both the bento and the dashboard. */
private val ScreenPadding = 16.dp

/** The header's height below the real status bar: 101 − 24.336 (the mock bar we drop). */
private val HeaderHeight = OmniHeaderHeight

/** 112 − 101 */
private val GreetingGap = 11.dp

/** 169 − (112 + 24) */
private val BentoGap = 33.dp

/** 546 − (169 + 169 + 10 + 167) — the goal row grew to 167 in this frame. */
private val SectionGap = 31.dp

/** The dashboard container's own 20 between the section title and the card stack. */
private val DashboardGap = 20.dp

/** The dashboard cards' own 24 between cards — also used as the landscape column gap. */
private val DashboardStackGap = 24.dp

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

@DevicePreviews
@Composable
private fun HomeScreenSleepSheetPreview() {
    OmniTheme { HomeScreen(startSleepSheetOpen = true) }
}
