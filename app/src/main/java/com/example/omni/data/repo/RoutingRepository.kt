package com.example.omni.data.repo

import com.example.omni.data.model.LatLng
import com.example.omni.data.model.Route
import com.example.omni.data.model.distanceMetersTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A driving route between two points (BACKEND_PLAN §11 Phase 9, "Routing").
 *
 * This is the seam the whole SOS map was built around. The screen asks for a [Route] and draws it; it
 * never learns which provider produced it. Today that is OSRM's public demo server; swapping in a
 * self-hosted OSRM, OpenRouteService, or Google Routes later is one new implementation and one line in
 * [com.example.omni.di.AppContainer] — no UI change, because [Route] does not change.
 *
 * The result is a [Result] rather than a thrown exception: a failed route is an expected state on this
 * screen (the demo server is down, the phone is offline, the two points are unroutable), and the card
 * shows "route unavailable" for it. It is never a crash, and it never falls back to an external maps
 * app — that was the whole reason this layer exists.
 */
interface RoutingRepository {
    suspend fun getRoute(origin: LatLng, destination: LatLng): Result<Route>
}

/**
 * OSRM over HTTP.
 *
 * The request asks for `geometries=geojson&overview=full`, so the geometry arrives as a plain
 * `[lng, lat]` coordinate array — no encoded-polyline decoder is needed, which is what keeps this whole
 * feature at zero new parsing dependencies. The response has about five fields we read, so it is parsed
 * with the platform's own `org.json` rather than pulling in a serialization library.
 *
 * OkHttp is shared with Coil; a short read timeout keeps a dead demo server from hanging the button.
 */
class OsrmRoutingRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : RoutingRepository {

    override suspend fun getRoute(origin: LatLng, destination: LatLng): Result<Route> =
        withContext(Dispatchers.IO) {
            runCatching {
                // OSRM orders coordinates lng,lat — the opposite of how they are usually spoken.
                val coords = "${origin.lng},${origin.lat};${destination.lng},${destination.lat}"
                val url = "$baseUrl/route/v1/driving/$coords" +
                    "?overview=full&geometries=geojson&alternatives=false&steps=false"

                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("OSRM returned HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    parseRoute(body)
                }
            }
        }

    /**
     * Pulls the first route out of an OSRM response.
     *
     * `code` is OSRM's own status string; anything but "Ok" (e.g. "NoRoute") is a routing failure, not
     * an HTTP one, so it is turned into a failed [Result] here rather than read as an empty geometry.
     */
    private fun parseRoute(json: String): Route {
        val root = JSONObject(json)
        val code = root.optString("code", "")
        if (code != "Ok") throw IOException("OSRM code=$code")

        val routes = root.optJSONArray("routes")
            ?: throw IOException("OSRM response had no routes array")
        if (routes.length() == 0) throw IOException("OSRM returned no route")

        val route = routes.getJSONObject(0)
        val distance = route.optDouble("distance", 0.0)
        val duration = route.optDouble("duration", 0.0)

        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val geometry = ArrayList<LatLng>(coordinates.length())
        for (i in 0 until coordinates.length()) {
            val pair = coordinates.getJSONArray(i)
            // GeoJSON is [lng, lat]; the app speaks lat,lng.
            geometry.add(LatLng(lat = pair.getDouble(1), lng = pair.getDouble(0)))
        }
        if (geometry.isEmpty()) throw IOException("OSRM route had no geometry")

        return Route(geometry = geometry, distanceMeters = distance, durationSeconds = duration)
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * The preview/test routing repository — a straight line between the two points.
 *
 * A preview that opens the SOS sheet must not reach the network, and a straight line is enough to draw
 * the polyline and prove the card's distance and ETA render. The duration assumes 30 km/h, a plausible
 * city speed, so "6 min" in a preview is not a wild number.
 */
class PreviewRoutingRepository : RoutingRepository {
    override suspend fun getRoute(origin: LatLng, destination: LatLng): Result<Route> {
        val metres = origin.distanceMetersTo(destination)
        return Result.success(
            Route(
                geometry = listOf(origin, destination),
                distanceMeters = metres,
                // 30 km/h, a plausible city speed, so a preview's ETA is not a wild number.
                durationSeconds = metres / (30_000.0 / 3_600.0),
            ),
        )
    }
}
