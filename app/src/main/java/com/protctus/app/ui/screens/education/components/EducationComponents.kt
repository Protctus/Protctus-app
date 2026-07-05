package com.protctus.app.ui.screens.education.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protctus.app.ui.screens.education.theme.EduColors
import com.protctus.app.ui.screens.education.theme.EduDimens
import com.protctus.app.ui.screens.education.theme.EduShapes
import com.protctus.app.ui.screens.education.theme.EduTypography
import kotlin.math.sin
import kotlin.random.Random

/**
 * Protctus Education — Premium Component Library
 *
 * DESIGN PHILOSOPHY: Every component must feel physical, tactile, premium.
 * Buttons have 3D depth. Cards have weight. Interactions feel satisfying.
 *
 * STRICT: Every color from EduColors → Color.kt. No Color(). No hex. No glow.
 */

// ═══════════════════════════════════════════════════════════════════
//  Edu3DButton — Premium 3D Button with Duolingo-beating depth
//
//  The signature component. A thick bottom edge creates the illusion
//  of a physical, pressable button. On press, the face translates
//  down and the depth shrinks — like pressing a real button.
// ═══════════════════════════════════════════════════════════════════

@Composable
fun Edu3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    faceColor: Color = EduColors.SurfaceLight,
    depthColor: Color = EduColors.Background,
    textColor: Color = EduColors.TextPrimary,
    isEnabled: Boolean = true,
    isCorrect: Boolean? = null, // null=default, true=correct, false=wrong
    depthHeight: Dp = 5.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate the depth offset: full depth when idle, zero when pressed
    val depthOffset by animateDpAsState(
        targetValue = if (isPressed && isEnabled) 0.dp else depthHeight,
        animationSpec = tween(80),
        label = "depth_offset"
    )

    // Animate colors based on answer state
    val animatedFaceColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> EduColors.SuccessMuted
            false -> EduColors.ErrorMuted
            null -> if (isEnabled) faceColor else EduColors.SurfaceDark
        },
        animationSpec = tween(200),
        label = "face_color"
    )

    val animatedDepthColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> EduColors.Success
            false -> EduColors.Error
            null -> if (isEnabled) depthColor else EduColors.SurfaceDark
        },
        animationSpec = tween(200),
        label = "depth_color"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> EduColors.Success
            false -> EduColors.Error
            null -> if (isEnabled) EduColors.CardBorder else EduColors.SurfaceDark
        },
        animationSpec = tween(200),
        label = "border_color"
    )

    val animatedTextColor = when (isCorrect) {
        true -> EduColors.Success
        false -> EduColors.Error
        null -> if (isEnabled) textColor else EduColors.TextDisabled
    }

    // Outer container: holds both the depth layer and the face
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            )
    ) {
        // Depth layer (the thick bottom edge that creates 3D illusion)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .clip(RoundedCornerShape(EduShapes.RadiusLg))
                .background(animatedDepthColor)
                .border(1.5.dp, animatedBorderColor, RoundedCornerShape(EduShapes.RadiusLg))
        )

        // Face layer (the button surface — offset up by depth amount)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = -(depthOffset))
                .clip(RoundedCornerShape(EduShapes.RadiusLg))
                .background(animatedFaceColor)
                .border(1.5.dp, animatedBorderColor, RoundedCornerShape(EduShapes.RadiusLg))
                .heightIn(min = EduDimens.ButtonHeight)
                .padding(horizontal = EduDimens.SpacingXl, vertical = EduDimens.SpacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = EduTypography.Label.copy(color = animatedTextColor),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Bottom padding to account for the depth so layout doesn't jump
    Spacer(modifier = Modifier.height(depthHeight))
}

// ═══════════════════════════════════════════════════════════════════
//  Edu3DPrimaryButton — Primary CTA with the accent color face
// ═══════════════════════════════════════════════════════════════════

@Composable
fun Edu3DPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val depthOffset by animateDpAsState(
        targetValue = if (isPressed && isEnabled) 0.dp else 5.dp,
        animationSpec = tween(80),
        label = "primary_depth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            )
    ) {
        // Depth layer — darker shade of primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .clip(RoundedCornerShape(EduShapes.RadiusLg))
                .background(EduColors.Primary.copy(alpha = 0.5f))
        )

        // Face — full primary color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = -(depthOffset))
                .clip(RoundedCornerShape(EduShapes.RadiusLg))
                .background(if (isEnabled) EduColors.Primary else EduColors.SurfaceDark)
                .heightIn(min = EduDimens.ButtonHeight)
                .padding(horizontal = EduDimens.SpacingXl, vertical = EduDimens.SpacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = EduTypography.Title.copy(
                    color = if (isEnabled) EduColors.TextPrimary else EduColors.TextDisabled
                ),
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(5.dp))
}

