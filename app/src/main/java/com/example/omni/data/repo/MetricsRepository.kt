package com.example.omni.data.repo

import com.example.omni.data.model.DailyMetrics
import kotlinx.coroutines.flow.Flow

/**
 * The per-day numbers under `users/{uid}/days/{date}` — what the dashboard's stat tiles read.
 *
 * Separate from [UserRepository] because the lifetimes differ: a profile is one document that lives as
 * long as the account, while these are one document per day and the app only ever looks at today's.
 */
interface MetricsRepository {
    /**
     * Emits [date]'s metrics on every change, and `null` while that day has no document — a day with
     * nothing logged, which is the normal state at 00:01. A snapshot listener, so a write made anywhere
     * (this app, another device, the Firebase console) lands without a refresh.
     */
    fun observeDay(uid: String, date: String): Flow<DailyMetrics?>

    /**
     * Every day document in [month] (`yyyy-MM`), keyed by its `yyyy-MM-dd` id.
     *
     * One query rather than 31 [observeDay] listeners, because the caller is the month picker: it draws a
     * whole calendar at once and only needs each day's roll-up, not a live per-day subscription. Days with
     * nothing logged are simply absent from the map — an empty square is the honest reading of "nothing
     * happened", and materialising a zeroed [DailyMetrics] for them would make an untouched month look
     * like a fully logged one whose numbers all came out 0.
     */
    fun observeMonth(uid: String, month: String): Flow<Map<String, DailyMetrics>>

    /**
     * Adds one glass of water to [date], creating the document if it does not exist.
     *
     * Deliberately not `setWater(count)`: two devices doing read-modify-write on the same day would
     * overwrite each other, while two increments both land (BACKEND_PLAN §4 rule 7).
     */
    suspend fun addGlass(uid: String, date: String)

    /**
     * Records [steps] as [date]'s step count, creating the document if it does not exist.
     *
     * Absolute, not an increment, and that is the opposite call from [addGlass] on purpose. A glass is an
     * *event* — the app is the only thing that knows it happened, so every one has to be added. Steps are
     * a *reading*: the device's sensor already holds the day's running total, so the app is republishing a
     * number it can always recompute. Incrementing it would double-count the moment a write were retried
     * or replayed from the offline queue.
     *
     * The cost is that two phones walking the same day overwrite each other rather than summing — last
     * writer wins. That is the honest outcome anyway: the two counts are the same legs, and summing them
     * would report double. Each device keeps its own local truth and the UI shows the larger of the two
     * (see `HomeViewModel`), so the number never goes backwards.
     */
    suspend fun setSteps(uid: String, date: String, steps: Int)

    /**
     * Records how long the user slept on [date], creating the document if it does not exist.
     *
     * Absolute for the same reason as [setSteps] but for a different one again: this is the only metric in
     * the app the user *states* rather than the app observing it, and stating it twice means correcting the
     * first answer, not sleeping twice. Re-opening the sheet therefore overwrites.
     */
    suspend fun setSleepHours(uid: String, date: String, hours: Float)
}
