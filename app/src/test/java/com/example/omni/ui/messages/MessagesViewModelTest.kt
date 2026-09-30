package com.example.omni.ui.messages

import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.Doctor
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.Message
import com.example.omni.data.model.User
import com.example.omni.data.model.conversationIdOf
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.DoctorRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The messaging state machine's two fixes:
 *  - a verified doctor is reachable in the "new message" picker without following them or being Omni+
 *    (BUG 1/8/9/10), because the picker now merges the `doctors` directory into the verified list; and
 *  - a sent message is shown at once as an optimistic echo and reconciled to a single bubble when the
 *    server copy arrives (BUG 5/6/11), because the message is written by the backend over HTTP, not by
 *    Firestore's local cache, so there is nothing to make it appear otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val messages = FakeMessageRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        professionals: List<User> = emptyList(),
        doctors: List<Doctor> = emptyList(),
    ) = MessagesViewModel(
        auth,
        FakeUserRepository(professionals),
        messages,
        FakeFollowRepository(),
        FakeDoctorRepository(doctors),
        com.example.omni.data.revenuecat.PreviewRevenueCatRepository(),
    )

    /** Keeps `uiState` hot — `WhileSubscribed` emits nothing without a collector. */
    private fun TestScope.subscribe(model: MessagesViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    @Test
    fun `a directory doctor is reachable in the picker without following or Omni+`() = runTest(dispatcher) {
        val doctor = Doctor(uid = "doc-1", name = "Dr. House", specialty = "Cardiology")
        val model = viewModel(doctors = listOf(doctor))
        subscribe(model)

        model.togglePicker()
        advanceUntilIdle()

        val state = model.uiState.value
        val row = state.professionals.firstOrNull { it.uid == "doc-1" }
        assertNotNull("The directory doctor was not offered in the picker", row)
        assertEquals("Cardiology", row!!.discipline)
        assertTrue("A directory doctor must carry the verified badge", row.verified)
        assertTrue("Reaching the doctor must not depend on following anyone", state.people.isEmpty())
    }

    @Test
    fun `a sent message appears immediately, before the backend confirms it`() = runTest(dispatcher) {
        val model = viewModel()
        subscribe(model)
        model.startWith(ProfessionalRowState("other", "Dr. X", "Cardiology", verified = true), "Me", null)
        advanceUntilIdle()

        model.onDraftChange("hello")
        model.send()
        advanceUntilIdle()

        val chat = model.uiState.value.open
        assertNotNull(chat)
        assertEquals(listOf("hello"), chat!!.messages.map { it.text })
        // The echo is stamped with the authenticated uid, so it sits on the sender's side.
        assertEquals(Me, chat.messages.first().senderId)
        assertEquals("The message was still sent to the backend", listOf("hello"), messages.sent)
    }

    @Test
    fun `the echo reconciles to one bubble when the server copy arrives`() = runTest(dispatcher) {
        val model = viewModel()
        subscribe(model)
        model.startWith(ProfessionalRowState("other", "Dr. X", "Cardiology", verified = true), "Me", null)
        advanceUntilIdle()
        model.onDraftChange("hello")
        model.send()
        advanceUntilIdle()

        // The listener now delivers the backend's own copy, with the server-assigned id.
        messages.messages.value = listOf(
            Message(id = "server-1", senderId = Me, text = "hello", createdAt = 1_000L),
        )
        advanceUntilIdle()

        val chat = model.uiState.value.open!!
        assertEquals("The echo and the server copy showed as two bubbles", 1, chat.messages.size)
        assertEquals("server-1", chat.messages.first().id)
    }

    @Test
    fun `two identical messages resolve to two bubbles, not one`() = runTest(dispatcher) {
        val model = viewModel()
        subscribe(model)
        model.startWith(ProfessionalRowState("other", "Dr. X", "Cardiology", verified = true), "Me", null)
        advanceUntilIdle()
        model.onDraftChange("hi")
        model.send()
        model.onDraftChange("hi")
        model.send()
        advanceUntilIdle()

        assertEquals(2, model.uiState.value.open!!.messages.size)

        // One server copy must consume exactly one echo, not both.
        messages.messages.value = listOf(
            Message(id = "server-1", senderId = Me, text = "hi", createdAt = 1_000L),
        )
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.open!!.messages.size)
    }

    @Test
    fun `a failed send removes the optimistic echo`() = runTest(dispatcher) {
        messages.failSend = true
        val model = viewModel()
        subscribe(model)
        model.startWith(ProfessionalRowState("other", "Dr. X", "Cardiology", verified = true), "Me", null)
        advanceUntilIdle()

        model.onDraftChange("does not send")
        model.send()
        advanceUntilIdle()

        assertTrue(
            "A message that never left must not linger in the thread",
            model.uiState.value.open!!.messages.isEmpty(),
        )
    }

    private companion object {
        const val Me = "uid-me"
    }
}

