package com.protctus.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.AuthState
import com.protctus.app.viewmodel.AuthViewModel
import com.protctus.app.ui.theme.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignInScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val authState by authViewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Verified -> {
                val settingsManager = com.protctus.app.data.local.SettingsManager(context)
                val isBiometricEnabled = settingsManager.isBiometricEnabled.first()
                if (isBiometricEnabled) {
                    navController.navigate(Route.BiometricUnlock) {
                        popUpTo(Route.SignIn) { inclusive = true }
                    }
                } else {
                    navController.navigate(Route.Dashboard) {
                        popUpTo(Route.SignIn) { inclusive = true }
                    }
                }
                authViewModel.resetState()
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar((authState as AuthState.Error).message)
                authViewModel.resetState()
            }
            else -> {}
        }
    }

    SignInScreenContent(
        authState = authState,
        snackbarHostState = snackbarHostState,
        onSignInClick = { email, password ->
            authViewModel.signInWithEmail(email, password)
        },
        onSignUpClick = {
            navController.navigate(Route.SignUp)
        },
        onForgotPasswordClick = {
            navController.navigate(Route.ResetPassword)
        },
        onBackClick = {
            navController.popBackStack()
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignInScreenContent(
    authState: AuthState,
    snackbarHostState: SnackbarHostState,
    onSignInClick: (String, String) -> Unit,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 🔴 MAGIC FIX 1: Preview mode check so it doesn't render blank in Android Studio
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            isVisible = true
        }
    }

    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "btnScale"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryText
                    )
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -30 }, animationSpec = tween(300))
            ) {
                Text(
                    text = "Let's get you\nsigned in!",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = PrimaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 44.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, delayMillis = 50)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(300, delayMillis = 50, easing = EaseOutBack))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sign_in),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, delayMillis = 100)) + slideInVertically(initialOffsetY = { 30 }, animationSpec = tween(300, delayMillis = 100))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$".toRegex()
                    val isEmailValid = email.isEmpty() || email.matches(emailPattern)

                    AuthInputField(
                        value = email,
                        onValueChange = { email = it },
                        hint = "Enter your email",
                        icon = Icons.Outlined.Email,
                        isError = !isEmailValid,
                        autofillType = AutofillType.EmailAddress
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AuthInputField(
                        value = password,
                        onValueChange = { password = it },
                        hint = "Enter your password",
                        icon = Icons.Outlined.Lock,
                        isPassword = true,
                        autofillType = AutofillType.Password
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = stringResource(R.string.forgot_password),
                            color = PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onForgotPasswordClick() }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val isLoading = authState is AuthState.Loading
                    val isFormValid = email.isNotEmpty() && password.isNotEmpty() && email.matches(emailPattern)

                    val buttonBgColor by animateColorAsState(
                        targetValue = if (isFormValid) PrimaryText else SurfaceColor,
                        animationSpec = tween(300), label = "btnBg"
                    )
                    val buttonTextColor by animateColorAsState(
                        targetValue = if (isFormValid) DarkBackground else SecondaryText,
                        animationSpec = tween(300), label = "btnText"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(buttonScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(buttonBgColor)
                            .clickable(
                                interactionSource = buttonInteractionSource,
                                indication = null,
                                enabled = isFormValid && !isLoading
                            ) {
                                onSignInClick(email, password)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = stringResource(R.string.sign_in),
                                color = buttonTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val signUpText = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Normal)) {
                            append("Don't have an account yet? ")
                        }
                        withStyle(style = SpanStyle(color = PrimaryText, fontWeight = FontWeight.Bold)) {
                            append("Sign up")
                        }
                    }
                    Text(
                        text = signUpText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSignUpClick() }
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isError: Boolean = false,
    autofillType: AutofillType? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val autofillNode = remember {
        autofillType?.let {
            AutofillNode(
                autofillTypes = listOf(it),
                onFill = { filledValue -> onValueChange(filledValue) }
            )
        }
    }

    LaunchedEffect(autofillNode) {
        autofillNode?.let {
            autofillTree.plusAssign(it)
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (isError) ErrorColor else if (isFocused) PrimaryText else OutlineColor,
        animationSpec = tween(300), label = "borderColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isError) ErrorColor else if (isFocused) PrimaryText else AccentColor,
        animationSpec = tween(300), label = "iconColor"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = PrimaryText, fontSize = 15.sp),
        singleLine = true,
        cursorBrush = SolidColor(PrimaryText),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .onGloballyPositioned { layoutCoordinates ->
                autofillNode?.boundingBox = layoutCoordinates.boundsInWindow()
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    autofillNode?.let { autofill?.requestAutofillForNode(it) }
                } else {
                    autofillNode?.let { autofill?.cancelAutofillForNode(it) }
                }
            },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = SecondaryText,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
                if (isPassword) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle Password Visibility",
                            tint = AccentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewSignInScreen() {
    ProtctusTheme {
        SignInScreenContent(
            authState = AuthState.Idle,
            snackbarHostState = remember { SnackbarHostState() },
            onSignInClick = { _, _ -> },
            onSignUpClick = {},
            onForgotPasswordClick = {}
        )
    }
}