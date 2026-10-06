package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One facility in the public SOS directory — a `hospitals/{id}` or a `pharmacies/{id}` document
 * (BACKEND_PLAN §7).
 *
 * One shape for both halves of the directory on purpose: the two collections are seeded by the same
 * script from the same OpenStreetMap export, carry the same fields, and are measured, sorted, routed,
 * dialled and labelled by exactly the same code — the only thing that differs is the colour of the
 * pin. [isPharmacy] is set by the mapper, never stored: a document's collection is its kind, and a
 * stored flag could contradict it.
 *
 * The collection is seeded once from OpenStreetMap data (see `tools/seed-hospitals.md` in the repo
 * root) and is read-only for clients, which is what makes it work offline: Firestore's cache holds
 * every document the app has ever read, and a directory that never changes never needs a refresh.
 *
 * [distanceKm] is not stored — it is computed against the user's location at read time (see
 * [withDistanceFrom]) because it is only meaningful relative to someone standing somewhere.
 */
data class Hospital(
    val id: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val phone: String? = null,
    val rating: Double? = null,
    val distanceKm: Double? = null,
    val isPharmacy: Boolean = false,
)

fun DocumentSnapshot.toHospital(isPharmacy: Boolean = false): Hospital? {
    if (!exists()) return null
    return Hospital(
        id = id,
        name = getString("name").orEmpty(),
        type = getString("type").orEmpty().ifBlank { if (isPharmacy) "Pharmacy" else "Hospital" },
        lat = (get("lat") as? Number)?.toDouble() ?: 0.0,
        lng = (get("lng") as? Number)?.toDouble() ?: 0.0,
        phone = getString("phone")?.takeIf { it.isNotBlank() },
        rating = (get("rating") as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0.0 },
        isPharmacy = isPharmacy,
    )
}

/** The Firestore map for a seed script; the app itself never writes these documents. */
fun Hospital.toSeedMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "type" to type,
    "lat" to lat,
    "lng" to lng,
    "phone" to phone,
    "rating" to rating,
    "source" to "openstreetmap",
)

/**
 * Distance from (`fromLat`, `fromLng`) to this hospital, in kilometres, by the haversine formula.
 *
 * Computed locally rather than through a Directions call — the plan's own instruction (§11 Phase 9):
 * a distance matrix is a paid API for a number a sphere and four trig calls can produce.
 */
fun Hospital.withDistanceFrom(fromLat: Double, fromLng: Double): Hospital {
    val dLat = Math.toRadians(lat - fromLat)
    val dLng = Math.toRadians(lng - fromLng)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(fromLat)) * cos(Math.toRadians(lat)) * sin(dLng / 2) * sin(dLng / 2)
    val km = 6_371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    return copy(distanceKm = km)
}

/**
 * "1.2 km" — the drive label, in the unit the plan mandates for this screen.
 *
 * The design's "4 mins (0.8 mi)" is a template leftover; BACKEND_PLAN §11 Phase 9 switches the whole
 * screen to kilometres because the app targets Bangladesh. Sub-kilometre distances show one decimal;
 * past that, whole numbers — a hospital 12.4 km away is "12 km", not a number implying precision the
 * haversine formula does not have.
 */
fun Hospital.distanceLabel(): String {
    val km = distanceKm ?: return "—"
    return if (km < 10.0) "%.1f km".format(km) else "%.0f km".format(km)
}
