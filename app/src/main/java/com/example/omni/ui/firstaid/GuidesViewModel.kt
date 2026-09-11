package com.example.omni.ui.firstaid

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Guide
import com.example.omni.data.model.GuideSeverity
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.GuideProgressRepository
import com.example.omni.data.repo.GuideRepository
import com.example.omni.data.repo.percentOf
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

/**
 * One row of the guide list — the guide, flattened to what the card draws.
 *
 * Flattened rather than passing the [Guide] itself plus a percentage, because the card must not be able
 * to reach the steps: a list row that could read `guide.steps[0].detail` is a row that will eventually
 * try to render it.
 */
data class GuideCardState(
    val id: String,
    val title: String,
    val category: String,
    val severity: GuideSeverity,
    val stepCount: Int,
    val percent: Int,
)

/**
 * The open guide and how far through it the user is.
 *
 * The full [Guide] here, unlike the list rows: the detail screen is the one place that legitimately
 * renders every step.
 */
data class OpenGuideState(
    val guide: Guide,
    val completed: Set<Int>,
    val percent: Int,
)

/**
 * Everything the first-aid pages render.
 *
 * [open] is what makes the list and the detail one destination rather than two: the router owns
 * "the user is in first aid", this owns "and is reading the choking guide". Keeping it here rather than
 * in `MainActivity` is what survives a rotation, and it is also the key the completed-step listener is
 * built on — a selection that drives a query belongs beside the query.
 *
 * @property matches the guides the search box is showing. Every guide when the query is blank.
 * @property guideCount how many guides the bundle holds, regardless of the query — the "7 guides" in the
 *   header, which must not shrink as the user types.
 */
data class GuidesUiState(
    val query: String = "",
    val matches: List<GuideCardState> = emptyList(),
    val guideCount: Int = 0,
    val totalSteps: Int = 0,
    val open: OpenGuideState? = null,
)

/**
 * The first-aid guides — bundled content joined to per-user progress (BACKEND_PLAN §7).
 *
 * The two halves come from different places on purpose. The content is in the APK
 * ([GuideRepository]), so it is read synchronously in the constructor and never awaited; the progress is
 * in Firestore ([GuideProgressRepository]), so it arrives as a flow and starts empty. That split is what
 * makes the acceptance criterion hold: with the device in airplane mode from a fresh install, every
 * guide still opens and every step still ticks, because the only thing the network was ever needed for
 * was syncing a tick that Firestore has already applied locally.
 *
 * Search is in-memory for the same reason — a dozen guides is a `filter`, not a query.
 */
