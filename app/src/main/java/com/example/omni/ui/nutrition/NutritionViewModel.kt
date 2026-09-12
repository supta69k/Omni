package com.example.omni.ui.nutrition

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.model.DayNutrition
import com.example.omni.data.model.Meal
import com.example.omni.data.model.todayKeyFlow
import com.example.omni.data.model.toDayNutrition
import com.example.omni.data.model.weekEndingOn
import com.example.omni.data.repo.AuthRepository
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
    /** The month whose days the week strip shows. Displayed as a tappable label above the strip. */
    val selectedMonth: java.time.YearMonth = java.time.YearMonth.now(),
    val meals: List<Meal> = emptyList(),
    val nutrition: DayNutrition = DayNutrition(),
    val steps: Int = 0,
    val glasses: Int = 0,
    val sleepHours: Float = 0f,
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
) : ViewModel() {

    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * The seven days ending today, rebuilt whenever today changes.
     *
     * Derived from [todayKeyFlow] rather than computed once, so an app left open past midnight slides the
     * strip forward instead of stranding the user on a week that ended yesterday.
     */
    private val week: Flow<List<DayChipState>> = todayKeyFlow().map { key ->
        val today = runCatching { LocalDate.parse(key) }.getOrElse { LocalDate.now() }
        weekEndingOn(today).map { date ->
            DayChipState(
                date = date,
                initial = date.dayOfWeek
                    .getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    .take(1)
                    .uppercase(Locale.getDefault()),
                number = "%02d".format(Locale.US, date.dayOfMonth),
                selectable = !date.isAfter(today),
            )
        }
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

    /** The day actually being read: the selection, or today when there is none. */
    private val activeDate: Flow<LocalDate> = combine(week, selected) { days, pick ->
        val today = days.lastOrNull()?.date ?: LocalDate.now()
        // A selection that has scrolled off the strip (open past midnight for a week) falls back to today
        // rather than reading a day the user can no longer see.
        pick?.takeIf { date -> days.any { it.date == date } } ?: today
    }.distinctUntilChanged()

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
        combine(week, activeDate, meals, metrics) { days, date, log, day ->
            NutritionUiState(
                week = days,
                selectedIndex = days.indexOfFirst { it.date == date }.coerceAtLeast(0),
                meals = log,
                nutrition = log.toDayNutrition(),
                steps = day.steps,
                glasses = day.waterGlasses,
                sleepHours = day.sleepHours,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = NutritionUiState(),
        )

    /** Ignores a tap on a future chip rather than trusting the UI to have disabled it. */
    fun onSelectDay(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        selected.value = date
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

    private companion object {
        /** Matches `HomeViewModel`: a rotation keeps the listeners, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L
    }
}

/** The selected day's document id, or `null` before the first emission has built the strip. */
private val NutritionUiState.dateKey: String?
    get() = week.getOrNull(selectedIndex)?.date?.toString()
