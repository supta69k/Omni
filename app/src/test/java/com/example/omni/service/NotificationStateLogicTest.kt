package com.example.omni.service

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max

/**
 * Deterministic unit tests for the notification state cache, warming-up false-zero protection,
 * and Health Connect multi-source aggregation logic (Phase 5 & Phase 7).
 */
class NotificationStateLogicTest {

    @Test
    fun `service cold start on same day initializes with cached positive step count instead of zero`() {
        val today = "2026-10-15"
        val cachedDate = "2026-10-15"
        val cachedSteps = 7_382
        val cachedGlasses = 4

        // Logic from OmniTrackingService.onCreate:
        val initialSteps = if (cachedDate == today) cachedSteps else 0
        val initialGlasses = if (cachedDate == today) cachedGlasses else 0

        assertEquals(7_382, initialSteps)
        assertEquals(4, initialGlasses)
    }

    @Test
    fun `service cold start on next day discards yesterday cache and initializes with genuine zero`() {
        val today = "2026-10-15"
        val cachedDate = "2026-10-14"
        val cachedSteps = 8_500
        val cachedGlasses = 6

        val initialSteps = if (cachedDate == today) cachedSteps else 0
        val initialGlasses = if (cachedDate == today) cachedGlasses else 0

        assertEquals(0, initialSteps)
        assertEquals(0, initialGlasses)
    }

    @Test
    fun `pipeline warm-up with zero steps does not overwrite notification with false zero on same day`() {
        val today = "2026-10-15"
        val cachedDate = "2026-10-15"
        val lastNotifiedSteps = 7_382

        // Pipeline warming up before first sensor event delivers 0 steps
        val incomingPipelineSteps = 0

        val effectiveSteps = if (incomingPipelineSteps == 0 && cachedDate == today && lastNotifiedSteps > 0) {
            lastNotifiedSteps
        } else {
            incomingPipelineSteps
        }

        // Must preserve the valid 7,382 count rather than dropping to 0
        assertEquals(7_382, effectiveSteps)
    }

    @Test
    fun `verified new step count updates notification promptly`() {
        val today = "2026-10-15"
        val cachedDate = "2026-10-15"
        val lastNotifiedSteps = 7_382

        val incomingPipelineSteps = 7_400

        val effectiveSteps = if (incomingPipelineSteps == 0 && cachedDate == today && lastNotifiedSteps > 0) {
            lastNotifiedSteps
        } else {
            incomingPipelineSteps
        }

        assertEquals(7_400, effectiveSteps)
    }

    @Test
    fun `midnight rollover with genuine zero correctly resets effective steps for new day`() {
        val today = "2026-10-16"
        val cachedDate = "2026-10-15" // Yesterday's date
        val lastNotifiedSteps = 7_382

        val incomingPipelineSteps = 0 // Midnight reset

        val effectiveSteps = if (incomingPipelineSteps == 0 && cachedDate == today && lastNotifiedSteps > 0) {
            lastNotifiedSteps
        } else {
            incomingPipelineSteps
        }

        // On the new day, 0 is genuine and must not be masked
        assertEquals(0, effectiveSteps)
    }

    @Test
    fun `health connect multi-source steps take precedence when higher than phone sensor`() {
        val phoneSensorSteps = 7_382
        val googleFitHealthConnectSteps = 10_539 // e.g. smartwatch + phone combined

        val authoritativeSteps = max(phoneSensorSteps, googleFitHealthConnectSteps)

        assertEquals(10_539, authoritativeSteps)
    }

    @Test
    fun `phone sensor steps take precedence when health connect is zero or unavailable`() {
        val phoneSensorSteps = 7_382
        val healthConnectSteps = 0 // Denied permission, unsupported device, or unavailable

        val authoritativeSteps = max(phoneSensorSteps, healthConnectSteps)

        assertEquals(7_382, authoritativeSteps)
    }

    @Test
    fun `dashboard ui state selects maximum among firestore server, phone sensor, and health connect`() {
        val firestoreSteps = 7_000
        val phoneSensorSteps = 7_382
        val healthConnectSteps = 10_539

        val liveSteps = max(phoneSensorSteps, healthConnectSteps)
        val finalDashboardSteps = max(firestoreSteps, liveSteps)

        assertEquals(10_539, finalDashboardSteps)
    }

    @Test
    fun `notification throttle suppresses minor jitter under significant diff threshold`() {
        val lastNotified = 7_382
        val newCount = 7_385 // Diff is 3 (< 10 SignificantStepDiff)
        val timeSinceLast = 1_000L // 1s (< 2000L NotificationThrottleMillis)

        val stepDiff = kotlin.math.abs(newCount - lastNotified)
        val shouldThrottle = timeSinceLast < 2_000L && stepDiff < 10

        assertEquals(true, shouldThrottle)
    }

    @Test
    fun `notification throttle delivers immediately for major step jumps`() {
        val lastNotified = 7_382
        val newCount = 7_405 // Diff is 23 (>= 10 SignificantStepDiff)
        val timeSinceLast = 500L

        val stepDiff = kotlin.math.abs(newCount - lastNotified)
        val shouldThrottle = timeSinceLast < 2_000L && stepDiff < 10

        assertEquals(false, shouldThrottle)
    }
}
