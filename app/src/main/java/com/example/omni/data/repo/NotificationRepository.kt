package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.AppNotification
import com.example.omni.data.model.NotificationPageSize
import com.example.omni.data.model.NotificationType
import com.example.omni.data.model.newNotificationMap
import com.example.omni.data.model.toAppNotification
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The bell's inbox, the FCM token, and the push preference (BACKEND_PLAN §11 Phase 10).
 *
 * Three concerns in one repository because they are one feature: an inbox nobody can be told about is
 * half a notification system, and the preference is what decides whether the other two run at all.
 * Two of them live on `users/{uid}` rather than under `notifications/`, which is why this type takes
 * the whole [FirebaseFirestore] handle instead of a single collection.
 */
interface NotificationRepository {

    /** The newest [NotificationPageSize] items, newest first. A listener, so a push updates the list. */
    fun observe(uid: String): Flow<List<AppNotification>>

    /** How many are unread — what the header's bell badge counts. See the implementation for why. */
    fun observeUnreadCount(uid: String): Flow<Int>

    /** Marks one item seen. `read` is the only key the rules let the recipient touch. */
    suspend fun markRead(uid: String, notificationId: String)

    /** The same for everything currently unread, in one batch. */
    suspend fun markAllRead(uid: String)

    /**
     * Writes an item into the signed-in user's *own* inbox.
     *
     * Only the client's own account: everything cross-account (a like, a comment, a follow) is written
     * by a Cloud Function running with admin credentials, because a client may not write into somebody
     * else's subtree and never should be able to.
     */
    suspend fun post(
        uid: String,
        type: NotificationType,
        title: String,
        body: String,
        deeplink: String? = null,
    )

    /** Adds this device's FCM token to `users/{uid}.fcmTokens`. Idempotent — it is an `arrayUnion`. */
    suspend fun registerToken(uid: String, token: String)

    /** Removes it again: what "push off" means in practice, and what a sign-out has to do. */
    suspend fun unregisterToken(uid: String, token: String)

    /** Persists `users/{uid}.prefs.pushNotifications`, which the settings toggle now writes. */
    suspend fun setPushEnabled(uid: String, enabled: Boolean)

    /** The same for `prefs.offlineCache`, so the second switch remembers its position too. */
    suspend fun setOfflineCacheEnabled(uid: String, enabled: Boolean)
}

/**
 * [NotificationRepository] over Firestore.
 *
 * **On the badge count.** BACKEND_PLAN §11 Phase 10 says to bind the bell to `users/{uid}.unread
 * .notifications`. That field is maintained by the Phase 12 Cloud Functions and the deployed rules
 * explicitly forbid a client from writing it — so until those functions exist it is absent, reads as 0,
 * and the badge could never appear no matter how full the inbox was. [observeUnreadCount] therefore
 * counts the unread items directly, which the rules *do* allow the owner to read. It is one extra
 * listener on a small collection, it is ground truth rather than a cache, and when the functions land
 * the denormalised counter becomes an optimisation this can switch to rather than a prerequisite.
 */
