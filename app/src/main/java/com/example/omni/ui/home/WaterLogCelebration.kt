package com.example.omni.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.ui.motion.OmniMotion
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniWaterTeal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium water logging success celebration animation.
 *
 * Appears as a lightweight overlay near the center of the visible screen whenever a glass of water
 * has been successfully persisted. The sequence consists of:
 *
 *  1. **Appear:** The glass smoothly fades in and scales up with an emphasized spring-like ease.
 *  2. **Fill:** Crisp blue/cyan water rises inside the glass with a fluid, gently undulating surface.
 *  3. **Celebrate:** A subtle celebratory bounce accompanies tasteful radiant water droplets and a sparkle accent.
 *  4. **Dismiss:** The glass smoothly fades out and scales down, leaving the dashboard undisturbed.
 *
 * Touch events are NOT consumed: users can scroll or tap again while the animation plays.
 * Rapid repeated water entries restart the surge and celebration gracefully.
 *
 * @param trigger Monotonically updated timestamp or ID of successful water logging events.
 */
@Composable
fun WaterLogCelebrationOverlay(
    trigger: Long,
    modifier: Modifier = Modifier,
    onBlurFractionChange: ((Float) -> Unit)? = null,
) {
    if (trigger <= 0L) {
        onBlurFractionChange?.invoke(0f)
        return
    }

    val progress = remember { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (!OmniMotion.animationsEnabled) {
            // Respect accessibility reduced-motion preference: brief 350ms static confirmation
            isPlaying = true
            progress.snapTo(1f)
            onBlurFractionChange?.invoke(0.5f)
            kotlinx.coroutines.delay(350L)
            onBlurFractionChange?.invoke(0f)
            isPlaying = false
            return@LaunchedEffect
        }

        isPlaying = true
        progress.snapTo(0f)
        try {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AnimationDurationMillis,
                    easing = LinearEasing,
                ),
            ) {
                val p = value
                val fraction = when {
                    p < 0.20f -> (p / 0.20f).coerceIn(0f, 1f)
                    p > 0.82f -> (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    else -> 1f
                }
                onBlurFractionChange?.invoke(fraction)
            }
        } finally {
            onBlurFractionChange?.invoke(0f)
            isPlaying = false
        }
    }

    if (!isPlaying) {
        onBlurFractionChange?.invoke(0f)
        return
    }

    val p = progress.value

    // Background blur & scrim fraction:
    // 0.00..0.20: Smooth fade in (0.0 -> 1.0)
    // 0.20..0.82: Full blur depth (1.0)
    // 0.82..1.00: Smooth fade out (1.0 -> 0.0)
    val blurFraction = when {
        p < 0.20f -> (p / 0.20f).coerceIn(0f, 1f)
        p > 0.82f -> (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)
        else -> 1f
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // TIMING PHASES (normalized 0.0 -> 1.0 over 1700ms)
    // ─────────────────────────────────────────────────────────────────────────────────────────

    // Scale calculation:
    // 0.00..0.22: Entrance scale (0.45 -> 1.03 -> 1.00)
    // 0.58..0.76: Celebratory bounce (1.00 -> 1.06 -> 1.00)
    // 0.82..1.00: Dismissal shrink (1.00 -> 0.88)
    val scale = when {
        p < 0.22f -> {
            val t = p / 0.22f
            0.45f + (1.03f - 0.45f) * FastOutSlowInEasing.transform(t)
        }
        p < 0.35f -> {
            val t = (p - 0.22f) / 0.13f
            1.03f - 0.03f * t
        }
        p in 0.58f..0.76f -> {
            val t = (p - 0.58f) / 0.18f
            1f + 0.06f * sin(t * PI.toFloat())
        }
        p > 0.82f -> {
            val t = (p - 0.82f) / 0.18f
            1f - 0.12f * FastOutSlowInEasing.transform(t)
        }
        else -> 1f
    }

    // Alpha calculation:
    // 0.00..0.18: Fade in (0.0 -> 1.0)
    // 0.18..0.82: Full opacity (1.0)
    // 0.82..1.00: Fade out (1.0 -> 0.0)
    val alpha = when {
        p < 0.18f -> (p / 0.18f).coerceIn(0f, 1f)
        p > 0.82f -> (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)
        else -> 1f
    }

    // Water fill fraction:
    // 0.00..0.12: 0f
    // 0.12..0.62: 0.0 -> 0.82f
    // 0.62..1.00: 0.82f
    val fillFraction = when {
        p < 0.12f -> 0f
        p < 0.62f -> {
            val t = (p - 0.12f) / 0.50f
            0.82f * CubicBezierEasing(0.2f, 0f, 0.2f, 1f).transform(t)
        }
        else -> 0.82f
    }

    // Wave undulation phase
    val wavePhase = p * 6f * PI.toFloat()

    // Wave amplitude: higher while filling, gently dampens once filled
    val waveAmplitudeFactor = when {
        p < 0.12f -> 0f
        p < 0.62f -> 1f
        p < 0.78f -> 1f - (p - 0.62f) / 0.16f * 0.65f
        else -> 0.35f
    }

    // Celebration droplets & sparkles progress (0.0 -> 1.0 during 0.58..0.85)
    val celebrationProgress = if (p in 0.58f..0.86f) {
        (p - 0.58f) / 0.28f
    } else {
        0f
    }

    // Render container (non-blocking overlay)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Translucent background scrim: adds atmospheric depth and serves as fallback on API < 31
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = blurFraction * 0.14f
                }
                .background(Color.Black),
        )

        // Unblurred crisp glass and badge column
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Animated Water Glass Canvas
            Canvas(
                modifier = Modifier.size(CanvasSize),
            ) {
                drawGlassCelebration(
                    fillFraction = fillFraction,
                    wavePhase = wavePhase,
                    waveAmplitudeFactor = waveAmplitudeFactor,
                    celebrationProgress = celebrationProgress,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Elegant celebratory pill badge underneath
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(OmniInk.copy(alpha = 0.90f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Mini water droplet indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(WaterCyanBright),
                )
                Text(
                    text = "+1 Glass Logged",
                    style = HomeType.HeroCaption.copy(
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    ),
                )
            }
        }
    }
}

