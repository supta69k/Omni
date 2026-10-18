package com.example.omni.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dispatches updates to all active Omni home-screen widgets.
 *
 * Optimized for zero main-thread overhead:
 * - Checks AppWidgetManager first; does zero work if no widgets are placed.
 * - Debounces rapid successive updates onto a shared IO background scope.
 * - Catches and isolates all launcher exceptions.
 */
object OmniWidgetUpdater {
    private const val TAG = "OmniWidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var waterUpdateJob: Job? = null
    private var foodUpdateJob: Job? = null

    fun hasWaterWidgets(context: Context): Boolean {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, OmniWaterWidgetReceiver::class.java))
            ids != null && ids.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun hasFoodWidgets(context: Context): Boolean {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, OmniFoodWidgetReceiver::class.java))
            ids != null && ids.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun updateAllWidgets(context: Context) {
        updateWaterWidgets(context)
        updateFoodWidgets(context)
    }

    fun updateWaterWidgets(context: Context) {
        val appContext = context.applicationContext
        if (!hasWaterWidgets(appContext)) return

        waterUpdateJob?.cancel()
        waterUpdateJob = scope.launch {
            delay(150L) // Coalesce rapid consecutive updates
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceIds = manager.getGlanceIds(OmniWaterWidget::class.java)
                if (glanceIds.isEmpty()) return@launch
                val widget = OmniWaterWidget()
                glanceIds.forEach { glanceId ->
                    widget.update(appContext, glanceId)
                }
            } catch (e: Exception) {
                Log.d(TAG, "No water widgets to update or update failed", e)
            }
        }
    }

    fun updateFoodWidgets(context: Context) {
        val appContext = context.applicationContext
        if (!hasFoodWidgets(appContext)) return

        foodUpdateJob?.cancel()
        foodUpdateJob = scope.launch {
            delay(150L) // Coalesce rapid consecutive updates
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceIds = manager.getGlanceIds(OmniFoodWidget::class.java)
                if (glanceIds.isEmpty()) return@launch
                val widget = OmniFoodWidget()
                glanceIds.forEach { glanceId ->
                    widget.update(appContext, glanceId)
                }
            } catch (e: Exception) {
                Log.d(TAG, "No food widgets to update or update failed", e)
            }
        }
    }
}
