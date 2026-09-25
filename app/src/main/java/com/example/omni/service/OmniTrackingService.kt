package com.example.omni.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.omni.MainActivity
import com.example.omni.R
import com.example.omni.data.model.todayKeyFlow
import com.example.omni.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The permanent foreground service that owns the step pipeline.
 *
 * Everything `DeviceStepsRepository.observeTodaySteps` does — the sensor listener, the reconcile
 * into DataStore, the throttled Firestore sync — is collected here and nowhere else: exactly one
 * listener and one reconciler per process, alive while Omni is closed, because the point of the
 * service is that counting does not depend on any screen. The dashboard reads the pipeline's
 * persisted state through `StepsRepository.observeLocalSteps` instead of running its own copy,
 * which is what keeps two reconcilers from racing on the same DataStore.
 *
 * The notification is the service's user face and the live panel in one: today's steps and water
 * glasses, updated as the flows move. A foreground service must show a notification anyway, so
 * the panel is free.
 *
 * Started from `OmniApplication` on every app open, from `OmniBootReceiver` after a reboot or an
 * app update, and again whenever the step permission is granted. On MIUI/POCO the boot restart
 * also needs Autostart enabled for Omni; without it the service still starts on the next app open
 * and the counter catches up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OmniTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.tracking_channel_description) },
        )
        // Foreground first — a startForegroundService must show the notification within seconds —
        // then swap in real numbers as the pipeline produces them.
        startForegroundWith(steps = 0, glasses = 0)
        observePipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The pipeline is keyed on the session and the date — the same keys the dashboard uses — so a
     * sign-out/in or a midnight swaps the flows instead of leaking one account's state into the
     * next, and the re-collected pipeline re-seeds through [com.example.omni.data.local.seedForRollover]
     * on its way up. `observeTodaySteps(null)` emits a flat zero: signed out, nothing counts and
     * nothing syncs, exactly as the repository documents.
     */
    private fun observePipeline() {
        val container = AppContainer.current
        combine(container.authRepository.sessionUid, todayKeyFlow()) { uid, today -> uid to today }
            .distinctUntilChanged()
            .flatMapLatest { (uid, today) ->
                combine(
                    container.stepsRepository.observeTodaySteps(uid),
                    if (uid == null) {
                        flowOf(null)
                    } else {
                        container.metricsRepository.observeDay(uid, today)
                    },
                ) { steps, day -> steps to (day?.waterGlasses ?: 0) }
            }
            .onEach { (steps, glasses) ->
                startForegroundWith(steps = steps, glasses = glasses)
            }
            .launchIn(scope)
    }

    private fun startForegroundWith(steps: Int, glasses: Int) {
        val notification = buildNotification(steps = steps, glasses = glasses)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 enforces the health type's permission at start; `start` gates on
            // ACTIVITY_RECOGNITION before the service is ever launched.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(steps: Int, glasses: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home_nav_shoe)
            .setContentTitle(getString(R.string.tracking_notification_title, steps))
            .setContentText(getString(R.string.tracking_notification_text, glasses))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    companion object {
        private const val TAG = "OmniTracking"
        private const val CHANNEL_ID = "omni_tracking"
        private const val NOTIFICATION_ID = 41

        /**
         * Starts the service, but only when it can legally run: on API 29+ the sensor is silent
         * without ACTIVITY_RECOGNITION, and on API 34+ *starting* a health-type foreground service
         * without it is an exception. Callers re-invoke this after the permission is granted — the
         * next app open or resume covers it.
         */
        fun start(context: Context) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACTIVITY_RECOGNITION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Tracking service not started — ACTIVITY_RECOGNITION not granted yet")
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, OmniTrackingService::class.java))
        }
    }
}
