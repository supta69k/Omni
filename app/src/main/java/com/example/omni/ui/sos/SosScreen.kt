package com.example.omni.ui.sos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.distanceLabel
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.MapType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCallButton
import com.example.omni.ui.theme.OmniHospitalCard
import com.example.omni.ui.theme.OmniHospitalName
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniMapSearchShadow
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniRouteButton
import com.example.omni.ui.theme.OmniSheetDetail
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSubtitle
import com.example.omni.ui.theme.OmniSheetSurface
import com.example.omni.ui.theme.OmniSheetTitle
import com.example.omni.ui.theme.OmniSosTrack
import com.example.omni.ui.theme.OmniTabInactive
import com.example.omni.ui.theme.OmniTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Emergency SOS — Figma frames `iPhone 14 & 15 Pro - 31` (node 134:18902) and `- 30` (node 134:6382).
 *
 * The two frames are one screen in two states, not two destinations: both draw the same white header,
 * the same centred search pill and a full-bleed map underneath, and they differ only in what sits over
 * the map's lower half.
 *
 *  - **- 31, at rest:** the red "Swipe to active SOS" track at y=728.
 *  - **- 30, activated:** the "Nearest Hospitals" sheet, and a map that has gained the facility
 *    callouts (which is why the two maps ship as separate images).
 *
 * Modelling them as one composable with [startActivated] is what keeps the bottom bar honest: the SOS
 * pill stays lit across both states, because the user never left the tab. Completing the swipe moves
 * between them and the sheet's grab handle — a link in the source, not decoration — moves back.
 *
 * Both frames draw the *Home* pill in Figma. That is the designer working from a duplicated artboard;
 * [UI_ARCHITECTURE.md](../../../../../../../../UI_ARCHITECTURE.md) §2a is explicit that a screen lights
 * the tab that opens it, so [OmniNavItem.Sos] is what ships.
 *
 * The map is a 3x raster of Figma's own `Full Map` node rather than a vector: the source is a street
 * network of several thousand paths, and the markers, the route line and the callouts are baked into it
 * exactly as drawn. Re-export both at 3x from nodes 172:12325 (`Full Map2 1`, 415x851) and 172:12321
 * (`Full Map 3`, 415x855) whenever the designer redraws them — the overlays above are placed from the
 * frame's coordinates, not the bitmap's, so a new map needs no code change.
 */
