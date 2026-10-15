package com.example.omni.data.local

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads aggregated daily step counts from Health Connect when available.
 *
 * Health Connect aggregates step records from **every source** that writes them — Google Fit,
 * Samsung Health, wearables, the phone's own sensor — and deduplicates overlapping time ranges
 * automatically. This is why Google Fit can show more steps than Omni's phone-only hardware
 * counter: it includes data from all synced sources. Reading from Health Connect gives Omni the
 * same aggregated total.
 *
 * **Availability**:
 *  - Requires API 28+ (Android 9). On 26–27 [isAvailable] is false and [observeAggregatedSteps]
 *    emits 0 forever.
 *  - Requires the Health Connect app to be installed (built-in on Android 14+, a Play Store
 *    download on 9–13).
 *  - Requires the user to grant the `READ_STEPS` Health Connect permission at runtime. When denied,
 *    the flow emits 0 and the sensor pipeline remains the sole source — no feature is lost.
 *
 * This source is **supplementary**: it is combined with the sensor pipeline's total via `max()`
 * by whoever displays the count. It does not replace the sensor pipeline, which is still needed
 * for real-time step events and for the offline-first, no-permission-required fallback.
 */
class HealthConnectStepsSource(private val context: Context) {

    /**
     * True when Health Connect is available on this device. A false here means the sensor pipeline
     * is the only source, and all HC-related UI (if any) should be hidden.
     */
    val isAvailable: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@lazy false
        try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    /** The single permission this source needs. */
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    /**
     * Whether the app has been granted the Health Connect `READ_STEPS` permission.
     *
     * Suspend because the permission check is an IPC to the Health Connect provider. Returns false
     * when Health Connect is unavailable.
     */
    suspend fun hasPermission(): Boolean {
        if (!isAvailable) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            requiredPermissions.all { it in granted }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Today's aggregated step total from Health Connect, re-queried every [PollIntervalMillis].
     *
     * Health Connect does not offer a real-time listener — it is a pull API — so this flow polls.
     * The interval is generous (60 seconds) because the sensor pipeline already provides real-time
     * updates; this is the supplementary number that closes the gap with Google Fit, not the live
     * counter the notification needs for every step.
     *
     * Emits 0 when Health Connect is unavailable, permission is denied, or a query fails. Callers
     * combine this with the sensor total via `max()`, so a 0 simply means the sensor is the sole
     * source — no feature is lost.
     */
    fun observeAggregatedSteps(): Flow<Int> = flow {
        if (!isAvailable) {
            emit(0)
            return@flow
        }
        while (true) {
            emit(queryTodaySteps())
            delay(PollIntervalMillis)
        }
    }

    /**
     * One-shot query: today's total steps from Health Connect.
     *
     * Uses [AggregateRequest] which automatically deduplicates overlapping records from different
     * sources, returning the same number Health Connect's own UI (and Google Fit) would show.
     */
    private suspend fun queryTodaySteps(): Int {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!requiredPermissions.all { it in granted }) return 0

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val startOfDay = today.atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant()
            val endOfDay = today.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant()

            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                ),
            )
            (response[StepsRecord.COUNT_TOTAL] ?: 0L).toInt().coerceAtLeast(0)
        } catch (cause: Exception) {
            Log.w(TAG, "Health Connect step query failed", cause)
            0
        }
    }

    private companion object {
        const val TAG = "HealthConnect"
        /**
         * How often to re-query Health Connect. Generous because the sensor pipeline handles
         * real-time updates; this is the supplementary sync with Google Fit's aggregated data.
         */
        const val PollIntervalMillis = 60_000L
    }
}