class GuidesViewModel(
    private val authRepository: AuthRepository,
    private val guideRepository: GuideRepository,
    private val progressRepository: GuideProgressRepository,
) : ViewModel() {

    /** Read once: the bundle cannot change while the app is running. */
    private val guides: List<Guide> = guideRepository.list()

    /** `null` whenever nobody is signed in — the key every read restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    private val query = MutableStateFlow("")

    private val openId = MutableStateFlow<String?>(null)

    /**
     * Every guide's percentage, on one collection listener rather than one per row.
     *
     * Twelve document listeners for twelve rows would each cost a registration and each fire
     * independently, recomposing the list a dozen times for one sync.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val percents: Flow<Map<String, Int>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyMap())
        } else {
            progressRepository.observeAllPercents(uid).catch { cause ->
                Log.w("Omni", "users/$uid/guideProgress could not be read", cause)
                emit(emptyMap())
            }
        }
    }

    /**
     * The open guide's finished steps.
     *
     * Keyed on the open guide as well as the session, so closing one guide and opening another swaps the
     * listener instead of holding both. Nothing open means no listener at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val completedSteps: Flow<Set<Int>> =
        combine(uid, openId) { uid, guideId -> uid to guideId }
            .distinctUntilChanged()
            .flatMapLatest { (uid, guideId) ->
                if (uid == null || guideId == null) {
                    flowOf(emptySet())
                } else {
                    progressRepository.observeCompletedSteps(uid, guideId).catch { cause ->
                        Log.w("Omni", "users/$uid/guideProgress/$guideId could not be read", cause)
                        emit(emptySet())
                    }
                }
            }

    val uiState: StateFlow<GuidesUiState> =
        combine(query, percents, openId, completedSteps) { query, percents, openId, completed ->
            val open = guides.firstOrNull { it.id == openId }
            GuidesUiState(
                query = query,
                matches = guides.filter { it.matches(query) }.map { guide ->
                    GuideCardState(
                        id = guide.id,
                        title = guide.title,
                        category = guide.category,
                        severity = guide.severity,
                        stepCount = guide.steps.size,
                        percent = percents[guide.id] ?: 0,
                    )
                },
                guideCount = guides.size,
                totalSteps = guideRepository.totalSteps,
                open = open?.let {
                    OpenGuideState(
                        guide = it,
                        completed = completed,
                        // Derived from the set rather than read from [percents], so the ring on the
                        // detail screen and the ticks beside it can never disagree by a frame.
                        percent = percentOf(completed, it.steps.size),
                    )
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = GuidesUiState(
                matches = guides.map {
                    GuideCardState(it.id, it.title, it.category, it.severity, it.steps.size, 0)
                },
                guideCount = guides.size,
                totalSteps = guideRepository.totalSteps,
            ),
        )

    fun onQueryChange(value: String) {
        // Clamped where it enters rather than where it is drawn: the search field is one line in a
        // 415dp frame, and the filter below has nothing to gain from the rest of a pasted paragraph.
        query.value = value.take(MaxQueryLength)
    }

    fun openGuide(id: String) {
        openId.value = id
    }

    /** Returns whether anything was open, so the caller can fall through to its own back behaviour. */
    fun closeGuide(): Boolean {
        val wasOpen = openId.value != null
        openId.value = null
        return wasOpen
    }

    /**
     * Ticks or un-ticks one step of the open guide.
     *
     * The new set is computed here, from the set the listener last emitted, and written whole together
     * with the percentage it implies — see [GuideProgressRepository.setCompletedSteps] for why that is
     * the right shape rather than an `arrayUnion` or a transaction.
     *
     * Nothing is applied optimistically and nothing needs to be: Firestore updates its local cache and
     * re-fires the listener before the round-trip, so the row ticks on the same frame as the tap, with
     * the device offline included.
     */
    fun toggleStep(order: Int) {
        val open = uiState.value.open ?: return
        val uid = authRepository.currentUid ?: return
        val next = if (order in open.completed) open.completed - order else open.completed + order
        viewModelScope.launch {
            try {
                progressRepository.setCompletedSteps(
                    uid = uid,
                    guideId = open.guide.id,
                    completed = next,
                    percent = percentOf(next, open.guide.steps.size),
                )
            } catch (cause: Exception) {
                // Only reachable when the rules reject the write; an offline write does not fail, it
                // queues. Nothing is shown, because nothing was optimistically applied — the row simply
                // never ticks.
                Log.w("Omni", "Marking step $order of ${open.guide.id} failed", cause)
            }
        }
    }

    private companion object {
        /** Outlives a rotation; a backgrounded app stops paying for the progress listeners. */
        const val ListenerGraceMillis = 5_000L

        /** Longer than the longest guide title, and far longer than anyone types into a filter. */
        const val MaxQueryLength = 40

        /**
         * Matches on the title, the category and the step titles — the three things a user searching a
         * first-aid app types. Not the step bodies: "call an ambulance" appears in most of them, so
         * matching prose would return everything and teach the user the search is useless.
         */
        fun Guide.matches(query: String): Boolean {
            val needle = query.trim()
            if (needle.isEmpty()) return true
            return title.contains(needle, ignoreCase = true) ||
                category.contains(needle, ignoreCase = true) ||
                steps.any { it.title.contains(needle, ignoreCase = true) }
        }
    }
}
