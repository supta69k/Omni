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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow

/**
 * The hardware step counter, as a flow.
 *
 * `TYPE_STEP_COUNTER` rather than `TYPE_STEP_DETECTOR` (BACKEND_PLAN §12): the counter is maintained by
 * the sensor hub in low power whether or not this process is alive, so it keeps counting while Omni is
 * closed and the app can catch up on the next reading.
 *
 * Prefers the wake-up variant of the sensor when available (`getDefaultSensor(type, true)`), falling
 * back to the default sensor. Wake-up sensors ensure hardware FIFO events are delivered even when
 * the application processor is asleep in the user's pocket.
 *
 * Registers with 5-second max report latency (`5_000_000` us) to allow the hardware sensor hub to batch
 * readings in FIFO, waking the CPU at most once every few seconds while walking to conserve battery.
 */
class StepCounterSource(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** False on a device with no pedometer — an emulator, or plenty of budget phones. */
    val isAvailable: Boolean get() = stepCounter != null

    /**
     * Raw since-boot counts, one per sensor event.
     *
     * Registering delivers the current value almost immediately, which is what makes a cold start show
     * the real number instead of waiting for the next step.
     */
    fun rawCounts(): Flow<Int> {
        val sensor = stepCounter ?: return emptyFlow()
        val manager = sensorManager ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val raw = event.values.firstOrNull()?.toInt() ?: return
                    trySend(raw)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = manager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                BatchLatencyMicros,
            )
            if (!registered) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()
    }

    private companion object {
        const val BatchLatencyMicros = 5_000_000 // 5 seconds
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
