package com.example.omni.ui.nutrition

import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealAnalysis
import com.example.omni.data.model.MealAnalysisError
import com.example.omni.data.model.MealAnalysisItem
import com.example.omni.data.model.MealAnalysisResult
import com.example.omni.data.model.MealAnalysisTotals
import com.example.omni.data.model.MealSlot
import com.example.omni.data.model.CoachGuidance
import com.example.omni.data.model.CoachState
import com.example.omni.data.repo.CoachRepository
import com.example.omni.data.repo.CoachResult
import com.example.omni.data.repo.MealAnalysisRepository
import com.example.omni.data.repo.MealRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.ui.feed.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The nutrition page's AI meal flow (Phase 7) and the promise that it does not disturb the manual food
 * log it plugs into.
 *
 * The whole point of the design is that an AI estimate travels the *same* write path as a hand-typed
 * meal — [NutritionViewModel.confirmAiMeal] simply calls [NutritionViewModel.saveMeal] — so these tests
 * assert three things at once: that the AI state machine (analyze → review / clarify / fail) behaves,
 * that a confirmed estimate is *added* to the day rather than replacing it, and that account isolation
 * (A → B → A) holds for AI-logged meals exactly as it does for manual ones.
 *
 * Never the real Gemini or the real backend: [FakeMealAnalysisRepository] returns whatever result the
 * test arms, so no unit test spends a byte of free-tier quota (spec: "Do NOT call the real Gemini API
 * in unit tests"). The meal repository is a per-account *server*, so a read scoped to one uid cannot see
 * another's writes — which is what makes the isolation test mean something.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val auth = FakeAuthRepository(Me)
    private val metrics = FakeMetricsRepository()
    private val meals = FakeMealRepository()
    private val analyzer = FakeMealAnalysisRepository()

    /** AI meal analysis is Omni+-gated, so the analyzer tests run with an active subscription. */
    private val omniPlus = com.example.omni.data.revenuecat.PreviewRevenueCatRepository(
        com.example.omni.data.revenuecat.RevenueCatState.Active(
            mapOf(com.example.omni.data.revenuecat.EntitlementIds.OMNI_PLUS to true),
        ),
    )

    /** Today, because the page follows today by default and refuses a future day. */
    private val today = LocalDate.now().toString()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = NutritionViewModel(auth, metrics, meals, analyzer, omniPlus, coach)

    /** The coach's transport fake — records what was requested, answers what a test tells it to. */
    private val coach = FakeCoachRepository()

    /** A free (non-Omni+) account, for the AI gate test. */
    private fun freeViewModel() = NutritionViewModel(
        auth, metrics, meals, analyzer,
        com.example.omni.data.revenuecat.PreviewRevenueCatRepository(),
        FakeCoachRepository(),
    )

    /** Keeps `uiState` hot so the week strip (and thus `dateKey`) is built — `WhileSubscribed` needs a collector. */
    private fun TestScope.open(model: NutritionViewModel) {
        backgroundScope.launch { model.uiState.collect { } }
        backgroundScope.launch { model.aiMeal.collect { } }
    }

    // Driven with `runCurrent()`, never `advanceUntilIdle()`: the ViewModel collects `todayKeyFlow()`,
    // an endless midnight ticker that `advanceUntilIdle()` would spin on forever.

    // ---- The AI state machine -------------------------------------------------------------------

    @Test
    fun `a successful analysis becomes a reviewable estimate without writing anything`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            runCurrent()

            analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
            model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
            runCurrent()

            val state = model.aiMeal.value
            assertTrue("Expected Review, was $state", state is AiMealState.Review)
            assertEquals(494, (state as AiMealState.Review).analysis.totals.calories)
            // An estimate is not a log entry: nothing has been saved yet.
            assertTrue(meals.mealsFor(Me, today).isEmpty())
        }

    @Test
    fun `a vague description surfaces the clarification question instead of guessing`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            runCurrent()

            analyzer.nextResult = MealAnalysisResult.Success(
                MealAnalysis(
                    mealName = "Rice and fish",
                    items = emptyList(),
                    totals = MealAnalysisTotals(),
                    estimated = true,
                    needsClarification = true,
                    clarificationQuestion = "About how much rice and fish did you eat?",
                ),
            )
            model.analyzeMeal("rice and fish")
            runCurrent()

            val state = model.aiMeal.value
            assertTrue(state is AiMealState.NeedsClarification)
            assertEquals("About how much rice and fish did you eat?", (state as AiMealState.NeedsClarification).question)
            assertTrue(meals.mealsFor(Me, today).isEmpty())
        }

    @Test
    fun `a failed analysis carries the specific error and leaves manual logging working`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            runCurrent()

            analyzer.nextResult = MealAnalysisResult.Failure(MealAnalysisError.Unavailable)
            model.analyzeMeal("2 eggs")
            runCurrent()

            val state = model.aiMeal.value
            assertTrue(state is AiMealState.Failed)
            assertEquals(MealAnalysisError.Unavailable, (state as AiMealState.Failed).error)

            // The whole reason failures are typed and non-fatal: the manual path is still there.
            model.saveMeal(Meal(id = "", name = "Apple", slot = MealSlot.SNACK, calories = 95))
            runCurrent()
            assertEquals(1, meals.mealsFor(Me, today).size)
        }

    @Test
    fun `blank text never reaches the analyzer`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()

        model.analyzeMeal("   ")
        runCurrent()

        assertEquals(0, analyzer.calls)
        assertTrue(model.aiMeal.value is AiMealState.Failed)
    }

    @Test
    fun `a free user's analyze never reaches the paid analyzer`() = runTest(dispatcher) {
        val model = freeViewModel()
        open(model)
        runCurrent()

        model.analyzeMeal("2 eggs and toast")
        runCurrent()

        // The Omni+ gate short-circuits before the Gemini-backed backend is ever called.
        assertEquals(0, analyzer.calls)
        assertTrue(model.aiMeal.value is AiMealState.Idle)
    }

    @Test
    fun `dismissing the AI flow returns it to idle`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs and toast")
        runCurrent()
        assertTrue(model.aiMeal.value is AiMealState.Review)

        model.dismissAiMeal()
        runCurrent()
        assertEquals(AiMealState.Idle, model.aiMeal.value)
    }

    // ---- Confirm writes through the ordinary food-log path (Phases J/K) --------------------------

    @Test
    fun `confirming an estimate saves one meal and closes the flow`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()

        val review = model.aiMeal.value as AiMealState.Review
        model.confirmAiMeal(review.analysis.toMeal(slot = MealSlot.BREAKFAST))
        runCurrent()

        val logged = meals.mealsFor(Me, today)
        assertEquals(1, logged.size)
        assertEquals("Eggs, bread and peanut butter", logged.single().name)
        assertEquals(494, logged.single().calories)
        // A blank id was allocated by the repo (the create-or-replace contract), and loggedAt was stamped.
        assertTrue(logged.single().id.isNotBlank())
        assertTrue(logged.single().loggedAt > 0L)
        // The flow closed itself.
        assertEquals(AiMealState.Idle, model.aiMeal.value)
    }

    @Test
    fun `a confirmed AI meal is added to the day, not substituted for it`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()

        // A manual meal already on the day.
        model.saveMeal(Meal(id = "", name = "Oatmeal", slot = MealSlot.BREAKFAST, calories = 150))
        runCurrent()
        assertEquals(150, model.uiState.value.nutrition.calories)

        // Then an AI-estimated one on top of it.
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()
        val review = model.aiMeal.value as AiMealState.Review
        model.confirmAiMeal(review.analysis.toMeal(slot = MealSlot.LUNCH))
        runCurrent()

        // Two meals on the day and the calories summed — 150 added to 494, never replaced.
        assertEquals(2, meals.mealsFor(Me, today).size)
        assertEquals(150 + 494, model.uiState.value.nutrition.calories)
    }

    @Test
    fun `an edited estimate saves the user's numbers, not the model's`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()

        val review = model.aiMeal.value as AiMealState.Review
        // The review sheet lets the user correct the numbers before confirming — 494 → 500 here.
        model.confirmAiMeal(review.analysis.toMeal(slot = MealSlot.DINNER, name = "Late dinner", calories = 500))
        runCurrent()

        val logged = meals.mealsFor(Me, today).single()
        assertEquals("Late dinner", logged.name)
        assertEquals(500, logged.calories)
        assertEquals(MealSlot.DINNER, logged.slot)
    }

    // ---- Fiber is a first-class value on the AI path ----------------------------------------------

    @Test
    fun `an AI estimate carries its analyzed fiber into the log`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("oatmeal with milk and a banana")
        runCurrent()

        // The review sheet confirms the estimate with the fiber it was analyzed to have — the same
        // default path every other macro takes through [MealAnalysis.toMeal].
        val review = model.aiMeal.value as AiMealState.Review
        model.confirmAiMeal(review.analysis.toMeal(slot = MealSlot.BREAKFAST))
        runCurrent()

        val logged = meals.mealsFor(Me, today).single()
        assertEquals(sampleAnalysis.totals.fiber, logged.fiber)
        assertEquals(sampleAnalysis.totals.fiber, model.uiState.value.nutrition.fiber)
    }

    @Test
    fun `an edited estimate preserves the analyzed fiber`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()

        // The review sheet edits the calories but leaves the fiber field as analyzed — the edit must
        // not zero it the way the old hardcoded toMeal did.
        val review = model.aiMeal.value as AiMealState.Review
        model.confirmAiMeal(review.analysis.toMeal(slot = MealSlot.LUNCH, calories = 500))
        runCurrent()

        assertEquals(sampleAnalysis.totals.fiber, meals.mealsFor(Me, today).single().fiber)
    }

    @Test
    fun `daily fiber aggregates across manual and AI meals`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()

        model.saveMeal(
            Meal(id = "", name = "Apple", slot = MealSlot.SNACK, calories = 95, fiber = 4.2f),
        )
        runCurrent()
        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()
        model.confirmAiMeal((model.aiMeal.value as AiMealState.Review).analysis.toMeal(slot = MealSlot.LUNCH))
        runCurrent()

        assertEquals(2, meals.mealsFor(Me, today).size)
        assertEquals(4.2f + sampleAnalysis.totals.fiber, model.uiState.value.nutrition.fiber)
    }

    // ---- Manual logging regression --------------------------------------------------------------

    @Test
    fun `manual logging still works untouched by the AI flow`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()

        model.saveMeal(Meal(id = "", name = "Banana", slot = MealSlot.SNACK, calories = 105, carbs = 27f))
        runCurrent()

        val logged = meals.mealsFor(Me, today).single()
        assertEquals("Banana", logged.name)
        assertEquals(105, logged.calories)
        assertEquals(105, model.uiState.value.nutrition.calories)
        assertEquals(0, analyzer.calls) // the analyzer was never involved
    }

    // ---- Account isolation, A -> B -> A (for AI-logged meals) ------------------------------------

    @Test
    fun `an AI meal logged by one account never leaks to another`() = runTest(dispatcher) {
        val model = viewModel()
        open(model)
        runCurrent()

        analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
        model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
        runCurrent()
        model.confirmAiMeal((model.aiMeal.value as AiMealState.Review).analysis.toMeal(slot = MealSlot.BREAKFAST))
        runCurrent()
        assertEquals(494, model.uiState.value.nutrition.calories)

        // B signs in — must see an empty day, not Me's AI meal.
        auth.signInAs(Other)
        runCurrent()
        assertTrue(model.uiState.value.meals.isEmpty())
        assertEquals(0, model.uiState.value.nutrition.calories)

        // A returns — their meal is read back from their own account.
        auth.signInAs(Me)
        runCurrent()
        assertEquals(494, model.uiState.value.nutrition.calories)
        // And B's account never received it.
        assertTrue(meals.mealsFor(Other, today).isEmpty())
    }

    @Test
    fun `a confirmed AI meal is attributed to the current session, not a cached uid`() =
        runTest(dispatcher) {
            val model = viewModel()
            open(model)
            runCurrent()

            auth.signInAs(Other)
            runCurrent()
            analyzer.nextResult = MealAnalysisResult.Success(sampleAnalysis)
            model.analyzeMeal("2 eggs, 2 slices of bread and peanut butter")
            runCurrent()
            model.confirmAiMeal((model.aiMeal.value as AiMealState.Review).analysis.toMeal(slot = MealSlot.BREAKFAST))
            runCurrent()

            assertEquals(1, meals.mealsFor(Other, today).size)
            assertTrue(meals.mealsFor(Me, today).isEmpty())
        }

    private companion object {
        const val Me = "uid-me"
        const val Other = "uid-other"

        /** The spec's own example meal, with the server-recomputed 494 kcal total and its fiber. */
        val sampleAnalysis = MealAnalysis(
            mealName = "Eggs, bread and peanut butter",
            items = listOf(
                MealAnalysisItem("Egg", 2f, "large", 144, 12.6f, 0.8f, 9.6f, 0f),
                MealAnalysisItem("Bread", 2f, "slice", 160, 6f, 30f, 2f, 3.6f),
                MealAnalysisItem("Peanut butter", 1f, "tbsp", 190, 8f, 6f, 16f, 0.9f),
            ),
            totals = MealAnalysisTotals(calories = 494, protein = 26.6f, carbs = 36.8f, fat = 27.6f, fiber = 4.5f),
            estimated = true,
            needsClarification = false,
            clarificationQuestion = null,
        )
    }
}

