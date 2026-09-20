package com.example.omni.ui.nutrition

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.model.DayNutrition
import com.example.omni.data.model.Meal
import com.example.omni.data.model.MealAnalysis
import com.example.omni.data.model.MealAnalysisError
import com.example.omni.data.model.MealAnalysisResult
import com.example.omni.data.model.todayKeyFlow
import com.example.omni.data.model.toDayNutrition
import com.example.omni.data.model.weekEndingOn
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MealAnalysisRepository
import com.example.omni.data.repo.MealRepository
import com.example.omni.data.repo.MetricsRepository
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One chip in the week strip.
 *
 * @property initial the weekday's first letter. Localised, because a Bengali or German phone should not
 *   read "S M T W T F S" — [LocalDate.toString] is a machine format but a *label* is for a person.
 * @property number the day of the month, zero-padded to two digits like the design's "01".
 * @property selectable a future day cannot be chosen (BACKEND_PLAN §11 Phase 6, "do not let it select a
 *   future date"). Only reachable in the hour either side of midnight, and only then because the strip is
 *   built once per composition while the clock keeps moving.
 */
data class DayChipState(
    val date: LocalDate,
    val initial: String,
    val number: String,
    val selectable: Boolean,
)

/**
 * Everything the nutrition page renders.
 *
 * @property nutrition summed from [meals] rather than read off the day document. The two agree within a
 *   round-trip, but the sum of the list on screen is the number that cannot contradict the rows above it —
 *   and a meal's write is visible in the local cache before the roll-up merge has been applied to it.
 * @property steps, [glasses], [sleepHours] the activity ring page — the same three metrics Home shows, from
 *   the same document, which is why the water goal fix in §3.1 applies to this screen too.
 */
data class NutritionUiState(
    val week: List<DayChipState> = emptyList(),
    val selectedIndex: Int = 0,
    /** The month the selected day falls in. Drawn as the tappable label above the strip. */
    val selectedMonth: YearMonth = YearMonth.now(),
    /** Whether the month picker is open. Held here, not in the screen, because it gates [monthCalories]. */
    val monthPickerOpen: Boolean = false,
    /** The month the *picker* is showing, which is only the same as [selectedMonth] until an arrow is tapped. */
    val pickerMonth: YearMonth = YearMonth.now(),
    /** Calories logged per day of [pickerMonth]. A day with no document is absent, not zero. */
    val monthCalories: Map<LocalDate, Int> = emptyMap(),
    val meals: List<Meal> = emptyList(),
    val nutrition: DayNutrition = DayNutrition(),
    val steps: Int = 0,
    val glasses: Int = 0,
    val sleepHours: Float = 0f,
    val isRefreshing: Boolean = false,
)

/** Intermediate result of combining the first five flows before adding [isRefreshing]. */
private data class Quad(
    val days: List<DayChipState>,
    val date: LocalDate,
    val log: List<Meal>,
    val day: DailyMetrics,
    val month: MonthView,
)

/**
 * The AI meal-logging flow's state — a small state machine the log sheet drives (Phase 7).
 *
 * Kept out of [NutritionUiState] on purpose: the food log renders whether or not the AI sheet is open,
 * and folding an ephemeral, one-sheet-at-a-time flow into the day's steady state would make every meal
 * read re-emit when the user is only typing into the AI box. The estimate here is *not* saved — it is
 * what the user reviews; only [NutritionViewModel.confirmAiMeal] writes, through the ordinary food-log
 * path, so there is one source of truth.
 */
sealed interface AiMealState {
    /** No AI request in flight; the input sheet is either closed or waiting for text. */
    data object Idle : AiMealState

    /** Request sent — the sheet shows "Analyzing your meal…". */
    data object Analyzing : AiMealState

    /** An estimate came back and is ready to review, edit, and confirm. */
    data class Review(val analysis: MealAnalysis) : AiMealState

    /** The description was too vague; [question] is the single short prompt to show the user. */
    data class NeedsClarification(val question: String) : AiMealState

    /** Analysis failed; [error] carries the specific message, and manual logging is still offered. */
    data class Failed(val error: MealAnalysisError) : AiMealState
}

/**
 * The month picker's three pieces of state, folded into one value.
 *
 * Together rather than as three flows because Kotlin's typed [combine] stops at five, and [uiState]
 * already spends four on the day. They also change together: opening the picker, paging it and the
 * calories arriving are all one question — "what is the calendar showing".
 */
private data class MonthView(
    val open: Boolean,
    val month: YearMonth,
    val calories: Map<LocalDate, Int>,
)

/**
 * The nutrition page's own reads and writes — the selected day's meals, that day's metrics, and the log
 * sheet's saves and deletes.
 *
 * Held apart from [com.example.omni.ui.home.HomeViewModel] even though both read `days/{date}`, because the
 * *date* differs: Home is always today and re-keys itself at midnight, while this screen's date is whichever
 * chip is selected. A shared ViewModel would have to hold both notions of "the day" at once.
 *
 * Goals are not here, for the same reason they are not in `HomeViewModel`: they live on the account, so
 * `MainActivity` reads them off the session and passes them in (BACKEND_PLAN §4 rule 3).
 */
class NutritionViewModel(
    private val authRepository: AuthRepository,
    private val metricsRepository: MetricsRepository,
    private val mealRepository: MealRepository,
    private val mealAnalysisRepository: MealAnalysisRepository,
) : ViewModel() {

    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * Today, as a date that does not go stale.
     *
     * Derived from [todayKeyFlow] rather than computed once, so an app left open past midnight slides the
     * strip forward instead of stranding the user on a week that ended yesterday.
     */
    private val today: Flow<LocalDate> = todayKeyFlow().map { key ->
        runCatching { LocalDate.parse(key) }.getOrElse { LocalDate.now() }
    }

    /**
     * Which chip is selected, as a date rather than an index.
     *
     * A date and not an index because the strip moves: at midnight the seven chips shift by one, and an
     * index would silently re-point at a different day. `null` means "follow today", which is what the
     * screen opens on — the design highlights the fourth chip, but the design's week is a mock, and a real
     * user opening the page is looking at today.
     */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** Tracks pull-to-refresh state. */
    private val isRefreshing = MutableStateFlow(false)

    /**
     * The day actually being read: the selection, or today when there is none.
     *
     * Only a *future* selection is refused. It used to be narrower than that — a date outside the seven
     * chips on screen fell back to today — which was right while the strip was the only way to choose a
     * day and wrong the moment the month picker could hand over one from March. The strip now follows the
     * selection instead (see [week]), so "off the strip" is no longer a thing a selection can be.
     */
    private val activeDate: Flow<LocalDate> = combine(today, selected) { today, pick ->
        pick?.takeIf { !it.isAfter(today) } ?: today
    }.distinctUntilChanged()

    /**
     * The seven chips on screen — the week ending today, or the week ending on the selected day when that
     * day is not in this one.
     *
     * The strip stays put for an ordinary tap inside the current week (which is every tap the design's own
     * UI can produce) and only slides when the picker hands over a day from further back. Without that, a
     * day chosen from the calendar would be read and written correctly while no chip on screen was lit.
     */
    private val week: Flow<List<DayChipState>> = combine(today, activeDate) { today, date ->
        val thisWeek = weekEndingOn(today)
        val anchor = if (thisWeek.any { it == date }) today else date
        weekEndingOn(anchor).map { day ->
            DayChipState(
                date = day,
                initial = day.dayOfWeek
                    .getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    .take(1)
                    .uppercase(Locale.getDefault()),
                number = "%02d".format(Locale.US, day.dayOfMonth),
                selectable = !day.isAfter(today),
            )
        }
    }

    /** Whether the month picker is open, and which month it has been paged to. */
    private val picking = MutableStateFlow(false)
    private val browsing = MutableStateFlow<YearMonth?>(null)

    /**
     * The calendar the picker draws.
     *
     * The month query only runs while the sheet is open: it is thirty-odd documents the rest of the screen
     * has no use for, and the day the page actually shows has its own listener already. Closing the sheet
     * tears it down again, which is why [picking] is part of the key rather than a flag the UI keeps to
     * itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthView: Flow<MonthView> =
        combine(uid, activeDate, picking, browsing) { uid, date, open, browse ->
            Triple(uid, open, browse ?: YearMonth.from(date))
        }
            .distinctUntilChanged()
            .flatMapLatest { (uid, open, month) ->
                if (uid == null || !open) {
                    flowOf(MonthView(open, month, emptyMap()))
                } else {
                    metricsRepository.observeMonth(uid, month.toString())
                        .map { days -> MonthView(open, month, days.toCalorieMap()) }
                        .catch { cause ->
                            Log.w("Omni", "users/$uid/days for $month could not be read", cause)
                            emit(MonthView(open, month, emptyMap()))
                        }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val meals: Flow<List<Meal>> = combine(uid, activeDate) { uid, date -> uid to date }
        .flatMapLatest { (uid, date) ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                mealRepository.observeMeals(uid, date.toString()).catch { cause ->
                    Log.w("Omni", "users/$uid/days/$date/meals could not be read", cause)
                    emit(emptyList())
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metrics: Flow<DailyMetrics> = combine(uid, activeDate) { uid, date -> uid to date }
        .flatMapLatest { (uid, date) ->
            if (uid == null) {
                flowOf(DailyMetrics(date.toString()))
            } else {
                metricsRepository.observeDay(uid, date.toString())
                    .map { it ?: DailyMetrics(date.toString()) }
                    .catch { cause ->
                        Log.w("Omni", "users/$uid/days/$date could not be read", cause)
                        emit(DailyMetrics(date.toString()))
                    }
            }
        }

    val uiState: StateFlow<NutritionUiState> =
        combine(
            combine(week, activeDate, meals, metrics, monthView) { days, date, log, day, month ->
                Quad(days, date, log, day, month)
            },
            isRefreshing,
        ) { base, refreshing ->
            NutritionUiState(
                week = base.days,
                selectedIndex = base.days.indexOfFirst { it.date == base.date }.coerceAtLeast(0),
                selectedMonth = YearMonth.from(base.date),
                monthPickerOpen = base.month.open,
                pickerMonth = base.month.month,
                monthCalories = base.month.calories,
                meals = base.log,
                nutrition = base.log.toDayNutrition(),
                steps = base.day.steps,
                glasses = base.day.waterGlasses,
                sleepHours = base.day.sleepHours,
                isRefreshing = refreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = NutritionUiState(),
        )

    /**
     * The AI meal flow's state. Starts [AiMealState.Idle]; the sheet moves it through analyzing →
     * review → confirmed (back to idle) or a typed failure. Held as its own flow, not in [uiState],
     * for the reason [AiMealState]'s doc gives.
     */
    private val _aiMeal = MutableStateFlow<AiMealState>(AiMealState.Idle)
    val aiMeal: StateFlow<AiMealState> = _aiMeal

    /**
     * Sends [text] to the backend for an estimate.
     *
     * Never writes anything: a successful estimate becomes [AiMealState.Review] (or
     * [AiMealState.NeedsClarification]) for the user to confirm, and every failure becomes a typed
     * [AiMealState.Failed] the sheet turns into one specific sentence — leaving manual logging intact.
     * A second call while one is already [AiMealState.Analyzing] is ignored so a double-tap cannot
     * spend two of the day's AI budget.
     */
    fun analyzeMeal(text: String) {
        if (_aiMeal.value is AiMealState.Analyzing) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _aiMeal.value = AiMealState.Failed(MealAnalysisError.InvalidInput)
            return
        }
        _aiMeal.value = AiMealState.Analyzing
        viewModelScope.launch {
            _aiMeal.value = when (val result = mealAnalysisRepository.analyzeMeal(trimmed)) {
                is MealAnalysisResult.Success -> {
                    val analysis = result.analysis
                    if (analysis.needsClarification) {
                        AiMealState.NeedsClarification(
                            analysis.clarificationQuestion
                                ?: "Could you add a little more detail about what you ate?",
                        )
                    } else {
                        AiMealState.Review(analysis)
                    }
                }
                is MealAnalysisResult.Failure -> AiMealState.Failed(result.error)
            }
        }
    }

    /**
     * Confirms the reviewed (possibly edited) estimate by saving it through the ordinary food-log path,
     * then closes the AI flow. This is the *only* AI code that writes: it reuses [saveMeal] so the day
     * roll-up, Firestore schema, and owner-only rules are exactly the manual path's — the confirmed
     * meal's calories are added to the day, never replacing it.
     */
    fun confirmAiMeal(meal: Meal) {
        saveMeal(meal)
        _aiMeal.value = AiMealState.Idle
    }

    /** Closes the AI flow without saving — from the input sheet, an error, or a review the user backed out of. */
    fun dismissAiMeal() {
        _aiMeal.value = AiMealState.Idle
    }

    /** Ignores a tap on a future chip rather than trusting the UI to have disabled it. */
    fun onSelectDay(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        selected.value = date
        // Choosing a day answers the question the picker was open to ask, so it closes — and the paged
        // month is dropped, so re-opening starts on the month of whatever day is now selected rather
        // than wherever the last browse left off.
        picking.value = false
        browsing.value = null
    }

    fun onOpenMonthPicker() {
        picking.value = true
    }

    /** Closing without choosing keeps the day, and forgets which month was being read. */
    fun onDismissMonthPicker() {
        picking.value = false
        browsing.value = null
    }

    /**
     * Pages the picker to another month.
     *
     * A month past the current one is refused for the same reason a future chip is: there is nothing to
     * show there, and letting the calendar walk forward forever invites a query per empty month.
     */
    fun onBrowseMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now())) return
        browsing.value = month
    }

    /**
     * Saves a meal to the selected day.
     *
     * The date is read from [uiState] here rather than recomputed as `todayKey()`, which is the opposite of
     * what the water and sleep writes do — and correct for the opposite reason: those are always *today* by
     * definition, while this one belongs to whichever day the user is looking at. Logging breakfast onto
     * yesterday is a supported thing to do.
     */
    fun saveMeal(meal: Meal) {
        val uid = authRepository.currentUid ?: return
        val date = uiState.value.dateKey ?: return
        viewModelScope.launch {
            try {
                // Stamped here, not in the sheet: the sheet cannot know whether it is editing, and an edit
                // must keep its original position in the log rather than jumping to the end.
                val stamped = if (meal.loggedAt > 0L) meal else meal.copy(loggedAt = System.currentTimeMillis())
                mealRepository.saveMeal(uid, date, stamped)
            } catch (cause: Exception) {
                Log.w("Omni", "Saving ${meal.name} to $date failed", cause)
            }
        }
    }

    fun deleteMeal(mealId: String) {
        val uid = authRepository.currentUid ?: return
        val date = uiState.value.dateKey ?: return
        viewModelScope.launch {
            try {
                mealRepository.deleteMeal(uid, date, mealId)
            } catch (cause: Exception) {
                Log.w("Omni", "Deleting $mealId from $date failed", cause)
            }
        }
    }

    /**
     * Force refresh — shows the pull-to-refresh indicator for 1.2s then dismisses.
     * The data is already live via Firestore listeners, so this only needs to drive the indicator.
     */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                kotlinx.coroutines.delay(1200)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private companion object {
        /** Matches `HomeViewModel`: a rotation keeps the listeners, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L
    }
}

/**
 * The selected day's document id, or `null` before the first emission has built the strip.
 *
 * Reading it off the strip is safe because the strip is built *around* the selection — a day chosen from
 * the month picker slides the seven chips onto its own week — so `week[selectedIndex]` is the selected day
 * rather than a fallback to today.
 */
private val NutritionUiState.dateKey: String?
    get() = week.getOrNull(selectedIndex)?.date?.toString()

/** Day documents as "which day had how many calories", dropping ids that are not a date and days at 0. */
private fun Map<String, DailyMetrics>.toCalorieMap(): Map<LocalDate, Int> = mapNotNull { (key, day) ->
    val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
    if (day.calories <= 0) null else date to day.calories
}.toMap()
