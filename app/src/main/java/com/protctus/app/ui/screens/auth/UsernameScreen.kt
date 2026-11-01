package com.protctus.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusPrimaryButton
import com.protctus.app.ui.components.ProtctusTextField
import com.protctus.app.viewmodel.AuthViewModel
import com.protctus.app.viewmodel.UsernameState
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth

@Composable
fun UsernameScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val usernameState by authViewModel.usernameState.collectAsState()

    var username by remember { mutableStateOf("") }
    
    // Username state is now purely manual input
    val interactionSource = remember { MutableInteractionSource() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(usernameState) {
        when (usernameState) {
            is UsernameState.Available -> {
                authViewModel.saveUsername(username)
            }
            is UsernameState.Saved -> {
                navController.navigate(Route.PlanSelection) {
                    popUpTo(Route.Username) { inclusive = true }
                }
                authViewModel.resetUsernameState()
            }
            is UsernameState.Taken -> {
                snackbarHostState.showSnackbar("Username already taken")
                authViewModel.resetUsernameState()
            }
            is UsernameState.Invalid -> {
                snackbarHostState.showSnackbar("Only letters, numbers, and underscores (3-20 chars)")
                authViewModel.resetUsernameState()
            }
            is UsernameState.Error -> {
                snackbarHostState.showSnackbar((usernameState as UsernameState.Error).message)
                authViewModel.resetUsernameState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(com.protctus.app.ui.theme.DarkBackground)
                .padding(horizontal = 24.dp),
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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.username),
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFFFFFFF),
                letterSpacing = 0.5.sp,
                lineHeight = 40.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.choose_a_unique_username_for_your_accoun),
                fontSize = 14.sp,
                color = Color(0xFF7F909F),
                textAlign = TextAlign.Start,
                lineHeight = 24.sp,
                letterSpacing = 0.2.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(64.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.username),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFFFFF),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ProtctusTextField(
                    value = username,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isLetterOrDigit() || it == '_' }
                        if (sanitized.length <= 20) {
                            username = sanitized
                        }
                    },
                    hint = "Set Your Username",
                    leadingIcon = {
                        Text(
                            text = stringResource(R.string.str_14135),
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7F909F)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val isLoading = usernameState is UsernameState.Checking

            if (isLoading) {
                CircularProgressIndicator(
                    color = com.protctus.app.ui.theme.PrimaryText,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                    ProtctusPrimaryButton(
                        text = "Continue",
                        onClick = {
                            if (username.isNotBlank()) {
                                authViewModel.validateUsername(username)
                            }
                        }
                    )
                }
            }
        }
    }
}
