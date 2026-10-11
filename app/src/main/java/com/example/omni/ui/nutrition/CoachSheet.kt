package com.example.omni.ui.nutrition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.R
import com.example.omni.data.model.CoachError
import com.example.omni.data.model.CoachGuidance
import com.example.omni.data.model.CoachState
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.BodyFont
import com.example.omni.ui.theme.NutritionType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.PlusJakartaSans

/**
 * The AI Food Coach's sheet — the same hand-built sheet vocabulary as [AiMealSheet] (grab handle,
 * [OmniSheetSurface], no `ModalBottomSheet`) so it reads as one more Omni sheet, not an "AI feature":
 * no gradient, no chat bubble, no robot.
 *
 * The whole flow is one state machine ([CoachState]) the ViewModel owns — and every rule it renders
 * is the *server's* decision, never the client's: the summary and tips are the backend's, the meter
 * is a server-side counter, and [CoachState.LimitReached] is the free tier's "used up", rendered as
 * the way in to Omni+ rather than as an error. Nothing here computes.
 *
 * @param onSeeOmniPlus routes the limit state to the paywall; the sheet itself never navigates.
 * @param onRetry re-runs a failed session — the same fetch, without the opening state change.
 * @param onDismiss closes the sheet; manual logging is always still available behind it.
 */
@Composable
internal fun CoachSheet(
    visible: Boolean,
    state: CoachState,
    onSeeOmniPlus: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val designWindow = LocalDesignWindow.current
    val maxSheetHeight = (designWindow.heightDp * 0.88f).coerceAtLeast(320.dp)

    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
                .clip(SheetShape)
                .background(OmniSheetSurface)
                .sheetNoRipple(onClick = {})
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = SheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(GrabTop))
            Image(
                painter = painterResource(R.drawable.ic_hosp_grab),
                contentDescription = "Close",
                modifier = Modifier
                    .width(59.dp)
                    .height(5.dp)
                    .sheetNoRipple(onClick = onDismiss),
            )
            Spacer(Modifier.height(HeadingTop))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(animationSpec = OmniMotion.fast<Float>()) + slideInVertically(
                            animationSpec = OmniMotion.fast<IntOffset>(),
                            initialOffsetY = { it / 4 },
                        ) togetherWith fadeOut(animationSpec = OmniMotion.fast<Float>())
                    },
                    label = "coachState",
                ) { target ->
                    when (target) {
                        CoachState.Loading -> LoadingContent()
                        is CoachState.Ready -> ReadyContent(
                            guidance = target.guidance,
                            onDismiss = onDismiss,
                        )
                        CoachState.LimitReached -> LimitContent(
                            onSeeOmniPlus = onSeeOmniPlus,
                            onDismiss = onDismiss,
                        )
                        is CoachState.Failed -> FailedContent(
                            message = target.error.message,
                            onRetry = onRetry,
                            onDismiss = onDismiss,
                        )
                        CoachState.Idle -> LoadingContent()
                    }
                }
            }

            Spacer(Modifier.height(SheetBottomGap))
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LoadingPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = OmniSetApply,
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(LoadingGap))
        Text(
            text = "Reading your week…",
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = (-0.15).sp,
            ),
            color = OmniNutriMacroLabel,
        )
    }
}

/**
 * The redesigned AI Coach result view:
 * 1. Clean header: Title + subtitle paired with the server's focus metric badge.
 * 2. Main result: Formatted summary card with comfortable reading typography.
 * 3. Grouped recommendations: Actionable advice items with accent markers.
 * 4. Meter: Honest daily quota indicator.
 * 5. Action: Full-width comfortable "Got it" button to dismiss.
 */
@Composable
private fun ReadyContent(
    guidance: CoachGuidance,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 1. Header: Section title & subtitle on left, Focus Chip on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "AI Coach",
                    style = NutritionType.SectionTitle,
                    color = OmniSectionTitle,
                    maxLines = 1,
                )
                Spacer(Modifier.height(SubtitleTop))
                Text(
                    text = "Daily personalized guidance",
                    style = TextStyle(
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = (-0.13).sp,
                    ),
                    color = OmniNutriMacroLabel,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(12.dp))

            // The focus chip — the server's own "what needs attention" pick, never the client's.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OmniSetApply.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Focus: ${focusLabel(guidance.focus)}",
                    style = TextStyle(
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = (-0.12).sp,
                    ),
                    color = OmniInk,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(HeaderToSummaryGap))

        // 2. Main Result: Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner16))
                .background(OmniFieldSurface)
                .padding(horizontal = SummaryPaddingH, vertical = SummaryPaddingV),
        ) {
            Text(
                text = guidance.summary,
                style = TextStyle(
                    fontFamily = BodyFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.15).sp,
                ),
                color = OmniCardInk,
            )
        }

        // 3. Actionable Recommendations
        if (guidance.tips.isNotEmpty()) {
            Spacer(Modifier.height(SectionGap))

            Text(
                text = "Key recommendations",
                style = TextStyle(
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = (-0.14).sp,
                ),
                color = OmniSectionTitle,
                maxLines = 1,
            )

            Spacer(Modifier.height(TipListTopGap))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TipItemGap),
            ) {
                guidance.tips.forEach { tip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TipCorner12))
                            .background(OmniFieldSurface.copy(alpha = 0.6f))
                            .padding(horizontal = TipPaddingH, vertical = TipPaddingV),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(OmniSetApply),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = tip,
                            style = TextStyle(
                                fontFamily = BodyFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = (-0.14).sp,
                            ),
                            color = OmniCardInk,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(SectionGap))

        // 4. Session meter
        Text(
            text = if (guidance.limit == null) {
                "Omni+ · unlimited coaching"
            } else {
                "${guidance.used} of ${guidance.limit} free sessions used today"
            },
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.12).sp,
            ),
            color = OmniNutriMacroLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ButtonTopGap))

        // 5. Primary Action Button
        CoachPrimaryButton(
            label = "Got it",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun LimitContent(
    onSeeOmniPlus: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Daily session used up",
            style = NutritionType.SectionTitle,
            color = OmniSectionTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SubtitleTop))
        Text(
            text = "Your daily AI Coach session is used up. Omni+ gives unlimited daily coaching — every day, for every metric.",
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.14).sp,
            ),
            color = OmniNutriMacroLabel,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ButtonTopGap))

        CoachPrimaryButton(
            label = "See Omni+",
            backgroundColor = OmniSetApply,
            textColor = OmniOnInk,
            onClick = onSeeOmniPlus,
        )

        Spacer(Modifier.height(SecondaryButtonGap))

        CoachSecondaryButton(
            label = "Dismiss",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Coaching unavailable",
            style = NutritionType.SectionTitle,
            color = OmniSectionTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SubtitleTop))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner16))
                .background(OmniFieldSurface)
                .padding(horizontal = SummaryPaddingH, vertical = SummaryPaddingV),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = TextStyle(
                    fontFamily = BodyFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.14).sp,
                ),
                color = OmniAlertRed,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(ButtonTopGap))

        CoachPrimaryButton(
            label = "Try again",
            onClick = onRetry,
        )

        Spacer(Modifier.height(SecondaryButtonGap))

        CoachSecondaryButton(
            label = "Dismiss",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun CoachPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = OmniInk,
    textColor: Color = OmniOnInk,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(backgroundColor)
            .pressEffect()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.15).sp,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoachSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(OmniFieldSurface)
            .pressEffect()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.15).sp,
            ),
            color = OmniCardInk,
            maxLines = 1,
        )
    }
}

