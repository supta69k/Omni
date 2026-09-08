package com.example.omni.data.local

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The hardware step counter, as a flow.
 *
 * `TYPE_STEP_COUNTER` rather than `TYPE_STEP_DETECTOR` (BACKEND_PLAN §12): the counter is maintained by
 * the sensor hub in low power whether or not this process is alive, so it keeps counting while Omni is
 * closed and the app can catch up on the next reading. The detector only fires per step, to a listener
 * that has to be registered at the time — which would need a foreground service to be worth anything.
 *
 * What it emits is **steps since boot**, not steps today. [StepState.reconcile] is what turns one into
 * the other; nothing in this class knows about days.
 *
 * The registration itself needs no permission, but from API 29 up the OS silently withholds events
 * unless `ACTIVITY_RECOGNITION` has been granted, so callers gate this on the permission rather than
 * waiting for values that never arrive.
 */
class StepCounterSource(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** False on a device with no pedometer — an emulator, or plenty of budget phones. */
    val isAvailable: Boolean get() = stepCounter != null

    /**
     * Raw since-boot counts, one per sensor event.
     *
     * Registering delivers the current value almost immediately, which is what makes a cold start show
     * the real number instead of waiting for the next step. `SENSOR_DELAY_NORMAL` is a hint the step
     * counter's batching largely ignores; in practice events arrive in bursts of a few seconds while
     * walking and not at all while still.
     */
    fun rawCounts(): Flow<Int> {
        val sensor = stepCounter ?: return emptyFlow()
        val manager = sensorManager ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // The value is a float only because the sensor API has one shape for everything;
                    // this one is a whole count.
                    val raw = event.values.firstOrNull()?.toInt() ?: return
                    trySend(raw)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { manager.unregisterListener(listener) }
        }
    }
}

/**
 * An identity for the current boot: the wall-clock instant the device came up.
 *
 * Both terms move together while the device is up, so the difference is constant — and a reboot resets
 * [SystemClock.elapsedRealtime] to zero, which moves it. Compared with a tolerance rather than for
 * equality, because the wall clock can be corrected under us; see [StepState.reconcile].
 */
fun currentBootId(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()
