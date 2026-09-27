package com.example.omni.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.Doctor
import com.example.omni.data.model.MaxMessageLength
import com.example.omni.data.model.Message
import com.example.omni.data.model.Profession
import com.example.omni.data.model.User
import com.example.omni.data.model.UserRole
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.DoctorRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.data.revenuecat.RevenueCatRepository
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One row of the "new message" picker — somebody this user can start a thread with.
 *
 * Flattened rather than passing [User] itself, for [com.example.omni.ui.firstaid.GuideCardState]'s
 * reason: a picker row that could reach `user.email` is a row that will eventually render it.
 *
 * @property discipline the line under the name. For a verified professional it is their discipline; for
 *   somebody this user follows it is how they are connected. It is *not* a username — `users/{uid}`
 *   has no handle field, so there is nothing to draw there and inventing one would be a lie.
 */
data class ProfessionalRowState(
    val uid: String,
    val name: String,
    val discipline: String,
    val photoUrl: String? = null,
    val verified: Boolean = false,
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
    val otherUid: String,
    val otherName: String,
    val otherPhotoUrl: String? = null,
    val otherVerified: Boolean = false,
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
 * @property people the accounts this user follows — the social half of the picker.
 * @property professionals the verified directory — the healthcare half, unchanged.
 *
 * Both are only ever populated while [pickerOpen] is true — see [MessagesViewModel].
 */
data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val people: List<ProfessionalRowState> = emptyList(),
    val professionals: List<ProfessionalRowState> = emptyList(),
    val pickerOpen: Boolean = false,
    val loading: Boolean = true,
    val open: OpenChatState? = null,
    val isDoctor: Boolean = false,
    val patients: List<Conversation> = emptyList(),
    /**
     * Whether the signed-in user has active Omni+. The Verified Doctors entry in the chat list is a
     * direct doorway to the doctor directory for a subscriber, and a paywall prompt for a free user —
     * driven by the app-wide access flow, not a per-screen check.
     */
    val isOmniPlusActive: Boolean = false,
)

/**
 * Messaging (BACKEND_PLAN §11 Phase 11) — the thread list, the picker and one open chat.
 *
 * One ViewModel for both pages, for the reason [com.example.omni.ui.firstaid.GuidesViewModel] gives: the
 * back gesture inside a chat has to land on the list rather than leaving Messages, and that is a decision
 * about *state*, not about routing.
 *
 * **The picker has two halves, and that is the whole of who you may write to.** The verified directory
 * ([UserRepository.observeProfessionals]) is BACKEND_PLAN §12's healthcare answer and is untouched. Beside
 * it is the social one: the accounts this user follows ([FollowRepository.observeFollowing]), because a
 * social app whose only correspondents are doctors is a dead end — the Messages tab used to open on
 * "No verified professionals are available yet" and offer nothing else. There is still no free-text uid
 * entry: a thread can only be started with somebody you follow or somebody Omni has verified.
 *
 * The other door into a thread is the public profile's Message button, which calls [startWith] directly
 * (see `MainActivity`) and does not need the picker at all.
 */
class MessagesViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val followRepository: FollowRepository,
    private val doctorRepository: DoctorRepository,
    private val revenueCatRepository: RevenueCatRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key every read below restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    private val openThread = MutableStateFlow<OpenThread?>(null)
    private val draft = MutableStateFlow("")
    private val picker = MutableStateFlow(false)

    /**
     * Optimistic outgoing messages — shown the instant [send] is called, before the HTTP round-trip to
     * the backend commits them to Firestore.
     *
     * [messages] is server truth, and this app does not write a message to Firestore from the client
     * (the Render backend does), so without this echo the sender stares at an unchanged thread until
     * Render answers — seconds on a cold free-tier dyno, and previously *nothing at all* when the
     * backend's post-commit notification step made the call return non-2xx even though the message had
     * already been written (the recipient saw it, the sender did not). That was the sender-missing-message
     * bug.
     *
     * Reconciled by content in [undelivered]/[pruneDelivered]: the moment the server copy of an echo
     * arrives on [messages], the matching optimistic one is dropped, so a message is one bubble, never
     * two. Cleared whenever the open thread changes so an echo never leaks into another chat.
     */
    private val pending = MutableStateFlow<List<Message>>(emptyList())

    /** Distinguishes otherwise-identical optimistic messages; never shown. */
    private var pendingSeq = 0L

    /**
     * Live profiles keyed by uid — the source of truth that overrides the stale denormalised identity
     * baked onto each [Conversation] when the thread was last opened.
     *
     * The thread summary carries `participantNames.{other}` and `participantPhotos.{other}` — the
     * denormalisation that keeps a thread list from being N extra `users/{uid}` reads per row — but
     * that denormalisation is only refreshed on [openConversation] (i.e. when somebody starts a new
     * thread). A user who *renamed themselves* or *changed their photo* after a thread already
     * existed keeps their old identity in every inbox until somebody re-opens it, and the conversation
     * list opens threads rather than starting them, so the identity is never refreshed that way.
     *
     * The fix is the same shape [com.example.omni.ui.feed.FeedViewModel] uses for posts: a session
     * cache of profiles, one `users/{uid}` read per missing uid, and the live profile applied on top
     * of whatever the conversation's denormalised copy said.
     */
    private val authors = MutableStateFlow<Map<String, User>>(emptyMap())

    /** Uids already asked for, so a missing profile is not re-requested on every emission. */
    private val requestedAuthors = mutableSetOf<String>()

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
     * The conversation list with live identity applied — fresh `name` and `photoUrl` win over the
     * thread's own denormalised copy.
     *
     * The trigger lives on this flow rather than on [conversations] so the resolution fires only
     * while the list is something a screen is drawing (and could miss it).
     */
    private val enrichedConversations: Flow<List<Conversation>> = conversations
        .onEach { list -> resolveAuthors(list.map { it.otherUid }) }
        .combine(authors) { list, people ->
            list.map { it.withAuthor(people[it.otherUid]) }
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
                    // Two directories, one list. The `users`-verified professionals (app-approved doctors
                    // and nutritionists) and the `doctors` consultation directory are separate collections;
                    // a doctor seeded straight into `doctors` never had a `users.verified` flag, so before
                    // this merge the only way to reach such a doctor from the picker was to follow them, or
                    // to be an Omni+ subscriber and reach them through Browse Doctors — which is the reported
                    // bug. Merged and de-duplicated by uid (the `users` row wins, being the canonical
                    // profile), every verified doctor becomes a normal, ungated direct message for anyone.
                    val verified = userRepository.observeProfessionals()
                        .catch { cause ->
                            Log.w("Omni", "The professional directory could not be read", cause)
                            emit(emptyList())
                        }
                    val doctors = doctorRepository.observeDoctors()
                        .catch { cause ->
                            Log.w("Omni", "The doctor directory could not be read", cause)
                            emit(emptyList())
                        }
                    combine(verified, doctors) { verifiedUsers, directoryDoctors ->
                        (verifiedUsers.map { it.toRow() } + directoryDoctors.map { it.toRow() })
                            .asSequence()
                            .filter { it.uid != uid }
                            .distinctBy { it.uid }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                    }
                }
            }

    /**
     * The social half of the picker — the accounts this user follows, resolved to rows.
     *
     * Open-gated for the same reason the directory is, and **read once per change of the follow set**
     * rather than listened to per person: `users/{uid}` is the one document in this app that barely
     * changes, and N live registrations for a list somebody is scrolling past is exactly the "download
     * the whole collection" trade §17 rules out. The set itself *is* live, so following somebody from
     * the feed puts them in this list without a restart.
     *
     * Capped at [PeopleLimit]. Past that the picker stops being a list and starts being a directory,
     * and the way to reach somebody further down is the feed's search, which already opens their
     * profile and its Message button.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val people: Flow<List<ProfessionalRowState>> =
        combine(uid, picker) { uid, open -> uid to open }
            .distinctUntilChanged()
            .flatMapLatest { (uid, open) ->
                if (!open || uid == null) {
                    flowOf(emptyList())
                } else {
                    followRepository.observeFollowing(uid)
                        .map { ids ->
                            ids.asSequence()
                                // I follow myself in nobody's data model, but a stray write would put
                                // a row here that `conversationIdOf(uid, uid)` cannot open.
                                .filter { it != uid }
                                .sorted()
                                .take(PeopleLimit)
                                .toList()
                                .mapNotNull { userRepository.getUser(it)?.toFollowedRow() }
                                .sortedBy { it.name.lowercase() }
                        }
                        .catch { cause ->
                            Log.w("Omni", "The people $uid follows could not be read", cause)
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

    /**
     * Server messages with any not-yet-confirmed optimistic ones appended.
     *
     * [pruneDelivered], wired onto [messages], removes an echo from [pending] the moment its server
     * twin arrives — and it runs *before* this combine sees the new server list, so the append here is
     * simply "whatever is still pending". Matching is done in exactly one place (the prune), so a single
     * server message can never consume two echoes.
     */
    private val mergedMessages: Flow<List<Message>> = messages
        .onEach(::pruneDelivered)
        .combine(pending) { server, echoes ->
            if (echoes.isEmpty()) server else server + echoes
        }

    private val chatRaw: Flow<OpenChatState?> =
        combine(uid, openThread, mergedMessages, draft) { uid, thread, messages, draft ->
            if (uid == null || thread == null) {
                null
            } else {
                OpenChatState(
                    conversationId = thread.conversationId,
                    selfUid = uid,
                    otherUid = thread.otherUid,
                    otherName = thread.otherName,
                    otherPhotoUrl = thread.otherPhotoUrl,
                    messages = messages,
                    draft = draft,
                )
            }
        }
        .onEach { state ->
            // The header is drawn off the open thread's identity, which is the denormalised copy
            // baked on when the thread was opened. A rename or new photo of the other participant
            // never reaches this header until somebody re-opens the chat, which is exactly the bug
            // the user reported ("chat profile name and picture not showing"). Resolving it here on
            // every open keeps the header live, the same shape [enrichedConversations] applies to
            // the list.
            if (state != null) resolveAuthors(listOf(state.otherUid))
        }

    /**
     * The chat, with the open thread's other-participant identity replaced by the live profile when
     * one is in hand.
     *
     * Sub-flow rather than a fifth argument on [chatRaw]'s combine — the typed `combine` overloads
     * stop at five flows, and [uiState] already uses all five on the messages side.
     */
    private val chat: Flow<OpenChatState?> = chatRaw.combine(authors) { state, people ->
        if (state == null) {
            null
        } else {
            val live = people[state.otherUid]
            state.copy(
                otherName = live?.name?.ifBlank { null } ?: state.otherName,
                otherPhotoUrl = live?.photoUrl ?: state.otherPhotoUrl,
                otherVerified = live?.verified ?: state.otherVerified,
            )
        }
    }

    /**
     * Whether the signed-in account is a **verified doctor** — the gate for the Patients tab.
     *
     * Verified-professional role plus the DOCTOR discipline: a nutritionist who clears review is a
     * professional without consultation threads, and the patients list would always be empty for
     * them. One `users/{uid}` listener, alive while Messages is — the profile is the one document
     * the app treats as cheap.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val isDoctor: Flow<Boolean> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(false)
        } else {
            userRepository.observeUser(uid)
                .map { it?.role == UserRole.PROFESSIONAL && it.profession == Profession.DOCTOR }
                .catch { cause ->
                    Log.w("Omni", "users/$uid could not be read for the Patients gate", cause)
                    emit(false)
                }
        }
    }

    /**
     * The doctor's half of Messages: the Omni+ consultation threads, most recent first.
     *
     * A consultation is the same document a social thread is — participants, denormalised names —
     * with an id that begins `consultation_`, so the filter needs no extra field, query or index.
     * It shares [enrichedConversations]' live-identity pass, so a patient who renames themselves
     * shows under their new name here too.
     */
    private val patients: Flow<List<Conversation>> = combine(enrichedConversations, isDoctor) { list, doctor ->
        if (doctor) list.filter { it.isConsultation } else emptyList()
    }

    val uiState: StateFlow<MessagesUiState> =
        combine(enrichedConversations, people, professionals, picker, chat) {
                conversations, people, professionals, picker, chat ->
            MessagesUiState(
                conversations = conversations,
                people = people,
                professionals = professionals,
                pickerOpen = picker,
                loading = false,
                open = chat,
            )
        }.combine(combine(isDoctor, patients) { isDoctor, patients -> isDoctor to patients }) { state, doctor ->
            state.copy(isDoctor = doctor.first, patients = doctor.second)
        }.combine(revenueCatRepository.isOmniPlusActive) { state, omniPlus ->
            state.copy(isOmniPlusActive = omniPlus)
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
        pending.value = emptyList()
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
     * Starts (or re-enters) the thread with one person — a picker row, or a public profile's Message
     * button, which hands in the profile it is standing on.
     *
     * [selfName] and [selfPhotoUrl] come from the session the router already holds rather than from a
     * second `users/{uid}` listener here — they are denormalised onto the thread so the *other* side can
     * draw this user's name without a read (see [com.example.omni.data.model.conversationIdentityMap]).
     *
     * The chat opens only once the document is known to exist. Opening it optimistically would put the
     * user in front of an input box whose first message could fail the rules, and this is the one write in
     * the app where "it looked like it worked" is worst.
     *
     * Refuses a thread with myself. The picker already filters me out of both its lists, but this is also
     * the profile page's message button, and a profile can be mine — the router holds it back, and this
     * holds it back again at the layer that actually writes, because a thread whose two participants are
     * one person is a document no screen can render and no rule should accept.
     */
    fun startWith(professional: ProfessionalRowState, selfName: String, selfPhotoUrl: String?) {
        val uid = authRepository.currentUid ?: return
        if (uid == professional.uid) {
            Log.w("Omni", "Refusing a conversation with myself ($uid)")
            return
        }
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
                pending.value = emptyList()
                openThread.value = OpenThread(
                    conversationId = conversationId,
                    otherUid = professional.uid,
                    otherName = professional.name,
                    otherPhotoUrl = professional.photoUrl,
                )
            } catch (cause: Exception) {
                // Now a plain write, so this is the same offline story as every other write in the app:
                // Firestore takes it locally and the `await` completes. What reaches here is a rules
                // rejection. The picker stays open, which is the honest outcome — nothing happened.
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
     * The field is cleared immediately and the message is shown at once as an optimistic echo
     * ([pending]) — the message is written by the backend over HTTP, not by Firestore's own local
     * cache, so the echo is what makes the bubble appear on the same frame the input empties instead of
     * after the round-trip. The echo is reconciled away when the server copy arrives on the listener
     * (never a duplicate), and removed if the send actually failed. The draft is not restored on
     * failure, because putting text back into a box the user has already started retyping in is worse
     * than losing it.
     */
    fun send() {
        val thread = openThread.value ?: return
        val uid = authRepository.currentUid ?: return
        val body = draft.value.trim()
        if (body.isEmpty()) return

        draft.value = ""
        // senderId is the authenticated uid — the same value the backend stamps from the ID token — so
        // the echo sits on the sender's side of the column and matches its server twin for reconciliation.
        val echo = Message(
            id = "pending-${pendingSeq++}",
            senderId = uid,
            text = body,
            createdAt = System.currentTimeMillis(),
        )
        pending.value = pending.value + echo
        viewModelScope.launch {
            try {
                messageRepository.send(
                    conversationId = thread.conversationId,
                    senderId = uid,
                    recipientId = thread.otherUid,
                    text = body,
                )
            } catch (cause: Exception) {
                // The write did not land. Drop the echo so the thread does not imply it did; if it
                // secretly did land, the listener still delivers the real copy.
                pending.value = pending.value.filterNot { it.id == echo.id }
                Log.w("Omni", "A message to ${thread.otherUid} could not be sent", cause)
            }
        }
    }

    /** Returns whether a chat was open, so the caller can fall through to its own back behaviour. */
    fun closeChat(): Boolean {
        val wasOpen = openThread.value != null
        openThread.value = null
        draft.value = ""
        pending.value = emptyList()
        return wasOpen
    }

    /** The selection, kept private: `otherUid` is routing state, not something a screen renders. */
    private data class OpenThread(
        val conversationId: String,
        val otherUid: String,
        val otherName: String,
        val otherPhotoUrl: String?,
    )

    /**
     * The conversation as its other participant is *now*, falling back to the identity denormalised
     * onto the thread.
     *
     * A live `name` wins when it is non-blank; a live `photoUrl` wins when it is non-null. Either
     * field missing on the live profile keeps whatever the thread had — the thread is still the
     * source of truth for an account that has not been read this session.
     */
    private fun Conversation.withAuthor(author: User?): Conversation {
        if (author == null) return this
        return copy(
            otherName = author.name.ifBlank { otherName },
            otherPhotoUrl = author.photoUrl ?: otherPhotoUrl,
            otherVerified = author.verified,
        )
    }

    /**
     * The optimistic echoes whose server copy has *not* yet arrived on [server].
     *
     * Matched by (senderId, text) and counted, not by identity: the backend assigns its own message id,
     * so an echo cannot be matched by id, but sending the same words twice must still resolve to two
     * bubbles — so each server copy consumes exactly one echo.
     */
    private fun undelivered(server: List<Message>, echoes: List<Message>): List<Message> {
        val counts = HashMap<Pair<String, String>, Int>()
        server.forEach { m -> counts[m.senderId to m.text] = (counts[m.senderId to m.text] ?: 0) + 1 }
        return echoes.filter { echo ->
            val key = echo.senderId to echo.text
            val n = counts[key] ?: 0
            if (n > 0) {
                counts[key] = n - 1
                false
            } else {
                true
            }
        }
    }

    /** Drops from [pending] any optimistic message the server has now delivered. */
    private fun pruneDelivered(server: List<Message>) {
        val current = pending.value
        if (current.isEmpty()) return
        val remaining = undelivered(server, current)
        if (remaining.size != current.size) pending.value = remaining
    }

    /**
     * Fetches any of [uids] not already held or already asked for. One read each, once.
     *
     * A uid that resolves to `null` (the account exists but could not be read, or never did) is not
     * re-fetched — same shape [com.example.omni.ui.feed.FeedViewModel.resolveAuthors] uses.
     */
    private fun resolveAuthors(uids: List<String>) {
        val missing = uids.filter { it.isNotBlank() && requestedAuthors.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { uid ->
                val user = userRepository.getUser(uid)
                if (user != null) authors.value = authors.value + (uid to user)
            }
        }
    }

    private companion object {
        /** Outlives a rotation; a backgrounded app stops paying for the thread listeners. */
        const val ListenerGraceMillis = 5_000L

        /**
         * How many followed accounts the picker resolves.
         *
         * One `users/{uid}` read each, so this is the cap on what opening the picker costs. Somebody
         * further down the list is reached through the feed's search, which opens their profile and
         * its Message button — the same [startWith], one tap further away.
         */
        const val PeopleLimit = 40

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
            verified = verified,
        )

        /**
         * A row for a consultation-directory doctor (the `doctors` collection). Its discipline is the
         * doctor's own specialty, and a directory doctor is verified by definition — the directory is
         * admin-written only.
         */
        fun Doctor.toRow(): ProfessionalRowState = ProfessionalRowState(
            uid = uid,
            name = name.ifBlank { "Doctor" },
            discipline = specialty.ifBlank { "Doctor" },
            photoUrl = photoUrl,
            verified = verified,
        )

        /**
         * The same row for somebody this user follows.
         *
         * The second line is their discipline when they have one and "You follow them" otherwise — the
         * relationship, which is the reason they are in this list at all. `users/{uid}` carries no
         * handle, so there is no `@username` to draw here.
         */
        fun User.toFollowedRow(): ProfessionalRowState = ProfessionalRowState(
            uid = uid,
            name = name.ifBlank { "Omni member" },
            discipline = when (profession) {
                Profession.DOCTOR -> "Doctor"
                Profession.NUTRITIONIST -> "Nutritionist"
                null -> "You follow them"
            },
            photoUrl = photoUrl,
            verified = verified,
        )
    }
}
