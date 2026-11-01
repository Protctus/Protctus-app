package com.protctus.app.ui.screens.education.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protctus.app.ui.screens.education.theme.EduColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Protctus Sentinel — Premium Cyber Guardian Character
 *
 * A detailed, professional AI cyber guardian that gamifies
 * cybersecurity awareness. Features:
 * - Hexagonal shield body with circuit-line accents
 * - Visor/screen face with expressive LED eyes
 * - Signal antenna on top
 * - Body armor details
 * - Multiple emotional states for quiz feedback
 *
 * STRICT: All colors from EduColors → Color.kt. No Color(). No hex.
 */

enum class MascotState {
    IDLE, HAPPY, SAD, THINKING, CELEBRATING
}

@Composable
fun ProtctusMascot(
    modifier: Modifier = Modifier,
    state: MascotState = MascotState.IDLE,
    size: Dp = 100.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sentinel")

    // Floating hover
    val hoverY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hover"
    )

    // Signal pulse on antenna
    val signalPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signal"
    )

    // Eye blink
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5000
                1f at 0
                1f at 4600
                0.05f at 4750
                1f at 4900
                1f at 5000
            }
        ),
        label = "blink"
    )

    // Circuit glow pulse
    val circuitPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circuit"
    )

    // State-driven colors
    val eyeColor by animateColorAsState(
        targetValue = when (state) {
            MascotState.IDLE -> EduColors.Primary
            MascotState.HAPPY, MascotState.CELEBRATING -> EduColors.Success
            MascotState.SAD -> EduColors.Error
            MascotState.THINKING -> EduColors.Accent
        },
        animationSpec = tween(350),
        label = "eye_col"
    )

    val accentColor by animateColorAsState(
        targetValue = when (state) {
            MascotState.IDLE -> EduColors.Primary
            MascotState.HAPPY, MascotState.CELEBRATING -> EduColors.Success
            MascotState.SAD -> EduColors.Error
            MascotState.THINKING -> EduColors.Accent
        },
        animationSpec = tween(350),
        label = "accent_col"
    )

    val eyeShape by animateFloatAsState(
        targetValue = when (state) {
            MascotState.HAPPY, MascotState.CELEBRATING -> 0.5f
            MascotState.SAD -> -0.5f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "eye_shape"
    )

    val bodyTilt by animateFloatAsState(
        targetValue = when (state) {
            MascotState.THINKING -> 10f
            MascotState.CELEBRATING -> sin(hoverY * 1.5f) * 12f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 150f),
        label = "tilt"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = size.toPx()
            val h = size.toPx()
            val cx = w / 2f
            val cy = h / 2f + hoverY

            withTransform({
                rotate(bodyTilt, Offset(cx, cy))
            }) {

                // ── 1. ANTENNA ──
                val antennaBaseY = cy - h * 0.38f
                val antennaTipY = cy - h * 0.48f

                // Antenna stalk
                drawLine(
                    color = EduColors.CardBorder,
                    start = Offset(cx, antennaBaseY),
                    end = Offset(cx, antennaTipY),
                    strokeWidth = w * 0.025f,
                    cap = StrokeCap.Round
                )

                // Antenna tip (signal dot)
                drawCircle(
                    color = accentColor.copy(alpha = signalPulse),
                    radius = w * 0.03f,
                    center = Offset(cx, antennaTipY)
                )

                // Signal arcs (small curved lines radiating from antenna)
                val arcRadius1 = w * 0.06f
                val arcRadius2 = w * 0.10f
                for (arcR in listOf(arcRadius1, arcRadius2)) {
                    val arcPath = Path().apply {
                        val startAngle = -60f
                        val sweepAngle = -60f
                        val rect = androidx.compose.ui.geometry.Rect(
                            cx - arcR, antennaTipY - arcR,
                            cx + arcR, antennaTipY + arcR
                        )
                        arcTo(rect, startAngle, sweepAngle, true)
                    }
                    drawPath(
                        path = arcPath,
                        color = accentColor.copy(alpha = signalPulse * 0.6f),
                        style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
                    )
                }

                // ── 2. MAIN BODY (Hexagonal Shield) ──
                val bodyPath = Path().apply {
                    // Hexagonal shield with rounded feel
                    val bodyW = w * 0.38f
                    val bodyTop = cy - h * 0.35f
                    val bodyMid = cy + h * 0.05f
                    val bodyBottom = cy + h * 0.38f
                    val shoulderY = bodyTop + h * 0.08f

                    moveTo(cx, bodyTop)
                    // Top right shoulder
                    lineTo(cx + bodyW, shoulderY)
                    // Right side curves to bottom point
                    cubicTo(
                        cx + bodyW, bodyMid,
                        cx + bodyW * 0.6f, bodyBottom - h * 0.08f,
                        cx, bodyBottom
                    )
                    // Left side mirrors
                    cubicTo(
                        cx - bodyW * 0.6f, bodyBottom - h * 0.08f,
                        cx - bodyW, bodyMid,
                        cx - bodyW, shoulderY
                    )
                    close()
                }

                // Body fill
                drawPath(path = bodyPath, color = EduColors.SurfaceLight)

                // Body border
                drawPath(
                    path = bodyPath,
                    color = EduColors.CardBorder,
                    style = Stroke(width = w * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // ── 3. ARMOR ACCENT LINES (Circuit traces) ──
                // Left circuit trace
                drawLine(
                    color = accentColor.copy(alpha = circuitPulse),
                    start = Offset(cx - w * 0.25f, cy + h * 0.02f),
                    end = Offset(cx - w * 0.12f, cy + h * 0.02f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor.copy(alpha = circuitPulse),
                    start = Offset(cx - w * 0.12f, cy + h * 0.02f),
                    end = Offset(cx - w * 0.12f, cy + h * 0.12f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round
                )
                // Circuit node
                drawCircle(
                    color = accentColor.copy(alpha = circuitPulse),
                    radius = w * 0.015f,
                    center = Offset(cx - w * 0.12f, cy + h * 0.12f)
                )

                // Right circuit trace
                drawLine(
                    color = accentColor.copy(alpha = circuitPulse),
                    start = Offset(cx + w * 0.25f, cy + h * 0.02f),
                    end = Offset(cx + w * 0.12f, cy + h * 0.02f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor.copy(alpha = circuitPulse),
                    start = Offset(cx + w * 0.12f, cy + h * 0.02f),
                    end = Offset(cx + w * 0.12f, cy + h * 0.12f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = accentColor.copy(alpha = circuitPulse),
                    radius = w * 0.015f,
                    center = Offset(cx + w * 0.12f, cy + h * 0.12f)
                )

                // Center chevron accent (V shape on chest)
                val chevronPath = Path().apply {
                    moveTo(cx - w * 0.08f, cy + h * 0.16f)
                    lineTo(cx, cy + h * 0.22f)
                    lineTo(cx + w * 0.08f, cy + h * 0.16f)
                }
                drawPath(
                    path = chevronPath,
                    color = accentColor.copy(alpha = circuitPulse + 0.1f),
                    style = Stroke(width = w * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // ── 4. VISOR / FACE SCREEN ──
                val visorW = w * 0.48f
                val visorH = h * 0.22f
                val visorY = cy - h * 0.18f
                val visorTopLeft = Offset(cx - visorW / 2, visorY)

                // Visor background
                drawRoundRect(
                    color = EduColors.Background,
                    topLeft = visorTopLeft,
                    size = Size(visorW, visorH),
                    cornerRadius = CornerRadius(w * 0.06f)
                )

                // Visor border (subtle)
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.3f),
                    topLeft = visorTopLeft,
                    size = Size(visorW, visorH),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = Stroke(width = w * 0.015f)
                )

                // ── 5. EYES (LED-style, expressive) ──
                val eyeCenterY = visorY + visorH * 0.5f
                val eyeSpacing = w * 0.10f
                val eyeW = w * 0.065f
                val eyeH = h * 0.08f * blink

                fun drawLedEye(eyeCx: Float) {
                    if (eyeShape == 0f) {
                        // Neutral: rounded rectangle LED
                        drawRoundRect(
                            color = eyeColor,
                            topLeft = Offset(eyeCx - eyeW / 2, eyeCenterY - eyeH / 2),
                            size = Size(eyeW, eyeH),
                            cornerRadius = CornerRadius(eyeW * 0.35f)
                        )
                        // Small highlight dot (top-right of eye)
                        drawCircle(
                            color = EduColors.TextPrimary.copy(alpha = 0.4f),
                            radius = eyeW * 0.15f,
                            center = Offset(eyeCx + eyeW * 0.15f, eyeCenterY - eyeH * 0.2f)
                        )
                    } else {
                        // Happy (^) or Sad (v) curved eyes
                        val curvePath = Path().apply {
                            val yOff = eyeH * eyeShape
                            moveTo(eyeCx - eyeW * 0.9f, eyeCenterY + yOff)
                            quadraticTo(
                                eyeCx, eyeCenterY - yOff * 2.2f,
                                eyeCx + eyeW * 0.9f, eyeCenterY + yOff
                            )
                        }
                        drawPath(
                            path = curvePath,
                            color = eyeColor,
                            style = Stroke(width = eyeW * 0.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                drawLedEye(cx - eyeSpacing)
                drawLedEye(cx + eyeSpacing)

                // ── 6. MOUTH (small, expressive) ──
                val mouthY = visorY + visorH * 0.78f
                when (state) {
                    MascotState.HAPPY, MascotState.CELEBRATING -> {
                        // Smile arc
                        val smilePath = Path().apply {
                            moveTo(cx - w * 0.05f, mouthY)
                            quadraticTo(cx, mouthY + h * 0.025f, cx + w * 0.05f, mouthY)
                        }
                        drawPath(
                            path = smilePath,
                            color = eyeColor.copy(alpha = 0.8f),
                            style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
                        )
                    }
                    MascotState.SAD -> {
                        // Frown
                        val frownPath = Path().apply {
                            moveTo(cx - w * 0.04f, mouthY + h * 0.015f)
                            quadraticTo(cx, mouthY - h * 0.015f, cx + w * 0.04f, mouthY + h * 0.015f)
                        }
                        drawPath(
                            path = frownPath,
                            color = eyeColor.copy(alpha = 0.6f),
                            style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
                        )
                    }
                    MascotState.THINKING -> {
                        // Flat line
                        drawLine(
                            color = eyeColor.copy(alpha = 0.5f),
                            start = Offset(cx - w * 0.04f, mouthY),
                            end = Offset(cx + w * 0.04f, mouthY),
                            strokeWidth = w * 0.015f,
                            cap = StrokeCap.Round
                        )
                    }
                    else -> {
                        // Neutral small dash
                        drawLine(
                            color = EduColors.TextDisabled.copy(alpha = 0.4f),
                            start = Offset(cx - w * 0.025f, mouthY),
                            end = Offset(cx + w * 0.025f, mouthY),
                            strokeWidth = w * 0.012f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // ── 7. CELEBRATING SPARKLES ──
                if (state == MascotState.CELEBRATING) {
                    val sparklePositions = listOf(
                        Offset(cx - w * 0.35f, cy - h * 0.25f),
                        Offset(cx + w * 0.35f, cy - h * 0.20f),
                        Offset(cx - w * 0.30f, cy + h * 0.15f),
                        Offset(cx + w * 0.32f, cy + h * 0.10f),
                    )
                    sparklePositions.forEachIndexed { i, pos ->
                        val sparkleAlpha = ((signalPulse + i * 0.25f) % 1f)
                        val sparkleSize = w * 0.02f * (0.5f + sparkleAlpha * 0.5f)
                        // Draw small + shaped sparkle
                        drawLine(
                            color = EduColors.Accent.copy(alpha = sparkleAlpha * 0.8f),
                            start = Offset(pos.x - sparkleSize, pos.y),
                            end = Offset(pos.x + sparkleSize, pos.y),
                            strokeWidth = w * 0.012f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = EduColors.Accent.copy(alpha = sparkleAlpha * 0.8f),
                            start = Offset(pos.x, pos.y - sparkleSize),
                            end = Offset(pos.x, pos.y + sparkleSize),
                            strokeWidth = w * 0.012f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
