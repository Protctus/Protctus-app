package com.protctus.app.ui.screens.auth

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.protctus.app.navigation.Route

@Composable
fun BiometricUnlockScreen(navController: NavController) {
    val context = LocalContext.current
    val cancelText = stringResource(R.string.cancel)

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
                        // If user cancels or fails too many times, we can show a retry button or close app
                        Toast.makeText(context, "Authentication necessary: $errString", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Unlock successful, go to Home
                        navController.navigate(Route.Dashboard) {
                            popUpTo(Route.BiometricUnlock) { inclusive = true }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Fingerprint not recognized", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Protctus")
                .setSubtitle("Confirm your fingerprint to access")
                .setNegativeButtonText(cancelText)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(context, "Biometric system not available", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        showBiometricPrompt()
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
            // A blank background while the OS bottom sheet authenticates.
            // A secondary "Unlock" button is rendered here only if they cancel the prompt
            androidx.compose.material3.TextButton(onClick = { showBiometricPrompt() }) {
                androidx.compose.material3.Text(stringResource(R.string.tap_to_unlock), color = Color.White)
            }
        }
    }
}
