package com.example.omni.ui.sos

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.LatLng
import com.example.omni.data.model.Route
import com.example.omni.data.model.distanceLabel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLatLng

/**
 * The SOS screen's map: MapLibre Native drawing MapTiler's OpenStreetMap vector tiles, in Omni's own
 * palette (BACKEND_PLAN §11 Phase 9, "The map").
 *
 * ## Why a real map and not a picture of one
 *
 * The screen used to draw a static bitmap and hand the actual navigation to Google Maps through a
 * `geo:` intent. Both are gone. Google Maps Platform needs a billing account this project will not
 * have, and the intent meant the one screen that matters in an emergency ended by leaving the app.
 * MapLibre renders locally from vector tiles, which costs a tile quota rather than a per-request bill,
 * and the route comes back as coordinates Omni draws itself.
 *
 * ## One view, held across recomposition
 *
 * [MapView] owns a GL surface and a tile cache; recreating it on recomposition would restart both and
 * flash the map. So exactly one is `remember`ed, `AndroidView` only ever re-attaches it, and every
 * change — markers, route, camera — is pushed into it as a *side effect* keyed on the data that
 * changed. Nothing here rebuilds the map to change a pin.
 *
 * ## The density trap
 *
 * Every pixel number handed to MapLibre below comes from `resources.displayMetrics.density`, never from
 * `LocalDensity.current`. Inside [com.example.omni.ui.DesignFrame] the composition's density is scaled
 * to the 415dp artboard, so `LocalDensity` would quietly hand the map a figure a few percent off — and
 * MapLibre, being an Android View, measures in real screen pixels regardless.
 *
 * @param selectedId the hospital whose card is open — drawn as the dark pin.
 * @param nearestId the closest hospital of all — drawn haloed, whether or not it is selected.
 * @param recenterTick bumped by the ViewModel whenever the camera should return to the user. A counter
 *   rather than a callback because Compose re-runs effects on *values*, and "recentre again" has to be
 *   distinguishable from "recentre" without inventing an event queue.
 * @param bottomInsetPx how much of the map the hospital sheet covers, in **physical pixels**, so
 *   bounds-fitting keeps the pins in the part of the map the user can actually see. Pixels rather than
 *   `Dp` on purpose: the caller sits inside [com.example.omni.ui.DesignFrame], where a `Dp` is an
 *   *artboard* unit, and only the caller's own `LocalDensity` knows what that is in real pixels.
 * @param onStyleFailed the tiles or the style could not be loaded. The screen says so; it never falls
 *   back to an external maps app.
 */
