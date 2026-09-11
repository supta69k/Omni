package com.example.omni.ui.feed

import com.example.omni.data.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The story rail's one rule: it shows the active stories of the people I follow, plus my own.
 *
 * `stories` is a global collection with a global listener — every live story in the app arrives here
 * — so "whose stories are these" is decided entirely by the ViewModel, and these tests are that
 * decision. The chain under test is the one the brief drew: *current user → following relationships →
 * followed user ids → their active stories → the rail*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val stories = FakeStoryRepository()
    private val media = FakeMediaRepository()
    private val follows = FakeFollowRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StoriesViewModel(auth, stories, media, follows)

    private fun TestScope.open(model: StoriesViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    @Test
    fun `a stranger's story is never on the rail`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-stranger", Stranger))
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertTrue(
            "The rail is the people I follow, not the whole app",
            model.uiState.value.tiles.isEmpty(),
        )
    }

    @Test
    fun `the rail shows the stories of people I follow`() = runTest(dispatcher) {
        stories.server.value = listOf(
            story("s-ben", Ben, createdAt = 20L),
            story("s-stranger", Stranger, createdAt = 30L),
        )
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertEquals(listOf(Ben), model.uiState.value.tiles.map { it.authorId })
    }

    /**
     * My own story is mine to see whoever I follow — and it is on the rail *once*.
     *
     * The strip draws my tile as the share affordance and skips my uid in its tile loop, so a second
     * copy here would be a second tile on screen. The brief calls this out by name.
     */
    @Test
    fun `my own story is on the rail exactly once`() = runTest(dispatcher) {
        stories.server.value = listOf(
            story("s-mine-1", Me, createdAt = 10L),
            story("s-mine-2", Me, createdAt = 11L),
        )
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf(Me), state.tiles.map { it.authorId })
        assertEquals("Two stories, one tile", 2, state.tiles.single().storyCount)
        assertTrue(state.mine)
    }

    /** My own tile is never ringed as unseen — I have seen what I posted. */
    @Test
    fun `my own tile is not offered as unseen`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-mine", Me))
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertFalse(model.uiState.value.tiles.single().unseen)
    }

    /**
     * One follow, three surfaces.
     *
     * The rail reads the same `users/{me}/following` listener the feed's Follow pill and the Following
     * tab read, so following someone from a post row puts their story here without a reopen. That is
     * §9's single source of truth stated as a test rather than as a comment.
     */
    @Test
    fun `following someone puts their story on the rail without a reopen`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-ben", Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertTrue(model.uiState.value.tiles.isEmpty())

        follows.follow(Me, Ben)
        advanceUntilIdle()

        assertEquals(listOf(Ben), model.uiState.value.tiles.map { it.authorId })
    }

    @Test
    fun `unfollowing takes their story off the rail`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-ben", Ben))
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.tiles.size)

        follows.unfollow(Me, Ben)
        advanceUntilIdle()

        assertTrue(model.uiState.value.tiles.isEmpty())
    }

    /**
     * The account switch (§11).
     *
     * `Me` follows Ben, so Ben's story is on the rail. `Other` signs in following nobody: the
     * following listener is keyed on the session through `flatMapLatest`, so the previous account's
     * set is *cancelled* rather than retained beside the new uid — and the rail is empty, which is
     * what a brand-new account sees.
     */
    @Test
    fun `signing in as somebody else empties the previous account's rail`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-ben", Ben))
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.tiles.size)

        auth.signInAs(Other)
        advanceUntilIdle()

        assertTrue(
            "The previous account's followed stories were still on the rail",
            model.uiState.value.tiles.isEmpty(),
        )
    }

    @Test
    fun `a signed-out viewer has no rail at all`() = runTest(dispatcher) {
        stories.server.value = listOf(story("s-ben", Ben))
        follows.set(Me, setOf(Ben))
        auth.signOut()
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertTrue(model.uiState.value.tiles.isEmpty())
        assertFalse(model.uiState.value.mine)
    }

    /** A tile's stories play oldest first, and the strip itself is newest author first. */
    @Test
    fun `the rail is ordered newest author first`() = runTest(dispatcher) {
        stories.server.value = listOf(
            story("s-ben", Ben, createdAt = 10L),
            story("s-carla", Carla, createdAt = 50L),
        )
        follows.set(Me, setOf(Ben, Carla))
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertEquals(listOf(Carla, Ben), model.uiState.value.tiles.map { it.authorId })
    }

    private companion object {
        const val Me = "uid-me"
        const val Ben = "uid-ben"
        const val Carla = "uid-carla"
        const val Other = "uid-other"
        const val Stranger = "uid-stranger"

        fun story(id: String, authorId: String, createdAt: Long = 1L) = Story(
            id = id,
            authorId = authorId,
            authorName = "whoever",
            imageUrl = "https://example.invalid/$id.jpg",
            createdAt = createdAt,
            // Inside its window: the repository's query is what applies the expiry, and these tests
            // are about *whose* stories reach the rail, not about when they leave it.
            expiresAt = Long.MAX_VALUE,
        )
    }
}
