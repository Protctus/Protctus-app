package com.protctus.app.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusPrimaryButton
import com.protctus.app.ui.theme.ProtctusTheme
import kotlinx.coroutines.delay

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.protctus.app.viewmodel.AuthViewModel
import com.protctus.app.viewmodel.AuthState

@Composable
fun VerificationScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }

    var otpCode by remember { mutableStateOf("") }
    var isOtpError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.OtpVerificationFailed -> {
                isOtpError = true
                snackbarHostState.showSnackbar((authState as AuthState.OtpVerificationFailed).message)
                authViewModel.resetState()
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar((authState as AuthState.Error).message)
                authViewModel.resetState()
            }
            is AuthState.Verified -> {
                delay(800) // Brief delay so the user sees the green success state
                navController.navigate(Route.Fingerprint) {
                    popUpTo(Route.Verification) { inclusive = true }
                }
                authViewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.imePadding()) },
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .background(com.protctus.app.ui.theme.DarkBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { navController.popBackStack() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.verification),
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFFFFFFF),
                letterSpacing = 0.5.sp,
                lineHeight = 40.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Please enter the code sent to your\nemail address.",
                fontSize = 14.sp,
                color = Color(0xFF7F909F),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                letterSpacing = 0.2.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = otpCode,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            otpCode = value
                            isOtpError = false // Reset error state on new input
                            // Auto-verify when 6 digits are entered
                            if (value.length == 6) {
                                authViewModel.authenticateWithEmailOtp(value)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(72.dp)
                        .focusRequester(focusRequester)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val isFilled = otpCode.length > i
                        val isActive = otpCode.length == i

                        // The Tactile Spring Animation
                        val scale by animateFloatAsState(
                            targetValue = if (isActive && otpCode.isNotEmpty()) 1.08f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ), label = "scale"
                        )

                        // Smooth border color transition based on verification status
                        val borderColor by animateColorAsState(
                            targetValue = when {
                                authState is AuthState.Verified && isFilled -> com.protctus.app.ui.theme.PrimaryText // Brand success state
                                isOtpError && isFilled -> Color(0xFFEF4444) // Error Red
                                isActive -> Color(0xFFFFFFFF) // Active box border
                                isFilled -> Color(0xFF7F909F) // Filled box border
                                else -> Color(0xFFFFFFFF)     // Empty box border matching design
                            },
                            animationSpec = tween(300), label = "border"
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(scale)
                                .background(com.protctus.app.ui.theme.DarkBackground, RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFilled) {
                                Text(
                                    text = otpCode[i].toString(),
                                    fontSize = 24.sp,
                                    color = if (authState is AuthState.Verified) com.protctus.app.ui.theme.PrimaryText else Color(0xFF7F909F),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))



            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProtctusPrimaryButton(
                    text = when {
                        isLoading && authState is AuthState.SavingProfile -> "Saving Profile..."
                        isLoading -> "Verifying..."
                        else -> "Continue"
                    },
                    onClick = {
                        if (authState is AuthState.Verified) {
                            navController.navigate(Route.Fingerprint) {
                                popUpTo(Route.Verification) { inclusive = true }
                            }
                            authViewModel.resetState()
                        } else if (otpCode.length == 6) {
                            authViewModel.authenticateWithEmailOtp(otpCode)
                        }
                    },
                    enabled = (otpCode.length == 6 || authState is AuthState.Verified) && !isLoading
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val resendText = if (authState is AuthState.OtpCooldown) {
                val seconds = (authState as AuthState.OtpCooldown).secondsLeft
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                val s = seconds % 60
                if (h > 0) "Resend in ${String.format("%02d:%02d:%02d", h, m, s)}"
                else "Resend in ${String.format("%02d:%02d", m, s)}"
            } else {
                "Resend"
            }
            val canResend = authState !is AuthState.OtpCooldown && !isLoading && authState !is AuthState.SavingProfile

            Text(
                text = resendText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = if (canResend) Color.White else Color(0xFF555555),
                modifier = Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = canResend
                ) {
                    authViewModel.resendOtp()
                }
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_6_pro")
@Composable
fun PreviewVerificationScreen() {
    ProtctusTheme {
        VerificationScreen(navController = rememberNavController())
    }
}