@Composable
fun OmniMap(
    hospitals: List<Hospital>,
    selectedId: String?,
    nearestId: String?,
    userLocation: LatLng?,
    route: Route?,
    recenterTick: Int,
    onSelectHospital: (String) -> Unit,
    onStyleReady: () -> Unit,
    onStyleFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
) {
    val context = LocalContext.current

    // A missing key is a configuration state, not a crash: `local.properties` is gitignored, so a fresh
    // checkout has none. Report it and draw nothing — the screen paints the explanation.
    if (!OmniMapConfig.hasKey) {
        LaunchedEffect(Unit) { onStyleFailed(MissingKeyMessage) }
        return
    }

    val density = remember(context) { context.resources.displayMetrics.density }

    // The callbacks are read from inside MapLibre listeners that outlive a single composition, so they
    // are held by reference rather than captured — otherwise a tap would call last frame's lambda.
    val selectHospital by rememberUpdatedState(onSelectHospital)
    val styleFailed by rememberUpdatedState(onStyleFailed)
    val styleReady by rememberUpdatedState(onStyleReady)

    val mapView = remember(context) {
        // Must precede the first MapView: it loads the native library and the shared HTTP stack.
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    /** Non-null only once the style is up and Omni's own sources and layers are installed. */
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    /** Whether the camera has been placed at all — the first placement jumps, the rest animate. */
    var framed by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.getMapAsync { libreMap ->
            libreMap.uiSettings.apply {
                // North-up, like the design: rotating and tilting a map whose labels are laid out for
                // one orientation only makes it harder to read.
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                // MapLibre's own logo is optional; OpenStreetMap's and MapTiler's attribution is not.
                // Keep the ⓘ, tucked clear of the hospital card.
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            libreMap.setMinZoomPreference(MinZoom)
            libreMap.setMaxZoomPreference(MaxZoom)

            libreMap.addOnMapClickListener { point ->
                val screen = libreMap.projection.toScreenLocation(point)
                val tapped = libreMap.pinAt(screen)
                if (tapped != null) selectHospital(tapped)
                // Consume the tap only when it landed on a pin, so a tap on empty map still pans.
                tapped != null
            }

            val builder = OmniMapConfig.styleUrl
                ?.let { Style.Builder().fromUri(it) }
                ?: Style.Builder().fromJson(OmniMapConfig.styleJson(context))

            libreMap.setStyle(builder) { loaded ->
                loaded.installOmniLayers(context)
                map = libreMap
                style = loaded
                styleReady()
            }
        }

        // Fires for a dead key, a revoked quota, or no network on a cold cache — the ways this map can
        // legitimately fail to appear, all of which the screen has a sentence for.
        //
        // Two deliberate narrowings. It only reports while [style] is still null, because the same
        // callback fires for a single tile that timed out *after* the style loaded — a blip that heals
        // itself on the next pan, and reporting it would leave a permanent error over a working map.
        // And it reports [MissingStyleMessage] rather than MapLibre's own text, which names the failing
        // URL — the style URL carries the MapTiler key in a query parameter, and the one place it must
        // never end up is on screen. The real message goes to the log.
        val failureListener = MapView.OnDidFailLoadingMapListener { message ->
            Log.w("Omni", "The map style could not be loaded: $message")
            if (style == null) styleFailed(MissingStyleMessage)
        }
        mapView.addOnDidFailLoadingMapListener(failureListener)

        onDispose { mapView.removeOnDidFailLoadingMapListener(failureListener) }
    }

    // ---- Lifecycle -----------------------------------------------------------------------------
    //
    // MapView is a plain Android View: it has no idea Compose left the screen, and left running it
    // holds a GL context and keeps fetching tiles. `started` guards the teardown so `onStop` is never
    // called on a view that was never started, which is what a fast open-and-back does.

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    started = true
                    mapView.onStart()
                }
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> {
                    started = false
                    mapView.onStop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // ---- Data in ------------------------------------------------------------------------------
    //
    // Three sources, each rewritten only when its own input changes. Rewriting a GeoJSON source is a
    // handful of features re-parsed; it does not touch the tiles, the camera or the GL context.

    // OpenStreetMap's and MapTiler's attribution is a licence condition, not decoration, so it is kept
    // and moved instead of switched off — up out of the way whenever the hospital sheet is covering
    // the corner it lives in.
    LaunchedEffect(map, bottomInsetPx) {
        val libreMap = map ?: return@LaunchedEffect
        val edge = (AttributionMarginDp * density).toInt()
        libreMap.uiSettings.setAttributionMargins(edge, 0, 0, bottomInsetPx + edge)
    }

    LaunchedEffect(style, hospitals, selectedId, nearestId) {
        val source = style?.getSourceAs<GeoJsonSource>(HospitalSourceId) ?: return@LaunchedEffect
        source.setGeoJson(hospitalFeatures(hospitals, selectedId, nearestId))
    }

    LaunchedEffect(style, userLocation) {
        val source = style?.getSourceAs<GeoJsonSource>(UserSourceId) ?: return@LaunchedEffect
        source.setGeoJson(userFeatures(userLocation))
    }

    LaunchedEffect(style, route) {
        val source = style?.getSourceAs<GeoJsonSource>(RouteSourceId) ?: return@LaunchedEffect
        source.setGeoJson(routeFeatures(route))
    }

    // ---- Camera -------------------------------------------------------------------------------

    // The first placement, the moment there is anything worth looking at. No animation: an emergency
    // screen should open already showing the answer, not fly towards it.
    LaunchedEffect(map, userLocation != null, hospitals.size) {
        val libreMap = map ?: return@LaunchedEffect
        if (framed) return@LaunchedEffect
        val target = userLocation ?: hospitals.firstOrNull()?.let { LatLng(it.lat, it.lng) }
            ?: return@LaunchedEffect
        libreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target.toMapLatLng(), UserZoom))
        framed = true
    }

    // The recentre control, and — through the same counter — the first fix of the session.
    LaunchedEffect(map, recenterTick) {
        if (recenterTick == 0) return@LaunchedEffect
        val libreMap = map ?: return@LaunchedEffect
        val here = userLocation ?: return@LaunchedEffect
        libreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(here.toMapLatLng(), UserZoom),
            CameraMillis,
        )
        framed = true
    }

    // A tapped pin brings its hospital into view — with the user too, when there is one, because the
    // question being asked is "how far is that from me".
    LaunchedEffect(map, selectedId) {
        val libreMap = map ?: return@LaunchedEffect
        if (!framed) return@LaunchedEffect
        val hospital = hospitals.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        libreMap.frame(
            points = listOfNotNull(userLocation, LatLng(hospital.lat, hospital.lng)),
            density = density,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
            animate = true,
        )
    }

    // A drawn route is framed end to end, so its whole shape is visible at once.
    LaunchedEffect(map, route) {
        val libreMap = map ?: return@LaunchedEffect
        val line = route?.geometry ?: return@LaunchedEffect
        if (line.isEmpty()) return@LaunchedEffect
        libreMap.frame(
            points = line,
            density = density,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
            animate = true,
        )
        framed = true
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

// ---- Style installation ------------------------------------------------------------------------

/**
 * Adds Omni's three sources and seven layers on top of whatever the vector style brought.
 *
 * Order is z-order: the route goes above the roads it follows, the user dot above the route it starts
 * from, and the pins above everything. The three pin layers share one source and differ only in their
 * filter, which is what makes selecting a hospital a property change on one feature rather than a
 * marker being destroyed and rebuilt.
 */
private fun Style.installOmniLayers(context: Context) {
    addImage(PinImagePlain, OmniMapPins.bitmap(context, StatePlain))
    addImage(PinImageNearest, OmniMapPins.bitmap(context, StateNearest))
    addImage(PinImageSelected, OmniMapPins.bitmap(context, StateSelected))

    addSource(GeoJsonSource(RouteSourceId, FeatureCollection.fromFeatures(emptyList())))
    addSource(GeoJsonSource(UserSourceId, FeatureCollection.fromFeatures(emptyList())))
    addSource(GeoJsonSource(HospitalSourceId, FeatureCollection.fromFeatures(emptyList())))

    // A white sleeve under the route line. Without it the purple runs invisible where it crosses the
    // pink primary roads it is most likely to be following.
    addLayer(
        LineLayer(RouteCasingLayerId, RouteSourceId).withProperties(
            PropertyFactory.lineColor(RouteCasingColor.toArgb()),
            PropertyFactory.lineWidth(RouteCasingWidth),
            PropertyFactory.lineOpacity(RouteCasingOpacity),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    addLayer(
        LineLayer(RouteLayerId, RouteSourceId).withProperties(
            PropertyFactory.lineColor(RouteColor.toArgb()),
            PropertyFactory.lineWidth(RouteWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )

    // "You are here": a soft disc for presence at a glance, a hard dot for the actual coordinate.
    addLayer(
        CircleLayer(UserHaloLayerId, UserSourceId).withProperties(
            PropertyFactory.circleRadius(UserHaloRadius),
            PropertyFactory.circleColor(UserHaloColor.toArgb()),
            PropertyFactory.circleOpacity(UserHaloOpacity),
        ),
    )
    addLayer(
        CircleLayer(UserDotLayerId, UserSourceId).withProperties(
            PropertyFactory.circleRadius(UserDotRadius),
            PropertyFactory.circleColor(UserDotColor.toArgb()),
            PropertyFactory.circleStrokeWidth(UserDotRingWidth),
            PropertyFactory.circleStrokeColor(UserDotRingColor.toArgb()),
        ),
    )

    addLayer(pinLayer(HospitalLayerId, PinImagePlain, StatePlain, strong = false))
    addLayer(pinLayer(NearestLayerId, PinImageNearest, StateNearest, strong = true))
    addLayer(pinLayer(SelectedLayerId, PinImageSelected, StateSelected, strong = true))
}

/**
 * One state's pins and their labels.
 *
 * The icon never drops out — `allow-overlap` plus `ignore-placement`, because a hospital that vanishes
 * because a label wanted the space is unacceptable on this screen. The *label* is the opposite:
 * `optional` and non-overlapping, so a dense district loses text rather than turning into a solid block
 * of it.
 *
 * @param strong the nearest and the selected hospital, whose labels are inked and a point larger.
 */
private fun pinLayer(id: String, image: String, state: String, strong: Boolean): SymbolLayer =
    SymbolLayer(id, HospitalSourceId).withProperties(
        PropertyFactory.iconImage(image),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        // The bitmap carries its own shadow padding below the tip; nudge it back down so the point of
        // the pin, not the bottom of its shadow, sits on the coordinate.
        PropertyFactory.iconOffset(arrayOf(0f, PinTipOffset)),
        PropertyFactory.textField(Expression.get(LabelProperty)),
        // "Noto Sans Medium" only: it is the one weight this style's glyph endpoint is already proven
        // to serve (see `place-label` in omni_map_style.json). A stack naming a weight MapTiler does
        // not have would take every label on the map down with it, so emphasis is size and colour.
        PropertyFactory.textFont(arrayOf(LabelFont)),
        PropertyFactory.textSize(if (strong) LabelSizeStrong else LabelSize),
        PropertyFactory.textColor((if (strong) PinLabelStrongColor else PinLabelColor).toArgb()),
        PropertyFactory.textHaloColor(PinLabelHaloColor.toArgb()),
        PropertyFactory.textHaloWidth(LabelHaloWidth),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, LabelOffsetEms)),
        PropertyFactory.textMaxWidth(LabelMaxWidthEms),
        PropertyFactory.textLetterSpacing(LabelLetterSpacing),
        PropertyFactory.textOptional(true),
        PropertyFactory.textAllowOverlap(false),
    ).also { layer ->
        layer.setFilter(Expression.eq(Expression.get(StateProperty), Expression.literal(state)))
    }

// ---- Features ----------------------------------------------------------------------------------

/**
 * The directory as map features, each tagged with the state that decides which layer draws it.
 *
 * The label is the hospital and its live distance — the design's own callout ("Savlon: (3.6km)") — with
 * the nearest one saying so in words, because a halo tells the eye *which* pin is special and only text
 * says *why*.
 */
private fun hospitalFeatures(
    hospitals: List<Hospital>,
    selectedId: String?,
    nearestId: String?,
): FeatureCollection = FeatureCollection.fromFeatures(
    hospitals.map { hospital ->
        val state = when (hospital.id) {
            selectedId -> StateSelected
            nearestId -> StateNearest
            else -> StatePlain
        }
        Feature.fromGeometry(Point.fromLngLat(hospital.lng, hospital.lat)).apply {
            addStringProperty(IdProperty, hospital.id)
            addStringProperty(StateProperty, state)
            addStringProperty(LabelProperty, hospital.mapLabel(isNearest = hospital.id == nearestId))
        }
    },
)

/**
 * "Metro General · 0.8 km", or "Nearest · Metro General · 0.8 km".
 *
 * Names are clipped at a word boundary: the seeded OpenStreetMap directory has entries like "Chittagong
 * Medical College Hospital", and a label that long stops being a label.
 */
private fun Hospital.mapLabel(isNearest: Boolean): String {
    val short = name.trim().let { full ->
        if (full.length <= MaxLabelChars) {
            full
        } else {
            val cut = full.take(MaxLabelChars)
            (cut.substringBeforeLast(' ').takeIf { it.length >= MinLabelChars } ?: cut).trimEnd() + "…"
        }
    }
    val parts = buildList {
        if (isNearest) add("Nearest")
        add(short)
        if (distanceKm != null) add(distanceLabel())
    }
    return parts.joinToString(LabelSeparator)
}

private fun userFeatures(location: LatLng?): FeatureCollection = FeatureCollection.fromFeatures(
    listOfNotNull(location?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }),
)

private fun routeFeatures(route: Route?): FeatureCollection {
    // A one-point "line" is not a line; MapLibre would draw nothing and log a warning per frame.
    val points = route?.geometry?.takeIf { it.size >= 2 } ?: return FeatureCollection.fromFeatures(emptyList())
    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line)))
}

