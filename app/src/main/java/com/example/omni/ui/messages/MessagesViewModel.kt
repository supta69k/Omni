package com.example.omni.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.MaxMessageLength
import com.example.omni.data.model.Message
import com.example.omni.data.model.Profession
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One row of the "new message" picker — a verified professional, flattened to what the row draws.
 *
 * Flattened rather than passing [User] itself, for [com.example.omni.ui.firstaid.GuideCardState]'s
 * reason: a picker row that could reach `user.email` is a row that will eventually render it.
 */
data class ProfessionalRowState(
    val uid: String,
    val name: String,
    val discipline: String,
    val photoUrl: String? = null,
)

/**
 * The open thread — the chat page.
 *
 * [selfUid] is here rather than read from the repository by the screen because it is what decides which
 * side of the column a bubble sits on, and a screen must not be able to ask who is signed in
 * (BACKEND_PLAN §4 rule 1).
 */
data class OpenChatState(
    val conversationId: String,
    val selfUid: String,
    val otherName: String,
    val otherPhotoUrl: String? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
) {
    /** Whitespace is not a message. The send button is drawn disabled rather than absent — see the screen. */
    val canSend: Boolean get() = draft.isNotBlank()
}

/**
 * Everything the two messaging pages render.
 *
 * [open] is what makes the list and the chat one destination rather than two, exactly as
 * [com.example.omni.ui.firstaid.GuidesUiState.open] does for first aid: the router owns "the user is in
 * Messages", this owns "and is talking to Dr. Rahman". Keeping it here is what survives a rotation, and it
 * is the key the message listener is built on.
 *
 * @property professionals only ever populated while [pickerOpen] is true — see [MessagesViewModel].
 */
data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val professionals: List<ProfessionalRowState> = emptyList(),
    val pickerOpen: Boolean = false,
    val loading: Boolean = true,
    val open: OpenChatState? = null,
)

/**
 * Messaging (BACKEND_PLAN §11 Phase 11) — the thread list, the professional picker and one open chat.
 *
 * One ViewModel for both pages, for the reason [com.example.omni.ui.firstaid.GuidesViewModel] gives: the
 * back gesture inside a chat has to land on the list rather than leaving Messages, and that is a decision
 * about *state*, not about routing.
 *
 * Who a user may write to is §12's product answer — "users → verified professionals only" — which is why
 * the only way into a new thread is [startWith] over [UserRepository.observeProfessionals]. There is no
 * free-text uid entry and no user search, so a thread with an ordinary account cannot be created from this
 * app at all. The rules do not enforce that (any two signed-in accounts may still create a thread), and
 * that gap is recorded in BACKEND_PLAN §0.1 rather than hidden here.
 */
class MessagesViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key every read below restarts on. */
    private val uid: Flow<String?> = authRepository.authState
        .map { state -> if (state == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()

    private val openThread = MutableStateFlow<OpenThread?>(null)
    private val draft = MutableStateFlow("")
    private val picker = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversations: Flow<List<Conversation>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            messageRepository.observeConversations(uid).catch { cause ->
                Log.w("Omni", "The conversation list for $uid could not be read", cause)
                emit(emptyList())
            }
        }
    }

    /**
     * The picker's contents — and **only while the picker is open**.
     *
     * The directory is a collection-wide listener over every verified account; holding it for the whole
     * time somebody is reading their messages would be paying for a list nobody is looking at. Closing the
     * picker drops the registration.
     *
     * The signed-in user is filtered out of their own picker: a verified professional would otherwise see
     * their own name, and `conversationIdOf(uid, uid)` is a thread with one participant, which
     * [com.example.omni.data.model.toConversation] correctly refuses to map — a row that could never open.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val professionals: Flow<List<ProfessionalRowState>> =
        combine(uid, picker) { uid, open -> uid to open }
            .distinctUntilChanged()
            .flatMapLatest { (uid, open) ->
                if (!open) {
                    flowOf(emptyList())
                } else {
                    userRepository.observeProfessionals()
                        .map { list -> list.filter { it.uid != uid }.map { it.toRow() } }
                        .catch { cause ->
                            Log.w("Omni", "The professional directory could not be read", cause)
                            emit(emptyList())
                        }
                }
            }

    /**
     * The open thread's messages.
     *
     * Keyed on the thread id alone, so re-opening the same chat does not tear the listener down and put an
     * identical one back, and closing the chat leaves no listener at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages: Flow<List<Message>> = openThread
        .map { it?.conversationId }
        .distinctUntilChanged()
        .flatMapLatest { conversationId ->
            if (conversationId == null) {
                flowOf(emptyList())
            } else {
                messageRepository.observeMessages(conversationId).catch { cause ->
                    Log.w("Omni", "Thread $conversationId could not be read", cause)
                    emit(emptyList())
                }
            }
        }

    private val chat: Flow<OpenChatState?> =
        combine(uid, openThread, messages, draft) { uid, thread, messages, draft ->
            if (uid == null || thread == null) {
                null
            } else {
                OpenChatState(
                    conversationId = thread.conversationId,
                    selfUid = uid,
                    otherName = thread.otherName,
                    otherPhotoUrl = thread.otherPhotoUrl,
                    messages = messages,
                    draft = draft,
                )
            }
        }

    val uiState: StateFlow<MessagesUiState> =
        combine(conversations, professionals, picker, chat) { conversations, professionals, picker, chat ->
            MessagesUiState(
                conversations = conversations,
                professionals = professionals,
                pickerOpen = picker,
                loading = false,
                open = chat,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = MessagesUiState(),
        )

    /** Opens or closes the "new message" picker. Opening it is what starts the directory listener. */
    fun togglePicker() {
        picker.value = !picker.value
    }

    /**
     * Opens an existing thread from the list, and marks it read.
     *
     * The unread count is zeroed on open rather than on scroll: this app's threads are short consultations,
     * and "you opened it" is the only read signal a client can honestly produce — per-message receipts are
     * out of reach, see [com.example.omni.data.repo.FirestoreMessageRepository].
     */
    fun open(conversation: Conversation) {
        picker.value = false
        draft.value = ""
        openThread.value = OpenThread(
            conversationId = conversation.id,
            otherUid = conversation.otherUid,
            otherName = conversation.otherName,
            otherPhotoUrl = conversation.otherPhotoUrl,
        )

        if (conversation.unread == 0) return
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                messageRepository.markRead(conversation.id, uid)
            } catch (cause: Exception) {
                Log.w("Omni", "Thread ${conversation.id} could not be marked read", cause)
            }
        }
    }

    /**
     * Starts (or re-enters) the thread with one professional.
     *
     * [selfName] and [selfPhotoUrl] come from the session the router already holds rather than from a
     * second `users/{uid}` listener here — they are denormalised onto the thread so the *other* side can
     * draw this user's name without a read (see [com.example.omni.data.model.newConversationMap]).
     *
     * The chat opens only once the document is known to exist. Opening it optimistically would put the
     * user in front of an input box whose first message could fail the rules, and this is the one write in
     * the app where "it looked like it worked" is worst.
     */
    fun startWith(professional: ProfessionalRowState, selfName: String, selfPhotoUrl: String?) {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                val conversationId = messageRepository.openConversation(
                    selfUid = uid,
                    selfName = selfName,
                    selfPhotoUrl = selfPhotoUrl,
                    otherUid = professional.uid,
                    otherName = professional.name,
                    otherPhotoUrl = professional.photoUrl,
                )
                picker.value = false
                draft.value = ""
                openThread.value = OpenThread(
                    conversationId = conversationId,
                    otherUid = professional.uid,
                    otherName = professional.name,
                    otherPhotoUrl = professional.photoUrl,
                )
            } catch (cause: Exception) {
                // Reachable offline, unlike every other write in this app: `openConversation` has to
                // *read* before it creates, and a read of a document the cache has never seen cannot be
                // served locally. The picker stays open, which is the honest outcome — nothing happened.
                Log.w("Omni", "A thread with ${professional.uid} could not be opened", cause)
            }
        }
    }

    /** Clamped where it enters, like every other bound string (`UI_ARCHITECTURE.md` §6 rule 8). */
    fun onDraftChange(value: String) {
        draft.value = value.take(MaxMessageLength)
    }

    /**
     * Sends the draft.
     *
     * The field is cleared before the write, not after it: Firestore applies the message to its local cache
     * and re-fires the thread listener before the round-trip, so the bubble appears on the same frame the
     * input empties — offline included. A failure here is a rules rejection rather than a lost network, and
     * it is logged rather than restored into the field, because putting text back into a box the user has
     * already started retyping in is worse than losing it.
     */
    fun send() {
        val thread = openThread.value ?: return
        val uid = authRepository.currentUid ?: return
        val body = draft.value.trim()
        if (body.isEmpty()) return

        draft.value = ""
        viewModelScope.launch {
            try {
                messageRepository.send(
                    conversationId = thread.conversationId,
                    senderId = uid,
                    recipientId = thread.otherUid,
                    text = body,
                )
            } catch (cause: Exception) {
                Log.w("Omni", "A message to ${thread.otherUid} could not be sent", cause)
            }
        }
    }

    /** Returns whether a chat was open, so the caller can fall through to its own back behaviour. */
    fun closeChat(): Boolean {
        val wasOpen = openThread.value != null
        openThread.value = null
        draft.value = ""
        return wasOpen
    }

    /** The selection, kept private: `otherUid` is routing state, not something a screen renders. */
    private data class OpenThread(
        val conversationId: String,
        val otherUid: String,
        val otherName: String,
        val otherPhotoUrl: String?,
    )

    private companion object {
        /** Outlives a rotation; a backgrounded app stops paying for the thread listeners. */
        const val ListenerGraceMillis = 5_000L

        /**
         * The discipline under a name in the picker.
         *
         * `profession` is nullable on [User] but never null here — [UserRepository.observeProfessionals]
         * drops accounts without one — so the fallback is unreachable rather than a real state, and says
         * so rather than rendering an empty line.
         */
        fun User.toRow(): ProfessionalRowState = ProfessionalRowState(
            uid = uid,
            name = name.ifBlank { "Omni member" },
            discipline = when (profession) {
                Profession.DOCTOR -> "Doctor"
                Profession.NUTRITIONIST -> "Nutritionist"
                null -> "Verified professional"
            },
            photoUrl = photoUrl,
        )
    }
}
