package com.example.omni.ui.sos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.LatLng
import com.example.omni.data.model.distanceLabel
import com.example.omni.data.model.etaLabel
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.components.OmniHeader
import com.example.omni.ui.components.OmniTabScaffold
import com.example.omni.ui.components.OmniHeaderHeight
import com.example.omni.ui.components.OmniHeaderState
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.MapType
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCallButton
import com.example.omni.ui.theme.OmniHeroPink
import com.example.omni.ui.theme.OmniHospitalCard
import com.example.omni.ui.theme.OmniHospitalName
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniMapSearchShadow
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniRouteButton
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.OmniSheetDetail
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSubtitle
import com.example.omni.ui.theme.OmniSheetSurface
import com.example.omni.ui.theme.OmniSheetTitle
import com.example.omni.ui.theme.OmniSosTrack
import com.example.omni.ui.theme.OmniTabInactive
import com.example.omni.ui.theme.OmniTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Emergency SOS — Figma frames `iPhone 14 & 15 Pro - 31` (node 134:18902), `- 30` (node 134:6382) and
 * the map study `172:14`.
 *
 * The frames are one screen in two states, not two destinations: both draw the same white header, the
 * same centred search pill and a full-bleed map underneath, and they differ only in what sits over the
 * map's lower half.
 *
 *  - **- 31, at rest:** the red "Swipe to active SOS" track at y=728.
 *  - **- 30, with the sheet:** the "Nearest Hospitals" sheet over a map showing facility callouts.
 *
 * Modelling them as one composable with [startWithSheet] is what keeps the bottom bar honest: the SOS
 * pill stays lit across both states, because the user never left the tab. Completing the swipe *or*
 * tapping a pin moves between them, and the sheet's grab handle — a link in the source, not decoration
 * — moves back. Only the swipe raises [onActivate]; a pin tap is navigation, not an emergency.
 *
 * Both frames draw the *Home* pill in Figma. That is the designer working from a duplicated artboard;
 * [UI_ARCHITECTURE.md](../../../../../../../../UI_ARCHITECTURE.md) §2a is explicit that a screen lights
 * the tab that opens it, so [OmniNavItem.Sos] is what ships.
 *
 * **Layout change (`UI_ARCHITECTURE.md` §6 rules 8/9/11).** The map is no longer the 3x raster of
 * Figma's `Full Map` node — it is [OmniMap], a live MapLibre view over OpenStreetMap vector tiles, styled
 * from `res/raw/omni_map_style.json` into the same palette the bitmap was drawn in (white land, pale
 * blue-grey water, hero-pink primary roads, call-button teal side streets). The bitmap's baked-in pins,
 * callouts and route line become [OmniMapPins] and the route layers, which draw the *real* directory at
 * the *real* coordinates. Three things the design does not contain follow from that and are built out of
 * parts it already owns:
 *
 *  1. The search pill becomes a real field. Same 325 x 43 box, same icon, same placeholder; "Clear"
 *     appears in the tail only while there is something to clear.
 *  2. A notice banner in the pill's own shape sits under it, for the eleven states a live map has and a
 *     mock does not — no permission, location off, no directory, no match, no route, no tiles. It is the
 *     only thing on the screen that can say "this did not work", and it replaces every silent failure.
 *  3. A recenter control, [RecenterSize] round in the sheet surface, drawn from the palette because the
 *     design ships no crosshair asset. It hides itself when there is no fix to centre on.
 *
 * **The directory is facilities, not just hospitals (`UI_ARCHITECTURE.md` §6 rule 9).** The
 * `pharmacies` collection rides the same pipeline — seeded by the same script, mapped to the same
 * [Hospital] shape, measured and sorted by the same ViewModel — so the sheet's rail and the map's
 * pins carry both. The design's sheet title "Nearest Hospitals" becomes "Nearest Facilities" to stay
 * truthful about a list that now mixes the two, matching the subtitle's own "Found N facilities
 * within 8 km"; the pharmacy pins are the call button's teal with a "P" where the hospital pin wears
 * its "H". Pharmacies are an Omni+ perk (the paywall names it): a free account's directory is
 * hospitals only, because the ViewModel never opens the pharmacy listener without the entitlement —
 * while hospitals, distances, routes and 999 are never gated, on a screen whose own rule is that
 * nothing blocks a way out. Nothing else the design draws moves.
 *
 * Nothing the design *does* draw moves.
 */
