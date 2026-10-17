package com.example.omni.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.omni.MainActivity
import com.example.omni.data.model.todayKey
import com.example.omni.di.AppContainer

/**
 * Handles the "+ Add a glass" action directly from the home screen widget.
 *
 * Enforces the same business rules as the in-app HomeViewModel:
 * 1. Checks authentication (opens sign-in if unauthenticated).
 * 2. Enforces rapid double-tap debouncing.
 * 3. Enforces the daily goal ceiling (stops at goal, preventing accidental over-logging).
 * 4. Persists the increment to Firestore through [MetricsRepository.addGlass].
 * 5. Immediately updates the widget UI and refreshes app state.
 */
class AddWaterActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Enforce double-tap debouncing
        if (!WidgetActionDebounce.canExecute("water_add")) {
            return
        }

        val appContext = context.applicationContext
        AppContainer.ensureInit(appContext)
        val container = AppContainer.current
        val uid = container.authRepository.currentUid

        if (uid == null) {
            // Unauthenticated: launch the app so the user can sign in
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appContext.startActivity(intent)
            return
        }

        try {
            val currentData = OmniWidgetStore.getWaterData(appContext)

            // Hard stop at goal — water is an action-driven event, matching HomeViewModel
            if (currentData.glasses >= currentData.goal) {
                return
            }

            val today = todayKey()
            val newGlasses = currentData.glasses + 1

            // Persist through the authoritative Firestore repository
            container.metricsRepository.addGlass(uid, today)

            // Update local widget cache for instant responsiveness
            OmniWidgetStore.saveWaterData(
                context = appContext,
                uid = uid,
                date = today,
                glasses = newGlasses,
                goal = currentData.goal,
            )

            // Refresh this widget instance and any other widgets on screen
            OmniWaterWidget().update(appContext, glanceId)
            OmniWidgetUpdater.updateWaterWidgets(appContext)
        } catch (e: Exception) {
            Log.w("OmniWidget", "Failed to add glass of water from widget", e)
        }
    }
}