/**
 * A programmable analyzer that never touches the network or Gemini — the test arms [nextResult] and this
 * hands it back, recording that it was called and with what. That call log is what proves, e.g., that
 * blank input short-circuits before any request would have been made.
 */
private class FakeMealAnalysisRepository : MealAnalysisRepository {
    var nextResult: MealAnalysisResult = MealAnalysisResult.Failure(MealAnalysisError.Unknown)
    var calls = 0
        private set
    var lastText: String? = null
        private set

    override suspend fun analyzeMeal(mealText: String): MealAnalysisResult {
        calls++
        lastText = mealText
        return nextResult
    }
}

/**
 * An in-memory food log stored **per account and day**, honouring the real repository's create-or-replace
 * contract: a blank [Meal.id] is a new meal the store allocates an id for, and an existing id is an edit.
 * The per-uid scoping is the whole point — it is what lets the A → B → A test above catch a leak.
 */
private class FakeMealRepository : MealRepository {
    private val byKey = mutableMapOf<String, MutableStateFlow<List<Meal>>>()
    private var seq = 0

    private fun key(uid: String, date: String) = "$uid/$date"
    private fun stream(uid: String, date: String) =
        byKey.getOrPut(key(uid, date)) { MutableStateFlow(emptyList()) }

    override fun observeMeals(uid: String, date: String): Flow<List<Meal>> = stream(uid, date)

