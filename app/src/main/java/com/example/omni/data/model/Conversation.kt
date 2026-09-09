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
)

/** One message — `conversations/{conversationId}/messages/{messageId}`. Text only, per §11 Phase 11. */
data class Message(
    val id: String,
    val senderId: String,
    val text: String,
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
 * The document written when a thread is first opened.
 *
 * `unread` starts at zero for both: opening a chat is not a message, and the recipient must not see a
 * badge for a thread nobody has said anything in yet.
 */
fun newConversationMap(
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
    "lastMessage" to "",
    "lastMessageAt" to FieldValue.serverTimestamp(),
    "lastSenderId" to "",
    "unread" to mapOf(selfUid to 0, otherUid to 0),
)

/** The message document. Server-stamped for the same reason every other write in this app is. */
fun newMessageMap(senderId: String, text: String): Map<String, Any?> = mapOf(
    "senderId" to senderId,
    "text" to text.take(MaxMessageLength),
    "createdAt" to FieldValue.serverTimestamp(),
    "readBy" to listOf(senderId),
)

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
        lastMessageAt = (get("lastMessageAt") as? Number)?.toLong() ?: 0L,
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
        createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L,
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
