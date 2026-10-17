package com.example.omni.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Android AppWidgetReceiver for the Omni Water Tracker widget.
 */
class OmniWaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OmniWaterWidget()
}
