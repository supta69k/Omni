package com.example.omni.ui.feed

import com.example.omni.data.model.Post
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The feed's social behaviour: the Discover/Following switch, the Follow pill on a post row, and the
 * people search behind the header's field.
 *
 * Like the profile page's tests, these are mostly *timing* assertions — the failures they exist for
 * are not "the query is wrong" but "the right query's answer arrived while the screen was still
 * holding the previous one". The `combine` that builds `uiState` retains each source's last value, so
 * every tab switch and every account switch has a window in which the loaded page and the segment
 * label disagree; the fakes are built so a test can stop time inside that window and look.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val feed = FakeFeedRepository()
    private val media = FakeMediaRepository()
    private val follows = FakeFollowRepository()
    private val users = FakeUserRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = FeedViewModel(auth, feed, media, follows, users)

    /** Keeps `uiState` hot — `WhileSubscribed` emits nothing without a collector. */
    private fun TestScope.open(model: FeedViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
        backgroundScope.launch { model.searchState.collect { } }
    }

    // ---- The Discover / Following switch (§5) ----------------------------------------------------

    @Test
    fun `discover shows the newest page`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(3)
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        assertEquals(FeedSegment.Discover, model.uiState.value.segment)
        assertEquals(listOf("noise-2", "noise-1", "noise-0"), model.uiState.value.posts.map { it.id })
    }

    /**
     * The reason Following is a query and not a filter.
     *
     * Ben posted once, a while ago, and twenty strangers have posted since. Discover's page stops at
     * twenty, so Ben's post is not in it — filtering the loaded page for "authors I follow" would
     * therefore show an empty Following tab to someone who follows Ben and nobody else. This is the
     * exact scenario in the brief, and it is only satisfiable by asking the database.
     */
    @Test
    fun `following finds a followed author's post that discover's page never reached`() =
        runTest(dispatcher) {
            val bensPost = post("ben-1", Ben, createdAt = 1L)
            feed.server.value = noisyStrangers(FeedPageSize) + bensPost
            follows.set(Me, setOf(Ben))

            val model = viewModel()
            open(model)
            advanceUntilIdle()
            assertFalse(
                "Ben's post is older than the whole first page — Discover cannot have it",
                model.uiState.value.posts.any { it.id == "ben-1" },
            )

            model.onSegmentChange(FeedSegment.Following)
            advanceUntilIdle()

            assertEquals(listOf("ben-1"), model.uiState.value.posts.map { it.id })
        }

    @Test
    fun `the following query asks for the people I follow plus me`() = runTest(dispatcher) {
        follows.set(Me, setOf(Ben, Carla))
        val model = viewModel()
        open(model)
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()

        assertEquals(setOf(Me, Ben, Carla), feed.lastAuthorIds)
    }

    /** Following nobody is my own posts and nothing else — never a second Discover. */
    @Test
    fun `following nobody shows only my own posts`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(3) + post("mine-1", Me, createdAt = 5L)
        val model = viewModel()
        open(model)
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()

        assertEquals(listOf("mine-1"), model.uiState.value.posts.map { it.id })
    }

    /**
     * The retained-value window, stopped and inspected.
     *
     * With the Following query silent, the tap on the tab is the only thing that has happened: the
     * segment has changed and no page has arrived for it. What must *not* be on screen is Discover's
     * page under Following's name, which is what a flat `combine` produces and what "do NOT show
     * unrelated users' posts" forbids.
     */
    @Test
    fun `switching tabs never leaves the previous tab's posts on screen`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(3)
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertEquals(3, model.uiState.value.posts.size)

        feed.authorsQuerySilent = true
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("The label moves on the tap, as it should", FeedSegment.Following, state.segment)
        assertTrue("Discover's posts were still on screen under Following", state.posts.isEmpty())
    }

    /** And back again: returning to Discover must not be stuck on whatever Following showed. */
    @Test
    fun `switching back to discover restores the global page`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(3)
        val model = viewModel()
        open(model)
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()
        assertTrue(model.uiState.value.posts.isEmpty())

        model.onSegmentChange(FeedSegment.Discover)
        advanceUntilIdle()

        assertEquals(3, model.uiState.value.posts.size)
    }

    /** The tab reacts to the relationship, not to a reopen — the switch works without a restart. */
    @Test
    fun `unfollowing removes their posts from the following tab`() = runTest(dispatcher) {
        feed.server.value = listOf(post("ben-1", Ben, createdAt = 9L))
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()
        assertEquals(listOf("ben-1"), model.uiState.value.posts.map { it.id })

        follows.set(Me, emptySet())
        advanceUntilIdle()

        assertTrue(model.uiState.value.posts.isEmpty())
    }

    @Test
    fun `load more is offered on discover and never on following`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(FeedPageSize)
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertTrue("A full first page means there may be another", model.uiState.value.canLoadMore)

        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()

        assertFalse(
            "Following is one query over a capped author set — there is no cursor to page",
            model.uiState.value.canLoadMore,
        )
    }

    // ---- The Follow pill on a post row (§1, §9) --------------------------------------------------

    @Test
    fun `following from a post row targets the post's author`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        model.toggleFollow(Ben)
        advanceUntilIdle()

        assertEquals(listOf(Me to Ben), follows.follows)
    }

    @Test
    fun `a row can never follow me to myself`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        model.toggleFollow(Me)
        advanceUntilIdle()

        assertTrue(follows.follows.isEmpty())
        assertTrue(follows.unfollows.isEmpty())
    }

    /** The pill's state is the listener's, so the same follow reads the same on every screen. */
    @Test
    fun `the pill's state comes from the follow listener`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        assertTrue(model.uiState.value.following.isEmpty())

        model.toggleFollow(Ben)
        advanceUntilIdle()

        assertEquals(setOf(Ben), model.uiState.value.following)
    }

    @Test
    fun `a second tap unfollows rather than following twice`() = runTest(dispatcher) {
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        model.toggleFollow(Ben)
        advanceUntilIdle()

        assertEquals(listOf(Me to Ben), follows.unfollows)
        assertTrue("Following an existing follow again would be the duplicate", follows.follows.isEmpty())
    }

    /**
     * A refused write is reported and nothing is left pretending.
     *
     * This is the "do not fake the state locally" clause as a test: after a refusal the pill's set is
     * exactly what the server holds, which is nothing.
     */
    @Test
    fun `a refused follow is reported and changes nothing`() = runTest(dispatcher) {
        follows.refuseNextWrite = true
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        var failure: String? = null
        model.toggleFollow(Ben) { failure = it }
        advanceUntilIdle()

        assertNotNull("A refusal the user cannot see reads as a dead button", failure)
        assertTrue(failure!!.contains("PERMISSION_DENIED"))
        assertTrue(model.uiState.value.following.isEmpty())
    }

    @Test
    fun `a signed-out viewer is told to sign in rather than silently ignored`() = runTest(dispatcher) {
        auth.signOut()
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        var failure: String? = null
        model.toggleFollow(Ben) { failure = it }
        advanceUntilIdle()

        assertNotNull(failure)
        assertTrue(follows.follows.isEmpty())
    }

    // ---- Account isolation (§11) -----------------------------------------------------------------

    /**
     * The previous account's Following tab must not survive a switch.
     *
     * `Me` follows Ben and is looking at Ben's post. `Other` signs in following nobody: the tab is
     * re-keyed on the session, so what is on screen is `Other`'s answer — their own posts, of which
     * there are none — and not Ben's post left over from the account that logged out.
     */
    @Test
    fun `signing in as somebody else re-reads the following tab`() = runTest(dispatcher) {
        feed.server.value = listOf(post("ben-1", Ben, createdAt = 9L))
        follows.set(Me, setOf(Ben))
        val model = viewModel()
        open(model)
        model.onSegmentChange(FeedSegment.Following)
        advanceUntilIdle()
        assertEquals(listOf("ben-1"), model.uiState.value.posts.map { it.id })

        auth.signInAs(Other)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(Other, state.myUid)
        assertTrue("The previous account's follow set leaked", state.following.isEmpty())
        assertTrue("The previous account's feed leaked", state.posts.isEmpty())
    }

    // ---- Searching for people (§3) ---------------------------------------------------------------

    @Test
    fun `typing a name asks firestore once, at the pause`() = runTest(dispatcher) {
        users.people[Ben] = user(Ben, "Ben Carter")
        users.people[Carla] = user(Carla, "Carla Diaz")
        val model = viewModel()
        open(model)

        // Four keystrokes, no pause between them: only the last one is a question.
        model.onSearchQueryChange("b")
        model.onSearchQueryChange("be")
        model.onSearchQueryChange("ben")
        advanceUntilIdle()

        assertEquals(listOf("ben"), users.searchCalls)
        assertEquals(listOf(Ben), model.searchState.value.results.map { it.uid })
        assertFalse(model.searchState.value.isSearching)
    }

    /**
     * The result carries the person it names.
     *
     * The screen's row hands `user.uid` to `onOpenProfile`, so this is the half of "do not show my
     * profile for every search result" that a JVM test can hold: the uid in the result is the found
     * account's, never the viewer's.
     */
    @Test
    fun `a result names the account it found and not the viewer`() = runTest(dispatcher) {
        users.people[Me] = user(Me, "Bennett Me")
        users.people[Ben] = user(Ben, "Ben Carter")
        val model = viewModel()
        open(model)

        model.onSearchQueryChange("ben c")
        advanceUntilIdle()

        assertEquals(listOf(Ben), model.searchState.value.results.map { it.uid })
    }

    @Test
    fun `one letter asks nobody`() = runTest(dispatcher) {
        users.people[Ben] = user(Ben, "Ben Carter")
        val model = viewModel()
        open(model)

        model.onSearchQueryChange("b")
        advanceUntilIdle()

        assertTrue("A one-letter prefix is the whole collection", users.searchCalls.isEmpty())
        assertTrue(model.searchState.value.results.isEmpty())
        assertTrue("The panel says 'keep typing', not 'nobody'", model.searchState.value.isTooShort)
    }

    @Test
    fun `a failed search says so instead of looking empty`() = runTest(dispatcher) {
        users.searchFails = true
        val model = viewModel()
        open(model)

        model.onSearchQueryChange("ben")
        advanceUntilIdle()

        val state = model.searchState.value
        assertNotNull(state.error)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `clearing the search closes the panel`() = runTest(dispatcher) {
        users.people[Ben] = user(Ben, "Ben Carter")
        val model = viewModel()
        open(model)
        model.onSearchQueryChange("ben")
        advanceUntilIdle()
        assertTrue(model.searchState.value.isOpen)

        model.clearSearch()
        advanceUntilIdle()

        val state = model.searchState.value
        assertFalse(state.isOpen)
        assertTrue(state.results.isEmpty())
        assertNull(state.error)
    }

    /** Searching must not disturb the feed — the reason it is a flow of its own. */
    @Test
    fun `searching leaves the feed's state alone`() = runTest(dispatcher) {
        feed.server.value = noisyStrangers(3)
        users.people[Ben] = user(Ben, "Ben Carter")
        val model = viewModel()
        open(model)
        advanceUntilIdle()
        val before = model.uiState.value

        model.onSearchQueryChange("ben")
        advanceUntilIdle()

        assertTrue("A keystroke rebuilt the feed", before === model.uiState.value)
    }

    private companion object {
        const val Me = "uid-me"
        const val Ben = "uid-ben"
        const val Carla = "uid-carla"
        const val Other = "uid-other"

        /** `FeedViewModel.PageSize`, which is private — kept in step deliberately. */
        const val FeedPageSize = 20

        fun post(id: String, authorId: String, createdAt: Long) = Post(
            id = id,
            authorId = authorId,
            authorName = "whoever",
            body = "body of $id",
            createdAt = createdAt,
        )

        /** [count] posts by [count] different strangers, newest last. */
        fun noisyStrangers(count: Int) = (0 until count).map { index ->
            post("noise-$index", "uid-stranger-$index", createdAt = 1_000L + index)
        }

        fun user(uid: String, name: String) =
            com.example.omni.data.model.User(uid = uid, name = name, email = "$uid@omni.test")
    }
}
