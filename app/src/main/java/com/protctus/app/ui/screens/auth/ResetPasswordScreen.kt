package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.PasswordResetState
import com.protctus.app.viewmodel.PasswordResetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(navController: NavController) {
    // Activity-scoped ViewModel so it's shared across the 3-screen flow
    val activity = LocalContext.current as ComponentActivity
    val vm: PasswordResetViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }

    // ── Entrance animations ──
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600), label = "headerAlpha"
    )
    val headerOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else (-30).dp,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "headerOffset"
    )
    val formScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "formScale"
    )

    // Navigate to EmailVerification when OTP is sent
    LaunchedEffect(state) {
        when (state) {
            is PasswordResetState.OtpSent -> {
                navController.navigate(Route.EmailVerification)
            }
            is PasswordResetState.OtpCooldown -> {
                // If we're still on this screen and cooldown starts, also navigate
                navController.navigate(Route.EmailVerification) {
                    launchSingleTop = true
                }
            }
            is PasswordResetState.Error -> {
                Toast.makeText(context, (state as PasswordResetState.Error).message, Toast.LENGTH_SHORT).show()
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = headerOffset)
                    .alpha(headerAlpha)
            ) {
                IconButton(onClick = {
                    vm.fullReset()
                    navController.popBackStack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Text(
                text = stringResource(R.string.reset_your_password),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.offset(y = headerOffset).alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Enter your email address below\nand we'll send you a verification code",
                fontSize = 14.sp,
                color = Color(0xFFA0A0A0),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Email input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(formScale)
            ) {
                Text(stringResource(R.string.email_address), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text(stringResource(R.string.enter_email_address), color = Color(0xFF71717A)) },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF71717A))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3F3F46),
                        unfocusedBorderColor = Color(0xFF3F3F46),
                        unfocusedContainerColor = Color(0xFF262626),
                        focusedContainerColor = Color(0xFF262626),
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Send OTP button
            Button(
                onClick = {
                    if (email.isNotBlank() && email.contains("@")) {
                        vm.sendOtpToEmail(email)
                    } else {
                        Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).scale(formScale),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(12.dp),
                enabled = state !is PasswordResetState.Loading
            ) {
                if (state is PasswordResetState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.send_verification_code), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .alpha(headerAlpha),
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