// ---- Camera helpers ----------------------------------------------------------------------------

/**
 * Frames [points], leaving the header and the hospital card room to sit over the map without covering
 * anything the fit was supposed to reveal.
 *
 * Falls back to a plain centre-and-zoom for one point, or for several that are effectively the same
 * point: [LatLngBounds] rejects fewer than two coordinates outright, and a zero-span bounds fits to
 * maximum zoom, which lands the camera inside a building.
 */
private fun MapLibreMap.frame(
    points: List<LatLng>,
    density: Float,
    topInsetPx: Int,
    bottomInsetPx: Int,
    animate: Boolean,
) {
    val distinct = points.distinctBy { it.lat to it.lng }
    if (distinct.isEmpty()) return

    val update = if (distinct.size < 2) {
        CameraUpdateFactory.newLatLngZoom(distinct.first().toMapLatLng(), UserZoom)
    } else {
        val bounds = LatLngBounds.Builder()
            .includes(distinct.map { it.toMapLatLng() })
            .build()
        val edge = (FrameEdgeDp * density).toInt()
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            edge,
            topInsetPx + edge,
            edge,
            bottomInsetPx + edge,
        )
    }

    if (animate) animateCamera(update, CameraMillis) else moveCamera(update)
}

/** The id of the hospital rendered under [screen], or `null` for a tap on open map. */
private fun MapLibreMap.pinAt(screen: PointF): String? =
    queryRenderedFeatures(screen, SelectedLayerId, NearestLayerId, HospitalLayerId)
        .firstNotNullOfOrNull { it.getStringProperty(IdProperty) }