@Composable
fun SosScreen(
    header: OmniHeaderState = OmniHeaderState(),
    hospitals: List<Hospital> = DefaultHospitals,
    hasLocation: Boolean = false,
    /**
     * How many emergency contacts the account holds — the label on the sheet's alert pill, and the
     * only thing this screen needs to know about them. Zero turns the pill into the way to add one.
     */
    contactCount: Int = 0,
    onNavigate: (OmniNavItem) -> Unit = {},
    onSearch: () -> Unit = {},
    onFindRoute: (Hospital) -> Unit = {},
    onCallHospital: (Hospital) -> Unit = {},
    /**
     * The national emergency line. Offered when the directory is empty, which is the only state in
     * which every other way out of this screen is gone (BACKEND_PLAN §11 Phase 9 — the dial must
     * work "regardless of network state").
     */
    onCallEmergency: () -> Unit = {},
    /**
     * Texts the saved contacts, or opens the page to save one when there are none — the caller decides
     * which, because it is the same pill either way and only the router knows where that page is.
     */
    onAlertContacts: () -> Unit = {},
    /** Fires the moment the swipe completes — the screen's own signal that SOS was activated. */
    onActivate: () -> Unit = {},
    /**
     * Opens straight into the hospitals sheet. Exists so a preview can render `- 30` without a
     * gesture, and so a future alert can deep-link into it.
     */
    startActivated: Boolean = false,
) {
    var activated by remember { mutableStateOf(startActivated) }

    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            // The map starts where the header ends (Figma puts it at y=101, the header's own height)
            // and runs off the bottom of the artboard, so it is cropped rather than letterboxed on a
            // screen shorter than the 851 the design draws.
            Image(
                painter = painterResource(
                    if (activated) R.drawable.map_hospitals else R.drawable.map_sos,
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = OmniHeaderHeight),
            )

            // The header is opaque white in both frames; only the search pill floats on the map.
            Column(modifier = Modifier.fillMaxWidth()) {
                OmniHeader(
                    state = header,
                    onProfileClick = { onNavigate(OmniNavItem.Setting) },
                    onMessagesClick = { onNavigate(OmniNavItem.Messages) },
                    onNotificationsClick = { onNavigate(OmniNavItem.Notifications) },
                )

                Spacer(Modifier.height(SearchGap))

                MapSearchField(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = onSearch,
                )
            }

            AnimatedVisibility(
                visible = !activated,
                enter = slideInVertically(tween(StateChangeMillis)) { it } + fadeIn(tween(StateChangeMillis)),
                exit = slideOutVertically(tween(StateChangeMillis)) { it } + fadeOut(tween(StateChangeMillis)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = SliderStart, bottom = OmniNavBottomGap + OmniNavHeight + SliderNavGap),
            ) {
                SosSwipeTrack(onActivate = {
                    activated = true
                    onActivate()
                })
            }

            AnimatedVisibility(
                visible = activated,
                enter = slideInVertically(tween(StateChangeMillis)) { it } + fadeIn(tween(StateChangeMillis)),
                exit = slideOutVertically(tween(StateChangeMillis)) { it } + fadeOut(tween(StateChangeMillis)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            ) {
                HospitalSheet(
                    hospitals = hospitals,
                    hasLocation = hasLocation,
                    contactCount = contactCount,
                    onDismiss = { activated = false },
                    onFindRoute = onFindRoute,
                    onCall = onCallHospital,
                    onCallEmergency = onCallEmergency,
                    onAlertContacts = onAlertContacts,
                )
            }

            OmniBottomNav(
                selected = OmniNavItem.Sos,
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
 * The pill over the map — Figma `Search Container` (node 140:31806), 325 x 43.
 *
 * It is not the feed's search row: that one is 48 tall, fully rounded and filled #F5F5F5, while this
 * is a 14-radius card in the sheet's own off-white, lifted off the map by a shadow. Same icon and
 * same placeholder, different object.
 */
@Composable
private fun MapSearchField(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Box(
        modifier = modifier
            .width(SearchWidth)
            .height(SearchHeight)
            // Figma's `0 1 5.5 rgba(0,0,0,.25)` is a blur with almost no offset, which Compose's
            // single-elevation shadow cannot express; 2dp is the closest visual match.
            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = OmniMapSearchShadow, spotColor = OmniMapSearchShadow)
            .clip(RoundedCornerShape(14.dp))
            .background(OmniSheetSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 19.dp)
                .offset(y = 0.5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_feed_search),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Search here",
                style = MapType.SearchHint,
                color = OmniTabInactive,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * "Swipe to active SOS" — Figma `Component 4` (node 134:31718), a 376 x 61 track at radius 48.
 *
 * The design draws one state, the knob parked at its 23 inset, and names the gesture in the label. So
 * the knob is dragged rather than tapped: it follows the finger across the track, the label fades out
 * behind it, and a release short of [ActivateAt] springs it back. Nothing here is drawn that Figma
 * does not draw — the travel is the same knob moving inside the same track.
 */
@Composable
private fun SosSwipeTrack(modifier: Modifier = Modifier, onActivate: () -> Unit) {
    val travel = SliderWidth - SliderInset * 2 - SliderKnob
    val travelPx = with(LocalDensity.current) { travel.toPx() }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(SliderWidth)
            .height(SliderHeight)
            .clip(RoundedCornerShape(48.dp))
            .background(OmniSosTrack)
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { progress.snapTo((progress.value + delta / travelPx).coerceIn(0f, 1f)) }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (progress.value >= ActivateAt) {
                        progress.animateTo(1f)
                        onActivate()
                        progress.snapTo(0f)
                    } else {
                        progress.animateTo(0f)
                    }
                },
            ),
    ) {
        // The label sits at 23 + 46 + 27 — the knob's slot plus the design's own gap — and the track's
        // inner row is 0.5 off centre, which `CenterStart` absorbs.
        Text(
            text = "Swipe to active SOS",
            style = MapType.SosLabel,
            color = OmniOnInk,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SosLabelStart)
                .graphicsLayer { alpha = 1f - progress.value },
        )

        Image(
            painter = painterResource(R.drawable.ic_sos_knob),
            contentDescription = "Activate SOS",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SliderInset)
                .offset { IntOffset((progress.value * travelPx).roundToInt(), 0) }
                .size(SliderKnob),
        )
    }
}

