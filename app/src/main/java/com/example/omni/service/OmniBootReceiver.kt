package com.example.omni.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the tracking service after a reboot or an app update — the two moments the process and
 * its foreground state are wiped and nothing else would bring the pipeline back until the next
 * time the app was opened. The service re-seeds from its own persisted anchor, so nothing that was
 * counted before the interruption is lost.
 *
 * On MIUI/POCO this receiver needs Omni's **Autostart** toggle; without it the restart happens on
 * the next app open instead.
 */
class OmniBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Restarting the tracking service after ${intent.action}")
                OmniTrackingService.start(context)
            }
        }
    }

    private companion object {
        const val TAG = "OmniTracking"
    }
}
