package com.example.omni.ui.home

import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.model.Guide
import com.example.omni.data.model.GuideSeverity
import com.example.omni.data.repo.GuideProgressRepository
import com.example.omni.data.repo.GuideRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.data.repo.StepsRepository
import com.example.omni.ui.feed.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val testUid = "user_test_123"
    private val auth = FakeAuthRepository(testUid)
    private val metrics = TestMetricsRepository()
    private val steps = TestStepsRepository()
    private val guideProgress = TestGuideProgressRepository()
    private val guides = TestGuideRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(
        authRepository = auth,
        metricsRepository = metrics,
        stepsRepository = steps,
        guideProgressRepository = guideProgress,
        guideRepository = guides,
    )

    private fun TestScope.open(model: HomeViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    @Test
    fun addGlass_whenSuccessful_callsRepoAndEmitsSuccessEvent() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val events = mutableListOf<Long>()
        backgroundScope.launch {
            model.waterLoggedEvents.collect { events.add(it) }
        }

        model.addGlass(goal = 8)
        advanceUntilIdle()

        assertEquals(1, metrics.addGlassCalls.size)
        assertEquals(testUid, metrics.addGlassCalls[0].first)
        assertEquals(1, events.size)
        assertTrue(events[0] > 0L)
    }

    @Test
    fun addGlass_whenAtOrPastGoal_doesNotCallRepoAndDoesNotEmit() = runTest(dispatcher) {
        metrics.dayMetrics.value = DailyMetrics(date = "2026-10-16", waterGlasses = 8)

        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val events = mutableListOf<Long>()
        backgroundScope.launch {
            model.waterLoggedEvents.collect { events.add(it) }
        }

        model.addGlass(goal = 8)
        advanceUntilIdle()

        assertEquals(0, metrics.addGlassCalls.size)
        assertEquals(0, events.size)
    }

    @Test
    fun addGlass_whenDatabaseWriteFails_doesNotEmitSuccessEvent() = runTest(dispatcher) {
        metrics.shouldFail = true

        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val events = mutableListOf<Long>()
        backgroundScope.launch {
            model.waterLoggedEvents.collect { events.add(it) }
        }

        model.addGlass(goal = 8)
        advanceUntilIdle()

        assertEquals(1, metrics.addGlassCalls.size)
        // Since database threw an exception, no success animation event is emitted!
        assertEquals(0, events.size)
    }

    @Test
    fun addGlass_whenSignedOut_doesNothing() = runTest(dispatcher) {
        auth.signOut()

        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val events = mutableListOf<Long>()
        backgroundScope.launch {
            model.waterLoggedEvents.collect { events.add(it) }
        }

        model.addGlass(goal = 8)
        advanceUntilIdle()

        assertEquals(0, metrics.addGlassCalls.size)
        assertEquals(0, events.size)
    }

    @Test
    fun addGlass_debouncesRapidDoubleTaps() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        advanceUntilIdle()

        val events = mutableListOf<Long>()
        backgroundScope.launch {
            model.waterLoggedEvents.collect { events.add(it) }
        }

        // Tap 1
        model.addGlass(goal = 8)
        // Immediate rapid Tap 2 within debounce window (< 300ms)
        model.addGlass(goal = 8)
        advanceUntilIdle()

        // Only one glass operation should be dispatched
        assertEquals(1, metrics.addGlassCalls.size)
        assertEquals(1, events.size)
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// TEST REPOSITORIES
// ─────────────────────────────────────────────────────────────────────────────────────────────

private class TestMetricsRepository : MetricsRepository {
    val dayMetrics = MutableStateFlow<DailyMetrics?>(DailyMetrics("2026-10-16", waterGlasses = 3))
    val addGlassCalls = mutableListOf<Pair<String, String>>()
    var shouldFail = false

    override fun observeDay(uid: String, date: String): Flow<DailyMetrics?> = dayMetrics.asStateFlow()
    override fun observeMonth(uid: String, month: String): Flow<Map<String, DailyMetrics>> = flowOf(emptyMap())

    override suspend fun addGlass(uid: String, date: String) {
        addGlassCalls.add(uid to date)
        if (shouldFail) {
            throw IOException("Simulated network/Firestore rules failure")
        }
        val current = dayMetrics.value ?: DailyMetrics(date)
        dayMetrics.value = current.copy(waterGlasses = current.waterGlasses + 1)
    }

    override suspend fun setSteps(uid: String, date: String, steps: Int) = Unit
    override suspend fun setSleepHours(uid: String, date: String, hours: Float) = Unit
}

private class TestStepsRepository : StepsRepository {
    override val isAvailable: Boolean = true
    override fun observeTodaySteps(uid: String?): Flow<Int> = flowOf(4500)
    override fun observeLocalSteps(uid: String?): Flow<Int> = flowOf(4500)
}

private class TestGuideProgressRepository : GuideProgressRepository {
    override fun observePercent(uid: String, guideId: String): Flow<Int> = flowOf(25)
    override fun observeAllPercents(uid: String): Flow<Map<String, Int>> = flowOf(emptyMap())
    override fun observeCompletedSteps(uid: String, guideId: String): Flow<Set<Int>> = flowOf(emptySet())
    override suspend fun setCompletedSteps(uid: String, guideId: String, completed: Set<Int>, percent: Int) = Unit
}

private class TestGuideRepository : GuideRepository {
    private val sampleGuides = listOf(
        Guide(
            id = "cpr",
            title = "CPR",
            category = "Resuscitation",
            severity = GuideSeverity.Emergency,
            intro = "Cardiopulmonary resuscitation",
            steps = emptyList(),
            warnings = emptyList(),
        ),
    )

    override fun list(): List<Guide> = sampleGuides
    override fun byId(id: String): Guide? = sampleGuides.firstOrNull { it.id == id }
    override val totalSteps: Int get() = 5
}
