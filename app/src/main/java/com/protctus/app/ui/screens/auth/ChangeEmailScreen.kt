package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.viewmodel.PasswordResetState
import com.protctus.app.viewmodel.PasswordResetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeEmailScreen(navController: NavController) {
    val vm: PasswordResetViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var currentPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // ── Entrance animations ──
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val headerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500), label = "hAlpha"
    )
    val formScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "fScale"
    )
    val formAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200), label = "fAlpha"
    )

    // Handle results
    LaunchedEffect(state) {
        when (state) {
            is PasswordResetState.EmailChangeSuccess -> {
                val msg = (state as PasswordResetState.EmailChangeSuccess).message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                vm.fullReset()
                navController.popBackStack()
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
                text = stringResource(R.string.change_email),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A verification link will be sent to your\nnew email address",
                fontSize = 14.sp,
                color = Color(0xFFA0A0A0),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.alpha(headerAlpha)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Current password for re-authentication
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(formScale)
                    .alpha(formAlpha)
            ) {
                Text(stringResource(R.string.current_password), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    placeholder = { Text(stringResource(R.string.enter_current_password), color = Color(0xFF71717A)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF71717A)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Color(0xFF71717A)
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

            Spacer(modifier = Modifier.height(16.dp))

            // New email field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(formScale)
                    .alpha(formAlpha)
            ) {
                Text(stringResource(R.string.new_email_address), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    placeholder = { Text(stringResource(R.string.enter_new_email), color = Color(0xFF71717A)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF71717A)) },
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

            Button(
                onClick = {
                    when {
                        currentPassword.isBlank() -> Toast.makeText(context, "Enter current password", Toast.LENGTH_SHORT).show()
                        newEmail.isBlank() || !newEmail.contains("@") -> Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                        else -> vm.changeEmail(currentPassword, newEmail)
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
                    Text(stringResource(R.string.change_email), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