    override suspend fun saveMeal(uid: String, date: String, meal: Meal) {
        val id = meal.id.ifBlank { "m${seq++}" }
        val stored = meal.copy(id = id)
        val s = stream(uid, date)
        s.value = s.value.filterNot { it.id == id } + stored
    }

    override suspend fun deleteMeal(uid: String, date: String, mealId: String) {
        val s = stream(uid, date)
        s.value = s.value.filterNot { it.id == mealId }
    }

    fun mealsFor(uid: String, date: String): List<Meal> = byKey[key(uid, date)]?.value ?: emptyList()
}

/** Metrics are not what these tests exercise; every day reads as empty, every month as nothing logged. */
private class FakeMetricsRepository : MetricsRepository {
    override fun observeDay(uid: String, date: String): Flow<DailyMetrics?> = flowOf(null)
    override fun observeMonth(uid: String, month: String): Flow<Map<String, DailyMetrics>> = flowOf(emptyMap())
    override suspend fun addGlass(uid: String, date: String) = Unit
    override suspend fun setSteps(uid: String, date: String, steps: Int) = Unit
    override suspend fun setSleepHours(uid: String, date: String, hours: Float) = Unit
}


/** The coach transport's in-memory twin: answers what a test tells it, records what was asked. */
private class FakeCoachRepository : CoachRepository {
    var result: CoachResult = CoachResult.Success(
        CoachGuidance(
            summary = "Your steps are trending up while fiber lags behind.",
            focus = "fiber",
            tips = listOf("Add a pear at breakfast.", "Swap white rice for brown.", "Keep the morning walk."),
            starter = false,
            used = 1,
            limit = 1,
        ),
    )

    override suspend fun requestDailyGuidance(today: String): CoachResult = result
}