/**
 * Draws the complete glass silhouette, fluid rising water with waves, bubbles, glass highlights,
 * and celebratory radiant droplets/sparkles.
 */
private fun DrawScope.drawGlassCelebration(
    fillFraction: Float,
    wavePhase: Float,
    waveAmplitudeFactor: Float,
    celebrationProgress: Float,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f + 4.dp.toPx()

    val topWidth = GlassTopWidth.toPx()
    val bottomWidth = GlassBottomWidth.toPx()
    val height = GlassHeight.toPx()
    val cornerRadius = GlassCornerRadius.toPx()

    val topY = centerY - height / 2f
    val bottomY = centerY + height / 2f
    val topHalfW = topWidth / 2f
    val bottomHalfW = bottomWidth / 2f

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // 1. SOFT RADIAL GLOW BEHIND THE GLASS
    // ─────────────────────────────────────────────────────────────────────────────────────────
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                OmniWaterTeal.copy(alpha = 0.35f),
                WaterCyanBright.copy(alpha = 0.12f),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = 82.dp.toPx(),
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // 2. GLASS SILHOUETTE PATHS (OUTER & INNER)
    // ─────────────────────────────────────────────────────────────────────────────────────────
    val glassPath = Path().apply {
        moveTo(centerX - topHalfW, topY)
        lineTo(centerX + topHalfW, topY)
        lineTo(centerX + bottomHalfW, bottomY - cornerRadius)
        quadraticTo(
            centerX + bottomHalfW, bottomY,
            centerX + bottomHalfW - cornerRadius, bottomY,
        )
        lineTo(centerX - bottomHalfW + cornerRadius, bottomY)
        quadraticTo(
            centerX - bottomHalfW, bottomY,
            centerX - bottomHalfW, bottomY - cornerRadius,
        )
        close()
    }

    val wallInset = 2.5.dp.toPx()
    val innerTopHalfW = topHalfW - wallInset
    val innerBottomHalfW = bottomHalfW - wallInset
    val innerBottomY = bottomY - wallInset
    val innerCornerRadius = (cornerRadius - wallInset).coerceAtLeast(2.dp.toPx())

    val innerGlassPath = Path().apply {
        moveTo(centerX - innerTopHalfW, topY + wallInset)
        lineTo(centerX + innerTopHalfW, topY + wallInset)
        lineTo(centerX + innerBottomHalfW, innerBottomY - innerCornerRadius)
        quadraticTo(
            centerX + innerBottomHalfW, innerBottomY,
            centerX + innerBottomHalfW - innerCornerRadius, innerBottomY,
        )
        lineTo(centerX - innerBottomHalfW + innerCornerRadius, innerBottomY)
        quadraticTo(
            centerX - innerBottomHalfW, innerBottomY,
            centerX - innerBottomHalfW, innerBottomY - innerCornerRadius,
        )
        close()
    }

    // Subtle translucent tint of glass body
    drawPath(
        path = glassPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x22FFFFFF),
                Color(0x0EFFFFFF),
                Color(0x28AFDFDF),
            ),
            startY = topY,
            endY = bottomY,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // 3. FLUID WATER FILL WITH UNDULATING SURFACE WAVES
    // ─────────────────────────────────────────────────────────────────────────────────────────
    if (fillFraction > 0.005f) {
        clipPath(innerGlassPath) {
            val usableHeight = (height - 18.dp.toPx())
            val waterSurfaceY = innerBottomY - usableHeight * fillFraction
            val baseAmplitude = 3.6.dp.toPx() * waveAmplitudeFactor
            val wavelength = 46.dp.toPx()

            val waterPath = Path().apply {
                val startX = 0f
                val endX = size.width
                val steps = 24
                val stepWidth = (endX - startX) / steps

                val initialY = waterSurfaceY + baseAmplitude * sin(wavePhase)
                moveTo(startX, initialY)

                for (i in 1..steps) {
                    val x = startX + i * stepWidth
                    // Dual-frequency sine wave for natural organic water motion
                    val waveY = waterSurfaceY +
                        baseAmplitude * sin(2f * PI.toFloat() * (x / wavelength) + wavePhase) +
                        (baseAmplitude * 0.4f) * cos(3.2f * PI.toFloat() * (x / wavelength) + wavePhase * 1.3f)
                    lineTo(x, waveY)
                }

                lineTo(endX, size.height)
                lineTo(startX, size.height)
                close()
            }

            // Liquid vertical gradient
            drawPath(
                path = waterPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        WaterCyanBright,
                        WaterBlueMid,
                        WaterBlueDeep,
                    ),
                    startY = waterSurfaceY - baseAmplitude,
                    endY = bottomY,
                ),
            )

            // Meniscus illuminated wave crest line
            val crestPath = Path().apply {
                val startX = 0f
                val endX = size.width
                val steps = 24
                val stepWidth = (endX - startX) / steps

                val initialY = waterSurfaceY + baseAmplitude * sin(wavePhase)
                moveTo(startX, initialY)

                for (i in 1..steps) {
                    val x = startX + i * stepWidth
                    val waveY = waterSurfaceY +
                        baseAmplitude * sin(2f * PI.toFloat() * (x / wavelength) + wavePhase) +
                        (baseAmplitude * 0.4f) * cos(3.2f * PI.toFloat() * (x / wavelength) + wavePhase * 1.3f)
                    lineTo(x, waveY)
                }
            }

            drawPath(
                path = crestPath,
                color = Color(0xD8FFFFFF),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )

            // Rising miniature bubbles
            if (fillFraction > 0.15f) {
                val bubble1Y = waterSurfaceY + (innerBottomY - waterSurfaceY) * 0.42f + sin(wavePhase * 2f) * 3.dp.toPx()
                val bubble2Y = waterSurfaceY + (innerBottomY - waterSurfaceY) * 0.68f - cos(wavePhase * 1.4f) * 3.5.dp.toPx()
                val bubble3Y = waterSurfaceY + (innerBottomY - waterSurfaceY) * 0.28f + cos(wavePhase * 1.8f) * 2.5.dp.toPx()

                drawCircle(
                    color = Color(0x80FFFFFF),
                    radius = 2.0.dp.toPx(),
                    center = Offset(centerX - 13.dp.toPx(), bubble1Y),
                )
                drawCircle(
                    color = Color(0x65FFFFFF),
                    radius = 2.6.dp.toPx(),
                    center = Offset(centerX + 11.dp.toPx(), bubble2Y),
                )
                drawCircle(
                    color = Color(0x75FFFFFF),
                    radius = 1.6.dp.toPx(),
                    center = Offset(centerX - 2.dp.toPx(), bubble3Y),
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // 4. GLASS OUTLINE & SPECULAR SHEEN HIGHLIGHTS
    // ─────────────────────────────────────────────────────────────────────────────────────────
    // Subtle dark outer contour for crisp contrast on bright backgrounds
    drawPath(
        path = glassPath,
        color = Color(0x35000000),
        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
    )

    // Crisp white glass rim & walls
    drawPath(
        path = glassPath,
        color = Color(0x95FFFFFF),
        style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
    )

    // Specular reflection streak down left wall
    val sheenStartX = centerX - topHalfW + 6.dp.toPx()
    val sheenEndX = centerX - bottomHalfW + 6.dp.toPx()
    drawLine(
        color = Color(0x55FFFFFF),
        start = Offset(sheenStartX, topY + 12.dp.toPx()),
        end = Offset(sheenEndX, bottomY - 16.dp.toPx()),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // Subtle bottom glass thickness curve
    drawLine(
        color = Color(0x40FFFFFF),
        start = Offset(centerX - bottomHalfW + cornerRadius, bottomY - 3.dp.toPx()),
        end = Offset(centerX + bottomHalfW - cornerRadius, bottomY - 3.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // 5. CELEBRATORY ACCENTS (RADIANT DROPLETS & SPARKLES)
    // ─────────────────────────────────────────────────────────────────────────────────────────
    if (celebrationProgress > 0f) {
        val cp = celebrationProgress
        // Droplets fade in quickly then fade out as they reach max radius
        val dropAlpha = sin(cp * PI.toFloat()).coerceIn(0f, 1f)

        // Droplet 1 (Top Left, -120°)
        val dist1 = (10.dp.toPx() + 24.dp.toPx() * cp)
        val drop1Center = Offset(
            centerX + cos(-120f * (PI / 180f).toFloat()) * (topHalfW * 0.7f + dist1),
            topY + sin(-120f * (PI / 180f).toFloat()) * dist1,
        )
        drawCircle(
            color = WaterCyanBright.copy(alpha = dropAlpha * 0.95f),
            radius = (4.0.dp.toPx() - 1.8.dp.toPx() * cp),
            center = drop1Center,
        )

        // Droplet 2 (Top Center, -90°)
        val dist2 = (8.dp.toPx() + 28.dp.toPx() * cp)
        val drop2Center = Offset(
            centerX - 4.dp.toPx(),
            topY - dist2,
        )
        drawCircle(
            color = WaterBlueMid.copy(alpha = dropAlpha),
            radius = (4.8.dp.toPx() - 2.2.dp.toPx() * cp),
            center = drop2Center,
        )

        // Droplet 3 (Top Right, -55°)
        val dist3 = (10.dp.toPx() + 24.dp.toPx() * cp)
        val drop3Center = Offset(
            centerX + cos(-55f * (PI / 180f).toFloat()) * (topHalfW * 0.7f + dist3),
            topY + sin(-55f * (PI / 180f).toFloat()) * dist3,
        )
        drawCircle(
            color = WaterTealAccent.copy(alpha = dropAlpha * 0.95f),
            radius = (3.8.dp.toPx() - 1.8.dp.toPx() * cp),
            center = drop3Center,
        )

        // Sparkle Accent on Upper Right Rim
        val sparkleAlpha = sin(cp * PI.toFloat()).coerceIn(0f, 1f)
        val sparkleX = centerX + topHalfW + 4.dp.toPx()
        val sparkleY = topY - 3.dp.toPx()
        val sparkleSize = (3.dp.toPx() + 5.dp.toPx() * sin(cp * PI.toFloat()))

        // 4-point sparkle star
        drawLine(
            color = Color.White.copy(alpha = sparkleAlpha),
            start = Offset(sparkleX - sparkleSize, sparkleY),
            end = Offset(sparkleX + sparkleSize, sparkleY),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = sparkleAlpha),
            start = Offset(sparkleX, sparkleY - sparkleSize),
            end = Offset(sparkleX, sparkleY + sparkleSize),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Center spark dot
        drawCircle(
            color = WaterCyanBright.copy(alpha = sparkleAlpha),
            radius = 1.5.dp.toPx(),
            center = Offset(sparkleX, sparkleY),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// DESIGN SYSTEM CONSTANTS & COLORS
// ─────────────────────────────────────────────────────────────────────────────────────────────

private const val AnimationDurationMillis = 1_700

private val CanvasSize = 170.dp
private val GlassTopWidth = 72.dp
private val GlassBottomWidth = 54.dp
private val GlassHeight = 98.dp
private val GlassCornerRadius = 10.dp

private val WaterCyanBright = Color(0xFF56CCF2)
private val WaterBlueMid = Color(0xFF2F80ED)
private val WaterBlueDeep = Color(0xFF135AB8)
private val WaterTealAccent = Color(0xFF72EFDD)
