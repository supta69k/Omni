package com.example.omni.ui.profile

import com.example.omni.data.model.AuthState
import com.example.omni.data.model.Comment
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.Post
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.FeedRepository
import com.example.omni.data.repo.FollowRepository
import com.example.omni.data.repo.PostAuthor
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The profile page's state machine — and the wrong-profile bug it was rewritten to make impossible.
 *
 * Every test here is a *timing* assertion, which is why this file exists at all: the page combines
 * five reads, and the defect was never in any one of them. It was in what the combination showed
 * while some had answered for the new profile and the rest were still holding their last answer for
 * the old one. A `combine` retains each source's last value, so re-keying the reads on a uid change
 * left the previous person's name, photo and posts on screen under the new person's uid — for as
 * long as the network took. The fakes below therefore answer only when a test tells them to; a fake
 * that resolved eagerly would pass against the broken version too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val users = FakeUserRepository()
    private val feed = FakeFeedRepository()
    private val follows = FakeFollowRepository()

    @Before
    fun installMainDispatcher() {
        // `viewModelScope` is hard-wired to Dispatchers.Main, which does not exist on the JVM.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileViewModel(auth, users, feed, follows)

    /** Keeps `uiState` hot for the length of the test — `WhileSubscribed` emits nothing otherwise. */
    private fun TestScope.open(model: ProfileViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    @Test
    fun `with no profile chosen the page is not loading`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertNull(model.uiState.value.uid)
        assertFalse("Nothing was asked for, so nothing is in flight", model.uiState.value.isLoading)
    }

    @Test
    fun `a page whose reads have not answered is loading and names nobody`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(Ben, state.uid)
        assertTrue(state.isLoading)
        assertNull(state.user)
        assertTrue(state.posts.isEmpty())
        // "Loading" and "deleted" are different facts, and the screen renders them differently.
        assertFalse(state.notFound)
    }

    /**
     * The regression this rewrite exists for.
     *
     * Open my own profile, let it answer in full, then tap through to Ben's and let nothing of his
     * answer yet. Under the old flat five-way `combine` the retained values meant *my* name and *my*
     * posts rendered under Ben's uid. Under the `flatMapLatest` the old reads are cancelled, so the
     * only thing the page can show is that it is loading his.
     */
    @Test
    fun `switching profiles never leaves the previous person on screen`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Me)
        users.answer(Me, profile(Me, "Sayed Mahir"))
        feed.answer(Me, listOf(post("p1", Me)))
        advanceUntilIdle()
        assertEquals("Sayed Mahir", model.uiState.value.user?.name)

        model.setProfileUid(Ben)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(Ben, state.uid)
        assertNull("My profile was still on screen under Ben's uid", state.user)
        assertTrue("My posts were still on screen under Ben's uid", state.posts.isEmpty())
        assertTrue(state.isLoading)
    }

    @Test
    fun `switching profiles shows the new person once their reads answer`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Me)
        users.answer(Me, profile(Me, "Sayed Mahir"))
        feed.answer(Me, listOf(post("p1", Me)))
        advanceUntilIdle()

        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, listOf(post("p2", Ben), post("p3", Ben)))
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(Ben, state.uid)
        assertEquals("Ben Carter", state.user?.name)
        assertEquals(listOf("p2", "p3"), state.posts.map { it.id })
        assertFalse(state.isLoading)
    }

    /**
     * `isMe` used to be `user?.uid == me`, which is false while `user` is null — so my own profile
     * spent every open briefly offering to follow and message myself. It is now decided from the two
     * uids, which are both known before any read starts.
     */
    @Test
    fun `my own profile is mine before any read has answered`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Me)
        advanceUntilIdle()

        assertTrue(model.uiState.value.isLoading)
        assertTrue("The follow and message buttons were offered on my own page", model.uiState.value.isMe)
    }

    @Test
    fun `somebody elses profile is never mine`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, emptyList())
        advanceUntilIdle()

        assertFalse(model.uiState.value.isMe)
    }

    /**
     * The race the brief drew: a read starts for account A, A signs out, B signs in, A's read
     * finishes and its result lands in B's UI.
     *
     * What must reset is the *viewer-scoped* half of the page — whether **I** follow this person.
     * Ben's own name and posts are not viewer-scoped, and re-serving them from cache is right: he is
     * the same person whoever is looking, and dropping them would flicker the page for no reason.
     * The two halves are told apart by the re-key: `me` changing cancels the old inner combine, so
     * `followStateOf` is re-subscribed for the new viewer and its first emission is that viewer's
     * own answer. Without it, A's `true` would still be the last value the combine held.
     */
    @Test
    fun `signing out drops the previous accounts follow state`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, listOf(post("p2", Ben)))
        follows.following(Me, Ben, value = true)
        advanceUntilIdle()
        assertTrue(model.uiState.value.iFollowThem)

        auth.signOut()
        auth.signInAs(Other)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(Ben, state.uid)
        assertFalse("The previous account's follow state leaked into this one", state.iFollowThem)
        assertFalse("Whose page it is does not depend on who is looking", state.isMe)
        assertEquals("Ben Carter", state.user?.name)
    }

    /** The same leak, in the direction that matters more: signing out must not leave me *as* them. */
    @Test
    fun `signing in as the person being viewed makes the page mine`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, emptyList())
        advanceUntilIdle()
        assertFalse(model.uiState.value.isMe)

        auth.signInAs(Ben)
        advanceUntilIdle()

        assertTrue("Ben's own page still offered to follow and message Ben", model.uiState.value.isMe)
    }

    /**
     * A profile is capped at 20 rows, so the row count is not the post count. The server's
     * aggregation wins whenever it can be run.
     */
    @Test
    fun `the post count comes from the server not from the loaded rows`() = runTest(dispatcher) {
        feed.counts[Ben] = 42
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, listOf(post("p2", Ben), post("p3", Ben)))
        advanceUntilIdle()

        assertEquals(42, model.uiState.value.postsCount)
    }

    /** Offline there is no cached aggregation, and a confident wrong number is worse than the rows. */
    @Test
    fun `an uncountable profile falls back to the rows it holds`() = runTest(dispatcher) {
        feed.counts[Ben] = -1
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, listOf(post("p2", Ben), post("p3", Ben)))
        advanceUntilIdle()

        assertEquals(2, model.uiState.value.postsCount)
    }

    @Test
    fun `a deleted account is named rather than left loading`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ghost)
        users.answer(Ghost, null)
        feed.answer(Ghost, emptyList())
        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state.notFound)
        assertFalse(state.isLoading)
        assertNull(state.user)
    }

    @Test
    fun `following somebody re-reads their follower count`() = runTest(dispatcher) {
        follows.followerCounts[Ben] = 7
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, emptyList())
        advanceUntilIdle()
        assertEquals(7, model.uiState.value.followers)

        follows.followerCounts[Ben] = 8
        model.toggleFollow()
        advanceUntilIdle()

        assertEquals(listOf(Me to Ben), follows.followed)
        assertEquals(8, model.uiState.value.followers)
    }

    @Test
    fun `unfollowing uses the state the button was drawn from`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, emptyList())
        follows.following(Me, Ben, value = true)
        advanceUntilIdle()

        model.toggleFollow()
        advanceUntilIdle()

        assertEquals(listOf(Me to Ben), follows.unfollowed)
        assertTrue(follows.followed.isEmpty())
    }

    @Test
    fun `I cannot follow myself`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.setProfileUid(Me)
        users.answer(Me, profile(Me, "Sayed Mahir"))
        feed.answer(Me, emptyList())
        advanceUntilIdle()

        model.toggleFollow()
        advanceUntilIdle()

        assertTrue(follows.followed.isEmpty())
        assertTrue(follows.unfollowed.isEmpty())
    }

    @Test
    fun `a signed-out viewer cannot follow`() = runTest(dispatcher) {
        auth.signOut()
        val model = viewModel()
        open(model)
        model.setProfileUid(Ben)
        users.answer(Ben, profile(Ben, "Ben Carter"))
        feed.answer(Ben, emptyList())
        advanceUntilIdle()

        model.toggleFollow()
        advanceUntilIdle()

        assertTrue(follows.followed.isEmpty())
    }

    private companion object {
        const val Me = "uid-me"
        const val Ben = "uid-ben"
        const val Other = "uid-other"
        const val Ghost = "uid-ghost"

        fun profile(uid: String, name: String) = User(uid = uid, name = name, email = "$uid@omni.test")

        fun post(id: String, authorId: String) = Post(
            id = id,
            authorId = authorId,
            authorName = "whoever",
            body = "body of $id",
        )
    }
}