// ═══════════════════════════════════════════════════════════════════
//  EduCard — Premium Card with subtle elevation
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = EduColors.SurfaceLight,
    borderColor: Color = EduColors.CardBorder,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "card_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(EduShapes.RadiusXl))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(EduShapes.RadiusXl))
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics { role = Role.Button }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduProgressRing — Animated Circular Progress with premium feel
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduProgressRing(
    modifier: Modifier = Modifier,
    progress: Float,
    size: Dp = 100.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = EduColors.Primary,
    backgroundColor: Color = EduColors.SurfaceDark
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ring_progress"
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = backgroundColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )
        if (animatedProgress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = stroke
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduProgressBar — Horizontal Linear Progress
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = EduColors.Primary,
    trackColor: Color = EduColors.SurfaceDark,
    height: Dp = EduDimens.ProgressBarHeight
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress_bar"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(size.height / 2)
        )
        if (animatedProgress > 0f) {
            drawRoundRect(
                color = color,
                size = size.copy(width = size.width * animatedProgress),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduStatusChip — Category / Status Badge
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduStatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(EduShapes.RadiusMd))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(EduShapes.RadiusMd))
            .padding(horizontal = EduDimens.SpacingMd, vertical = EduDimens.SpacingXs + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = EduTypography.Micro.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduStatBadge — Compact stat badge (streak, XP, etc.)
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduStatBadge(
    icon: @Composable () -> Unit,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(EduDimens.TouchTargetMin)
            .clip(RoundedCornerShape(EduShapes.RadiusFull))
            .background(EduColors.SurfaceLight)
            .border(1.dp, EduColors.CardBorder, RoundedCornerShape(EduShapes.RadiusFull))
            .padding(horizontal = EduDimens.SpacingLg),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EduDimens.SpacingXs)
        ) {
            icon()
            Text(text = value, style = EduTypography.Title)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduTopBar — Reusable Top Navigation
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduTopBar(
    onBackClick: () -> Unit,
    title: String = "",
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EduDimens.ScreenPadding, vertical = EduDimens.SpacingLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(EduDimens.TouchTargetMin)
                .clip(CircleShape)
                .background(EduColors.SurfaceLight)
                .border(1.dp, EduColors.CardBorder, CircleShape)
                .semantics { role = Role.Button }
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = EduColors.TextPrimary,
                modifier = Modifier.size(EduDimens.IconSizeMd)
            )
        }

        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = EduTypography.Title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = EduDimens.SpacingLg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(EduDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ConfettiOverlay — Celebration particles
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ConfettiOverlay(isTriggered: Boolean) {
    if (!isTriggered) return

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val fallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing)),
        label = "confetti_fall"
    )

    val particleColors = remember {
        listOf(EduColors.Primary, EduColors.Success, EduColors.Accent, EduColors.Secondary)
    }

    val particles = remember {
        List(40) {
            ConfettiParticle(
                xOffset = Random.nextFloat(),
                yOffset = Random.nextFloat(),
                speed = Random.nextFloat() * 0.4f + 0.6f,
                particleSize = Random.nextFloat() * 6f + 3f,
                isRect = Random.nextBoolean(),
                rotation = Random.nextFloat() * 360f,
                color = particleColors[it % particleColors.size]
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val y = ((p.yOffset + fallProgress * p.speed) % 1f) * h
            val x = p.xOffset * w + sin(y * 0.015f).toFloat() * 50f
            val alpha = (1f - (y / h) * 0.6f).coerceIn(0f, 1f)
            val rot = p.rotation + fallProgress * 360f * p.speed

            if (p.isRect) {
                drawContext.canvas.save()
                drawContext.canvas.translate(x, y)
                drawContext.canvas.rotate(rot)
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(-p.particleSize, -p.particleSize / 2),
                    size = Size(p.particleSize * 2, p.particleSize)
                )
                drawContext.canvas.restore()
            } else {
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.particleSize,
                    center = Offset(x, y)
                )
            }
        }
    }
}

data class ConfettiParticle(
    val xOffset: Float,
    val yOffset: Float,
    val speed: Float,
    val particleSize: Float,
    val isRect: Boolean,
    val rotation: Float,
    val color: Color
)
