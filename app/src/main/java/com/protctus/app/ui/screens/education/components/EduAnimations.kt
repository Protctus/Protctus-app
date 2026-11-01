package com.protctus.app.ui.screens.education.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Protctus Education — Shared Animation Utilities
 *
 * STRICT: No glow effects. No color pulses. No neon.
 * Animations rely ONLY on: scale, opacity, translation, elevation.
 */

// ── Shared Animation Specs ──

object EduMotion {
    // Quick interactions (button press, option select)
    val Quick = tween<Float>(200, easing = FastOutSlowInEasing)

    // Standard transitions (card appearance, navigation)
    val Standard = tween<Float>(350, easing = FastOutSlowInEasing)

    // Emphasis animations (completion, XP gain)
    val Emphasis = tween<Float>(500, easing = FastOutSlowInEasing)

    // Spring for bouncy interactions
    val Bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Spring for subtle interactions
    val Gentle = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Wraps content with a fade+slide entrance animation.
 * No glow. Uses opacity and vertical translation only.
 */
@Composable
fun AnimatedEntrance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        offsetY.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
    ) {
        content()
    }
}

/**
 * Wraps content with a scale+fade entrance for emphasis (e.g., XP counter).
 * No glow. Uses scale and opacity only.
 */
@Composable
fun AnimatedScaleEntrance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        alpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        scale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .scale(scale.value)
    ) {
        content()
    }
}

/**
 * Horizontal shake animation for wrong answers.
 * No glow. Uses horizontal translation only.
 */
@Composable
fun ShakeOnTrigger(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            // Quick shake: right → left → right → center
            offsetX.animateTo(12f, tween(50))
            offsetX.animateTo(-10f, tween(50))
            offsetX.animateTo(8f, tween(50))
            offsetX.animateTo(-6f, tween(50))
            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        modifier = modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
    ) {
        content()
    }
}
