package com.example.omni.ui.omniplus

import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.Message
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MessageRepository
import com.example.omni.data.repo.UserRepository
import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The consultation chat's sender-side echo (BUG 2 for the Omni+ doctor chat).
 *
 * This chat is a separate ViewModel from the social one, and it is written by the backend over HTTP,
 * so the sender's own message must be echoed optimistically and reconciled to a single bubble when the
 * server copy arrives — the same contract [com.example.omni.ui.messages.MessagesViewModel] holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoctorConsultationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val messages = FakeMessageRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DoctorConsultationViewModel(
        messages,
        FakeAuthRepository(Me),
        FakeUserRepository(),
        SavedStateHandle(mapOf("doctorUid" to Doctor, "doctorName" to "Dr. House")),
    )

    private fun TestScope.subscribe(model: DoctorConsultationViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    @Test
    fun `the consultation opens the canonical conversation, flagged as a consultation`() = runTest(dispatcher) {
        val model = viewModel()
        subscribe(model)
        advanceUntilIdle()

        // Same id the normal messaging flow computes for this pair — not a consultation_-prefixed one.
        val canonical = com.example.omni.data.model.conversationIdOf(Me, Doctor)
        assertEquals(canonical, model.uiState.value.conversationId)
        assertEquals(Doctor to true, messages.openedWith)
    }

    @Test
    fun `a sent message appears immediately and reconciles to one bubble`() = runTest(dispatcher) {
        val model = viewModel()
        subscribe(model)
        advanceUntilIdle()

        model.send("Hello Doctor")
        advanceUntilIdle()

        assertEquals(listOf("Hello Doctor"), model.uiState.value.messages.map { it.text })
        assertEquals(Me, model.uiState.value.messages.first().senderId)

        // The backend's copy arrives on the listener with the server id.
        messages.messages.value = listOf(
            Message(id = "server-1", senderId = Me, text = "Hello Doctor", createdAt = 1_000L),
        )
        advanceUntilIdle()

        assertEquals(1, model.uiState.value.messages.size)
        assertEquals("server-1", model.uiState.value.messages.first().id)
    }

    @Test
    fun `a failed send removes the echo`() = runTest(dispatcher) {
        messages.failSend = true
        val model = viewModel()
        subscribe(model)
        advanceUntilIdle()

        model.send("does not send")
        advanceUntilIdle()

        assertTrue(model.uiState.value.messages.isEmpty())
    }

    private companion object {
        const val Me = "uid-me"
        const val Doctor = "uid-doctor"
    }
}

private class FakeAuthRepository(uid: String?) : AuthRepository {
    private val state = MutableStateFlow(if (uid == null) AuthState.UNAUTHENTICATED else AuthState.AUTHENTICATED)
    override val authState: StateFlow<AuthState> = state.asStateFlow()
    private val session = MutableStateFlow(uid)
    override val sessionUid: StateFlow<String?> = session.asStateFlow()
    override val currentUid: String? get() = session.value
    override fun signOut() { session.value = null }
    override suspend fun signUp(name: String, email: String, password: String) = Unit
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun sendPasswordReset(email: String) = Unit
    override val currentEmail: String? get() = null
    override suspend fun changeEmail(currentPassword: String, newEmail: String) = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
    override suspend fun reloadCurrentUser() = Unit
    override val isEmailVerified: Boolean get() = true
    override suspend fun sendOtpCode(): Boolean = true
    override suspend fun verifyOtpCode(code: String): Boolean = true
}

private class FakeUserRepository : UserRepository {
    override fun observeUser(uid: String): Flow<User?> = MutableStateFlow(null)
    override suspend fun getUser(uid: String): User? = null
    override fun observeProfessionals(): Flow<List<User>> = MutableStateFlow(emptyList())
    override suspend fun searchByName(query: String, limit: Int): List<User> = emptyList()
    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) = Unit
    override suspend fun updateGoals(uid: String, goals: HealthGoals) = Unit
}

private class FakeMessageRepository : MessageRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    var failSend = false

    /** The (otherUid, consultation) of the last openConversation call, so a test can assert the id. */
    var openedWith: Pair<String, Boolean>? = null
    var openedId: String? = null

    override fun observeConversations(uid: String): Flow<List<Conversation>> = MutableStateFlow(emptyList())
    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages
    override suspend fun openConversation(
        selfUid: String, selfName: String, selfPhotoUrl: String?,
        otherUid: String, otherName: String, otherPhotoUrl: String?, consultation: Boolean,
    ): String {
        openedWith = otherUid to consultation
        openedId = com.example.omni.data.model.conversationIdOf(selfUid, otherUid)
        return openedId!!
    }
    override suspend fun send(
        conversationId: String, senderId: String, recipientId: String, text: String, imageUrl: String?,
    ) { if (failSend) throw IllegalStateException("Message failed") }
    override suspend fun markRead(conversationId: String, uid: String) = Unit
}
