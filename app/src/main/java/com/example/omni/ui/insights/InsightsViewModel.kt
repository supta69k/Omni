package com.example.omni.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.DailyMetrics
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.MetricsRepository
import com.example.omni.domain.HealthInsights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/**
 * The Health Insights Hub's state: the current month's data, the month before it, and both pushed
 * through the pure [HealthInsights] builder so the screen never computes a number.
 *
 * Two month listeners, not one — the comparison is the feature. Both are snapshot listeners, so a
 * log made anywhere (this phone, another device, the console) moves the deltas without a refresh,
 * exactly as the Nutrition month picker behaves. Goals arrive from the profile in [onGoals] — the
 * same tell-don't-listen rule every other ViewModel's goals follow (`BACKEND_PLAN` §4 rule 3).
 */
class InsightsViewModel(
    authRepository: AuthRepository,
    private val metricsRepository: MetricsRepository,
) : ViewModel() {

    /** The account's goals, as told by [MainActivity] off the session profile. */
    private val goals = MutableStateFlow(HealthInsights.Goals(8, 10_000, 8f, 30f, 2_000))

    fun onGoals(
        waterGoal: Int,
        stepsGoal: Int,
        sleepGoal: Float,
        fiberGoal: Float,
        calorieGoal: Int,
    ) {
        goals.value = HealthInsights.Goals(waterGoal, stepsGoal, sleepGoal, fiberGoal, calorieGoal)
    }

    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * The month pair on screen. Today's month, always — the insights are "this month against last",
     * and there is no browsing to older pairs until a design asks for one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val months: StateFlow<UiState> = combine(uid, goals) { uid, goals -> uid to goals }
        .flatMapLatest { (uid, goals) ->
            if (uid == null) {
                flowOf(UiState())
            } else {
                val currentMonth = YearMonth.now()
                val previousMonth = currentMonth.minusMonths(1)
                combine(
                    metricsRepository.observeMonth(uid, currentMonth.toString())
                        .catch { cause -> emit(monthReadFailed(uid, currentMonth, cause)) },
                    metricsRepository.observeMonth(uid, previousMonth.toString())
                        .catch { cause -> emit(monthReadFailed(uid, previousMonth, cause)) },
                ) { current, previous ->
                    UiState(
                        insights = HealthInsights.build(
                            currentMonth = currentMonth,
                            currentDays = current,
                            previousMonth = previousMonth,
                            previousDays = previous,
                            waterGoal = goals.waterGoal,
                            stepsGoal = goals.stepsGoal,
                            sleepGoal = goals.sleepGoal,
                            fiberGoal = goals.fiberGoal,
                            calorieGoal = goals.calorieGoal,
                        ),
                        loading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val uiState: StateFlow<UiState> = months

    /** A failed month read is an empty month, not a broken page — the same catch the picker uses. */
    private fun monthReadFailed(uid: String, month: YearMonth, cause: Throwable): Map<String, DailyMetrics> {
        android.util.Log.w("OmniInsights", "users/$uid/days for $month could not be read", cause)
        return emptyMap()
    }

    /** The screen's whole render input: the built insights, plus the loading flag for first paint. */
    data class UiState(
        val insights: HealthInsights? = null,
        val loading: Boolean = true,
    )
}
