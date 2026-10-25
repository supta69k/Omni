package com.example.omni.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.omni.di.AppContainer

/**
 * Restarts the tracking service after a reboot, app update, or watchdog trigger — ensuring
 * the step counter pipeline and persistent notification stay alive 24/7 without requiring
 * the user to keep the app open in Recents.
 *
 * On MIUI/POCO this receiver needs Omni's **Autostart** toggle; without it the restart happens on
 * the next app open instead.
 */
class OmniBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON,
            OmniTrackingService.ACTION_WATCHDOG_RESTART -> {
                Log.i(TAG, "Restarting the tracking service after ${intent.action}")
                AppContainer.ensureInit(context)
                OmniTrackingService.start(context)
            }
        }
    }

    private companion object {
        const val TAG = "OmniTracking"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
