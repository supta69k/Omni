package com.example.omni.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.os.Looper

/**
 * The device's own location — one class, no interface, no fake (BACKEND_PLAN §4 rule 1).
 *
 * Two shapes of read, because the SOS map needs both:
 *
 *  - [lastKnown] is the one-shot the activation path and the first directory sort use: "where am I
 *    right now", once. `getCurrentLocation` returns a fresh fix when one is cheaply available and the
 *    last known otherwise — the honest trade for an emergency screen, where a slightly stale location
 *    beats a spinner.
 *  - [observeLocation] is the stream the live map draws the user marker from. It updates the marker as
 *    the phone moves and lets the ViewModel decide, on its own thresholds, when a move is far enough to
 *    be worth re-routing. It is a cold [Flow]: the fused client is only subscribed while someone is
 *    collecting, and [awaitClose] removes the callback the moment they stop, so a backgrounded SOS
 *    screen is not a battery drain.
 *
 * The permission is *not* asked for in here. A repository has no window to ask from; the screen's
 * caller (MainActivity, like the step permission before it) owns the launcher and hands the answer
 * down. Reading location without the permission yields `null`/no emissions rather than throwing — the
 * SecurityException the framework would raise is a developer error being turned into a user state.
 */
open class LocationRepository(
    context: Context,
) {
    /** The application context, so a repository outliving an Activity cannot leak one. */
    private val appContext = context.applicationContext

    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    /**
     * The freshest location the device can hand over without a dialog, or `null` when there is
     * neither a permission nor any fix to read.
     */
    @SuppressLint("MissingPermission") // The guard below is the no-permission path.
    open suspend fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (_: Exception) {
            // No play services, or the location is off: the caller degrades to the unsorted
            // directory rather than crashing the emergency screen.
            null
        }
    }

    /**
     * A stream of fixes while the SOS map is on screen.
     *
     * Balanced power rather than high accuracy: a hospital directory is sorted correctly by a fix good
     * to a few tens of metres, and the battery cost of GPS-grade accuracy is not worth it for that.
     * The 5-second interval and 25-metre minimum displacement are the coarse throttle; the ViewModel
     * layers its own re-routing threshold on top, so a phone sitting still on a table does not redraw a
     * route it already has.
     *
     * Emits nothing (and closes) without the permission — the collector keeps whatever last fix it had
     * and the map stays usable, unsorted, exactly as the refused-permission state is drawn.
     */
    @SuppressLint("MissingPermission") // The guard below is the no-permission path.
    open fun observeLocation(): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UpdateIntervalMillis,
        )
            .setMinUpdateDistanceMeters(MinDisplacementMeters)
            .setMinUpdateIntervalMillis(FastestIntervalMillis)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (cause: Exception) {
            // No play services on this device: close cleanly rather than crash. The map degrades to
            // the no-fix state.
            close(cause)
            return@callbackFlow
        }

        // The one thing this whole flow exists to guarantee: the listener is torn down when nobody is
        // collecting, so it cannot outlive the screen or leak.
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /**
     * Whether the device's location services are switched on at all.
     *
     * The permission and the master switch are two different refusals wearing the same face: with the
     * permission granted and location turned off, [lastKnown] returns `null` and [observeLocation] emits
     * nothing — indistinguishable, from the collector's side, from "no fix yet". The SOS map needs to
     * tell them apart, because one is answered by waiting and the other by a trip to Settings.
     */
    open fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            // `isLocationEnabled` is API 28; on 26–27 either provider being on is the same answer.
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /** Either grant is enough — a coarse fix still sorts a directory of hospitals correctly. */
    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val UpdateIntervalMillis = 5_000L
        const val FastestIntervalMillis = 3_000L
        const val MinDisplacementMeters = 25f
    }
}