@Composable
fun SosScreen(
    header: OmniHeaderState = OmniHeaderState(),
    /** The nearby, searched, distance-sorted slice of the directory — what the rail and the pins show. */
    hospitals: List<Hospital> = DefaultHospitals,
    selectedId: String? = DefaultHospitals.first().id,
    nearestId: String? = DefaultHospitals.first().id,
    /** Where the phone is, or `null` while there is no fix — the user dot and the route's origin. */
    userLocation: LatLng? = null,
    routeStatus: RouteStatus = RouteStatus.Idle,
    locationStatus: LocationStatus = LocationStatus.Locating,
    query: String = "",
    /**
     * How many hospitals the directory holds *before* the radius and the search cut it down. Zero and
     * [hospitals] empty is "nothing has downloaded"; non-zero and [hospitals] empty is "nothing matches",
     * and the two need opposite answers.
     */
    directorySize: Int = hospitals.size,
    /** Bumped by the ViewModel to mean "put the camera back on the user"; see [OmniMap]. */
    recenterTick: Int = 0,
    /**
     * How many emergency contacts the account holds — the label on the sheet's alert pill, and the
     * only thing this screen needs to know about them. Zero turns the pill into the way to add one.
     */
    contactCount: Int = 0,
    /** True until the directory's first snapshot lands, so "empty" is not announced before it is true. */
    loading: Boolean = false,
    onNavigate: (OmniNavItem) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSelectHospital: (String) -> Unit = {},
    /** Routes to this hospital, selecting it on the way — one tap from any card on the rail. */
    onFindRoute: (Hospital) -> Unit = {},
    onRecenter: () -> Unit = {},
    onCallHospital: (Hospital) -> Unit = {},
    /**
     * The national emergency line. Offered whenever the directory is empty, which is the only state in
     * which every other way out of this screen is gone (BACKEND_PLAN §11 Phase 9 — the dial must
     * work "regardless of network state").
     */
    onCallEmergency: () -> Unit = {},
    /**
     * Texts the saved contacts, or opens the page to save one when there are none — the caller decides
     * which, because it is the same control either way and only the router knows where that page is.
     */
    onAlertContacts: () -> Unit = {},
    /** Re-shows the system location dialog. Offered only while the OS will still show it. */
    onRequestLocation: () -> Unit = {},
    /** Omni's own page in system settings — the only way back from a permanent refusal. */
    onOpenAppSettings: () -> Unit = {},
    /** The phone's location toggle, for when the permission is granted but the radio is off. */
    onOpenLocationSettings: () -> Unit = {},
    /** Fires the moment the swipe completes — the screen's own signal that SOS was activated. */
    onActivate: () -> Unit = {},
    /**
     * Opens straight into the hospitals sheet. Exists so a preview can render `- 30` without a
     * gesture, and so a future alert can deep-link into it.
     */
    startWithSheet: Boolean = false,
) {
    var sheetOpen by remember { mutableStateOf(startWithSheet) }
    // The one thing about the map only the map knows. Held here because the notice banner is the
    // screen's single place for "this did not work", including "the tiles never arrived".
    var styleError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    val notice = mapNotice(
        styleError = styleError,
        locationStatus = locationStatus,
        routeStatus = routeStatus,
        query = query,
        visible = hospitals.size,
        directorySize = directorySize,
        loading = loading,
        onRequestLocation = onRequestLocation,
        onOpenAppSettings = onOpenAppSettings,
        onOpenLocationSettings = onOpenLocationSettings,
        onClearQuery = { onQueryChange("") },
        onCallEmergency = onCallEmergency,
    )

    DesignFrame {
        // Converted here, inside the frame, because these are artboard units: only this density knows
        // what a 480dp sheet is in the physical pixels MapLibre's camera padding is measured in.
        val density = LocalDensity.current
        val topInsetPx = with(density) {
            (SearchGap + SearchHeight + if (notice != null) NoticeGap + NoticeMinHeight else 0.dp).roundToPx()
        }
        val bottomInsetPx = with(density) {
            (if (sheetOpen) SheetHeight else SwipeSlot).roundToPx()
        }

        OmniTabScaffold(
            selected = OmniNavItem.Sos,
            onNavigate = onNavigate,
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            // The sheet is a fixed 480dp tall in the artboard, which is taller than a landscape
            // viewport (~415 design-dp). Left at 480 it is bottom-anchored, so the overflow clips off
            // the *top* — the grab handle and "Nearest Hospitals" title disappear exactly when the user
            // needs them. Its real content ends around 260dp (title, subtitle, one 114dp card row), so
            // capping the height to what the viewport can show trims only the empty tail and keeps the
            // header uncovered. In portrait the cap never bites (480 < the tall viewport), so nothing
            // there changes.
            val sheetHeight = SheetHeight.coerceAtMost(maxHeight - OmniHeaderHeight)

            // The map starts where the header ends (Figma puts it at y=101, the header's own height)
            // and runs off the bottom of the artboard. Its own box is therefore what the insets above
            // are measured against, and the opaque header never covers a tile the camera paid for.
            val mapModifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = OmniHeaderHeight)

            if (LocalInspectionMode.current) {
                // The preview renderer has no GL surface and no native library, so a real MapView
                // would take the whole pane down with it. The land colour and a label are the honest
                // stand-in; every overlay above still previews exactly as it ships.
                Box(mapModifier.background(OmniBackground), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Map renders on device",
                        style = MapType.SheetSubtitle,
                        color = OmniTabInactive,
                    )
                }
            } else {
                OmniMap(
                    hospitals = hospitals,
                    selectedId = selectedId,
                    nearestId = nearestId,
                    userLocation = userLocation,
                    route = (routeStatus as? RouteStatus.Ready)?.route,
                    recenterTick = recenterTick,
                    onSelectHospital = { id ->
                        // A pin tap is the design's own way into the sheet, and it must not record an
                        // SOS event — that is the swipe's job, and only the swipe's.
                        focus.clearFocus()
                        onSelectHospital(id)
                        sheetOpen = true
                    },
                    onStyleReady = { styleError = null },
                    onStyleFailed = { styleError = it },
                    modifier = mapModifier,
                    topInsetPx = topInsetPx,
                    bottomInsetPx = bottomInsetPx,
                )
            }

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
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                if (notice != null) {
                    Spacer(Modifier.height(NoticeGap))
                    NoticeBanner(
                        notice = notice,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            // Recenter sits above whatever is covering the map's bottom, so it never ends up under the
            // sheet — and animates between the two slots for the same reason the sheet slides.
            val recenterBottom by animateDpAsState(
                targetValue = (if (sheetOpen) SheetHeight else SwipeSlot) + RecenterGap,
                animationSpec = tween(StateChangeMillis),
                label = "recenter",
            )
            AnimatedVisibility(
                visible = userLocation != null,
                enter = fadeIn(tween(StateChangeMillis)),
                exit = fadeOut(tween(StateChangeMillis)),
                // Portrait: bottom-end (Figma's right corner). Landscape: bottom-start so the
                // button clears the side-docked hospitals sheet sitting at center-end.
                modifier = Modifier
                    .align(if (LocalDesignWindow.current.isLandscape) Alignment.BottomStart else Alignment.BottomEnd)
                    .navigationBarsPadding()
                    // The end-padding is what puts a 21dp gutter between the button and the right edge
                    // when it is anchored at BottomEnd (portrait). In landscape the button sits at
                    // BottomStart, so the end-padding just floats it 21dp off the rail — drop it.
                    .padding(
                        end = if (LocalDesignWindow.current.isLandscape) 0.dp else RecenterEnd,
                        bottom = recenterBottom,
                    ),
            ) {
                RecenterButton(onClick = onRecenter)
            }

            AnimatedVisibility(
                visible = !sheetOpen,
                enter = slideInVertically(tween(StateChangeMillis)) { it } + fadeIn(tween(StateChangeMillis)),
                exit = slideOutVertically(tween(StateChangeMillis)) { it } + fadeOut(tween(StateChangeMillis)),
                // Landscape has no bottom bar — the navigation is a rail on the left edge — so the
                // clearance only matters in portrait.
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = SliderStart,
                        bottom = if (LocalDesignWindow.current.isLandscape) {
                            SliderNavGap
                        } else {
                            OmniNavBottomGap + OmniNavHeight + SliderNavGap
                        },
                    ),
            ) {
                SosSwipeTrack(onActivate = {
                    sheetOpen = true
                    onActivate()
                })
            }

            AnimatedVisibility(
                visible = sheetOpen,
                // In portrait the sheet rises from the bottom edge (Figma's own slide). Landscape
                // moves it to the right edge — the sheet is 360 wide on a ~859dp content column, so
                // it sits inside the map's right gutter and the map continues to fill the rest of
                // the pane, instead of being covered by a 480-tall sheet that would clip the
                // grab handle off the top of a ~415dp tall window.
                enter = if (LocalDesignWindow.current.isLandscape) {
                    slideInHorizontally(tween(StateChangeMillis)) { it } + fadeIn(tween(StateChangeMillis))
                } else {
                    slideInVertically(tween(StateChangeMillis)) { it } + fadeIn(tween(StateChangeMillis))
                },
                exit = if (LocalDesignWindow.current.isLandscape) {
                    slideOutHorizontally(tween(StateChangeMillis)) { it } + fadeOut(tween(StateChangeMillis))
                } else {
                    slideOutVertically(tween(StateChangeMillis)) { it } + fadeOut(tween(StateChangeMillis))
                },
                modifier = Modifier
                    .align(if (LocalDesignWindow.current.isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                    .navigationBarsPadding(),
            ) {
                HospitalSheet(
                    hospitals = hospitals,
                    selectedId = selectedId,
                    nearestId = nearestId,
                    hasLocation = userLocation != null,
                    routeStatus = routeStatus,
                    query = query,
                    directorySize = directorySize,
                    contactCount = contactCount,
                    sheetHeight = sheetHeight,
                    onDismiss = { sheetOpen = false },
                    onSelect = onSelectHospital,
                    onFindRoute = onFindRoute,
                    onCall = onCallHospital,
                    onCallEmergency = onCallEmergency,
                    onAlertContacts = onAlertContacts,
                    onClearQuery = { onQueryChange("") },
                )
            }

            // The floating bar (portrait) / rail (landscape) is supplied by OmniTabScaffold now.
            // In landscape the sheet above sits in the content column, beside the rail; in portrait
            // the bar floats over the sheet's foot exactly as Figma frame `- 30` draws it.
        }
        }
    }
}

