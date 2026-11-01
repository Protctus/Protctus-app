package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun NewPasswordScreen(navController: NavController) {
    // Activity-scoped ViewModel — same instance as previous screens
    val activity = LocalContext.current as ComponentActivity
    val vm: PasswordResetViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    // ── Staggered entrance animations ──
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500), label = "headerAlpha"
    )
    val field1Scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(600, delayMillis = 200, easing = FastOutSlowInEasing), label = "f1Scale"
    )
    val field1Alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200), label = "f1Alpha"
    )
    val field2Scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(600, delayMillis = 400, easing = FastOutSlowInEasing), label = "f2Scale"
    )
    val field2Alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400), label = "f2Alpha"
    )
    val btnScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "btnScale"
    )

    // Handle state
    LaunchedEffect(state) {
        when (state) {
            is PasswordResetState.PasswordChanged -> {
                Toast.makeText(context, "Password changed successfully!", Toast.LENGTH_LONG).show()
                vm.fullReset()
                // Pop all the way back to SignIn
                navController.navigate(Route.SignIn) {
                    popUpTo(0) { inclusive = true }
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

            Row(modifier = Modifier.fillMaxWidth().alpha(headerAlpha)) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.enter_new_password),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.set_a_strong_password_to_protect_your_ac),
                fontSize = 14.sp,
                color = Color(0xFFA0A0A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Password Field
            Column(
                modifier = Modifier.fillMaxWidth().scale(field1Scale).alpha(field1Alpha)
            ) {
                Text(stringResource(R.string.password), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text(stringResource(R.string.enter_your_password), color = Color(0xFF71717A)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF71717A)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle visibility", tint = Color(0xFF71717A)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val hasMinLength = password.length >= 8
            val hasDigit = password.any { it.isDigit() }
            val hasUpper = password.any { it.isUpperCase() }
            val strengthScore = listOf(hasMinLength, hasDigit, hasUpper).count { it }
            val strengthColor = when (strengthScore) {
                0, 1 -> Color(0xFFEF4444)
                2 -> Color(0xFFF59E0B)
                3 -> Color(0xFF10B981)
                else -> Color(0xFFEF4444)
            }
            val strengthText = when (strengthScore) {
                0, 1 -> "Weak"
                2 -> "Good"
                3 -> "Strong"
                else -> "Weak"
            }

            if (password.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).scale(field1Scale).alpha(field1Alpha),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.password_strength), color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Text(text = strengthText, color = strengthColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().height(4.dp).scale(field1Scale).alpha(field1Alpha), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 1) strengthColor else Color(0xFF333333)))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 2) strengthColor else Color(0xFF333333)))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 3) strengthColor else Color(0xFF333333)))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Field
            Column(
                modifier = Modifier.fillMaxWidth().scale(field2Scale).alpha(field2Alpha)
            ) {
                Text(stringResource(R.string.re_type_password), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text(stringResource(R.string.re_enter_your_password), color = Color(0xFF71717A)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF71717A)) },
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle visibility", tint = Color(0xFF71717A)
                            )
                        }
                    },
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                    singleLine = true
                )
            }

            // Mismatch warning
            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.passwords_do_not_match), color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Set password button
            Button(
                onClick = {
                    when {
                        password.length < 6 -> Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        password != confirmPassword -> Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        else -> vm.setNewPasswordWithOtp(password)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).scale(btnScale),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(12.dp),
                enabled = state !is PasswordResetState.Loading
            ) {
                if (state is PasswordResetState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.set_new_password), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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