/**
 * "Nearest Hospitals" — Figma `Container` (node 134:12624), a 360 x 480 sheet at radius 20.
 *
 * Its content stops 340 in; the rest is the empty tail the floating bar sits on, which is why the
 * sheet is bottom-anchored at its full height instead of wrapping its children. The card rail is a
 * 322-wide window onto a horizontal scroll, exactly as the source marks it — the second hospital is
 * off to the right, not below.
 *
 * **Layout change (`UI_ARCHITECTURE.md` §6 rule 8/11).** BACKEND_PLAN §11 Phase 9 asks activation to
 * "notify saved emergency contacts", and the design has nowhere to say so. Rather than a Material
 * component that would hoist itself out of `DesignFrame`, the alert pill is the sheet's *own*
 * `Call Container` shape dropped into that empty tail: bottom-anchored at [AlertPillBottom], which is
 * the floating bar's slot plus four, so it lands at y=343 — three clear of where the design's content
 * ends and 34 clear of the bar. Nothing the design draws moves.
 */
@Composable
private fun HospitalSheet(
    hospitals: List<Hospital>,
    hasLocation: Boolean,
    contactCount: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onFindRoute: (Hospital) -> Unit = {},
    onCall: (Hospital) -> Unit = {},
    onCallEmergency: () -> Unit = {},
    onAlertContacts: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .width(SheetWidth)
            .height(SheetHeight)
            // Figma stacks three shadows on this node (0/30/80 plus two tight drop-shadows); one
            // elevation is all Compose offers, and 10dp reads closest to the lift they add up to.
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(RoundedCornerShape(20.dp))
            .background(OmniSheetSurface),
    ) {
        // The handle is a link in the source, so it is what closes the sheet again.
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Hide the hospitals",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = GrabTop)
                .width(59.dp)
                .height(5.dp)
                .clickable(onClick = onDismiss),
        )

        Column(modifier = Modifier.padding(start = SheetPadding, top = SheetHeaderTop)) {
            Text(
                text = "Nearest Hospitals",
                style = MapType.SheetTitle,
                color = OmniSheetTitle,
                maxLines = 1,
                softWrap = false,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                // The design's "Found 5 facilities within 5 miles " is a mock literal with a stray
                // trailing space; the count is the list's own, the radius is the eight kilometres
                // the screen actually searches, and the unit is kilometres because the app targets
                // Bangladesh (BACKEND_PLAN §11 Phase 9). Without a location the line says so —
                // an unsorted directory pretending to be "nearest" would be a lie.
                text = when {
                    hospitals.isEmpty() -> "The hospital directory hasn't reached this phone yet"
                    hasLocation -> "Found ${hospitals.size} facilities within 8 km"
                    else -> "${hospitals.size} facilities — turn on location to sort by distance"
                },
                style = MapType.SheetSubtitle,
                color = OmniSheetSubtitle,
                maxLines = 2,
            )

            Spacer(Modifier.height(HospitalRailGap))

            if (hospitals.isEmpty()) {
                // The one state BACKEND_PLAN §11 Phase 9 refuses to leave dead: "always keep the
                // emergency dial button working regardless of network state". Every other route out
                // of this screen goes through a hospital card, so with no directory — a first run
                // that has never been online, and nothing in Firestore's cache — the sheet would
                // otherwise be an empty box in the middle of an emergency. 999 is Bangladesh's
                // national emergency line and needs neither the directory nor a data connection.
                Column(
                    modifier = Modifier.width(HospitalCardWidth),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "It needs one online moment to download, then works offline for good.",
                        style = MapType.SheetSubtitle,
                        color = OmniSheetDetail,
                        maxLines = 3,
                    )
                    HospitalAction(
                        icon = R.drawable.ic_hosp_call,
                        label = "Call 999",
                        fill = OmniCallButton,
                        labelColor = OmniInk,
                        labelStart = 78.dp,
                        onClick = onCallEmergency,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .width(HospitalCardWidth)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    hospitals.forEach { hospital ->
                        HospitalCard(hospital = hospital, onFindRoute = onFindRoute, onCall = onCall)
                    }
                }
            }
        }

        // The alert pill, in the tail. Red like the swipe track it answers to when there is somebody
        // to text; the hospital tray's grey when there is not, because "go and add one" is a settings
        // errand and should not shout like an emergency.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = SheetPadding, bottom = AlertPillBottom)
                .width(HospitalCardWidth),
        ) {
            HospitalAction(
                icon = R.drawable.ic_home_search_chat,
                label = when (contactCount) {
                    0 -> "Add Alert Contacts"
                    1 -> "Alert 1 Contact"
                    else -> "Alert $contactCount Contacts"
                },
                fill = if (contactCount == 0) OmniHospitalCard else OmniSosTrack,
                labelColor = if (contactCount == 0) OmniInk else OmniOnInk,
                labelStart = AlertLabelStart,
                onClick = onAlertContacts,
            )
        }
    }
}