/**
 * The pill over the map — Figma `Search Container` (node 140:31806), 325 x 43.
 *
 * It is not the feed's search row: that one is 48 tall, fully rounded and filled #F5F5F5, while this
 * is a 14-radius card in the sheet's own off-white, lifted off the map by a shadow. Same icon and
 * same placeholder, different object.
 *
 * **Layout change (`UI_ARCHITECTURE.md` §6 rule 8).** The design's static "Search here" becomes a real
 * single-line field; the placeholder is what it draws while [query] is empty, so the resting state is
 * pixel-identical to the mock. Typing filters the directory (debounced in the ViewModel, not here), and
 * the IME's Search key only dismisses the keyboard because the results are already live.
 *
 * "Clear" is a word rather than an icon on purpose: the design ships no close glyph, and inventing one
 * by rotating the feed's `+` would be a private joke rather than a component. 325 − 19 − 24 − 6 leaves
 * 276 for the row, of which the word takes 33 at 13px, so the field itself keeps ~230 — around 26
 * characters of a hospital name, which is more than anyone types before the list narrows to one.
 *
 * The field keeps its own [TextFieldValue] instead of rendering [query] straight. A hoisted value has
 * to travel to the ViewModel and back before it reaches the glyph on screen, and at typing speed the
 * next key lands mid-round-trip: the field redraws with the value from *before* that key and the
 * character disappears. Local state means the text is on screen the frame it is typed; [query] is
 * still watched, but only to obey an instruction the field did not issue itself — the notice's
 * "Clear", or the ViewModel's length cap.
 */
