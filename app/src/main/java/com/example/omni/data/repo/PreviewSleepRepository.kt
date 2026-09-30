package com.example.omni.data.repo

import com.example.omni.data.model.SleepQuality
import com.example.omni.data.model.SleepRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory sleep repository for `@Preview` and tests.
 *
 * Starts with one plausible night (last night: 11 PM to 7 AM, GOOD quality, 8 hours) so a preview that
 * binds live data renders something sensible without a Firebase project. Saves and deletes really do
 * mutate the in-memory map, which makes interactive previews and unit tests work.
 */
class PreviewSleepRepository : SleepRepository {

    private val storage = MutableStateFlow(
        mapOf(
            PreviewDate to SleepRecord(
                date = PreviewDate,
                bedtime = "23:00",
                wakeTime = "07:00",
                durationMinutes = 480,  // 8 hours
                quality = SleepQuality.GOOD,
                notes = null,
            )
        )
    )

    override fun observeDay(uid: String, date: String): Flow<SleepRecord?> =
        storage.map { it[date] }

    override fun observeMonth(uid: String, month: String): Flow<Map<String, SleepRecord>> =
        storage.map { allRecords ->
            allRecords.filterKeys { it.startsWith(month) }
        }

    override suspend fun saveSleep(uid: String, record: SleepRecord) {
        storage.update { it + (record.date to record) }
    }

    override suspend fun deleteSleep(uid: String, date: String) {
        storage.update { it - date }
    }

    private companion object {
        // A plausible "today" for previews — matches what the design shows
        const val PreviewDate = "2025-01-15"
    }
}
