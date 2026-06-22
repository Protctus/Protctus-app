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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
fun SignUpScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    SignUpContent(
        authState = authState,
        onSignUpClick = { email, referralCode ->
            authViewModel.setPendingSignUpEmail(email)
            if (referralCode.isNotBlank()) {
                authViewModel.setPendingReferralCode(referralCode)
            }
            navController.navigate(Route.SetPassword)
        },
        onNavigateToVerification = {
            navController.navigate(Route.Verification)
        },
        onNavigateToSignIn = {
            navController.navigate(Route.SignIn) {
                popUpTo(Route.SignUp) { inclusive = true }
            }
        },
        onResetState = {
            authViewModel.resetState()
        },
        onBackClick = {
            navController.popBackStack()
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignUpContent(
    authState: AuthState,
    onSignUpClick: (String, String) -> Unit,
    onNavigateToVerification: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onResetState: () -> Unit,
    onBackClick: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.OtpSent -> {
                onNavigateToVerification()
                onResetState()
            }
            is AuthState.Error -> {
                snackbarHostState.showSnackbar((authState as AuthState.Error).message)
                onResetState()
            }
            else -> {}
        }
    }

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
            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, delayMillis = 50)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(300, delayMillis = 50, easing = EaseOutBack))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sign_up),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -30 }, animationSpec = tween(300))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.create_account),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        color = PrimaryText,
                        letterSpacing = 0.5.sp,
                        lineHeight = 40.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Enter your email address to receive\na secure OTP verification code.",
                        fontSize = 14.sp,
                        color = AccentColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300, delayMillis = 100)) + slideInVertically(initialOffsetY = { 30 }, animationSpec = tween(300, delayMillis = 100))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$".toRegex()
                    val isEmailValid = email.isEmpty() || email.matches(emailPattern)

                    AuthInputFieldLocal(
                        value = email,
                        onValueChange = { email = it },
                        hint = "Enter Email Address",
                        icon = Icons.Outlined.Email,
                        isError = !isEmailValid,
                        autofillType = AutofillType.EmailAddress
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional referral code
                    AuthInputFieldLocal(
                        value = referralCode,
                        onValueChange = { referralCode = it.uppercase().take(8) },
                        hint = "Referral Code (optional)",
                        icon = Icons.Outlined.CardGiftcard,
                        isError = false
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val isLoading = authState is AuthState.Loading
                    val isFormValid = email.isNotEmpty() && email.matches(emailPattern)

                    // Button Colors strictly from your palette
                    val buttonBgColor by animateColorAsState(
                        targetValue = if (isFormValid) PrimaryText else SurfaceColor,
                        animationSpec = tween(300), label = "btnBg"
                    )
                    val buttonTextColor by animateColorAsState(
                        targetValue = if (isFormValid) DarkBackground else SecondaryText,
                        animationSpec = tween(300), label = "btnText"
                    )

                    val buttonScale by animateFloatAsState(
                        targetValue = if (isButtonPressed) 0.97f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ), label = "btnScale"
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
                                onSignUpClick(email, referralCode)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = stringResource(R.string.continue_btn),
                                color = buttonTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val signInText = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = AccentColor, fontWeight = FontWeight.Normal)) {
                            append("Already have an account? ")
                        }
                        withStyle(style = SpanStyle(color = PrimaryText, fontWeight = FontWeight.Bold)) {
                            append("Sign in")
                        }
                    }
                    Text(
                        text = signInText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigateToSignIn() }
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AuthInputFieldLocal(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: ImageVector,
    isError: Boolean = false,
    autofillType: AutofillType? = null
) {
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

    // Border Colors strictly from your palette
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
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewSignUpScreen() {
    ProtctusTheme {
        SignUpContent(
            authState = AuthState.Idle,
            onSignUpClick = { _, _ -> },
            onNavigateToVerification = {},
            onNavigateToSignIn = {},
            onResetState = {}
        )
    }
}