@Composable
private fun MapSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current

    var field by remember { mutableStateOf(TextFieldValue(query)) }

    /** The last text this field sent up. Anything else arriving from above is a real instruction. */
    var sent by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        if (query != sent) {
            field = TextFieldValue(query, TextRange(query.length))
            sent = query
        }
    }

    fun edit(value: TextFieldValue) {
        field = value
        sent = value.text
        onQueryChange(value.text)
    }

    Box(
        modifier = modifier
            .width(SearchWidth)
            .height(SearchHeight)
            // Figma's `0 1 5.5 rgba(0,0,0,.25)` is a blur with almost no offset. On the white mock 2dp
            // read as the same lift; over live tiles it vanished into the map, so the pill is raised to
            // 6 — the shadow the design *means*, on the background the design never had.
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = OmniMapSearchShadow, spotColor = OmniMapSearchShadow)
            .clip(RoundedCornerShape(14.dp))
            .background(OmniSheetSurface),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 19.dp, end = 14.dp)
                .offset(y = 0.5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_feed_search),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            BasicTextField(
                value = field,
                onValueChange = ::edit,
                modifier = Modifier.weight(1f),
                textStyle = MapType.SearchHint.copy(color = OmniInk),
                singleLine = true,
                cursorBrush = SolidColor(OmniInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Nothing to submit — the list has been filtering since the second character. The key
                // puts the keyboard away, which is the only thing left to want.
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) {
                            Text(
                                text = "Search here",
                                style = MapType.SearchHint,
                                color = OmniTabInactive,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        input()
                    }
                },
            )

            if (field.text.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = MapType.HospitalType,
                    color = OmniAuthHeading,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clickable {
                        edit(TextFieldValue(""))
                        focus.clearFocus()
                    },
                )
            }
        }
    }
}

/**
 * The one line on this screen that can say "that did not work" — the search pill's shape, reused.
 *
 * **Layout change (`UI_ARCHITECTURE.md` §6 rule 11.)** A live map has states a mock does not: no
 * permission, a permanent refusal, the location radio off, an empty directory, a search with no match, a
 * route that failed, tiles that never loaded. The plan requires every one of them to be *named* rather
 * than shown as a blank rectangle, and the design has nowhere to name them. So this is built from parts
 * the screen already owns — [SearchWidth] wide, radius 14, [OmniSheetSurface], the same shadow — and
 * placed directly under the pill it belongs to. It wraps its own height rather than fixing it, because
 * the longest message runs to three lines on a narrow phone.
 *
 * At most one shows at a time, in the priority [mapNotice] sets: the thing the user can act on first.
 */
@Composable
private fun NoticeBanner(notice: MapNotice, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(SearchWidth)
            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = OmniMapSearchShadow, spotColor = OmniMapSearchShadow)
            .clip(RoundedCornerShape(14.dp))
            .background(OmniSheetSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notice.text,
            style = MapType.SheetSubtitle,
            color = OmniSheetDetail,
            maxLines = 3,
            modifier = Modifier.weight(1f),
        )

        if (notice.action != null) {
            Text(
                text = notice.action,
                style = MapType.SheetSubtitle,
                color = OmniAuthHeading,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .pressEffect()
                    .clickable(onClick = notice.onAction),
            )
        }
    }
}

/** One message and, when there is something to do about it, the word that does it. */
private class MapNotice(
    val text: String,
    val action: String? = null,
    val onAction: () -> Unit = {},
)

/**
 * Which of the map's failure states to name, in the order the user can act on them.
 *
 * Priority, top down: a map that never loaded beats everything, because nothing else on the screen is
 * true without it. Then the location chain, because it is the reason "nearest" would otherwise be a
 * guess — and each rung has a different way out (Settings for a block, the dialog for a refusal, the
 * radio toggle for a switched-off phone). Then an empty directory, which is the state that makes 999 the
 * only working button. Then a search that matched nothing, then a route that failed — last of the real
 * errors because the card's own button already reads "Retry the Route", so the banner only has to
 * explain *why*. "Finding your location…" is not an error and sits below all of them.
 *
 * Pure, so the whole table can be read in one place rather than traced through a composable.
 */
