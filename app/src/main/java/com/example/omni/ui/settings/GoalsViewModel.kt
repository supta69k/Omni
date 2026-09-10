package com.example.omni.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.CalorieGoalStep
import com.example.omni.data.model.FiberGoalStep
import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.MaxCalorieGoal
import com.example.omni.data.model.MaxFiberGoal
import com.example.omni.data.model.MaxSleepGoal
import com.example.omni.data.model.MaxStepsGoal
import com.example.omni.data.model.MaxWaterGoal
import com.example.omni.data.model.MinCalorieGoal
import com.example.omni.data.model.MinFiberGoal
import com.example.omni.data.model.MinSleepGoal
import com.example.omni.data.model.MinStepsGoal
import com.example.omni.data.model.MinWaterGoal
import com.example.omni.data.model.SleepGoalStep
import com.example.omni.data.model.StepsGoalStep
import com.example.omni.data.model.WaterGoalStep
import com.example.omni.data.model.goals
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.domain.formatAmount
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
import java.util.Locale
import kotlin.math.roundToInt

/** Which of the five targets a stepper tap is for — the one thing the page's rows differ by. */
enum class GoalKind { WATER, STEPS, SLEEP, FIBER, CALORIES }

/**
 * One row of the goals page: already worded, already formatted, already told where its range ends.
 *
 * Everything the card draws is a `String` or a `Boolean` by the time it gets here, so the composable
 * has no arithmetic in it at all — no plural rule, no decimal, no min/max comparison. That is what
 * lets the screen be a `forEach` over five rows instead of five hand-written cards that would drift
 * apart the first time one of them was edited.
 */
