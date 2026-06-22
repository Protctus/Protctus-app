package com.protctus.app.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protctus.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DopamineUIEngine {

    /**
     * Helper to trigger a heavy double haptic vibration pattern for profound success dopamine hit.
     */
    @Suppress("DEPRECATION")
    fun triggerHeavyDoubleHaptic(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 40), intArrayOf(0, 255, 0, 255), -1)
            vibrator.vibrate(effect)
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(longArrayOf(0, 30, 60, 40), -1)
        }
    }

    /**
     * Helper to trigger a single sharp error haptic.
     */
    @Suppress("DEPRECATION")
    fun triggerErrorHaptic(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, 255))
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)
        }
    }
}

/**
 * 1. The "Juicy" Spring Button
 * A visceral, heavily tactile 3D button. Pushing it visually reduces the bottom 3D shadow 
 * to give physical simulation of being pressed into the glass of the screen.
 */
@Composable
fun JuicyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AccentColor,
    textColor: Color = Color.Black,
    enabled: Boolean = true,
    shadowDepth: Dp = 6.dp
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Physics spring for scaling
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "JuicyButton_Scale"
    )

    // Physics spring for the 3D shadow depth reduction (Push-in effect)
    val bottomPadding by animateDpAsState(
        targetValue = if (isPressed) 0.dp else shadowDepth,
        animationSpec = spring(
            dampingRatio = 0.6f, 
            stiffness = Spring.StiffnessMedium
        ),
        label = "JuicyButton_Depth"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(56.dp + shadowDepth) // Reserve space for the 3D depth
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    
                    if (up != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            delay(50) // Micro-delay to let the bounce visually pop before routing
                            onClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // The darker "Shadow" or "Base" of the 3D button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    color = backgroundColor.copy(alpha = 0.6f), // Darker shade mock for base shadow
                    shape = RoundedCornerShape(16.dp)
                )
        )
        
        // The Top Face of the button which physically moves down
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding)
                .height(56.dp)
                .background(
                    color = if (enabled) backgroundColor else SurfaceColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (enabled) 0.dp else 1.dp,
                    color = if (enabled) Color.Transparent else OutlineColor,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                color = if (enabled) textColor else SecondaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * 2. The Anticipation Progress Bar
 * Features a shimmer effect that traverses the track to keep the UI feeling "Alive", 
 * coupled with a bouncy spring animation for value updates.
 */
@Composable
fun DopamineProgressBar(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    fillColor: Color = AccentColor,
    trackColor: Color = SurfaceColor
) {
    // Aggressive Spring for bouncing up to new values
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "DopamineProgressBar_Progress"
    )

    // Infinite translation for the shimmer
    val infiniteTransition = rememberInfiniteTransition(label = "Shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DopamineProgressBar_Shimmer"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            fillColor.copy(alpha = 0.9f),
            fillColor.copy(alpha = 0.4f),
            fillColor.copy(alpha = 0.9f)
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 400f, 0f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .clip(CircleShape)
                .background(brush = shimmerBrush)
        )
    }
}

/**
 * 3. The Variable Reward Result Layout
 * Can be hoisted deeply into a BottomSheet or Dialog.
 * Triggers rapid dopamine bursts via color morphs, haptics, and spring reveals.
 */
@Composable
fun VariableRewardView(
    isSuccess: Boolean,
    title: String,
    message: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasRevealed by remember { mutableStateOf(false) }

    // Color Morphs
    val targetColor = if (isSuccess) Color(0xFF22C55E) else ErrorColor
    val backgroundColor by animateColorAsState(
        targetValue = if (hasRevealed) targetColor else SurfaceColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "VariableReward_Color"
    )

    // Deep Spring Pop for the action button
    val buttonScale by animateFloatAsState(
        targetValue = if (hasRevealed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.4f, // Extremely bouncy
            stiffness = Spring.StiffnessLow
        ),
        label = "VariableReward_ButtonScale"
    )

    LaunchedEffect(isSuccess) {
        delay(100)
        hasRevealed = true
        if (isSuccess) {
            DopamineUIEngine.triggerHeavyDoubleHaptic(context)
        } else {
            DopamineUIEngine.triggerErrorHaptic(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title.uppercase(),
            color = if (hasRevealed && !isSuccess) Color.White else (if (isSuccess) Color.Black else PrimaryText),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = if (hasRevealed && !isSuccess) Color.White.copy(alpha = 0.9f) else (if (isSuccess) Color.Black.copy(alpha = 0.8f) else SecondaryText),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(40.dp))

        Box(modifier = Modifier.scale(buttonScale)) {
            JuicyButton(
                text = if (isSuccess) "CONTINUE" else "GOT IT. RETRY.",
                onClick = onContinue,
                backgroundColor = if (isSuccess) Color.Black else Color.White,
                textColor = if (isSuccess) Color.White else ErrorColor,
                shadowDepth = 6.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
