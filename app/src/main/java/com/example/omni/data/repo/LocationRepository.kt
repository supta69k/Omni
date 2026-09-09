package com.example.omni.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/**
 * The device's own location — one interface, one implementation, no fake (BACKEND_PLAN §4 rule 1).
 *
 * [lastKnown] rather than a continuous stream: the SOS screen needs "where am I right now" once per
 * activation and once per directory sort, not a walking breadcrumb. `getCurrentLocation` with a
 * balanced priority returns a fresh fix when one is cheaply available and the last known one
 * otherwise, which is the honest trade for an emergency screen — a slightly stale location beats a
 * spinner while someone is bleeding.
 *
 * The permission is *not* asked for in here. A repository has no window to ask from; the screen's
 * caller (MainActivity, like the step permission before it) owns the launcher and hands the answer
 * down. Calling [lastKnown] without the permission returns `null` rather than throwing — the
 * SecurityException the framework would raise is a developer error being turned into a user state.
 */
class LocationRepository(
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
    suspend fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (_: Exception) {
            // No play services, or the location is off: the caller degrades to the unsorted
            // directory rather than crashing the emergency screen.
            null
        }
    }

    /** Either grant is enough — a coarse fix still sorts a directory of hospitals correctly. */
    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