class FirestoreNotificationRepository(
    private val firestore: FirebaseFirestore,
) : NotificationRepository {

    override fun observe(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val registration = items(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(NotificationPageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Degraded, not closed: an unreadable inbox must not take down the screen the user
                    // is standing on. The empty state says there is nothing here, which is what they see.
                    Log.w("Omni", "notifications/$uid/items could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toAppNotification() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeUnreadCount(uid: String): Flow<Int> = callbackFlow {
        val registration = items(uid)
            .whereEqualTo("read", false)
            // Bounded so a neglected inbox cannot pull hundreds of documents down for one number. The
            // badge draws a dot, not a total, so anything past the cap is indistinguishable anyway.
            .limit(UnreadCountCap)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Omni", "The unread notification count could not be read", error)
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }.map { it.coerceAtLeast(0) }

    override suspend fun markRead(uid: String, notificationId: String) {
        items(uid).document(notificationId).update("read", true).await()
    }

    override suspend fun markAllRead(uid: String) {
        val unread = items(uid)
            .whereEqualTo("read", false)
            .limit(UnreadCountCap)
            .get()
            .await()
        if (unread.isEmpty) return

        // A batch, not a transaction: transactions need the network, and this is a write the user
        // expects to take effect the instant they open the screen — offline included (§4 rule 6).
        val batch = firestore.batch()
        unread.documents.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }

    override suspend fun post(
        uid: String,
        type: NotificationType,
        title: String,
        body: String,
        deeplink: String?,
    ) {
        items(uid)
            .document()
            .set(newNotificationMap(type, title, body, deeplink))
            .await()
    }

    override suspend fun registerToken(uid: String, token: String) {
        user(uid).set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
    }

    override suspend fun unregisterToken(uid: String, token: String) {
        user(uid).set(mapOf("fcmTokens" to FieldValue.arrayRemove(token)), SetOptions.merge()).await()
    }

    override suspend fun setPushEnabled(uid: String, enabled: Boolean) = setPref("pushNotifications", uid, enabled)

    override suspend fun setOfflineCacheEnabled(uid: String, enabled: Boolean) = setPref("offlineCache", uid, enabled)

    /**
     * One key inside the `prefs` map.
     *
     * Written as a nested map through `set(…, merge())` rather than as the dotted path `prefs.push…`
     * that `update()` would take: in a `set` a dot is part of the *name*, so the dotted version would
     * silently create a top-level field literally called "prefs.pushNotifications" and leave the real
     * map untouched. A merged nested map is recursive, so the sibling key survives.
     */
    private suspend fun setPref(key: String, uid: String, enabled: Boolean) {
        user(uid).set(mapOf("prefs" to mapOf(key to enabled)), SetOptions.merge()).await()
    }

    private fun items(uid: String) =
        firestore.collection(Notifications).document(uid).collection(Items)

    private fun user(uid: String) = firestore.collection(Users).document(uid)

    private companion object {
        const val Notifications = "notifications"
        const val Items = "items"
        const val Users = "users"

        /** See [observeUnreadCount]: a ceiling on the read, not on the inbox. */
        const val UnreadCountCap = 99L
    }
}

/**
 * The in-memory twin, so a preview or a test can render a full inbox without Firebase.
 *
 * Seeded with one of each kind the app can actually produce today — the client-written SOS record and a
 * system line — rather than a mock like and a mock comment, which nothing writes until Phase 12.
 */
class PreviewNotificationRepository : NotificationRepository {

    private val items = MutableStateFlow(
        listOf(
            AppNotification(
                id = "preview-0",
                type = NotificationType.SOS,
                title = "SOS recorded",
                body = "Your alert was saved with your location. 2 contacts were opened in Messages.",
                read = false,
                createdAt = System.currentTimeMillis() - 4 * 60_000L,
            ),
            AppNotification(
                id = "preview-1",
                type = NotificationType.SYSTEM,
                title = "Welcome to Omni",
                body = "Track water, steps and meals, and keep the first-aid guides on your phone.",
                read = true,
                createdAt = System.currentTimeMillis() - 26 * 60 * 60_000L,
            ),
        ),
    )

    override fun observe(uid: String): Flow<List<AppNotification>> = items

    override fun observeUnreadCount(uid: String): Flow<Int> = items.map { list -> list.count { !it.read } }

    override suspend fun markRead(uid: String, notificationId: String) {
        items.value = items.value.map { if (it.id == notificationId) it.copy(read = true) else it }
    }

    override suspend fun markAllRead(uid: String) {
        items.value = items.value.map { it.copy(read = true) }
    }

    override suspend fun post(
        uid: String,
        type: NotificationType,
        title: String,
        body: String,
        deeplink: String?,
    ) {
        items.value = listOf(
            AppNotification(
                id = "preview-${items.value.size}",
                type = type,
                title = title,
                body = body,
                deeplink = deeplink,
                createdAt = System.currentTimeMillis(),
            ),
        ) + items.value
    }

    override suspend fun registerToken(uid: String, token: String) = Unit

    override suspend fun unregisterToken(uid: String, token: String) = Unit

    override suspend fun setPushEnabled(uid: String, enabled: Boolean) = Unit

    override suspend fun setOfflineCacheEnabled(uid: String, enabled: Boolean) = Unit
}