/** One hospital — Figma `Hospital Container` (node 134:12631), 322 x 242. */
@Composable
private fun HospitalCard(hospital: Hospital, onFindRoute: (Hospital) -> Unit, onCall: (Hospital) -> Unit) {
    Column(
        modifier = Modifier.width(HospitalCardWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OmniHospitalCard),
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The photo alternates between the design's two — the directory carries no photo,
                // and two real photographs read better than one repeated six times.
                Image(
                    painter = painterResource(
                        if (hospital.id.hashCode() % 2 == 0) {
                            R.drawable.hospital_photo_1
                        } else {
                            R.drawable.hospital_photo_2
                        },
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(78.dp)
                        .height(103.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OmniBackground),
                )

                Column(
                    modifier = Modifier.width(194.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.width(183.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = hospital.name,
                            style = MapType.HospitalName,
                            color = OmniHospitalName,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = hospital.type,
                            style = MapType.HospitalType,
                            color = OmniSheetSubtitle,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_hosp_car),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            // The haversine distance in kilometres — the design's "4 mins (0.8 mi)"
                            // was a mock; a drive *time* would need the paid Routes API, which the
                            // plan explicitly declines (§11 Phase 9).
                            Text(
                                text = hospital.distanceLabel(),
                                style = MapType.SheetSubtitle,
                                color = OmniSheetDetail,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_hosp_star),
                                contentDescription = "Rating",
                                modifier = Modifier.size(18.dp),
                            )
                            // OpenStreetMap carries no ratings, so most real cards have none. The
                            // design's own "4.3" is a mock: printing it beside a hospital nobody has
                            // rated states a fact the directory does not hold. An em dash keeps the
                            // row's width and says the same thing honestly.
                            Text(
                                text = hospital.rating?.let { "%.1f".format(it) } ?: "—",
                                style = MapType.HospitalRating,
                                color = OmniSheetDetail,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        // Both buttons are full-width with the label group pushed in by the design's own asymmetric
        // padding — 85 on the route button, 78 on the call button — rather than centred.
        HospitalAction(
            icon = R.drawable.ic_hosp_location,
            label = "Find the Route",
            fill = OmniRouteButton,
            labelColor = OmniOnInk,
            labelStart = 85.dp,
            onClick = { onFindRoute(hospital) },
        )
        HospitalAction(
            icon = R.drawable.ic_hosp_call,
            label = "Call the Hospital",
            fill = OmniCallButton,
            // The source sets this #302E2E on the first card and pure black on the second; the two
            // are indistinguishable at 18px, so the ink token carries both.
            labelColor = OmniInk,
            labelStart = 78.dp,
            onClick = { onCall(hospital) },
        )
    }
}

/** A 322 x 50 action pill at radius 28 — Figma `Route Container` / `Call Container`. */
@Composable
private fun HospitalAction(
    icon: Int,
    label: String,
    fill: Color,
    labelColor: Color,
    labelStart: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(start = labelStart, top = 13.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MapType.HospitalName,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The preview default — the design's own two cards, promoted to the shared [Hospital] model so
 * `@DevicePreviews` renders the sheet exactly as Figma drew it while the real screen passes live
 * directory data.
 */
private val DefaultHospitals = listOf(
    Hospital(
        id = "city-central",
        name = "City Central Hospital",
        type = "Trauma Center Level 1 • 24/7 ER",
        lat = 22.3569,
        lng = 91.7832,
        rating = 4.3,
        distanceKm = 0.8,
    ),
    Hospital(
        id = "metro-general",
        name = "Metro General Care",
        type = "Urgent Care • Open till 10 PM",
        lat = 22.3375,
        lng = 91.8123,
        rating = 4.3,
        distanceKm = 2.1,
    ),
)

// ---- Geometry ----------------------------------------------------------------------------------
//
// From frames `- 31` (415 x 952) and `- 30` (415 x 957). Both put the header at 0..101, the search
// pill at (45, 113) and the map at y=101. `- 31` then adds the swipe track at (19, 728) with the bar
// at y=834; `- 30` adds the sheet at (28, 452).

/**
 * 113 − 24.336 − 76.664: from the bottom of the header *as [OmniHeader] emits it* — that composable
 * already carries the design's own 16.664 tail — down to the search pill at y=113.
 */
private val SearchGap = 12.dp

/** `Search Container`, centred: (415 − 325) / 2 = 45, which is its own x. */
private val SearchWidth = 325.dp
private val SearchHeight = 43.dp

private val SliderWidth = 376.dp
private val SliderHeight = 61.dp

/** The track's own x. 19 left, 20 right — the design's half-pixel asymmetry, kept. */
private val SliderStart = 19.dp

/** The knob's inset inside the track, and so the start of its travel. */
private val SliderInset = 23.dp
private val SliderKnob = 46.dp

/** 23 + 46 + 27 — the knob's slot plus the design's own gap before the label. */
private val SosLabelStart = 96.dp

/** 834 − 789: the clear air the design leaves between the track and the bar under it. */
private val SliderNavGap = 45.dp

/** How far the knob must travel before releasing it counts as a swipe rather than a fumble. */
private const val ActivateAt = 0.75f

/** Long enough for the eye to follow the sheet up, short enough not to delay an emergency. */
private const val StateChangeMillis = 280

private val SheetWidth = 360.dp
private val SheetHeight = 480.dp

/** The sheet's gutter — 18, three tighter than the pages' 21 and two wider than their 16. */
private val SheetPadding = 18.dp

/** 9 − 2.5: the handle's y, less the half-stroke `ic_hosp_grab` carries above its own line. */
private val GrabTop = 6.5.dp

private val SheetHeaderTop = 27.dp

/** 98 − (27 + 28 + 4 + 20): from the bottom of the subtitle to the card rail. */
private val HospitalRailGap = 19.dp

private val HospitalCardWidth = 322.dp

/**
 * The alert pill's bottom inset — [OmniNavHeight] + [OmniNavBottomGap] + 4, measured from the same
 * navigation-bar-padded bottom the floating bar is placed from. In a 480 sheet that puts the 50-high
 * pill at y=343: three below where the design's own content ends at 340, and four above the bar.
 */
private val AlertPillBottom = OmniNavHeight + OmniNavBottomGap + 4.dp

/**
 * 78 is the call button's inset and the pill borrows it, which leaves 322 − 78 − 24 − 12 = 208 for the
 * label — enough for "Add Alert Contacts" at 18px, the longest of the three.
 */
private val AlertLabelStart = 78.dp

@DevicePreviews
@Composable
private fun SosScreenPreview() {
    OmniTheme { SosScreen() }
}

@DevicePreviews
@Composable
private fun SosScreenActivatedPreview() {
    OmniTheme { SosScreen(startActivated = true, hasLocation = true, contactCount = 2) }
}

/** The state a fresh install in airplane mode lands on: no directory, and 999 as the way out. */
@DevicePreviews
@Composable
private fun SosScreenEmptyDirectoryPreview() {
    OmniTheme { SosScreen(startActivated = true, hospitals = emptyList()) }
}
