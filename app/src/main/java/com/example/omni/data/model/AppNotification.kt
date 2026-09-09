package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * One item in the bell's inbox — the `notifications/{uid}/items/{id}` document (BACKEND_PLAN §7).
 *
 * Named `AppNotification` rather than `Notification` on purpose: `android.app.Notification` is what the
 * messaging service builds, and two types with one name in the same feature would be read wrong every
 * time. This one is the *record*; that one is the tray entry the record can produce.
 *
 * [read] is the only key the security rules let a client change on an item it did not write, which is
 * exactly the shape the screen needs: open the list, mark them seen, never rewrite the text.
 */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val deeplink: String? = null,
    val read: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * What produced an item, which is all the screen uses to pick a colour.
 *
 * Stored as a string, mapped defensively: an item written by a newer Cloud Function than this build
 * knows about must render as [SYSTEM], not crash a list the user opened to read.
 */
enum class NotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    MESSAGE,
    VERIFICATION,

    /** Written by the client itself, for the user's own account — see [newNotificationMap]. */
    SOS,
    SYSTEM,
}

/**
 * The map written on create.
 *
 * `createdAt` is a server timestamp for the same reason every other write in this app uses one: a
 * phone with a wrong clock would otherwise sort its own notifications above or below everyone's
 * forever. Between the local write and the server's answer the field reads as 0, which
 * [relativeTimeOf] renders as "just now".
 */
fun newNotificationMap(
    type: NotificationType,
    title: String,
    body: String,
    deeplink: String? = null,
): Map<String, Any?> = mapOf(
    "type" to type.name,
    "title" to title.take(MaxNotificationTitleLength),
    "body" to body.take(MaxNotificationBodyLength),
    "deeplink" to deeplink,
    "read" to false,
    "createdAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toAppNotification(): AppNotification? {
    if (!exists()) return null
    return AppNotification(
        id = id,
        type = getString("type")
            ?.let { name -> NotificationType.entries.firstOrNull { it.name == name } }
            ?: NotificationType.SYSTEM,
        title = getString("title").orEmpty().take(MaxNotificationTitleLength),
        body = getString("body").orEmpty().take(MaxNotificationBodyLength),
        deeplink = getString("deeplink"),
        read = getBoolean("read") ?: false,
        createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L,
    )
}

/**
 * Clamped on the way in *and* on the way out (`UI_ARCHITECTURE.md` §6 rule 8).
 *
 * The title is one line in the row and the body is two, so a Cloud Function — or a console — sending a
 * paragraph must not be able to push the timestamp off the card. Truncating at the model boundary means
 * every screen that ever renders one of these inherits the clamp instead of having to remember it.
 */
const val MaxNotificationTitleLength = 60
const val MaxNotificationBodyLength = 160

/** One screenful and then some. The inbox is read, not paged — nobody scrolls to notification 200. */
const val NotificationPageSize = 50L
