package com.protctus.app.ui.screens.features

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.protctus.app.ui.theme.ProtctusTheme
import java.io.File

class ApkQuarantineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filePath = intent.getStringExtra("FILE_PATH") ?: return finish()
        val fileName = intent.getStringExtra("FILE_NAME") ?: "Unknown App"

        // Ensure status bar is transparent or black for the dialog style
        window.statusBarColor = android.graphics.Color.BLACK

        setContent {
            ProtctusTheme {
                QuarantineDialog(
                    fileName = fileName,
                    onDismiss = { finish() },
                    onAuthorize = { showBiometricPrompt(filePath) }
                )
            }
        }
    }

    private fun showBiometricPrompt(quarantinedFilePath: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    installApk(quarantinedFilePath)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize App Installation")
            .setSubtitle("Verify identity to override security block")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun installApk(quarantinedFilePath: String) {
        val quarantinedFile = File(quarantinedFilePath)
        if (!quarantinedFile.exists()) {
            Toast.makeText(this, "File not found. It may have been removed.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Rename back to .apk
        val apkFile = File(quarantinedFile.parent, quarantinedFile.name.removeSuffix(".quarantine"))
        quarantinedFile.renameTo(apkFile)

        // Install
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch installer", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

@Composable
fun QuarantineDialog(fileName: String, onDismiss: () -> Unit, onAuthorize: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1D21), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = "Shield",
                    tint = Color(0xFFFF4C4C),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "App Installation Blocked",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Protctus detected an attempt to download '$fileName'. Unverified apps can compromise your device.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B31)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("KEEP ME SAFE", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = onAuthorize,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFFF4C4C), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AUTHORIZE INSTALL ANYWAY", color = Color(0xFFFF4C4C), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}
