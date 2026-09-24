package com.example.omni.data.repo

import android.util.Log
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.ConversationPageSize
import com.example.omni.data.model.MaxMessageLength
import com.example.omni.data.model.Message
import com.example.omni.data.model.MessagePageSize
import com.example.omni.data.model.conversationIdOf
import com.example.omni.data.model.conversationIdentityMap
import com.example.omni.data.model.newMessageMap
import com.example.omni.data.model.toConversation
import com.example.omni.data.model.toMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Threads and messages (BACKEND_PLAN §11 Phase 11).
 *
 * Text only — no images, no typing indicators, no group chats. That scope is the plan's, and it is what
 * keeps a messaging feature from consuming the rest of the term.
 */
interface MessageRepository {

    /** Every thread this user is in, most recent first. A listener, so an incoming message reorders it. */
    fun observeConversations(uid: String): Flow<List<Conversation>>

    /** The last [MessagePageSize] messages in one thread, **oldest first** — the order a chat reads in. */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Ensures the thread between these two people exists and returns its id.
     *
     * Idempotent: the id is derived from the two uids ([conversationIdOf]), and the write is a *merge*
     * of [com.example.omni.data.model.conversationIdentityMap] — participants and their denormalised
     * names and photos only. A thread that already has messages in it keeps its summary and both
     * unread counts untouched, and the two names are refreshed to whatever they are called today.
     *
     * It never reads the document first. See the implementation's comment: a `get` on a conversation
     * that does not exist yet is denied by the read rule, not answered with "no such document", and
     * that denial is what made the Message button on a public profile do nothing.
     *
     * **Throws when the two uids are the same.** `conversationIdOf(uid, uid)` is `"uid_uid"` and its
     * `participants` array holds one entry twice, which no rule and no reader expects: the thread list
     * would show a row whose "other" participant is me, and [com.example.omni.data.model.toConversation]
     * cannot name an other side that is not there. It is a caller's bug, not a user's, so it fails loudly
     * here — one guard at the boundary every path crosses, rather than a test at each of them.
     */
    suspend fun openConversation(
        selfUid: String,
        selfName: String,
        selfPhotoUrl: String?,
        otherUid: String,
        otherName: String,
        otherPhotoUrl: String?,
    ): String

    /**
     * Writes one message (text, image, or both) and moves the thread's summary and the recipient's unread count.
     *
     * At least one of [text] or [imageUrl] must be non-blank/non-null, otherwise the call is a no-op.
     */
    suspend fun send(
        conversationId: String,
        senderId: String,
        recipientId: String,
        text: String = "",
        imageUrl: String? = null,
    )

    /** Zeroes this user's half of the thread's unread counter. Their half only — never the other's. */
    suspend fun markRead(conversationId: String, uid: String)
}

/**
 * [MessageRepository] over Firestore.
 *
 * **The thread summary is maintained by the client, not by a function.** §9's `onMessageCreated` is what
 * should move `lastMessage`, `lastMessageAt` and the unread counters, and it is Phase 12's. Until it
 * exists the sender does it in the same [com.google.firebase.firestore.WriteBatch] as the message, which
 * the deployed rules already permit (`allow update: if … request.auth.uid in resource.data.participants`).
 * A batch rather than a transaction, for the reason every write in this app gives: transactions need the
 * network, and a message typed on a train has to leave the moment it is typed.
 *
 * **`readBy` is written once and never grows.** The rules forbid updating a message at all
 * (`allow update, delete: if false`), which is right — a message nobody can edit after the fact is the
 * whole point of a consultation log — but it means per-message read receipts are not something a client
 * can maintain. The thread-level `unread` counter carries "seen" instead, which is what the list draws.
 * §11 Phase 11 puts read receipts out of scope anyway.
 *
 * **One composite index is required** (§7): `conversations` where `participants array-contains` ordered
 * by `lastMessageAt desc`. Firestore prints the console link to create it on the first failure; until it
 * exists [observeConversations] logs and shows an empty list rather than crashing the screen.
 */
class FirestoreMessageRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : MessageRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val backendBaseUrl = "https://omni-jx01.onrender.com"

    override fun observeConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val registration = firestore.collection(Conversations)
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(ConversationPageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Degraded, not closed — a missing index must not take down the screen the user is
                    // standing on, and the empty state says there is nothing here.
                    Log.w("Omni", "The conversation list could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toConversation(uid) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val registration = messages(conversationId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            // The *last* page, not the first: a chat opens at the bottom, so the newest messages are
            // the ones worth paying for, and ascending order is what the column then renders top-down.
            .limitToLast(MessagePageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Omni", "Thread $conversationId could not be read", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toMessage() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun openConversation(
        selfUid: String,
        selfName: String,
        selfPhotoUrl: String?,
        otherUid: String,
        otherName: String,
        otherPhotoUrl: String?,
    ): String {
        require(selfUid != otherUid) { "A conversation needs two different people" }
        val id = conversationIdOf(selfUid, otherUid)

        // One merged write, and **no read first**.
        //
        // This used to `get()` the document before creating it, and that is what broke the Message
        // button on a public profile. The read rule is
        // `allow read: if signedIn() && request.auth.uid in resource.data.participants`, and on a
        // document that does not exist `resource` is null — so the expression errors and the rule
        // *denies*. The client does not get "not found", it gets PERMISSION_DENIED, and the first
        // message between two people could therefore never be sent. Nothing was wrong with the rules;
        // the repository was asking a question it had no right to ask.
        //
        // [conversationIdentityMap] is merge-safe by construction: it carries the participants and
        // their denormalised names and photos, and none of the summary fields a merge would blank.
        conversation(id)
            .set(
                conversationIdentityMap(
                    selfUid = selfUid,
                    selfName = selfName,
                    selfPhotoUrl = selfPhotoUrl,
                    otherUid = otherUid,
                    otherName = otherName,
                    otherPhotoUrl = otherPhotoUrl,
                ),
                SetOptions.merge(),
            )
            .await()
        return id
    }

    override suspend fun send(
        conversationId: String,
        senderId: String,
        recipientId: String,
        text: String,
        imageUrl: String?,
    ) {
        val body = text.trim().take(MaxMessageLength)
        if (body.isEmpty() && imageUrl == null) return

        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Not signed in")
        val json = JSONObject()
            .put("recipientId", recipientId)
            .put("text", body)
        imageUrl?.let { json.put("imageUrl", it) }
        val request = Request.Builder()
            .url("$backendBaseUrl/conversations/$conversationId/messages")
            .header("Authorization", "Bearer $idToken")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Message failed: ${response.code}")
                }
            }
        }
    }

    override suspend fun markRead(conversationId: String, uid: String) {
        conversation(conversationId)
            .set(mapOf("unread" to mapOf(uid to 0)), SetOptions.merge())
            .await()
    }

    private fun conversation(id: String) = firestore.collection(Conversations).document(id)

    private fun messages(id: String) = conversation(id).collection(Messages)

    private companion object {
        const val Conversations = "conversations"
        const val Messages = "messages"
    }
}

/** The in-memory twin, so previews and tests can render a full thread without Firebase. */
class PreviewMessageRepository : MessageRepository {

    private val conversations = MutableStateFlow(
        listOf(
            Conversation(
                id = "preview-thread",
                otherUid = "dr-rahman",
                otherName = "Dr. Sadia Rahman",
                lastMessage = "Take the paracetamol with food, and send me a photo if the rash spreads.",
                lastMessageAt = System.currentTimeMillis() - 9 * 60_000L,
                lastSenderId = "dr-rahman",
                unread = 1,
            ),
        ),
    )

    private val messages = MutableStateFlow(
        listOf(
            Message(
                id = "m0",
                senderId = "self",
                text = "Good evening doctor. I've had a fever since last night, around 101.",
                createdAt = System.currentTimeMillis() - 22 * 60_000L,
            ),
            Message(
                id = "m1",
                senderId = "dr-rahman",
                text = "Take the paracetamol with food, and send me a photo if the rash spreads.",
                createdAt = System.currentTimeMillis() - 9 * 60_000L,
            ),
        ),
    )

    override fun observeConversations(uid: String): Flow<List<Conversation>> = conversations

    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages

    override suspend fun openConversation(
        selfUid: String,
        selfName: String,
        selfPhotoUrl: String?,
        otherUid: String,
        otherName: String,
        otherPhotoUrl: String?,
    ): String {
        require(selfUid != otherUid) { "A conversation needs two different people" }
        return conversationIdOf(selfUid, otherUid)
    }

    override suspend fun send(
        conversationId: String,
        senderId: String,
        recipientId: String,
        text: String,
        imageUrl: String?,
    ) {
        if (text.isBlank() && imageUrl == null) return
        messages.value = messages.value + Message(
            id = "m${messages.value.size}",
            senderId = senderId,
            text = text.trim(),
            imageUrl = imageUrl,
            createdAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markRead(conversationId: String, uid: String) {
        conversations.value = conversations.value.map {
            if (it.id == conversationId) it.copy(unread = 0) else it
        }
    }
}

/** The unread total the header's message badge draws, summed across threads. */
fun Flow<List<Conversation>>.unreadTotal(): Flow<Int> =
    map { list -> list.sumOf { it.unread }.coerceAtLeast(0) }