private fun LatLng.toMapLatLng(): MapLatLng = MapLatLng(lat, lng)

// ---- Constants ---------------------------------------------------------------------------------

/** Close enough to read street names, wide enough to hold a few hospitals — the opening view. */
private const val UserZoom = 14.0

private const val MinZoom = 4.0
private const val MaxZoom = 18.0

/** Long enough to read as a move rather than a cut, short enough not to be waited on. */
private const val CameraMillis = 900

/** Breathing room around a bounds fit, on top of whatever the UI is covering. */
private const val FrameEdgeDp = 48f

private const val AttributionMarginDp = 12f

private const val RouteWidth = 6f
private const val RouteCasingWidth = 11f
private const val RouteCasingOpacity = 0.95f

private const val UserDotRadius = 7f
private const val UserDotRingWidth = 3.5f
private const val UserHaloRadius = 19f
private const val UserHaloOpacity = 0.16f

/** Matches the shadow padding and half-stroke the pin bitmap adds below its tip. */
private const val PinTipOffset = 4.2f

private const val LabelFont = "Noto Sans Medium"
private const val LabelSize = 11f
private const val LabelSizeStrong = 12.5f
private const val LabelHaloWidth = 1.6f

/** Ems, not pixels — MapLibre's text offsets scale with the text. */
private const val LabelOffsetEms = 0.55f
private const val LabelMaxWidthEms = 8f
private const val LabelLetterSpacing = 0.02f

/** A middle dot with hair spaces, so the label reads as one phrase rather than a list. */
private const val LabelSeparator = " · "

private const val MaxLabelChars = 20
private const val MinLabelChars = 8

private const val MissingKeyMessage =
    "The map needs a MapTiler key. Add MAPTILER_KEY to local.properties and rebuild."

private const val MissingStyleMessage = "The map couldn't be loaded."
