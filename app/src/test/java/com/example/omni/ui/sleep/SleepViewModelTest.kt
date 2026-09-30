package com.example.omni.ui.sleep

import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import com.example.omni.data.repo.SleepRepository
import com.example.omni.ui.feed.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The sleep page's behaviour: that a night is written under the account that logged it, that a second save
 * onto the same date corrects the night rather than adding a second one, that a delete clears it, and that
 * switching accounts re-reads from scratch instead of leaving the previous person's nights on screen.
 *
 * The fake repository is a *server*, not a bag of canned answers: it stores records under the uid that wrote
 * them, and every read is scoped to a uid — so a test that saves as one account and reads as another is
 * asking the same question account isolation (adjustment #6) exists to answer. A fake that ignored the uid
 * would pass the isolation test while the real bug shipped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val sleep = FakeSleepRepository()

    /** Today, because [SleepViewModel.onSelectDate] refuses a future night and follows today by default. */
    private val today = LocalDate.now().toString()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SleepViewModel(auth, sleep)

    /** Keeps `uiState` hot — `WhileSubscribed` emits nothing without a collector. */
    private fun TestScope.open(model: SleepViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
    }

    // Every step here drives the scheduler with `runCurrent()`, never `advanceUntilIdle()`. The ViewModel
    // collects `todayKeyFlow()`, which is `while (true) { emit; delay(untilMidnight) }` — an intentionally
    // endless ticker that moves the page onto the new day at midnight. `advanceUntilIdle()` would advance
    // virtual time through every one of those midnight delays and never return; `runCurrent()` drains the
    // work queued at the current instant (the emit, the saves, the reads) and stops at the pending delay,
    // which is exactly the settling point every assertion below needs.

    // ---- Saving a night (§ save / update) --------------------------------------------------------

    @Test
    fun `saving a night stores it under the signed-in account with the computed duration`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            model.onSelectDate(today)
            runCurrent()

            model.onSaveSleep(today, "23:00", "07:00", SleepQuality.GOOD, "restful")
            runCurrent()

            val stored = sleep.recordFor(Me, today)
            assertEquals("23:00", stored?.bedtime)
            assertEquals("07:00", stored?.wakeTime)
            // The duration is computed by the ViewModel, never taken from the caller — 8 hours.
            assertEquals(480, stored?.durationMinutes)
            assertEquals(SleepQuality.GOOD, stored?.quality)
            assertEquals("restful", stored?.notes)

            // And it is on screen for the night the page is showing.
            assertEquals(480, model.uiState.value.selectedRecord?.durationMinutes)
            // The sheet closed on success and nothing is reported.
            assertTrue(model.uiState.value.editing.not())
            assertNull(model.uiState.value.error)
        }

    /**
     * One record per date (adjustment #8): a second save onto the same night is an edit, not a second row.
     *
     * The document id is the date, so the map ends with exactly one entry for today — the corrected one.
     */
    @Test
    fun `saving twice on the same date corrects the night rather than adding a second`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            model.onSelectDate(today)
            runCurrent()

            model.onSaveSleep(today, "23:00", "07:00", SleepQuality.FAIR, null)
            runCurrent()
            model.onSaveSleep(today, "22:00", "06:30", SleepQuality.EXCELLENT, null)
            runCurrent()

            assertEquals("Two saves on one date must not make two records", 1, sleep.countFor(Me))
            val stored = sleep.recordFor(Me, today)
            assertEquals("22:00", stored?.bedtime)
            assertEquals(510, stored?.durationMinutes) // 22:00 -> 06:30 = 8h30m
            assertEquals(SleepQuality.EXCELLENT, stored?.quality)
        }

    /** Impossible times are refused with a sentence, and nothing is written. */
    @Test
    fun `times that do not make a night are rejected without a write`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.onSelectDate(today)
        runCurrent()

        model.onSaveSleep(today, "07:00", "07:00", SleepQuality.GOOD, null)
        runCurrent()

        assertEquals(0, sleep.countFor(Me))
        assertTrue(model.uiState.value.error?.isNotBlank() == true)
    }

    // ---- Deleting a night (§ delete) -------------------------------------------------------------

    @Test
    fun `deleting a night removes it and clears the card`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.onSelectDate(today)
        runCurrent()
        model.onSaveSleep(today, "23:00", "07:00", SleepQuality.GOOD, null)
        runCurrent()
        assertEquals(1, sleep.countFor(Me))

        model.onDeleteSleep(today)
        runCurrent()

        assertEquals(0, sleep.countFor(Me))
        assertNull(model.uiState.value.selectedRecord)
        assertNull(model.uiState.value.confirmingDelete)
    }

    // ---- Account isolation (adjustment #6) -------------------------------------------------------

    /**
     * A→B→A, the exact sequence the brief names.
     *
     * `Me` logs a night. `Other` signs in and must see nothing — not `Me`'s night left over. `Me` signs
     * back in and their night is still there, read fresh from the account rather than from a global cache.
     */
    @Test
    fun `switching accounts re-reads and never leaks the previous account's nights`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            model.onSelectDate(today)
            runCurrent()
            model.onSaveSleep(today, "23:00", "07:00", SleepQuality.GOOD, null)
            runCurrent()
            assertEquals(480, model.uiState.value.selectedRecord?.durationMinutes)

            auth.signInAs(Other)
            runCurrent()
            assertNull("Other must not see Me's night", model.uiState.value.selectedRecord)
            assertTrue(model.uiState.value.monthRecords.isEmpty())

            auth.signInAs(Me)
            runCurrent()
            assertEquals(
                "Me's own night must come back on re-entry",
                480,
                model.uiState.value.selectedRecord?.durationMinutes,
            )
        }

    /**
     * A save lands under whoever is signed in *now*, not under a uid captured when the ViewModel was built.
     *
     * `Me` saves, `Other` signs in and saves the same date: two records under two accounts, each holding its
     * own night. This is the write-side half of isolation — the read side is the test above.
     */
    @Test
    fun `a save is attributed to the current session, not a cached uid`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.onSelectDate(today)
        runCurrent()
        model.onSaveSleep(today, "23:00", "07:00", SleepQuality.GOOD, null)
        runCurrent()

        auth.signInAs(Other)
        runCurrent()
        model.onSaveSleep(today, "01:00", "08:00", SleepQuality.FAIR, null)
        runCurrent()

        assertEquals(480, sleep.recordFor(Me, today)?.durationMinutes)
        assertEquals(420, sleep.recordFor(Other, today)?.durationMinutes)
    }

    // ---- The weekly average, end to end ----------------------------------------------------------

    @Test
    fun `one logged night reports its own duration as the weekly average`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        model.onSelectDate(today)
        runCurrent()
        model.onSaveSleep(today, "23:00", "07:00", SleepQuality.GOOD, null)
        runCurrent()

        assertEquals(480, model.uiState.value.weeklyAverageMinutes)
    }

    private companion object {
        const val Me = "uid-me"
        const val Other = "uid-other"
    }
}

