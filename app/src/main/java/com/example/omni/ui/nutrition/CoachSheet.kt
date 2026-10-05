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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.R
import com.example.omni.data.model.CoachError
import com.example.omni.data.model.CoachState
import com.example.omni.ui.components.OmniSheetScaffold
import com.example.omni.ui.components.sheetNoRipple
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriMacroLabel
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface

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
    OmniSheetScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                modifier = Modifier.width(59.dp).height(5.dp).sheetNoRipple(onClick = onDismiss),
            )

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
                    is CoachState.Ready -> ReadyContent(guidance = target.guidance)
                    CoachState.LimitReached -> LimitContent(onSeeOmniPlus = onSeeOmniPlus)
                    is CoachState.Failed ->
                        FailedContent(message = target.error.message, onRetry = onRetry)
                    CoachState.Idle -> LoadingContent()
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
            .padding(vertical = ContentGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = OmniSetApply, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(ContentGap))
        Text(
            text = "Reading your week…",
            style = CoachBody,
            color = OmniNutriMacroLabel,
        )
    }
}

@Composable
private fun ReadyContent(guidance: com.example.omni.data.model.CoachGuidance) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        // The focus chip — the server's own "what needs attention" pick, never the client's.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(OmniSetApply.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Focus: ${focusLabel(guidance.focus)}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.12).sp),
                color = OmniInk,
            )
        }

        Text(
            text = guidance.summary,
            style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.15).sp),
            color = OmniCardInk,
        )

        guidance.tips.forEach { tip ->
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "•  ", style = CoachBody, color = OmniSetApply)
                Text(text = tip, style = CoachBody, color = OmniCardInk)
            }
        }

        // The meter, worded for the reader — the server's own counter, rendered honestly: a free
        // user sees their one session of the day; Omni+ sees "unlimited". Nothing is computed here.
        Text(
            text = if (guidance.limit == null) {
                "Omni+ · unlimited coaching"
            } else {
                "${guidance.used} of ${guidance.limit} free sessions used today"
            },
            style = TextStyle(fontSize = 11.sp, letterSpacing = (-0.11).sp),
            color = OmniNutriMacroLabel,
        )
    }
}

@Composable
private fun LimitContent(onSeeOmniPlus: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        Text(
            text = "Your daily AI Coach session is used up",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.16).sp),
            color = OmniCardInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Omni+ gives unlimited daily coaching — every day, for every metric.",
            style = CoachBody,
            color = OmniNutriMacroLabel,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OmniSetApply)
                .clickable(onClick = onSeeOmniPlus)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "See Omni+",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.14).sp),
                color = OmniOnInk,
            )
        }
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        Text(
            text = message,
            style = CoachBody,
            color = OmniAlertRed,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OmniInk)
                .clickable(onClick = onRetry)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.14).sp),
                color = Color.White,
            )
        }
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

private val CoachBody = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = (-0.13).sp)

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SheetPadding = 20.dp
private val GrabTop = 12.dp
private val ContentGap = 12.dp
private val SheetBottomGap = 16.dp
