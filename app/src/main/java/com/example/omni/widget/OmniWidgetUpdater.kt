package com.example.omni.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dispatches updates to all active Omni home-screen widgets.
 *
 * Called whenever water or meals are logged (either from the home screen
 * widgets or within the app's ViewModels) to maintain strict data consistency.
 */
object OmniWidgetUpdater {
    private const val TAG = "OmniWidgetUpdater"

    fun updateAllWidgets(context: Context) {
        updateWaterWidgets(context)
        updateFoodWidgets(context)
    }

    fun updateWaterWidgets(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceIds = manager.getGlanceIds(OmniWaterWidget::class.java)
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
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceIds = manager.getGlanceIds(OmniFoodWidget::class.java)
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