private fun mapNotice(
    styleError: String?,
    locationStatus: LocationStatus,
    routeStatus: RouteStatus,
    query: String,
    visible: Int,
    directorySize: Int,
    loading: Boolean,
    onRequestLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onClearQuery: () -> Unit,
    onCallEmergency: () -> Unit,
): MapNotice? = when {
    styleError != null -> MapNotice(styleError)

    locationStatus == LocationStatus.Blocked -> MapNotice(
        "Location is blocked for Omni, so the directory can't be sorted by distance.",
        "Settings",
        onOpenAppSettings,
    )

    locationStatus == LocationStatus.Denied -> MapNotice(
        "Allow location and Omni will find the facility nearest you.",
        "Allow",
        onRequestLocation,
    )

    locationStatus == LocationStatus.ServicesOff -> MapNotice(
        "Your phone's location is switched off, so distances can't be measured.",
        "Settings",
        onOpenLocationSettings,
    )

    directorySize == 0 && !loading -> MapNotice(
        "The facilities directory hasn't reached this phone yet. 999 works without it.",
        "Call 999",
        onCallEmergency,
    )

    query.isNotBlank() && visible == 0 -> MapNotice(
        "Nothing in the directory matches that search.",
        "Clear",
        onClearQuery,
    )

    routeStatus is RouteStatus.Failed -> MapNotice(routeStatus.reason)

    locationStatus == LocationStatus.Locating -> MapNotice("Finding your location…")

    else -> null
}

/**
 * "Put the camera back on me" — [RecenterSize] round, in the sheet's surface, with the same lift as the
 * search pill.
 *
 * **Layout change (`UI_ARCHITECTURE.md` §6 rule 11.)** A live map can be panned, so it needs a way back;
 * the design, drawing a bitmap that could not move, ships no crosshair asset. The glyph is drawn from the
 * palette instead of exported — a ring, a dot and four ticks, in the route's own purple, so the button
 * and the thing it centres on are visibly the same colour. It is only composed while there is a fix to
 * centre on, which is what keeps it from being a dead tap (§2a rule 4).
 */
@Composable
private fun RecenterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(RecenterSize)
            .pressEffect()
            .shadow(3.dp, RoundedCornerShape(percent = 50), ambientColor = OmniMapSearchShadow, spotColor = OmniMapSearchShadow)
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniSheetSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(RecenterGlyph)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            val ring = outer * RingRadius
            val line = outer * GlyphStroke

            drawCircle(RecenterTint, radius = ring, center = centre, style = Stroke(line))
            drawCircle(RecenterTint, radius = outer * DotRadius, center = centre)

            // Four ticks at the compass points, from the ring out to the edge — the crosshair half of
            // the glyph, and what stops the ring from reading as a plain circle.
            for (degrees in 0 until 360 step 90) {
                val radians = Math.toRadians(degrees.toDouble())
                val dx = cos(radians).toFloat()
                val dy = sin(radians).toFloat()
                drawLine(
                    color = RecenterTint,
                    start = Offset(centre.x + dx * ring, centre.y + dy * ring),
                    end = Offset(centre.x + dx * outer, centre.y + dy * outer),
                    strokeWidth = line,
                    cap = StrokeCap.Round,
                )
            }
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
 *
 * **Why the progress is a plain float and not an `Animatable`.** It was one, written from
 * `scope.launch { progress.snapTo(...) }` inside the drag callback, and on a real phone the gesture
 * stuck about one swipe in three. `Animatable` serialises through a mutator mutex, so every launched
 * `snapTo` cancels the one before it; deltas arrive faster than coroutines get dispatched, so the
 * cancelled ones took their movement with them and the knob stalled under the finger. A drag is
 * already synchronous — [rememberDraggableState] hands over the delta on the input frame — so the
 * value it moves must be settable on that frame too. Only the release, which really is an animation,
 * goes through a coroutine, and it holds the one [Job] that can be cancelled by the next touch.
 */
