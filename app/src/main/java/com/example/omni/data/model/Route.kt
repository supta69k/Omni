package com.example.omni.data.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point on the map, in the app's own vocabulary.
 *
 * Deliberately Omni's type rather than MapLibre's `LatLng` or Android's `Location`: the routing layer
 * and the ViewModel both speak this, so neither has to import a map SDK or Play Services. The single
 * conversion to MapLibre's own type happens inside the map composable, which is the only place that
 * knows a map SDK exists at all.
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)

/**
 * One driving route between two points, as far as the SOS screen is concerned.
 *
 * [geometry] is the full polyline the map draws, origin first and destination last. [distanceMeters]
 * and [durationSeconds] come from the routing provider rather than from the geometry, because the
 * provider knows the speed limits and the geometry does not.
 *
 * Nothing here mentions OSRM. That is the point: [com.example.omni.data.repo.RoutingRepository] can be
 * re-pointed at a self-hosted OSRM, OpenRouteService or Google Routes and this type does not change,
 * so neither does a single line of the SOS UI.
 */
data class Route(
    val geometry: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

/**
 * "1.2 km" / "12 km" — the same rounding rule as [Hospital.distanceLabel], for the same reason: a
 * route length quoted to three decimals implies a precision road geometry does not have.
 */
fun Route.distanceLabel(): String {
    val km = distanceMeters / 1_000.0
    return if (km < 10.0) "%.1f km".format(km) else "%.0f km".format(km)
}

/**
 * "6 min" — the ETA, rounded up to the next whole minute and never to zero.
 *
 * "0 min" would be the literally-correct label for a hospital 200 m away, and a useless one in an
 * emergency; the floor of one minute is the honest reading of "you are nearly there". Past an hour it
 * reads "1 h 12 min", because a three-digit minute count is something the reader has to do arithmetic
 * on before it means anything.
 */
fun Route.etaLabel(): String {
    val minutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}

/**
 * Metres between two points, by the haversine formula — [Hospital.withDistanceFrom]'s maths, hoisted
 * so the ViewModel can ask "has the user moved far enough to be worth re-routing?" without inventing a
 * `Hospital` to measure against.
 */
fun LatLng.distanceMetersTo(other: LatLng): Double {
    val dLat = Math.toRadians(other.lat - lat)
    val dLng = Math.toRadians(other.lng - lng)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** The hospital's position, as the routing layer and the map want it. */
fun Hospital.position(): LatLng = LatLng(lat, lng)
