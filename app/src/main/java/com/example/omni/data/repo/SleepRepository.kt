package com.example.omni.data.repo

import com.example.omni.data.model.SleepRecord
import kotlinx.coroutines.flow.Flow

/**
 * Sleep tracking — detailed bedtime/wake/quality/notes records in `users/{uid}/sleep/{yyyy-MM-dd}`.
 *
 * This is the repository triad's interface. [FirestoreSleepRepository] is the real implementation;
 * [PreviewSleepRepository] is the in-memory fake for `@Preview` and tests (adjustment #1: reuse the
 * existing repository pattern, don't invent a new one).
 *
 * The collection holds the authoritative sleep data (adjustment #2: single source of truth). Every save
 * also synchronizes `DailyMetrics.sleepHours` via [MetricsRepository.setSleepHours] so Home's card stays
 * correct, but the detailed record here is the source — `days/{date}.sleepHours` is a derived cache.
 */
interface SleepRepository {

    /**
     * Observes one day's sleep record, or `null` when no sleep has been logged for that date.
     *
     * Hot: starts the Firestore listener immediately. Keyed on [uid] and [date] per adjustment #6 (account
     * isolation) — a new session uid restarts the flow, so data switches immediately on account change.
     *
     * @param uid the user's Firebase uid (from [AuthRepository.sessionUid])
     * @param date the sleep date as `yyyy-MM-dd` — the morning the user woke up
     */
    fun observeDay(uid: String, date: String): Flow<SleepRecord?>

    /**
     * Observes a month's sleep records as a map keyed by date.
     *
     * Hot: starts the Firestore listener immediately. The map is empty when no sleep has been logged in
     * that month. Used by the history view to show a month's worth of nights at once.
     *
     * @param uid the user's Firebase uid
     * @param month the month as `yyyy-MM` (e.g. "2025-01")
     * @return a map of `date → SleepRecord`, where date is `yyyy-MM-dd`
     */
    fun observeMonth(uid: String, month: String): Flow<Map<String, SleepRecord>>

    /**
     * Saves or updates a sleep record.
     *
     * If a record for [date] already exists, it is updated (adjustment #8: one record per date, not
     * multiple). If none exists, a new one is created. Every save also synchronizes
     * `DailyMetrics.sleepHours` (adjustment #2: keep the two in sync so they never drift apart).
     *
     * [durationMinutes] is computed by the caller via [com.example.omni.data.model.calculateSleepDuration]
     * and stored redundantly — the times are the source, the duration is the cached result.
     *
     * @param uid the user's Firebase uid
     * @param record the sleep record to save; [SleepRecord.date] is the document id
     */
    suspend fun saveSleep(uid: String, record: SleepRecord)

    /**
     * Deletes a sleep record.
     *
     * Also clears `DailyMetrics.sleepHours` for that date (sets it to 0) so Home's card reflects the
     * deletion. Idempotent: deleting a date that has no record is a no-op.
     *
     * @param uid the user's Firebase uid
     * @param date the sleep date to delete, as `yyyy-MM-dd`
     */
    suspend fun deleteSleep(uid: String, date: String)
}