data class GoalRow(
    val kind: GoalKind,
    val title: String,
    val subtitle: String,
    val value: String,
    val unit: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

/**
 * The goals page's state.
 *
 * [goals] is what the steppers show and what Save would write; [stored] is what the profile currently
 * says. Keeping both is what makes [dirty] answerable, and [dirty] is the whole interaction model of
 * this page: the button is live only when the two disagree, and it goes back to reading "Saved" by
 * itself when Firestore echoes the write back.
 *
 * @property loading true only until the profile listener's first emission. An account whose document
 *   has no `goals` map is not loading — it has the defaults, which is a real answer.
 */
data class GoalsUiState(
    val goals: HealthGoals = HealthGoals(),
    val stored: HealthGoals = HealthGoals(),
    val loading: Boolean = true,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = goals != stored

    /**
     * Derived rather than stored so it cannot fall out of step with [goals]. Recomputed per read, which
     * costs five small objects; they compare equal across recompositions, so Compose still skips the
     * cards that did not change.
     */
    val rows: List<GoalRow> get() = goals.rows()
}

/**
 * The five daily targets — `users/{uid}.goals` (BACKEND_PLAN §7).
 *
 * Goals belong to the account rather than to a day, which is why this reads [UserRepository] and not
 * the metrics one: raising tomorrow's water target must not rewrite what yesterday's card said. The
 * dashboard reads the same five numbers off the hoisted profile, so a save here moves every card on
 * Home and Fitness on the frame Firestore's listener fires.
 */
class GoalsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key the read restarts on. */
    private val uid: Flow<String?> = authRepository.authState
        .map { if (it == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()

    /**
     * What the profile says, or `null` until it has said anything — which is exactly what "loading"
     * means here, so the screen never has to be told separately.
     *
     * Clamped on arrival as well as on the way out: a target written by an older build or typed into
     * the console can sit outside the stepper's range, and showing a number the `−`/`+` could not have
     * produced would make the first tap look like it jumped.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stored: StateFlow<HealthGoals?> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(HealthGoals())
            } else {
                userRepository.observeUser(uid)
                    .map { it?.goals?.clamped() ?: HealthGoals() }
                    .catch { cause ->
                        Log.w("Omni", "users/$uid could not be read for its goals", cause)
                        emit(HealthGoals())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ListenerGraceMillis), null)

    /**
     * `null` until the first `−`/`+`.
     *
     * Before that the page simply mirrors the profile, so there is nothing to seed and nothing to race:
     * the listener's first emission *is* the initial value. After an edit the draft owns the number and
     * the listener can no longer stomp it mid-tap — including the echo of this page's own write, which
     * arrives as an ordinary snapshot a moment after Save.
     */
    private val draft = MutableStateFlow<HealthGoals?>(null)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<GoalsUiState> =
        combine(stored, draft, saving) { stored, draft, saving ->
            GoalsUiState(
                goals = draft ?: stored ?: HealthGoals(),
                stored = stored ?: HealthGoals(),
                loading = stored == null,
                saving = saving,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = GoalsUiState(),
        )

    /** Moves one target by one notch, clamped to what the cards can honestly draw. */
    fun step(kind: GoalKind, up: Boolean) {
        val base = draft.value ?: stored.value ?: return
        draft.value = when (kind) {
            GoalKind.WATER -> base.copy(water = base.water.nudge(WaterGoalStep, up))
            GoalKind.STEPS -> base.copy(steps = base.steps.nudge(StepsGoalStep, up))
            GoalKind.SLEEP -> base.copy(sleepHours = base.sleepHours.nudge(SleepGoalStep, up))
            GoalKind.FIBER -> base.copy(fiberGrams = base.fiberGrams.nudge(FiberGoalStep, up))
            GoalKind.CALORIES -> base.copy(calories = base.calories.nudge(CalorieGoalStep, up))
        }.clamped()
    }

    /**
     * Writes all five, once.
     *
     * One write for the page rather than one per tap: 10,000 steps to 30,000 is forty taps, and forty
     * round-trips for one decision is thirty-nine chances to leave the account on a number nobody chose.
     * Nothing leaves this ViewModel until the button is pressed — the same bargain
     * [com.example.omni.ui.home.SleepEntrySheet] makes with the sleep reading.
     *
     * A failure is logged and leaves [draft] alone, so the page still shows what the user chose and the
     * button goes back to reading "Save Goals" rather than pretending. Offline is not a failure:
     * Firestore applies the write to its cache and re-fires the listener before the round-trip, so the
     * button reads "Saved" in airplane mode too, which is the truth about what the app will do.
     */
    fun save() {
        val state = uiState.value
        if (state.loading || state.saving || !state.dirty) return
        val uid = authRepository.currentUid ?: return

        saving.value = true
        viewModelScope.launch {
            try {
                userRepository.updateGoals(uid, state.goals)
            } catch (cause: Exception) {
                Log.w("Omni", "Saving the daily goals failed", cause)
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * Throws the unsaved edits away.
     *
     * This ViewModel lives on the Activity's store, so leaving the page and coming back does *not*
     * forget a draft — which is the right default (an accidental back press should not cost the edit),
     * but it does mean an abandoned draft would sit there looking like a saved goal. This is the way
     * out, and the reason it is offered only while [GoalsUiState.dirty] is true.
     */
    fun discard() {
        draft.value = null
    }

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L
    }
}

/**
 * The five targets, worded and formatted for the page.
 *
 * The copy says what the number *is for* rather than repeating the unit that is already printed beside
 * it — "Glasses to drink each day" under a row reading "8 glasses" — because the subtitle is the only
 * place the page can explain that these are per-day targets and not today's readings.
 */
private fun HealthGoals.rows(): List<GoalRow> = listOf(
    GoalRow(
        kind = GoalKind.WATER,
        title = "Water",
        subtitle = "Glasses to drink each day",
        value = water.toString(),
        unit = if (water == 1) "glass" else "glasses",
        canDecrease = water > MinWaterGoal,
        canIncrease = water < MaxWaterGoal,
    ),
    GoalRow(
        kind = GoalKind.STEPS,
        title = "Steps",
        subtitle = "Steps to walk each day",
        value = grouped(steps),
        unit = "steps",
        canDecrease = steps > MinStepsGoal,
        canIncrease = steps < MaxStepsGoal,
    ),
    GoalRow(
        kind = GoalKind.SLEEP,
        title = "Sleep",
        subtitle = "Hours to sleep each night",
        value = formatAmount(sleepHours),
        unit = if (sleepHours == 1f) "hour" else "hours",
        canDecrease = sleepHours > MinSleepGoal,
        canIncrease = sleepHours < MaxSleepGoal,
    ),
    GoalRow(
        kind = GoalKind.FIBER,
        title = "Fibre",
        subtitle = "Grams of fibre to eat each day",
        value = formatAmount(fiberGrams),
        unit = "grams",
        canDecrease = fiberGrams > MinFiberGoal,
        canIncrease = fiberGrams < MaxFiberGoal,
    ),
    GoalRow(
        kind = GoalKind.CALORIES,
        title = "Calories",
        subtitle = "Calories to eat each day",
        value = grouped(calories),
        unit = "kcal",
        canDecrease = calories > MinCalorieGoal,
        canIncrease = calories < MaxCalorieGoal,
    ),
)

/**
 * `10,000` — [Locale.US] for the same reason [formatAmount] pins it: the value sits in a fixed row
 * beside two 42dp buttons, and a locale that groups with a space or a full stop would change how wide
 * five digits are.
 */
private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)

/**
 * One notch up or down, snapped back onto the stepper's own grid.
 *
 * The snap is what stops an off-grid value from staying off-grid forever: a console-typed 10,123 would
 * otherwise become 10,623, then 11,123, and every number the user ever saw would end in 23. Every bound
 * in `User.kt` sits on its own grid, so snapping and [HealthGoals.clamped] cannot disagree about where
 * the range ends.
 */
private fun Int.nudge(step: Int, up: Boolean): Int =
    ((this + if (up) step else -step).toFloat() / step).roundToInt() * step

private fun Float.nudge(step: Float, up: Boolean): Float =
    ((this + if (up) step else -step) / step).roundToInt() * step
