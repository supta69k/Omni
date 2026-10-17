package com.example.omni.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.omni.MainActivity
import com.example.omni.R

/**
 * Native Android Home-Screen Widget for Water Tracking.
 *
 * Designed with Omni's signature Soft-Bento hydration aesthetic:
 * - Teal palette with responsive light/dark themes
 * - Real-time glass count, daily goal, and progress bar
 * - 1-tap "+ Add a glass" action directly from the home screen
 * - Instant updates with local cache and authoritative Firestore persistence
 */
class OmniWaterWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // Small 2x2
            DpSize(220.dp, 110.dp), // Medium 4x2
            DpSize(260.dp, 180.dp), // Large 4x3+
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val waterData = OmniWidgetStore.getWaterData(context)

        provideContent {
            val size = LocalSize.current
            val isSmall = size.width < 190.dp
            val isLarge = size.height >= 150.dp
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
                    .background(OmniWidgetTheme.waterBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(14.dp),
            ) {
                if (!waterData.isAuthenticated) {
                    UnauthenticatedWaterContent()
                } else if (isSmall) {
                    SmallWaterWidgetContent(waterData)
                } else if (isLarge) {
                    LargeWaterWidgetContent(waterData)
                } else {
                    MediumWaterWidgetContent(waterData)
                }
            }
        }
    }

    @Composable
    private fun SmallWaterWidgetContent(data: WaterWidgetData) {
        val progress = (data.glasses.toFloat() / data.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val atGoal = data.glasses >= data.goal

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            // Header: Water icon + title
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_water_drop),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "Water",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // Main numbers: glasses / goal
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.glasses}",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "/${data.goal} gla",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp, start = 2.dp),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = OmniWidgetTheme.waterProgressFill,
                backgroundColor = OmniWidgetTheme.waterProgressTrack,
            )

            Spacer(GlanceModifier.defaultWeight())

            // "+ Add" action button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .cornerRadius(17.dp)
                    .background(OmniWidgetTheme.waterButtonBackground)
                    .clickable(actionRunCallback<AddWaterActionCallback>()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!atGoal) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        text = if (atGoal) "Goal Met!" else "+ 1 Glass",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterButtonText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun MediumWaterWidgetContent(data: WaterWidgetData) {
        val progress = (data.glasses.toFloat() / data.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val remaining = (data.goal - data.glasses).coerceAtLeast(0)
        val atGoal = data.glasses >= data.goal

        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column: Info & Progress
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_water_drop),
                        contentDescription = null,
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "Water today",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(GlanceModifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${data.glasses}",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "/${data.goal} glasses",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.padding(bottom = 2.dp, start = 3.dp),
                    )
                }

                Spacer(GlanceModifier.height(3.dp))

                Text(
                    text = if (atGoal) "Goal reached, nice work!"
                           else if (remaining == 1) "Keep going, 1 glass left"
                           else "Keep going, $remaining glasses left",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextSecondary,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )

                Spacer(GlanceModifier.height(6.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = OmniWidgetTheme.waterProgressFill,
                    backgroundColor = OmniWidgetTheme.waterProgressTrack,
                )
            }

            Spacer(GlanceModifier.width(16.dp))

            // Right column: Prominent "+ Add a glass" action button
            Box(
                modifier = GlanceModifier
                    .width(115.dp)
                    .height(44.dp)
                    .cornerRadius(22.dp)
                    .background(OmniWidgetTheme.waterButtonBackground)
                    .clickable(actionRunCallback<AddWaterActionCallback>()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!atGoal) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        text = if (atGoal) "Goal Met!" else "Add glass",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterButtonText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun LargeWaterWidgetContent(data: WaterWidgetData) {
        val progress = (data.glasses.toFloat() / data.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val remaining = (data.goal - data.glasses).coerceAtLeast(0)
        val atGoal = data.glasses >= data.goal

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_water_drop),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "Omni Hydration",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            // Big Numbers
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.glasses}",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "/${data.goal} glasses today",
                    style = TextStyle(
                        color = OmniWidgetTheme.waterTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = 4.dp, start = 4.dp),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = if (atGoal) "Daily water goal reached, fantastic!"
                       else "$remaining glasses left to reach your ${data.goal}-glass target",
                style = TextStyle(
                    color = OmniWidgetTheme.waterTextSecondary,
                    fontSize = 12.sp,
                ),
            )

            Spacer(GlanceModifier.height(10.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = OmniWidgetTheme.waterProgressFill,
                backgroundColor = OmniWidgetTheme.waterProgressTrack,
            )

            Spacer(GlanceModifier.defaultWeight())

            // Prominent button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .cornerRadius(22.dp)
                    .background(OmniWidgetTheme.waterButtonBackground)
                    .clickable(actionRunCallback<AddWaterActionCallback>()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!atGoal) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Text(
                        text = if (atGoal) "Goal Reached" else "+ Add a glass of water",
                        style = TextStyle(
                            color = OmniWidgetTheme.waterButtonText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun UnauthenticatedWaterContent() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.auth_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = "Sign in to Omni",
                style = TextStyle(
                    color = OmniWidgetTheme.waterTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Tap to open app and track water",
                style = TextStyle(
                    color = OmniWidgetTheme.waterTextSecondary,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}