/** The focus metric's human label — the server's enum, worded for the reader. */
private fun focusLabel(focus: String): String = when (focus) {
    "steps" -> "Steps"
    "sleep" -> "Sleep"
    "water" -> "Water"
    "fiber" -> "Fiber"
    "calories" -> "Calories"
    else -> "Balance"
}

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SheetPadding = 20.dp
private val GrabTop = 8.dp
private val HeadingTop = 16.dp
private val SubtitleTop = 3.dp
private val HeaderToSummaryGap = 16.dp
private val SectionGap = 16.dp
private val TipListTopGap = 10.dp
private val TipItemGap = 8.dp
private val ButtonTopGap = 18.dp
private val SecondaryButtonGap = 10.dp
private val SheetBottomGap = 20.dp
private val LoadingPaddingV = 32.dp
private val LoadingGap = 16.dp
private val SummaryPaddingH = 16.dp
private val SummaryPaddingV = 14.dp
private val TipPaddingH = 14.dp
private val TipPaddingV = 11.dp
private val CardCorner16 = 16.dp
private val TipCorner12 = 12.dp
private val ButtonCorner = 20.dp
private val ButtonHeight = 50.dp

@DevicePreviews
@Composable
private fun CoachSheetReadyPreview() {
    OmniTheme {
        DesignFrame {
            CoachSheet(
                visible = true,
                state = CoachState.Ready(
                    CoachGuidance(
                        summary = "Your steps are trending up while fiber lags behind. Increasing hydration will also support your energy levels.",
                        focus = "fiber",
                        tips = listOf(
                            "Add a pear or apple at breakfast for an easy fiber boost.",
                            "Swap white rice for brown rice or quinoa at lunch.",
                            "Keep your consistent 20-minute morning walk.",
                        ),
                        starter = false,
                        used = 1,
                        limit = 1,
                    ),
                ),
                onSeeOmniPlus = {},
                onRetry = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "Coach Sheet Long Content", showBackground = true, widthDp = 415, heightDp = 920)
@Composable
private fun CoachSheetLongContentPreview() {
    OmniTheme {
        DesignFrame {
            CoachSheet(
                visible = true,
                state = CoachState.Ready(
                    CoachGuidance(
                        summary = "You have made remarkable progress on hydration this week, exceeding your goal on 5 out of 7 days! However, your sleep duration has averaged only 5.8 hours, and your fiber intake has remained below the recommended target. Consistent sleep will help regulate your metabolism and improve recovery.",
                        focus = "sleep",
                        tips = listOf(
                            "Set a consistent wind-down routine starting 30 minutes before bed.",
                            "Limit caffeine consumption after 2:00 PM to improve deep sleep cycles.",
                            "Aim for at least 7.5 hours of sleep to support muscle recovery and daily energy.",
                            "Include a small serving of magnesium-rich almonds or pumpkin seeds with your evening snack.",
                        ),
                        starter = false,
                        used = 3,
                        limit = null,
                    ),
                ),
                onSeeOmniPlus = {},
                onRetry = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "Coach Sheet Limit Reached", showBackground = true, widthDp = 415, heightDp = 920)
@Composable
private fun CoachSheetLimitPreview() {
    OmniTheme {
        DesignFrame {
            CoachSheet(
                visible = true,
                state = CoachState.LimitReached,
                onSeeOmniPlus = {},
                onRetry = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "Coach Sheet Failed", showBackground = true, widthDp = 415, heightDp = 920)
@Composable
private fun CoachSheetFailedPreview() {
    OmniTheme {
        DesignFrame {
            CoachSheet(
                visible = true,
                state = CoachState.Failed(CoachError.Unavailable),
                onSeeOmniPlus = {},
                onRetry = {},
                onDismiss = {},
            )
        }
    }
}