@Composable
private fun SosSwipeTrack(modifier: Modifier = Modifier, onActivate: () -> Unit) {
    val travel = SliderWidth - SliderInset * 2 - SliderKnob
    val travelPx = with(LocalDensity.current) { travel.toPx() }

    var progress by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(SliderWidth)
            .height(SliderHeight)
            .clip(RoundedCornerShape(48.dp))
            .background(OmniSosTrack)
            .draggable(
                state = rememberDraggableState { delta ->
                    progress = (progress + delta / travelPx).coerceIn(0f, 1f)
                },
                orientation = Orientation.Horizontal,
                // A finger landing on a knob that is still springing home should catch it, not wait
                // for the animation to finish and the touch slop to be crossed all over again. That
                // wait is the other half of what "sometimes stuck" felt like.
                startDragImmediately = true,
                onDragStarted = { settle?.cancel() },
                onDragStopped = { velocity ->
                    settle?.cancel()
                    // Past three quarters, or flicked hard enough from past the halfway mark: a
                    // deliberate throw is as clear a "yes" as carrying the knob all the way, and in an
                    // emergency the shorter gesture is the one that gets made.
                    if (progress >= ActivateAt || (velocity >= FlingActivate && progress >= FlingFrom)) {
                        progress = 1f
                        onActivate()
                        // The sheet takes the track's place, so this usually never runs — the track is
                        // disposed mid-delay and `remember` hands back a fresh 0 next time it is
                        // shown. It exists for the case where the swipe changes nothing on screen,
                        // where a knob abandoned at the far end would be a dead control.
                        settle = scope.launch {
                            delay(ResetDelayMillis)
                            animate(1f, 0f) { value, _ -> progress = value }
                        }
                    } else {
                        settle = scope.launch {
                            animate(progress, 0f, animationSpec = SpringBack) { value, _ -> progress = value }
                        }
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
                .graphicsLayer { alpha = 1f - progress },
        )

        Image(
            painter = painterResource(R.drawable.ic_sos_knob),
            contentDescription = "Activate SOS",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SliderInset)
                .offset { IntOffset((progress * travelPx).roundToInt(), 0) }
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
 * **Layout change (`UI_ARCHITECTURE.md` §6 rule 8/11).** Three, all forced by the rail now holding the
 * real directory rather than two mock cards:
 *
 *  1. The scroll becomes a [LazyRow] keyed on hospital id, and follows [selectedId]. Tapping a pin on
 *     the map scrolls the rail to that hospital; tapping a card selects it, which moves the map. One
 *     selection, two views of it — the source's own 322 window, item-snapped so a card is never half on
 *     screen. Lazy because the directory runs to a hundred-odd entries and the design's card is not
 *     cheap.
 *  2. BACKEND_PLAN §11 Phase 9 asks activation to "notify saved emergency contacts". The design says so
 *     with the glyph in the header's right corner and nothing else — no label, so the count the control
 *     used to spell out lives in its content description instead. It was a full-width pill in the
 *     sheet's tail until node 134:12625 replaced it; the tail is empty again, and the floating bar sits
 *     on it as the source draws it.
 *  3. An empty rail is two different facts, and each gets the pill that answers it: no directory at all
 *     offers 999, a search that matched nothing offers to clear itself.
 */
@Composable
private fun HospitalSheet(
    hospitals: List<Hospital>,
    selectedId: String?,
    nearestId: String?,
    hasLocation: Boolean,
    routeStatus: RouteStatus,
    query: String,
    directorySize: Int,
    contactCount: Int,
    /** Height the sheet is allowed to occupy — [SheetHeight] in portrait, capped to the viewport in landscape. */
    sheetHeight: Dp = SheetHeight,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onSelect: (String) -> Unit = {},
    onFindRoute: (Hospital) -> Unit = {},
    onCall: (Hospital) -> Unit = {},
    onCallEmergency: () -> Unit = {},
    onAlertContacts: () -> Unit = {},
    onClearQuery: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .width(SheetWidth)
            .height(sheetHeight)
            // Figma stacks three shadows on this node (0/30/80 plus two tight drop-shadows); one
            // elevation is all Compose offers, and 10dp reads closest to the lift they add up to.
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(RoundedCornerShape(20.dp))
            .background(OmniSheetSurface),
    ) {
        // The handle is a link in the source, so it is what closes the sheet again.
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Hide the facilities",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = GrabTop)
                .width(59.dp)
                .height(5.dp)
                .pressEffect()
                .clickable(onClick = onDismiss),
        )

        // Bounded to the rail's own 322 rather than left to run to the sheet's edge: the subtitle is
        // live text now, and its longest variant would otherwise wrap at 342 and pass under the alert
        // glyph in the corner. 322 is where every other row on this sheet already ends.
        Column(
            modifier = Modifier
                .padding(start = SheetPadding, top = SheetHeaderTop)
                .width(HospitalCardWidth),
        ) {
            Text(
                text = "Nearest Facilities",
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
                // an unsorted directory pretending to be "nearest" would be a lie. The search case
                // does not quote the query back: a 60-character one would not fit the two lines the
                // design leaves here, and the field above still shows it.
                text = when {
                    directorySize == 0 -> "The facilities directory hasn't reached this phone yet"
                    hospitals.isEmpty() -> "Nothing in the directory matches that search"
                    hasLocation -> "Found ${hospitals.size} facilities within 8 km"
                    else -> "${hospitals.size} facilities — turn on location to sort by distance"
                },
                style = MapType.SheetSubtitle,
                color = OmniSheetSubtitle,
                maxLines = 2,
            )

            Spacer(Modifier.height(HospitalRailGap))

            when {
                // The one state BACKEND_PLAN §11 Phase 9 refuses to leave dead: "always keep the
                // emergency dial button working regardless of network state". Every other route out
                // of this screen goes through a hospital card, so with no directory — a first run
                // that has never been online, and nothing in Firestore's cache — the sheet would
                // otherwise be an empty box in the middle of an emergency. 999 is Bangladesh's
                // national emergency line and needs neither the directory nor a data connection.
                directorySize == 0 -> EmptyRail(
                    message = "It needs one online moment to download, then works offline for good.",
                    icon = R.drawable.ic_hosp_call,
                    label = "Call 999",
                    fill = OmniCallButton,
                    onClick = onCallEmergency,
                )

                // A directory that exists and a search that matched none of it. Nothing is wrong, so
                // nothing shouts: the tray's own grey, and the way back is to drop the filter.
                hospitals.isEmpty() -> EmptyRail(
                    message = "Clear the search to see every facility near you again.",
                    icon = R.drawable.ic_feed_search,
                    label = "Clear the Search",
                    fill = OmniHospitalCard,
                    onClick = onClearQuery,
                )

                else -> {
                    val rail = rememberLazyListState()
                    val selectedIndex = hospitals.indexOfFirst { it.id == selectedId }

                    // The rail's half of "one selection, two views": a pin tap on the map lands here.
                    // Keyed on the index rather than the id so a re-sort — which is what walking does
                    // to a distance-ordered list — also brings the card back into view.
                    LaunchedEffect(selectedIndex) {
                        if (selectedIndex >= 0) rail.animateScrollToItem(selectedIndex)
                    }

                    LazyRow(
                        state = rail,
                        modifier = Modifier.width(HospitalCardWidth),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        items(hospitals, key = { it.id }) { hospital ->
                            HospitalCard(
                                hospital = hospital,
                                isNearest = hospital.id == nearestId,
                                // Only the selected card can be routing, so only it shows a route's
                                // state; the rest keep the design's own "Find the Route".
                                routeStatus = if (hospital.id == selectedId) routeStatus else RouteStatus.Idle,
                                onSelect = { onSelect(hospital.id) },
                                onFindRoute = onFindRoute,
                                onCall = onCall,
                            )
                        }
                    }
                }
            }
        }

        // The alert control — the design's own glyph in the header's right corner, opposite the title.
        //
        // It replaces the full-width pill that used to sit in the sheet's tail: one icon, no label,
        // doing exactly what the pill did. Because nothing is written beside it, the count it used to
        // spell out moves into the content description, which is the only place a label is still owed.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = AlertButtonTop, end = AlertButtonEnd)
                .size(AlertButtonSize)
                .pressEffect()
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onAlertContacts),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_home_search_chat),
                contentDescription = when (contactCount) {
                    0 -> "Add alert contacts"
                    1 -> "Alert 1 saved contact"
                    else -> "Alert $contactCount saved contacts"
                },
                modifier = Modifier.size(AlertIconSize),
            )
        }
    }
}