/**
 * Signed in or out on demand.
 *
 * The uid is its own [StateFlow] rather than something derived from [authState], for the reason
 * `AuthRepository.sessionUid` exists: an enum cannot distinguish A from B, and the imperative read
 * that used to bridge the gap was the leak.
 */
private class FakeAuthRepository(uid: String?) : AuthRepository {

    private val state = MutableStateFlow(
        if (uid == null) AuthState.UNAUTHENTICATED else AuthState.AUTHENTICATED,
    )

    override val authState: StateFlow<AuthState> = state.asStateFlow()

    private val session = MutableStateFlow(uid)
    override val sessionUid: StateFlow<String?> = session.asStateFlow()

    override val currentUid: String?
        get() = session.value

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

    // Not exercised here — this test is about whose profile is read, not about credentials.
    override val currentEmail: String? get() = session.value?.let { "$it@omni.test" }
    override suspend fun changeEmail(currentPassword: String, newEmail: String) = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit

    // Email-verification / OTP surface — part of the interface, not what this test drives.
    override suspend fun reloadCurrentUser() = Unit
    override val isEmailVerified: Boolean get() = true
    override suspend fun sendOtpCode(): Boolean = true
    override suspend fun verifyOtpCode(code: String): Boolean = true
}

/**
 * A profile stream per uid that stays silent until [answer] is called.
 *
 * The silence is the point: a fake that emitted on subscription would hide the very window the
 * wrong-profile bug lived in.
 */