private class FakeAuthRepository(uid: String?) : AuthRepository {

    private val state = MutableStateFlow(
        if (uid == null) AuthState.UNAUTHENTICATED else AuthState.AUTHENTICATED,
    )
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    private val session = MutableStateFlow(uid)
    override val sessionUid: StateFlow<String?> = session.asStateFlow()
    override val currentUid: String? get() = session.value

    fun signInAs(uid: String) {
        session.value = uid
        state.value = AuthState.AUTHENTICATED
    }

    override fun signOut() {
        session.value = null
        state.value = AuthState.UNAUTHENTICATED
    }

    override suspend fun signUp(name: String, email: String, password: String) = signInAs("uid-$email")
    override suspend fun signIn(email: String, password: String) = signInAs("uid-$email")
    override suspend fun sendPasswordReset(email: String) = Unit
    override val currentEmail: String? get() = session.value?.let { "$it@omni.test" }
    override suspend fun changeEmail(currentPassword: String, newEmail: String) = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
    override suspend fun reloadCurrentUser() = Unit
    override val isEmailVerified: Boolean get() = true
    override suspend fun sendOtpCode(): Boolean = true
    override suspend fun verifyOtpCode(code: String): Boolean = true
}

/** Emits eagerly (unlike the profile test's) — this test is not about read timing. */
private class FakeUserRepository(private val professionals: List<User>) : UserRepository {
    override fun observeUser(uid: String): Flow<User?> = MutableStateFlow(null)
    override suspend fun getUser(uid: String): User? = professionals.firstOrNull { it.uid == uid }
    override fun observeProfessionals(): Flow<List<User>> = MutableStateFlow(professionals)
    override suspend fun searchByName(query: String, limit: Int): List<User> = emptyList()
    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) = Unit
    override suspend fun updateGoals(uid: String, goals: HealthGoals) = Unit
}

private class FakeMessageRepository : MessageRepository {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** One thread's messages — the "server truth" a test drives to simulate the listener. */
    val messages = MutableStateFlow<List<Message>>(emptyList())

    /** What actually reached the backend, so a test can assert the send still happened. */
    val sent = mutableListOf<String>()
    var failSend = false

    override fun observeConversations(uid: String): Flow<List<Conversation>> = conversations
    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages

    override suspend fun openConversation(
        selfUid: String,
        selfName: String,
        selfPhotoUrl: String?,
        otherUid: String,
        otherName: String,
        otherPhotoUrl: String?,
        consultation: Boolean,
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
        if (failSend) throw IllegalStateException("Message failed")
        sent += text
    }

    override suspend fun markRead(conversationId: String, uid: String) = Unit
}

private class FakeFollowRepository : FollowRepository {
    override fun observeFollowing(uid: String): Flow<Set<String>> = MutableStateFlow(emptySet())
    override fun observeFollows(uid: String, target: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun followerCount(uid: String): Int = 0
    override suspend fun follow(uid: String, target: String) = Unit
    override suspend fun unfollow(uid: String, target: String) = Unit
}

private class FakeDoctorRepository(private val doctors: List<Doctor>) : DoctorRepository {
    override fun observeDoctors(): Flow<List<Doctor>> = MutableStateFlow(doctors)
    override suspend fun getDoctor(uid: String): Doctor? = doctors.firstOrNull { it.uid == uid }
}

