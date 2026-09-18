package com.example.omni.ui.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.model.todayKey
import com.example.omni.data.model.todayKeyFlow
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.CprGuideId
import com.example.omni.data.repo.GuideProgressRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.data.repo.StepsRepository
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
import kotlin.math.max

/**
 * Everything the dashboard renders, in one object.
 *
 * One state rather than a flow per card: they are read on the same frame by the same page, and a screen
 * that collected five flows would recompose five times for one update.
 *
 * @property stepPermissionNeeded the device has a pedometer but the app has not been allowed to read it.
 *   Distinct from "no sensor", where there is nothing to ask for and therefore nothing to offer.
 * @property fiberGrams today's fibre roll-up. 0 until Phase 6 logs a meal, which is the honest reading.
 * @property cprPercent how far the CPR guide has been read. 0 until Phase 7 can record a step.
 */
data class HomeUiState(
    val glasses: Int = 0,
    val steps: Int = 0,
    val stepPermissionNeeded: Boolean = false,
    val fiberGrams: Float = 0f,
    val sleepHours: Float = 0f,
    val cprPercent: Int = 0,
    val isRefreshing: Boolean = false,
)

/**
 * The dashboard's own numbers — today's `users/{uid}/days/{date}` document, the device's step counter, the
 * CPR guide's progress, and the writes its cards trigger.
 *
 * Held apart from `SessionViewModel`, which owns the profile: the profile is one document for the life of
 * the account, this is a different document every day. Both are created in `MainActivity` and handed down
 * as plain values and lambdas (BACKEND_PLAN §4 rule 3), so [HomeScreen] still renders from its own
 * defaults in `@DevicePreviews`.
 *
 * Goals are deliberately **not** here. They live on the account, so `MainActivity` reads them off the
 * session's `User` and passes them in beside these values — one listener for the profile rather than a
 * second one inside this class.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val metricsRepository: MetricsRepository,
    private val stepsRepository: StepsRepository,
    private val guideProgressRepository: GuideProgressRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key both reads restart on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * Whether `ACTIVITY_RECOGNITION` has been granted, and `null` until `MainActivity` has said.
     *
     * A ViewModel cannot see a permission, so it is told — and told again on every resume, which is what
     * makes granting the permission from the system settings screen start the sensor without a restart.
     * The third state matters: without it the first frame of Home would report the permission missing and
     * flash "step tracking is off" at a user who had granted it months ago.
     */
    private val stepPermission = MutableStateFlow<Boolean?>(null)

    /** Tracks pull-to-refresh state */
    private val isRefreshing = MutableStateFlow(false)

    /**
     * Today's metrics — an empty day rather than `null` while nothing has been logged, so the cards never
     * have to render an absence.
     *
     * Keyed on the session **and** the date. The date part is what makes an app left open past midnight
     * move to the new day's document on its own instead of showing (and adding to) yesterday's.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val metrics: Flow<DailyMetrics> = combine(uid, todayKeyFlow()) { uid, date -> uid to date }
        .distinctUntilChanged()
        .flatMapLatest { (uid, date) ->
            if (uid == null) {
                flowOf(DailyMetrics(date))
            } else {
                metricsRepository.observeDay(uid, date)
                    .map { it ?: DailyMetrics(date) }
                    // The repository closes this flow on a rules rejection, which would otherwise crash
                    // the collector. An empty day is the safe read: the card shows 0 rather than a stale
                    // number, and the log line names the cause.
                    .catch { cause ->
                        Log.w("Omni", "users/$uid/days/$date could not be read", cause)
                        emit(DailyMetrics(date))
                    }
            }
        }

    /**
     * This device's own step count for today.
     *
     * Collected only while the permission is held: on API 29+ the OS withholds sensor events without it,
     * so registering a listener anyway would just hold hardware open for nothing.
     *
     * Keyed on the date as well as the session even though the repository computes today's key itself —
     * that is what resets the tile at midnight for a phone sitting on a table, where no sensor event
     * arrives to trigger the rollover from the other side.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val deviceSteps: Flow<Int> =
        combine(uid, todayKeyFlow(), stepPermission) { uid, date, granted -> Triple(uid, date, granted) }
            .distinctUntilChanged()
            .flatMapLatest { (uid, _, granted) ->
                if (granted != true) {
                    flowOf(0)
                } else {
                    stepsRepository.observeTodaySteps(uid).catch { cause ->
                        Log.w("Omni", "The step counter stopped", cause)
                        emit(0)
                    }
                }
            }

    /**
     * How far the CPR guide has been read.
     *
     * Keyed on the session only, not on the date: a guide is not a daily thing, and re-subscribing at
     * midnight would drop and re-open a listener for no reason.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cprPercent: Flow<Int> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(0)
        } else {
            guideProgressRepository.observePercent(uid, CprGuideId).catch { cause ->
                Log.w("Omni", "users/$uid/guideProgress/$CprGuideId could not be read", cause)
                emit(0)
            }
        }
    }

    /**
     * What Home renders.
     *
     * Steps are the **larger** of the device's live count and the day's stored one, which is the reading
     * "the most steps any of your devices has recorded today". It is what makes three awkward cases behave:
     * a fresh install shows the number the server already has instead of restarting the day at zero; a
     * refused permission still shows whatever another device synced; and the live count overtakes the
     * stored one within seconds of walking, since the sync deliberately lags.
     */
    val uiState: StateFlow<HomeUiState> =
        combine(metrics, deviceSteps, stepPermission, cprPercent, isRefreshing) { day, local, granted, cpr, refreshing ->
            HomeUiState(
                glasses = day.waterGlasses,
                steps = max(day.steps, local),
                stepPermissionNeeded = granted == false && stepsRepository.isAvailable,
                fiberGrams = day.fiberGrams,
                sleepHours = day.sleepHours,
                cprPercent = cpr,
                isRefreshing = refreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = HomeUiState(),
        )

    fun onStepPermission(granted: Boolean) {
        stepPermission.value = granted
    }

    /**
     * Force refresh all data — used by pull-to-refresh on Home.
     * Automatically sets isRefreshing to true and back to false after data loads.
     */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                // Add delay so the indicator is visible for at least 1 second
                kotlinx.coroutines.delay(1500)
                // Force re-emit by touching the flows
                val currentUid = uid.value
                if (currentUid != null) {
                    metricsRepository.observeDay(currentUid, todayKey()).collect { }
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private var lastGlassAt = 0L

    /**
     * One more glass, on today's document.
     *
     * The date is recomputed here rather than read from [uiState], so a tap at 00:00:01 lands on the new
     * day even if the listener has not swapped over yet.
     *
     * Nothing is written optimistically and nothing needs to be: Firestore applies the increment to its
     * local cache and re-fires the listener before the round-trip, so the icon fills on the same frame as
     * the tap, offline included.
     *
     * **The goal is a hard stop, and it is enforced here as well as on the card.** Water is the only
     * number in the app the app itself *causes* — steps come off the pedometer, sleep is stated, meals
     * are logged, and clamping any of those to a target would make the app lie about what happened. A
     * glass exists only because this button was pressed, so at the goal the press does nothing. The card
     * dims its `+` for the same reason, but the check is repeated here because the ceiling is a data
     * rule: `addGlass` is a server-side `FieldValue.increment` and cannot be clamped on the way in, so
     * the last place that can refuse is this method.
     *
     * @param goal the account's water target — passed in rather than observed, because this ViewModel
     *   does not read the profile and the dashboard already holds it (see `User.waterGoal`).
     */
    fun addGlass(goal: Int) {
        val uid = authRepository.currentUid ?: return
        if (uiState.value.glasses >= goal) return
        // Two taps inside a third of a second are a bounced finger, not two glasses. Deliberate double
        // logging still works — people pause longer than this between counted taps.
        val now = SystemClock.elapsedRealtime()
        if (now - lastGlassAt < DoubleTapWindowMillis) return
        lastGlassAt = now

        viewModelScope.launch {
            try {
                metricsRepository.addGlass(uid, todayKey())
            } catch (cause: Exception) {
                // Only reachable when the rules reject the write; an offline write does not fail, it
                // queues. Nothing is shown to the user because nothing was optimistically applied — the
                // card simply never moves.
                Log.w("Omni", "Adding a glass of water failed", cause)
            }
        }
    }

    private companion object {
        /** Outlives a rotation; a backgrounded app stops paying for a listener or the sensor. */
        const val ListenerGraceMillis = 5_000L
        const val DoubleTapWindowMillis = 300L
    }
}