private class FakeUserRepository : UserRepository {

    private val streams = mutableMapOf<String, MutableSharedFlow<User?>>()

    private fun stream(uid: String) = streams.getOrPut(uid) { MutableSharedFlow(replay = 1) }

    override fun observeUser(uid: String): Flow<User?> = stream(uid)

    suspend fun answer(uid: String, user: User?) = stream(uid).emit(user)

    override suspend fun getUser(uid: String): User? = null
    override fun observeProfessionals(): Flow<List<User>> = MutableStateFlow(emptyList())
    override suspend fun searchByName(query: String, limit: Int): List<User> = emptyList()
    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) = Unit
    override suspend fun updateGoals(uid: String, goals: HealthGoals) = Unit
}

/** Only the two members the profile page uses are real; the rest fail loudly if it ever reaches them. */
private class FakeFeedRepository : FeedRepository {

    private val streams = mutableMapOf<String, MutableSharedFlow<List<Post>>>()

    /** `countByAuthor`'s answer per author; -1 is the aggregation that could not be run, as offline. */
    val counts = mutableMapOf<String, Int>()

    private fun stream(authorId: String) =
        streams.getOrPut(authorId) { MutableSharedFlow(replay = 1) }

    override fun observeByAuthor(authorId: String, limit: Int): Flow<List<Post>> = stream(authorId)

    suspend fun answer(authorId: String, posts: List<Post>) = stream(authorId).emit(posts)

    /** Unknown unless a test says otherwise — a fake that counted would not be testing the fallback. */
    override suspend fun countByAuthor(authorId: String): Int = counts[authorId] ?: -1

    override fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>> =
        error("The profile page does not read the feed")

    override fun observeByAuthors(uid: String, authorIds: Set<String>, limit: Int): Flow<List<Post>> =
        error("The profile page does not read the Following tab")

    override suspend fun loadPage(uid: String, beforeCreatedAt: Long, pageSize: Int): List<Post> =
        error("The profile page does not page the feed")

    override suspend fun toggleLike(uid: String, postId: String) =
        error("Liking is the feed's action")

    override fun observeComments(postId: String): Flow<List<Comment>> =
        error("Commenting is the feed's action")

    override suspend fun addComment(uid: String, postId: String, authorName: String, body: String) =
        error("Commenting is the feed's action")

    override suspend fun createPost(uid: String, author: PostAuthor, body: String, imageUrl: String?) =
        error("Composing is the feed's action")

    override suspend fun repost(uid: String, postId: String, author: PostAuthor): Boolean =
        error("Reposting is the feed's action")
}

/** Records what was written, so a test can assert *who* was followed rather than that something was. */
private class FakeFollowRepository : FollowRepository {

    private val pairs = mutableMapOf<Pair<String, String>, MutableStateFlow<Boolean>>()

    val followerCounts = mutableMapOf<String, Int>()
    val followed = mutableListOf<Pair<String, String>>()
    val unfollowed = mutableListOf<Pair<String, String>>()

    private fun pair(uid: String, target: String) =
        pairs.getOrPut(uid to target) { MutableStateFlow(false) }

    fun following(uid: String, target: String, value: Boolean) {
        pair(uid, target).value = value
    }

    override fun observeFollows(uid: String, target: String): Flow<Boolean> = pair(uid, target)

    override fun observeFollowing(uid: String): Flow<Set<String>> =
        MutableStateFlow(pairs.filter { it.key.first == uid && it.value.value }.keys.map { it.second }.toSet())

    override suspend fun followerCount(uid: String): Int = followerCounts[uid] ?: 0

    override suspend fun follow(uid: String, target: String) {
        followed += uid to target
        pair(uid, target).value = true
    }

    override suspend fun unfollow(uid: String, target: String) {
        unfollowed += uid to target
        pair(uid, target).value = false
    }
}
