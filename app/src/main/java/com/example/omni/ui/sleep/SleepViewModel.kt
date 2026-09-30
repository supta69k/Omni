package com.example.omni.ui.sleep

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import com.example.omni.data.model.calculateSleepDuration
import com.example.omni.data.model.todayKeyFlow
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.SleepRepository
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

/**
 * Everything the sleep screen renders, in one object — the same one-state-per-screen shape
 * [com.example.omni.ui.home.HomeViewModel] uses, for the same reason: the cards are read on one frame by
 * one page, and five flows would mean five recompositions for one update.
 *
 * @property selectedDate which night the detail card is showing, as `yyyy-MM-dd`.
 * @property selectedRecord that night's record, or `null` when nothing has been logged for it.
 * @property monthRecords the whole visible month, keyed by date — what the history list draws from.
 * @property weeklyAverageMinutes the mean duration across the last seven logged nights, or 0 with none.
 * @property goalMinutes the account's sleep target in minutes, passed down from the session's `User`.
 * @property editing whether the entry sheet is open, and [editingRecord] what it opened on — `null` for a
 *   new entry, a record for an edit.
 * @property confirmingDelete the date awaiting a delete confirmation, or `null`.
 * @property error a user-facing sentence, never a raw Firebase exception (§16 of the master prompt).
 */
data class SleepUiState(
    val selectedDate: String = "",
    val selectedRecord: SleepRecord? = null,
    val monthRecords: Map<String, SleepRecord> = emptyMap(),
    val visibleMonth: String = "",
    val weeklyAverageMinutes: Int = 0,
    val goalMinutes: Int = 480,
    val editing: Boolean = false,
    val editingRecord: SleepRecord? = null,
    val confirmingDelete: String? = null,
    val error: String? = null,
)

/**
 * The sleep page's reads and writes — one night's detailed record, the month around it, and the saves,
 * edits and deletes the sheet triggers.
 *
 * Held apart from [com.example.omni.ui.home.HomeViewModel] for the reason `NutritionViewModel` is: Home is
 * always today and re-keys at midnight, while this screen's date is whichever night the user picked. A
 * shared ViewModel would have to hold both notions of "the day" at once.
 *
 * The goal is deliberately **not** read here. It lives on the account (`User.sleepGoal`), so `MainActivity`
 * reads it off the session and passes it in — one profile listener rather than a second one in this class
 * (BACKEND_PLAN §4 rule 3).
 *
 * Every read is keyed on [AuthRepository.sessionUid] and nothing is cached across sessions, so signing into
 * a different account restarts every flow rather than showing the previous account's nights.
 */
