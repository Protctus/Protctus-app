package com.protctus.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.ui.components.ProtctusPrimaryButton
import com.protctus.app.ui.components.ProtctusTextField

@Composable
fun SetPasswordScreen(
    navController: NavController,
    authViewModel: com.protctus.app.viewmodel.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel(viewModelStoreOwner = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity)
) {
    var password by remember { mutableStateOf("") }
    var retypePassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var retypePasswordVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.imePadding()) },
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(com.protctus.app.ui.theme.DarkBackground)
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
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

                Spacer(modifier = Modifier.height(24.dp))

                Image(
                    painter = painterResource(id = com.protctus.app.R.drawable.set_password),
                    contentDescription = "Set Password Illustration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.set_your_password),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFFFFFFF),
                    letterSpacing = 0.5.sp,
                    lineHeight = 40.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.password),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ProtctusTextField(
                        value = password,
                        onValueChange = { password = it },
                        hint = "Enter your password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Password",
                                tint = Color(0xFF7F909F),
                                modifier = Modifier.padding(start = 24.dp, end = 12.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = "Toggle Password Visibility",
                                tint = Color(0xFF7F909F),
                                modifier = Modifier
                                    .padding(end = 24.dp)
                                    .size(22.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { passwordVisible = !passwordVisible }
                            )
                        }
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.password_strength), color = Color(0xFF8E8E93), fontSize = 12.sp)
                        Text(text = strengthText, color = strengthColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 1) strengthColor else Color(0xFF333333)))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 2) strengthColor else Color(0xFF333333)))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (strengthScore >= 3) strengthColor else Color(0xFF333333)))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.re_type_password_1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ProtctusTextField(
                        value = retypePassword,
                        onValueChange = { retypePassword = it },
                        hint = "Re-enter your password",
                        visualTransformation = if (retypePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Password",
                                tint = Color(0xFF7F909F),
                                modifier = Modifier.padding(start = 24.dp, end = 12.dp).size(22.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = if (retypePasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = "Toggle Password Visibility",
                                tint = Color(0xFF7F909F),
                                modifier = Modifier
                                    .padding(end = 24.dp)
                                    .size(22.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { retypePasswordVisible = !retypePasswordVisible }
                            )
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val authState by authViewModel.authState.collectAsState()
                
                LaunchedEffect(authState) {
                    when (authState) {
                        is com.protctus.app.viewmodel.AuthState.OtpSent -> {
                            navController.navigate(com.protctus.app.navigation.Route.Verification)
                            authViewModel.resetState()
                        }
                        is com.protctus.app.viewmodel.AuthState.Error -> {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar((authState as com.protctus.app.viewmodel.AuthState.Error).message)
                            }
                            authViewModel.resetState()
                        }
                        else -> {}
                    }
                }

                val isLoading = authState is com.protctus.app.viewmodel.AuthState.Loading
                val isCooldown = authState is com.protctus.app.viewmodel.AuthState.OtpCooldown
                
                val buttonText = when {
                    isLoading -> "Verifying..."
                    isCooldown -> "Wait ${(authState as com.protctus.app.viewmodel.AuthState.OtpCooldown).secondsLeft}s"
                    else -> "Verify"
                }

                ProtctusPrimaryButton(
                    text = buttonText,
                    onClick = {
                        if (password != retypePassword) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Passwords do not match") }
                            return@ProtctusPrimaryButton
                        }
                        if (password.length < 6) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Password must be at least 6 characters") }
                            return@ProtctusPrimaryButton
                        }
                        
                        val email = authViewModel.pendingSignUpEmail
                        if (email.isBlank()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Email is missing. Please go back.") }
                            return@ProtctusPrimaryButton
                        }

                        authViewModel.initiateSignUp(email, password)
                    },
                    enabled = !isLoading && !isCooldown && password.length >= 6 && password == retypePassword
                )
            }
        }
    }
}