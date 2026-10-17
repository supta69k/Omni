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
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import com.example.omni.data.model.Meal
import kotlin.math.roundToInt

/**
 * Native Android Home-Screen Widget for Food & Nutrition Logging.
 *
 * Implements Omni's Soft-Bento food logging experience:
 * - Real-time calorie totals and macro breakdown (Protein, Carbs, Fat)
 * - 1-tap quick logging of recent meals directly from the home screen
 * - Prominent "+ Log meal" action opening the lightweight in-app entry sheet
 * - Transparent offline-first synchronization using authoritative repositories
 */
class OmniFoodWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 110.dp), // Small 2x2
            DpSize(240.dp, 110.dp), // Medium 4x2
            DpSize(260.dp, 180.dp), // Large 4x3+
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val foodData = OmniWidgetStore.getFoodData(context)

        provideContent {
            val size = LocalSize.current
            val isSmall = size.width < 220.dp
            val isLarge = size.height >= 150.dp

            val openNutritionAction = actionStartActivity<MainActivity>(
                actionParametersOf(
                    ActionParameters.Key<String>(MainActivity.EXTRA_DESTINATION) to MainActivity.DESTINATION_NUTRITION
                )
            )

            val logMealAction = actionStartActivity<MainActivity>(
                actionParametersOf(
                    ActionParameters.Key<String>(MainActivity.EXTRA_DESTINATION) to MainActivity.DESTINATION_NUTRITION,
                    ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_LOG_MEAL) to true,
                )
            )

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
                    .background(OmniWidgetTheme.foodBackground)
                    .clickable(openNutritionAction)
                    .padding(14.dp),
            ) {
                if (!foodData.isAuthenticated) {
                    UnauthenticatedFoodContent()
                } else if (isSmall) {
                    SmallFoodWidgetContent(foodData, logMealAction)
                } else if (isLarge) {
                    LargeFoodWidgetContent(foodData, logMealAction)
                } else {
                    MediumFoodWidgetContent(foodData, logMealAction)
                }
            }
        }
    }

    @Composable
    private fun SmallFoodWidgetContent(data: FoodWidgetData, logMealAction: androidx.glance.action.Action) {
        val progress = (data.calories.toFloat() / data.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val remaining = (data.calorieGoal - data.calories).coerceAtLeast(0)

        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_food),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "Food Log",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // Main numbers
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.calories}",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "/${data.calorieGoal} cal",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp, start = 2.dp),
                )
            }

            Spacer(GlanceModifier.height(2.dp))

            Text(
                text = if (remaining == 0) "Daily goal reached" else "$remaining cal left",
                style = TextStyle(
                    color = OmniWidgetTheme.foodTextMuted,
                    fontSize = 10.sp,
                ),
            )

            Spacer(GlanceModifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = OmniWidgetTheme.foodProgressFill,
                backgroundColor = OmniWidgetTheme.foodProgressTrack,
            )

            Spacer(GlanceModifier.defaultWeight())

            // "+ Log" button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .cornerRadius(16.dp)
                    .background(OmniWidgetTheme.foodButtonBackground)
                    .clickable(logMealAction),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "Log meal",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodButtonText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun MediumFoodWidgetContent(data: FoodWidgetData, logMealAction: androidx.glance.action.Action) {
        val progress = (data.calories.toFloat() / data.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val remaining = (data.calorieGoal - data.calories).coerceAtLeast(0)
        val firstRecent = data.recentMeals.firstOrNull()

        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column: Calories and Macros
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_food),
                        contentDescription = null,
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "Food Log",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(GlanceModifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${data.calories}",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "/${data.calorieGoal} kcal",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.padding(bottom = 2.dp, start = 2.dp),
                    )
                }

                Spacer(GlanceModifier.height(2.dp))

                Text(
                    text = if (remaining == 0) "Daily target met" else "$remaining kcal remaining",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextMuted,
                        fontSize = 11.sp,
                    ),
                )

                Spacer(GlanceModifier.height(4.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = OmniWidgetTheme.foodProgressFill,
                    backgroundColor = OmniWidgetTheme.foodProgressTrack,
                )

                Spacer(GlanceModifier.height(6.dp))

                // Macro summary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MacroBadge(text = "${data.protein.roundToInt()}g P", color = OmniWidgetTheme.proteinBadge)
                    Spacer(GlanceModifier.width(4.dp))
                    MacroBadge(text = "${data.carbs.roundToInt()}g C", color = OmniWidgetTheme.carbsBadge)
                    Spacer(GlanceModifier.width(4.dp))
                    MacroBadge(text = "${data.fat.roundToInt()}g F", color = OmniWidgetTheme.fatBadge)
                }
            }

            Spacer(GlanceModifier.width(12.dp))

            // Right column: Quick Log or "+ Log meal"
            Column(
                modifier = GlanceModifier.width(135.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (firstRecent != null) {
                    // 1-tap quick log of recent meal
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(12.dp)
                            .background(OmniWidgetTheme.foodPillBackground)
                            .padding(8.dp),
                    ) {
                        Column {
                            Text(
                                text = "Recent: ${firstRecent.name}",
                                style = TextStyle(
                                    color = OmniWidgetTheme.foodTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = "${firstRecent.calories} cal",
                                style = TextStyle(
                                    color = OmniWidgetTheme.foodTextMuted,
                                    fontSize = 10.sp,
                                ),
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .cornerRadius(12.dp)
                                    .background(OmniWidgetTheme.foodButtonBackground)
                                    .clickable(
                                        actionRunCallback<QuickLogMealActionCallback>(
                                            QuickLogMealActionCallback.parametersFor(firstRecent)
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+ Quick Log",
                                    style = TextStyle(
                                        color = OmniWidgetTheme.foodButtonText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(GlanceModifier.height(6.dp))

                    Text(
                        text = "+ Log new meal",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.clickable(logMealAction),
                    )
                } else {
                    // No recent meal yet — standard prominent log button
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .cornerRadius(22.dp)
                            .background(OmniWidgetTheme.foodButtonBackground)
                            .clickable(logMealAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_add),
                                contentDescription = null,
                                modifier = GlanceModifier.size(14.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "Log meal",
                                style = TextStyle(
                                    color = OmniWidgetTheme.foodButtonText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LargeFoodWidgetContent(data: FoodWidgetData, logMealAction: androidx.glance.action.Action) {
        val progress = (data.calories.toFloat() / data.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val remaining = (data.calorieGoal - data.calories).coerceAtLeast(0)

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
                    provider = ImageProvider(R.drawable.ic_widget_food),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "Omni Food & Nutrition",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            // Main stats row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${data.calories}",
                            style = TextStyle(
                                color = OmniWidgetTheme.foodTextPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = "/${data.calorieGoal} kcal",
                            style = TextStyle(
                                color = OmniWidgetTheme.foodTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = GlanceModifier.padding(bottom = 3.dp, start = 3.dp),
                        )
                    }
                    Text(
                        text = if (remaining == 0) "Daily calorie goal reached"
                               else "$remaining kcal remaining today",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodTextMuted,
                            fontSize = 11.sp,
                        ),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MacroBadge(text = "${data.protein.roundToInt()}g P", color = OmniWidgetTheme.proteinBadge)
                    Spacer(GlanceModifier.width(4.dp))
                    MacroBadge(text = "${data.carbs.roundToInt()}g C", color = OmniWidgetTheme.carbsBadge)
                    Spacer(GlanceModifier.width(4.dp))
                    MacroBadge(text = "${data.fat.roundToInt()}g F", color = OmniWidgetTheme.fatBadge)
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = OmniWidgetTheme.foodProgressFill,
                backgroundColor = OmniWidgetTheme.foodProgressTrack,
            )

            Spacer(GlanceModifier.height(10.dp))

            // Quick re-log section for recent meals
            if (data.recentMeals.isNotEmpty()) {
                Text(
                    text = "1-Tap Quick Log",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))

                data.recentMeals.take(2).forEach { meal ->
                    QuickMealRow(meal)
                    Spacer(GlanceModifier.height(4.dp))
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // Primary "+ Log new meal" button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .cornerRadius(19.dp)
                    .background(OmniWidgetTheme.foodButtonBackground)
                    .clickable(logMealAction),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = null,
                        modifier = GlanceModifier.size(14.dp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "+ Log new meal in Omni",
                        style = TextStyle(
                            color = OmniWidgetTheme.foodButtonText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun QuickMealRow(meal: Meal) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(OmniWidgetTheme.foodPillBackground)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = meal.name,
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = "${meal.calories} cal • ${meal.slot.label}",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodTextMuted,
                        fontSize = 10.sp,
                    ),
                )
            }

            Box(
                modifier = GlanceModifier
                    .height(24.dp)
                    .cornerRadius(12.dp)
                    .background(OmniWidgetTheme.foodButtonBackground)
                    .padding(horizontal = 10.dp)
                    .clickable(
                        actionRunCallback<QuickLogMealActionCallback>(
                            QuickLogMealActionCallback.parametersFor(meal)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+ Log",
                    style = TextStyle(
                        color = OmniWidgetTheme.foodButtonText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }

    @Composable
    private fun MacroBadge(text: String, color: androidx.glance.unit.ColorProvider) {
        Box(
            modifier = GlanceModifier
                .cornerRadius(6.dp)
                .background(color)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = OmniWidgetTheme.foodButtonText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Composable
    private fun UnauthenticatedFoodContent() {
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
                    color = OmniWidgetTheme.foodTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Tap to open app and track meals",
                style = TextStyle(
                    color = OmniWidgetTheme.foodTextSecondary,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}