class SleepViewModel(
    private val authRepository: AuthRepository,
    private val sleepRepository: SleepRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key every read restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * Which night the page is showing. `null` means "follow today", which is what the screen opens on and
     * what keeps an app left open past midnight from stranding the user on yesterday.
     */
    private val picked = MutableStateFlow<String?>(null)

    private val today: Flow<String> = todayKeyFlow()

    /** The date actually read: the pick, or today when there is none. A future date is refused. */
    private val activeDate: Flow<String> = combine(today, picked) { today, pick ->
        pick?.takeIf { it <= today } ?: today
    }.distinctUntilChanged()

    /** The month the history list is showing — the selected night's month unless the user paged away. */
    private val browsing = MutableStateFlow<String?>(null)

    private val visibleMonth: Flow<String> = combine(activeDate, browsing) { date, browse ->
        browse ?: date.take(7)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedRecord: Flow<SleepRecord?> =
        combine(uid, activeDate) { uid, date -> uid to date }
            .distinctUntilChanged()
            .flatMapLatest { (uid, date) ->
                if (uid == null) {
                    flowOf(null)
                } else {
                    sleepRepository.observeDay(uid, date).catch { cause ->
                        // The repository closes this flow on a rules rejection, which would otherwise
                        // crash the collector. An absent night is the safe read.
                        Log.w("Omni", "users/$uid/sleep/$date could not be read", cause)
                        emit(null)
                    }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecords: Flow<Map<String, SleepRecord>> =
        combine(uid, visibleMonth) { uid, month -> uid to month }
            .distinctUntilChanged()
            .flatMapLatest { (uid, month) ->
                if (uid == null) {
                    flowOf(emptyMap())
                } else {
                    sleepRepository.observeMonth(uid, month).catch { cause ->
                        Log.w("Omni", "users/$uid/sleep for $month could not be read", cause)
                        emit(emptyMap())
                    }
                }
            }

    /** The entry sheet's open/closed state and what it opened on — local, not derived from the data. */
    private val editing = MutableStateFlow<EditTarget?>(null)
    private val confirmingDelete = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    /**
     * The account's sleep target in minutes.
     *
     * Told to this class rather than read by it, exactly as Home is told its water goal: the profile has one
     * listener, in `SessionViewModel`, and duplicating it here would mean two.
     */
    private val goalMinutes = MutableStateFlow(DefaultGoalMinutes)

    val uiState: StateFlow<SleepUiState> =
        combine(
            combine(activeDate, selectedRecord, monthRecords, visibleMonth) { date, record, month, shown ->
                Snapshot(date, record, month, shown)
            },
            goalMinutes,
            editing,
            confirmingDelete,
            error,
        ) { snapshot, goal, edit, deleting, message ->
            SleepUiState(
                selectedDate = snapshot.date,
                selectedRecord = snapshot.record,
                monthRecords = snapshot.month,
                visibleMonth = snapshot.visibleMonth,
                weeklyAverageMinutes = weeklyAverage(snapshot.month, snapshot.date),
                goalMinutes = goal,
                editing = edit != null,
                editingRecord = edit?.record,
                confirmingDelete = deleting,
                error = message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = SleepUiState(),
        )

    /** The four date-driven reads bundled, because Kotlin's typed `combine` stops at five. */
    private data class Snapshot(
        val date: String,
        val record: SleepRecord?,
        val month: Map<String, SleepRecord>,
        val visibleMonth: String,
    )

    /** What the sheet is editing: an existing night, or nothing for a new one. */
    private data class EditTarget(val record: SleepRecord?)

    /**
     * The account's target, in hours as the profile stores it.
     *
     * Converted to minutes here so every comparison downstream is in one unit — a progress percentage
     * computed half in hours and half in minutes is the kind of bug that reads as "93%" when it is 93.75%.
     */
    fun onGoalHours(hours: Float) {
        goalMinutes.value = if (hours.isFinite()) (hours * 60).toInt().coerceAtLeast(1) else DefaultGoalMinutes
    }

    fun onSelectDate(date: String) {
        // A night that has not happened yet cannot be logged, and the UI is not trusted to have
        // disabled it — the same guard `NutritionViewModel.onSelectDay` applies.
        if (date > LocalDate.now().toString()) return
        picked.value = date
        browsing.value = null
    }

    fun onBrowseMonth(month: String) {
        browsing.value = month
    }

    /** Opens the sheet on a blank entry for the selected night. */
    fun onAddSleep() {
        error.value = null
        editing.value = EditTarget(null)
    }

    /** Opens the sheet on an existing night, so saving corrects it rather than adding a second one. */
    fun onEditSleep(record: SleepRecord) {
        error.value = null
        editing.value = EditTarget(record)
    }

    fun onDismissSheet() {
        editing.value = null
    }

    /**
     * Writes one night.
     *
     * The duration is computed here rather than taken from the caller, so the stored number and the two
     * times can never disagree: [calculateSleepDuration] is the only thing in the app that turns a pair of
     * clock times into a length, and it is overnight-safe.
     *
     * A save onto a date that already has a record is an **update**, not a second row — the document id is
     * the date, so `set` with the same id corrects the existing night.
     */
    fun onSaveSleep(date: String, bedtime: String, wakeTime: String, quality: SleepQuality, notes: String?) {
        // No session is an auth problem, not a network one — say so rather than staying silent or later
        // blaming the connection. The uid comes from the live session (AuthRepository.sessionUid), never a
        // cached Firebase currentUser, so a save always lands under whoever is signed in right now.
        val uid = authRepository.currentUid
        if (uid == null) {
            error.value = "You're signed out. Sign in to save your sleep."
            return
        }
        val minutes = calculateSleepDuration(bedtime, wakeTime)
        if (minutes <= 0) {
            error.value = "Those times don't make a night's sleep. Check them and try again."
            return
        }

        val existing = uiState.value.monthRecords[date]
        val record = SleepRecord(
            date = date,
            bedtime = bedtime,
            wakeTime = wakeTime,
            durationMinutes = minutes,
            quality = quality,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            // Preserved on an edit so the record keeps the moment it was first logged; the repository
            // fills it with a server timestamp when it is absent.
            createdAt = existing?.createdAt,
        )

        viewModelScope.launch {
            try {
                sleepRepository.saveSleep(uid, record)
                editing.value = null
                error.value = null
            } catch (cause: Exception) {
                // The UI stays friendly (§16), but the log names the real failure so a save that dies on a
                // rules rejection is not mistaken for a dropped connection. `cause` carries the Firestore
                // status (e.g. FirebaseFirestoreException: PERMISSION_DENIED) — a status code, not a secret;
                // never the id token, password or key that would breach §9.
                Log.w(
                    "Omni",
                    "Saving users/$uid/sleep/$date failed: ${cause.javaClass.simpleName}: ${cause.message}",
                    cause,
                )
                error.value = "That didn't save. Check your connection and try again."
            }
        }
    }

    fun onConfirmDelete(date: String) {
        confirmingDelete.value = date
    }

    fun onCancelDelete() {
        confirmingDelete.value = null
    }

    fun onDeleteSleep(date: String) {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                sleepRepository.deleteSleep(uid, date)
                confirmingDelete.value = null
                editing.value = null
                error.value = null
            } catch (cause: Exception) {
                Log.w("Omni", "Deleting sleep for $date failed", cause)
                confirmingDelete.value = null
                error.value = "That didn't delete. Check your connection and try again."
            }
        }
    }

    fun onDismissError() {
        error.value = null
    }

    private companion object {
        /** Outlives a rotation; a backgrounded app stops paying for a listener. Home's own number. */
        const val ListenerGraceMillis = 5_000L
        const val DefaultGoalMinutes = 480
    }
}

/**
 * The mean duration over the seven nights ending on [endDate], counting only nights that were logged.
 *
 * Nights with no record are skipped rather than counted as zero: someone who logged three good nights out
 * of seven slept well on the nights they recorded, and averaging in four zeroes would report that as a
 * crisis. With nothing logged at all the answer is 0, which the UI renders as an empty state rather than
 * a number.
 *
 * Reads from the month map the screen already holds, so a week that straddles a month boundary sees only
 * the part inside the visible month. That is a deliberate simplification: the alternative is a second
 * query for the previous month on every first-week-of-the-month render, and the average is a summary
 * rather than a figure anything depends on.
 */
internal fun weeklyAverage(records: Map<String, SleepRecord>, endDate: String): Int {
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return 0
    val window = (0..6).map { end.minusDays(it.toLong()).toString() }
    val logged = window.mapNotNull { records[it] }.map { it.durationMinutes }
    return if (logged.isEmpty()) 0 else logged.sum() / logged.size
}

/**
 * How much of the goal a night covers, as a whole percentage, capped at 100.
 *
 * Capped because the bar it drives cannot draw past its own end, and "you slept 140% of your goal" is not
 * a thing to congratulate anybody for. The uncapped ratio is still available to any caller that wants it —
 * this is the display figure.
 */
internal fun goalPercent(durationMinutes: Int, goalMinutes: Int): Int {
    if (goalMinutes <= 0) return 0
    return ((durationMinutes * 100f) / goalMinutes).toInt().coerceIn(0, 100)
}

/** The month a `yyyy-MM-dd` key belongs to, as `yyyy-MM`. */
internal fun monthOf(date: String): String = date.take(7)

/** Every day in [month] that has a record, newest first — what the history list iterates. */
internal fun sortedNights(records: Map<String, SleepRecord>): List<SleepRecord> =
    records.values.sortedByDescending { it.date }

/** The month before [month], as `yyyy-MM`. */
internal fun previousMonth(month: String): String =
    runCatching { YearMonth.parse(month).minusMonths(1).toString() }.getOrDefault(month)

/** The month after [month], as `yyyy-MM`. */
internal fun nextMonth(month: String): String =
    runCatching { YearMonth.parse(month).plusMonths(1).toString() }.getOrDefault(month)