/**
 * The pure averaging and progress helpers, tested on fixed dates so they hold whatever day the suite runs.
 *
 * [weeklyAverage] is the summary the design shows above the history; its one subtlety is that a night with
 * no record is *skipped*, not counted as zero, so a person who logged three good nights out of seven is not
 * told they are in crisis. [goalPercent] is the progress bar's fill, capped because a bar cannot draw past
 * its own end.
 */
class SleepMathTest {

    @Test
    fun `the average counts only nights that were logged`() {
        val records = mapOf(
            "2025-01-10" to night("2025-01-10", 480),
            "2025-01-09" to night("2025-01-09", 420),
            "2025-01-08" to night("2025-01-08", 360),
        )
        // (480 + 420 + 360) / 3 = 420 — the four missing nights are not averaged in as zeroes.
        assertEquals(420, weeklyAverage(records, "2025-01-10"))
    }

    @Test
    fun `nothing logged is an average of zero`() {
        assertEquals(0, weeklyAverage(emptyMap(), "2025-01-10"))
    }

    @Test
    fun `nights outside the seven-day window are not counted`() {
        val records = mapOf(
            "2025-01-10" to night("2025-01-10", 480),
            "2025-01-01" to night("2025-01-01", 60), // nine days before the end — out of the window
        )
        assertEquals(480, weeklyAverage(records, "2025-01-10"))
    }

    @Test
    fun `a malformed end date averages to zero rather than throwing`() {
        assertEquals(0, weeklyAverage(mapOf("x" to night("x", 480)), "not-a-date"))
    }

    @Test
    fun `progress is a whole percentage of the goal`() {
        assertEquals(100, goalPercent(480, 480))
        assertEquals(50, goalPercent(240, 480))
    }

    @Test
    fun `progress past the goal caps at one hundred`() {
        assertEquals(100, goalPercent(600, 480))
    }

    @Test
    fun `a non-positive goal is zero percent rather than a divide by zero`() {
        assertEquals(0, goalPercent(480, 0))
    }

    private fun night(date: String, minutes: Int) = SleepRecord(
        date = date,
        bedtime = "23:00",
        wakeTime = "07:00",
        durationMinutes = minutes,
        quality = SleepQuality.GOOD,
    )
}

/**
 * An in-memory sleep repository that stores records **per uid**, so a read scoped to one account cannot
 * see another's writes. That per-uid scoping is the whole point: it is what lets the isolation test above
 * mean something.
 */
private class FakeSleepRepository : SleepRepository {

    private val byUid = mutableMapOf<String, MutableStateFlow<Map<String, SleepRecord>>>()

    private fun stream(uid: String) = byUid.getOrPut(uid) { MutableStateFlow(emptyMap()) }

    override fun observeDay(uid: String, date: String): Flow<SleepRecord?> =
        stream(uid).map { it[date] }

    override fun observeMonth(uid: String, month: String): Flow<Map<String, SleepRecord>> =
        stream(uid).map { all -> all.filterKeys { it.startsWith(month) } }

    override suspend fun saveSleep(uid: String, record: SleepRecord) {
        stream(uid).value = stream(uid).value + (record.date to record)
    }

    override suspend fun deleteSleep(uid: String, date: String) {
        stream(uid).value = stream(uid).value - date
    }

    fun recordFor(uid: String, date: String): SleepRecord? = byUid[uid]?.value?.get(date)

    fun countFor(uid: String): Int = byUid[uid]?.value?.size ?: 0
}
