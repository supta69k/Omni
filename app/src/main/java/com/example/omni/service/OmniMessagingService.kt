package com.example.omni.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.omni.MainActivity
import com.example.omni.R
import com.example.omni.di.AppContainer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The FCM entry point (BACKEND_PLAN §11 Phase 10).
 *
 * Two jobs, and nothing else: keep `users/{uid}.fcmTokens` current, and turn an incoming message into a
 * tray entry. It deliberately does **not** write the notification into Firestore — the Cloud Function
 * that sent the push already wrote the record it is announcing, and doing it here as well would double
 * every item for a phone that happened to be awake.
 *
 * A service is not a screen, so it has no ViewModel and no lifecycle to hang a scope on; the writes run
 * on [scope], which lives as long as the process. That is acceptable precisely because the work is one
 * short Firestore write whose offline queue survives the process anyway.
 */
class
OmniMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A new token, either because this is a fresh install or because Firebase rotated it.
     *
     * Signed out, there is nowhere to put it — so it is dropped, and `MainActivity` re-reads the current
     * token the next time somebody signs in. That is the only reason a token can ever be missing from a
     * signed-in account, and it self-heals on the next launch.
     */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                AppContainer.current.notificationRepository.registerToken(uid, token)
            } catch (cause: Exception) {
                Log.w("Omni", "The new FCM token could not be stored", cause)
            }
        }
    }

    /**
     * Shows the push.
     *
     * Handles both payload shapes: a `notification` block (which the system draws itself when the app is
     * in the background, and hands here when it is in the foreground) and a data-only message (which
     * always comes here). Reading both means one code path decides what the tray entry looks like, so a
     * foreground push and a background one cannot end up worded differently.
     *
     * `prefs.pushNotifications` is honoured one step earlier and more reliably than a check here could
     * manage: switching it off removes this device's token, so the server has nothing to send to. A
     * check in this method would only fire after the message had already been delivered and paid for,
     * and would need a network read of its own to know the answer.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return

        if (!canPostNotifications()) {
            // The permission was refused, or revoked in system settings after being granted. Nothing
            // to do — the item is already in Firestore, so the bell will show it on next open.
            Log.i("Omni", "A push arrived but notifications are not permitted; the inbox still has it")
            return
        }

        ensureChannel(this)

        val open = Intent(this, MainActivity::class.java).apply {
            // The launcher's own flags: tapping the notification brings the existing task forward
            // rather than stacking a second copy of the app on top of itself.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            // So a two-line SOS or verification message is readable without opening the app.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        // The id is derived from the message rather than fixed, so two pushes stack instead of the
        // second silently replacing the first — but two deliveries of the *same* message collapse.
        val id = (message.messageId ?: body).hashCode()
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Matches the `default_notification_channel_id` meta-data in the manifest. */
        const val ChannelId = "omni_general"

        /**
         * Creates the channel if it is not already there.
         *
         * Idempotent, and called from both the service and the Activity: a channel must exist before the
         * *first* notification or Android drops it silently, and on a cold push the service may run
         * before any screen has. `createNotificationChannel` on an existing id only updates the name, so
         * calling it twice is free.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                ChannelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
