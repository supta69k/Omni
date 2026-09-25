package com.example.omni.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * One thread — the `conversations/{conversationId}` document (BACKEND_PLAN §7).
 *
 * The other participant's name and photo are **denormalised onto the thread**, exactly as the feed
 * denormalises a post's author. A conversation list of ten rows would otherwise be ten extra
 * `users/{uid}` reads before it could draw a single name, and offline it would draw none at all.
 *
 * @property otherUid resolved at mapping time from the two-element `participants` array, so no screen
 *   ever has to work out which of the two it is looking at.
 * @property unread the signed-in user's own count, not both. See [toConversation].
 */
data class Conversation(
    val id: String,
    val otherUid: String,
    val otherName: String,
    val otherPhotoUrl: String? = null,
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val lastSenderId: String = "",
    val unread: Int = 0,
) {

    /**
     * An Omni+ consultation thread rather than a social one. Consultation ids are built as
     * `consultation_{patientUid}_{doctorUid}` (see `DoctorConsultationViewModel`), and no Firebase
     * uid begins with that prefix, so the id alone is the marker — no extra field, no extra index.
     */
    val isConsultation: Boolean get() = id.startsWith("consultation_")
}

/** One message — `conversations/{conversationId}/messages/{messageId}`. Text or an image (or both). */
data class Message(
    val id: String,
    val senderId: String,
    val text: String = "",
    val imageUrl: String? = null,
    val createdAt: Long = 0L,
)

/**
 * The thread id: the two uids sorted and joined with `_`.
 *
 * Sorted so it is the same string whichever end computes it — which is what makes opening a chat
 * idempotent. Without it, two people tapping each other's name at the same moment would create two
 * threads holding half a conversation each.
 */
fun conversationIdOf(a: String, b: String): String =
    if (a < b) "${a}_$b" else "${b}_$a"

/**
 * The *identity* of a thread — who is in it, and what they are called.
 *
 * This is the whole document [com.example.omni.data.repo.MessageRepository.openConversation] writes,
 * and it is written with `SetOptions.merge()`. The four summary fields (`lastMessage`,
 * `lastMessageAt`, `lastSenderId`, `unread`) are **deliberately absent**: they are created by the first
 * [newMessageMap] that lands, and merging blanks over a thread that already has messages in it would
 * wipe the inbox preview, reset the other person's unread badge, and bump an untouched thread to the
 * top of their list every time somebody opened the chat.
 *
 * Merging identity, on the other hand, is wanted: a participant who has since changed their display
 * name or avatar is re-denormalised onto the thread by the next person who opens it.
 *
 * A thread with no messages therefore has no `lastMessageAt`, so `orderBy("lastMessageAt")` leaves it
 * out of both inboxes until somebody says something. That is the behaviour every messenger has — an
 * empty thread you opened and abandoned is not a conversation.
 *
 * It satisfies both halves of the deployed rule on `conversations/{cid}`. On a document that does not
 * exist yet the write is a `create`, and `participants` is present, two long and distinct. On one that
 * does it is an `update`, and the merged `participants` is byte-identical to the stored array because
 * both ends sort it — which is exactly what `request.resource.data.participants ==
 * resource.data.participants` asks.
 */
fun conversationIdentityMap(
    selfUid: String,
    selfName: String,
    selfPhotoUrl: String?,
    otherUid: String,
    otherName: String,
    otherPhotoUrl: String?,
): Map<String, Any?> = mapOf(
    "participants" to listOf(selfUid, otherUid).sorted(),
    "participantNames" to mapOf(
        selfUid to selfName.take(MaxParticipantNameLength),
        otherUid to otherName.take(MaxParticipantNameLength),
    ),
    "participantPhotos" to mapOf(selfUid to selfPhotoUrl, otherUid to otherPhotoUrl),
)

/**
 * The message document. Server-stamped for the same reason every other write in this app is.
 *
 * Either [text] or [imageUrl] (or both) must be present; a message with neither is rejected at the
 * repository boundary.
 */
fun newMessageMap(senderId: String, text: String, imageUrl: String? = null): Map<String, Any?> = buildMap {
    put("senderId", senderId)
    if (text.isNotBlank()) put("text", text.take(MaxMessageLength))
    if (imageUrl != null) put("imageUrl", imageUrl)
    put("createdAt", FieldValue.serverTimestamp())
    put("readBy", listOf(senderId))
}

/**
 * @param selfUid whose side of the thread this is — it decides which participant is "the other one"
 *   and which half of the `unread` map is this user's.
 */
fun DocumentSnapshot.toConversation(selfUid: String): Conversation? {
    if (!exists()) return null

    @Suppress("UNCHECKED_CAST")
    val participants = get("participants") as? List<String> ?: return null

    // A thread that does not have two people in it, or does not have *this* person in it, is a
    // half-written document rather than something to render. Dropped rather than drawn blank.
    val other = participants.firstOrNull { it != selfUid } ?: return null
    if (selfUid !in participants) return null

    return Conversation(
        id = id,
        otherUid = other,
        otherName = (get("participantNames.$other") as? String)
            .orEmpty()
            .take(MaxParticipantNameLength)
            .ifBlank { "Omni member" },
        otherPhotoUrl = get("participantPhotos.$other") as? String,
        lastMessage = getString("lastMessage").orEmpty().take(MaxMessageLength),
        lastMessageAt = millisOf("lastMessageAt"),
        lastSenderId = getString("lastSenderId").orEmpty(),
        unread = (get("unread.$selfUid") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
    )
}

fun DocumentSnapshot.toMessage(): Message? {
    if (!exists()) return null
    val senderId = getString("senderId").orEmpty()
    if (senderId.isBlank()) return null
    return Message(
        id = id,
        senderId = senderId,
        text = getString("text").orEmpty().take(MaxMessageLength),
        imageUrl = getString("imageUrl"),
        createdAt = millisOf("createdAt"),
    )
}

/**
 * Clamped at the model boundary, like every other bound string in this app
 * (`UI_ARCHITECTURE.md` §6 rule 8).
 *
 * A message is a bubble that wraps, so its cap is generous — this is a consultation channel, and
 * "describe your symptoms" is a paragraph. A name is one line beside an avatar, so its cap is the
 * header's.
 */
const val MaxMessageLength = 2_000
const val MaxParticipantNameLength = 40

/** One screenful of threads, and one long conversation. Neither list is paged — see §11 Phase 11. */
const val ConversationPageSize = 50L
const val MessagePageSize = 100L
