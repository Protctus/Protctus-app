package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import kotlinx.coroutines.delay

enum class AnimationState { DRAWING, IDLE, SUCCESS }

@Composable
fun FingerprintScreen(navController: NavController) {
    val context = LocalContext.current
    val cancelText = stringResource(R.string.cancel)
    var animState by remember { mutableStateOf(AnimationState.DRAWING) }

    val showBiometricPrompt = {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity != null) {
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Trigger the majestic Dribbble Checkmark Morph!
                        animState = AnimationState.SUCCESS
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Auth Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Protctus Secure Enclave")
                .setSubtitle("Verify biometric credential to proceed")
                .setNegativeButtonText(cancelText)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(context, "Biometric system not available", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        delay(150) // Let compose setup
        animState = AnimationState.IDLE // Start the draw progress gracefully
        delay(350)
        showBiometricPrompt()
    }

    LaunchedEffect(animState) {
        if (animState == AnimationState.SUCCESS) {
            delay(1600) // Watch the majestic morph drop and snap into place
            navController.navigate(Route.Username) {
                popUpTo(Route.Onboarding) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(com.protctus.app.ui.theme.DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            DribbbleFingerprintAnimation(state = animState)
        }
    }
}

@Composable
fun DribbbleFingerprintAnimation(state: AnimationState) {
    val drawProgress by animateFloatAsState(
        targetValue = if (state == AnimationState.DRAWING) 0f else 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "drawProgress"
    )

    val morphProgress by animateFloatAsState(
        targetValue = if (state == AnimationState.SUCCESS) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f, // Bouncy pop for the tick
            stiffness = Spring.StiffnessLow
        ),
        label = "morphProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val finalScale by animateFloatAsState(
        targetValue = if (state == AnimationState.SUCCESS) 1.25f else pulseScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "finalScale"
    )

    val rawStrokeColor = androidx.compose.ui.graphics.lerp(
        Color.White,
        Color(0xFF00E676), // Neon Success Checkmark Green
        morphProgress.coerceIn(0f, 1f)
    )

    Canvas(modifier = Modifier.size(140.dp).scale(finalScale)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeWidth = 14f

        fun lerpPoint(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

        for (i in 0..4) {
            val isMorphingArch = (i == 1) // The 2nd inner ring morphs into the Checkmark

            // Extraneous rings shrink into themselves
            val shrinkTrim = if (isMorphingArch) 1f else (1f - morphProgress.coerceIn(0f, 1f))
            if (shrinkTrim <= 0f) continue

            val R = (i + 1) * 16f

            // Fingerprint Coordinates
            val fpLeftX = -R
            val fpLeftY = 15f + i * 14f
            val fpTopX = 0f
            val fpTopY = -R
            val fpRightX = R
            val fpRightY = 5f + i * 16f
            
            // Perfect rounded bezier approximations
            val fpCp1LX = -R
            val fpCp1LY = -R * 0.552f
            val fpCp2LX = -R * 0.552f
            val fpCp2LY = -R
            
            val fpCp1RX = R * 0.552f
            val fpCp1RY = -R
            val fpCp2RX = R
            val fpCp2RY = -R * 0.552f

            // Checkmark Coordinates (Mapped only accurately to i == 1)
            val tkTopX = 0f
            val tkTopY = 25f
            val tkLeftX = -25f
            val tkLeftY = 0f
            val tkRightX = 35f
            val tkRightY = -35f
            
            // Straight lines require CPs placed along the segments
            val tkCp1LX = lerpPoint(tkTopX, tkLeftX, 0.33f)
            val tkCp1LY = lerpPoint(tkTopY, tkLeftY, 0.33f)
            val tkCp2LX = lerpPoint(tkTopX, tkLeftX, 0.66f)
            val tkCp2LY = lerpPoint(tkTopY, tkLeftY, 0.66f)
            
            val tkCp1RX = lerpPoint(tkTopX, tkRightX, 0.33f)
            val tkCp1RY = lerpPoint(tkTopY, tkRightY, 0.33f)
            val tkCp2RX = lerpPoint(tkTopX, tkRightX, 0.66f)
            val tkCp2RY = lerpPoint(tkTopY, tkRightY, 0.66f)

            // Calculate active interpolated state point
            val m = if (isMorphingArch) morphProgress else 0f
            
            val curLeftX = lerpPoint(fpLeftX, tkLeftX, m) + cx
            val curLeftY = lerpPoint(fpLeftY, tkLeftY, m) + cy
            val curTopX = lerpPoint(fpTopX, tkTopX, m) + cx
            val curTopY = lerpPoint(fpTopY, tkTopY, m) + cy
            val curRightX = lerpPoint(fpRightX, tkRightX, m) + cx
            val curRightY = lerpPoint(fpRightY, tkRightY, m) + cy
            
            val cpAL_X = lerpPoint(fpCp2LX, tkCp1LX, m) + cx
            val cpAL_Y = lerpPoint(fpCp2LY, tkCp1LY, m) + cy
            val cpBL_X = lerpPoint(fpCp1LX, tkCp2LX, m) + cx
            val cpBL_Y = lerpPoint(fpCp1LY, tkCp2LY, m) + cy
            
            val cpAR_X = lerpPoint(fpCp1RX, tkCp1RX, m) + cx
            val cpAR_Y = lerpPoint(fpCp1RY, tkCp1RY, m) + cy
            val cpBR_X = lerpPoint(fpCp2RX, tkCp2RX, m) + cx
            val cpBR_Y = lerpPoint(fpCp2RY, tkCp2RY, m) + cy

            // Construct Dual Symmetry Paths
            val pathLeft = Path().apply {
                moveTo(curTopX, curTopY)
                cubicTo(cpAL_X, cpAL_Y, cpBL_X, cpBL_Y, curLeftX, curLeftY)
            }
            val pathRight = Path().apply {
                moveTo(curTopX, curTopY)
                cubicTo(cpAR_X, cpAR_Y, cpBR_X, cpBR_Y, curRightX, curRightY)
            }

            // Expose the drawn lengths using PathMeasure tracking
            val finalTrim = drawProgress * shrinkTrim
            if (finalTrim <= 0f) continue

            val measLeft = PathMeasure()
            measLeft.setPath(pathLeft, false)
            val segLeft = Path()
            measLeft.getSegment(0f, measLeft.length * finalTrim, segLeft, true)
            
            val measRight = PathMeasure()
            measRight.setPath(pathRight, false)
            val segRight = Path()
            measRight.getSegment(0f, measRight.length * finalTrim, segRight, true)

            // Render
            drawPath(
                path = segLeft,
                color = rawStrokeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawPath(
                path = segRight,
                color = rawStrokeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}