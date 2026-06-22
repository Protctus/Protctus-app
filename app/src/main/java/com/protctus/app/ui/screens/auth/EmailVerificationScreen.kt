package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.PasswordResetState
import com.protctus.app.viewmodel.PasswordResetViewModel

@Composable
fun EmailVerificationScreen(navController: NavController) {
    // Activity-scoped ViewModel — same instance as ResetPasswordScreen
    val activity = LocalContext.current as ComponentActivity
    val vm: PasswordResetViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val targetEmail = vm.targetEmail ?: "your email"

    var otpDigits by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // ── Heavy entrance animations ──
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600), label = "hAlpha"
    )
    val headerSlide by animateDpAsState(
        targetValue = if (visible) 0.dp else (-40).dp,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "hSlide"
    )
    val formAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, delayMillis = 300), label = "fAlpha"
    )
    val btnAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 500), label = "bAlpha"
    )

    val cooldownText = when (state) {
        is PasswordResetState.OtpCooldown -> {
            val seconds = (state as PasswordResetState.OtpCooldown).secondsLeft
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            if (h > 0) "Resend in ${String.format("%02d:%02d:%02d", h, m, s)}"
            else "Resend in ${String.format("%02d:%02d", m, s)}"
        }
        else -> "Resend"
    }
    val canResend = state !is PasswordResetState.OtpCooldown && state !is PasswordResetState.Loading

    // Handle state
    LaunchedEffect(state) {
        when (state) {
            is PasswordResetState.OtpVerified -> {
                Toast.makeText(context, "Email verified!", Toast.LENGTH_SHORT).show()
                navController.navigate(Route.NewPassword)
            }
            is PasswordResetState.Error -> {
                Toast.makeText(context, (state as PasswordResetState.Error).message, Toast.LENGTH_SHORT).show()
                otpDigits = List(6) { "" }
                vm.resetState()
            }
            else -> {}
        }
    }

    Scaffold(containerColor = com.protctus.app.ui.theme.DarkBackground) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Back button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = headerSlide)
                    .alpha(headerAlpha),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = stringResource(R.string.confirm_your_email),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = headerSlide)
                    .alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle with real email
            val subtitleText = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFFA0A0A0))) {
                    append("We've sent a 6-digit verification code\nto ")
                }
                withStyle(style = SpanStyle(color = Color(0xFF8B89C8), fontWeight = FontWeight.SemiBold)) {
                    append(targetEmail)
                }
            }
            Text(
                text = subtitleText, fontSize = 14.sp, textAlign = TextAlign.Center,
                lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // OTP Input — 6 individual digit boxes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(formAlpha)
            ) {
                Text(stringResource(R.string.enter_verification_code), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 0 until 6) {
                        val digitScale by animateFloatAsState(
                            targetValue = if (visible) 1f else 0.5f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ), label = "digit$i"
                        )

                        BasicTextField(
                            value = otpDigits[i],
                            onValueChange = { value ->
                                if (value.length <= 1 && value.all { it.isDigit() }) {
                                    otpDigits = otpDigits.toMutableList().also { it[i] = value }
                                    if (value.isNotEmpty() && i < 5) {
                                        focusRequesters[i + 1].requestFocus()
                                    }
                                    if (otpDigits.all { it.isNotEmpty() }) {
                                        vm.verifyOtp(otpDigits.joinToString(""))
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                color = Color.White, fontSize = 24.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .scale(digitScale)
                                .focusRequester(focusRequesters[i])
                                .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (otpDigits[i].isNotEmpty()) Color(0xFF4ADE80) else Color(0xFF3F3F46),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 14.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    if (otpDigits[i].isEmpty()) {
                                        Text(stringResource(R.string.str_267402), color = Color(0xFF555555), fontSize = 20.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resend
                Text(
                    text = cooldownText,
                    color = if (canResend) Color(0xFF8B89C8) else Color(0xFF71717A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = canResend
                        ) { vm.resendOtp() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Verify button
            Button(
                onClick = {
                    val code = otpDigits.joinToString("")
                    if (code.length == 6) vm.verifyOtp(code)
                    else Toast.makeText(context, "Please enter all 6 digits", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).alpha(btnAlpha),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(12.dp),
                enabled = state !is PasswordResetState.Loading
            ) {
                if (state is PasswordResetState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.verify_and_continue), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).alpha(headerAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.need_help), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { Toast.makeText(context, "Opening Help...", Toast.LENGTH_SHORT).show() })
                Text(stringResource(R.string.str_12847), color = Color(0xFF71717A), fontSize = 12.sp)
                Text(stringResource(R.string.faq), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { Toast.makeText(context, "Opening FAQ...", Toast.LENGTH_SHORT).show() })
                Text(stringResource(R.string.str_12847), color = Color(0xFF71717A), fontSize = 12.sp)
                Text(stringResource(R.string.terms_of_use), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { Toast.makeText(context, "Opening Terms...", Toast.LENGTH_SHORT).show() })
            }
        }
    }
}