/** A sentence and the one pill that answers it, in the rail's slot. Both empty states are this shape. */
@Composable
private fun EmptyRail(
    message: String,
    icon: Int,
    label: String,
    fill: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(HospitalCardWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MapType.SheetSubtitle,
            color = OmniSheetDetail,
            maxLines = 3,
        )
        HospitalAction(
            icon = icon,
            label = label,
            fill = fill,
            labelColor = OmniInk,
            labelStart = 78.dp,
            onClick = onClick,
        )
    }
}

/** One hospital — Figma `Hospital Container` (node 134:12631), 322 x 242. */
@Composable
private fun HospitalCard(
    hospital: Hospital,
    isNearest: Boolean,
    routeStatus: RouteStatus,
    onSelect: () -> Unit,
    onFindRoute: (Hospital) -> Unit,
    onCall: (Hospital) -> Unit,
) {
    Column(
        modifier = Modifier.width(HospitalCardWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp)
                .pressEffect()
                .clip(RoundedCornerShape(8.dp))
                .background(OmniHospitalCard)
                // Tapping the card is the other way to choose a hospital: it moves the map's camera,
                // darkens that pin and makes this card's route button the live one.
                .clickable(onClick = onSelect),
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
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

                    // The rail's half of the map's haloed pin, and the reason it is a tag over the
                    // photo rather than a word in the distance row: "Nearest · 0.8 km" needs 112 of
                    // the 85 that row leaves, while 58 fits inside the 78-wide photo with room to
                    // spare. Same radius as the photo, the hero pink of the pin's own halo.
                    if (isNearest) {
                        Text(
                            text = "Nearest",
                            style = MapType.HospitalType,
                            color = OmniInk,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .padding(start = 4.dp, top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(OmniHeroPink)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }

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
                            // The straight-line haversine distance, until a route replaces it with the
                            // road one. The design's "4 mins (0.8 mi)" was a mock: a drive *time* has
                            // to be routed for, which is what the button underneath is.
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
            label = routeStatus.routeLabel(),
            fill = OmniRouteButton,
            labelColor = OmniOnInk,
            labelStart = 85.dp,
            // Not tappable mid-request: a second tap would cancel the first and start again, which
            // looks like the button not working.
            enabled = routeStatus !is RouteStatus.Loading,
            onClick = { onFindRoute(hospital) },
        )
        HospitalAction(
            icon = R.drawable.ic_hosp_call,
            // OpenStreetMap carries a number for maybe half of these. Naming the fallback rather than
            // dialling it silently is the difference between a button that lies and one that does not
            // (`UI_ARCHITECTURE.md` §6 rule 9). The label names the kind it is calling, since the rail
            // now mixes hospitals and pharmacies.
            label = when {
                hospital.phone == null -> "Call 999"
                hospital.isPharmacy -> "Call the Pharmacy"
                else -> "Call the Hospital"
            },
            fill = OmniCallButton,
            // The source sets this #302E2E on the first card and pure black on the second; the two
            // are indistinguishable at 18px, so the ink token carries both.
            labelColor = OmniInk,
            labelStart = 78.dp,
            onClick = { onCall(hospital) },
        )
    }
}

/**
 * What the route button says, which is the only place a route's state is visible on the card.
 *
 * Every label is measured against the 201 the design leaves after its own 85 inset, the 24 icon and the
 * 12 gap: the longest here is the ready state's "12.4 km · 18 min" at ~150. "Route Unavailable — Retry"
 * would have needed 237, which is why a failure says "Retry" on the button and explains itself in the
 * notice banner instead.
 */
private fun RouteStatus.routeLabel(): String = when (this) {
    RouteStatus.Idle -> "Find the Route"
    RouteStatus.Loading -> "Finding the Route…"
    is RouteStatus.Ready -> "${route.distanceLabel()} · ${route.etaLabel()}"
    is RouteStatus.Failed -> "Retry the Route"
}

/** A 322 x 50 action pill at radius 28 — Figma `Route Container` / `Call Container`. */
@Composable
private fun HospitalAction(
    icon: Int,
    label: String,
    fill: Color,
    labelColor: Color,
    labelStart: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect(enabled = enabled)
            .clip(RoundedCornerShape(28.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
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
 * directory data — plus one pharmacy, so the preview also shows the merged directory's other kind
 * and the teal card state it arrives in.
 */
private val DefaultHospitals = listOf(
    Hospital(
        id = "city-central",
        name = "City Central Hospital",
        type = "Trauma Center Level 1 • 24/7 ER",
        lat = 22.3569,
        lng = 91.7832,
        phone = "+8802333350000",
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
    Hospital(
        id = "city-point-pharmacy",
        name = "City Point Pharmacy",
        type = "Pharmacy • 24/7",
        lat = 22.3451,
        lng = 91.8012,
        isPharmacy = true,
        distanceKm = 1.4,
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

/** The pill's own 12 gap, reused between it and the notice under it. */
private val NoticeGap = 12.dp

/**
 * What the camera assumes a notice covers: one line of `SheetSubtitle` plus its 11 padding, rounded up.
 * An estimate on purpose — the banner wraps to two or three lines on a narrow phone, and this number
 * only feeds bounds-fitting's top margin, where being twenty pixels generous costs nothing and being
 * exact would mean measuring the text to place the camera.
 */
private val NoticeMinHeight = 44.dp

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

/** Everything the swipe state puts over the map's bottom: the bar's slot, the gap, and the track. */
private val SwipeSlot = OmniNavBottomGap + OmniNavHeight + SliderNavGap + SliderHeight

/** How far the knob must travel before releasing it counts as a swipe rather than a fumble. */
private const val ActivateAt = 0.75f

/**
 * The flick escape hatch, in pixels per second, and the point from which a flick is believed.
 *
 * [ActivateAt] alone punishes a fast, confident swipe: the finger leaves the screen before the knob
 * catches up and the gesture is read as a fumble. Past halfway with real speed behind it, the intent
 * is not in doubt. 900 px/s is roughly a deliberate throw — a slow drag that drifts to a stop comes
 * in under 200.
 */
private const val FlingActivate = 900f
private const val FlingFrom = 0.5f

/** How long an activated knob rests at the far end before gliding home, if it is still on screen. */
private const val ResetDelayMillis = 320L

/** A release short of the threshold: quick, and just springy enough to read as a rejection. */
private val SpringBack = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

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

/** The alert glyph, at the icon size the rest of the sheet draws its icons at. */
private val AlertIconSize = 24.dp

/**
 * 40 of target around a 24 glyph — the size this app gives every other bare icon, and eight of slack
 * on each side so a finger that lands beside the strokes still counts.
 */
private val AlertButtonSize = 40.dp

/**
 * Centred on the title's line rather than hung from the sheet's top: [SheetHeaderTop] + half of
 * `SheetTitle`'s 28 line box − half the target, i.e. 27 + 14 − 20.
 */
private val AlertButtonTop = 21.dp

/**
 * [SheetPadding] − ([AlertButtonSize] − [AlertIconSize]) / 2. The *glyph* lands on the sheet's own 18
 * gutter, in line with the title below it; the invisible target is what overhangs it.
 */
private val AlertButtonEnd = 10.dp

/** The recenter control: the pages' own 21 gutter in from the edge, the pill's 12 up from what it clears. */
private val RecenterSize = 44.dp
private val RecenterGlyph = 22.dp
private val RecenterEnd = 21.dp
private val RecenterGap = 12.dp

/** The route's purple, so the button and the dot it centres on read as the same thing. */
private val RecenterTint = OmniAuthHeading

/** The glyph, in fractions of its own radius: the ring, the centre dot, and the line weight. */
private const val RingRadius = 0.6f
private const val DotRadius = 0.2f
private const val GlyphStroke = 0.14f

@DevicePreviews
@Composable
private fun SosScreenPreview() {
    OmniTheme { SosScreen() }
}

@DevicePreviews
@Composable
private fun SosScreenSheetPreview() {
    OmniTheme {
        SosScreen(
            startWithSheet = true,
            userLocation = LatLng(22.3600, 91.7800),
            locationStatus = LocationStatus.Fixed,
            contactCount = 2,
        )
    }
}

/** The state a fresh install in airplane mode lands on: no directory, and 999 as the way out. */
@DevicePreviews
@Composable
private fun SosScreenEmptyDirectoryPreview() {
    OmniTheme {
        SosScreen(
            startWithSheet = true,
            hospitals = emptyList(),
            selectedId = null,
            nearestId = null,
            directorySize = 0,
            locationStatus = LocationStatus.Fixed,
        )
    }
}

/** A refusal, which is the state the notice banner exists for. */
@DevicePreviews
@Composable
private fun SosScreenLocationBlockedPreview() {
    OmniTheme { SosScreen(locationStatus = LocationStatus.Blocked) }
}

