package com.protctus.app.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.viewmodel.SettingsViewModel
import com.protctus.app.navigation.Route

@Composable
fun AccountSecurityScreen(navController: NavController) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val isBiometricEnabled by settingsViewModel.isBiometricEnabled.collectAsState()
    val isRealTimeEnabled by settingsViewModel.isRealTimeProtectionEnabled.collectAsState()
    val isPhishingEnabled by settingsViewModel.isPhishingShieldEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    
    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            settingsViewModel.setPhishingShieldEnabled(true)
            val vpnIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, vpnIntent)
        } else {
            settingsViewModel.setPhishingShieldEnabled(false)
        }
    }

    // Current device info
    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(com.protctus.app.ui.theme.DarkBackground)
        ) {
            
            Spacer(modifier = Modifier.height(28.dp))

            // Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { navController.popBackStack() }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(R.string.account_security),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Toggle items backed by Room DB
            SecurityToggleItem(
                title = "Phishing Shield (VPN)",
                icon = Icons.Outlined.VpnLock,
                checked = isPhishingEnabled,
                onCheckedChange = { _ ->
                    if (!isPhishingEnabled) {
                        val intent = android.net.VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            settingsViewModel.setPhishingShieldEnabled(true)
                            val vpnIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
                            androidx.core.content.ContextCompat.startForegroundService(context, vpnIntent)
                        }
                    } else {
                        settingsViewModel.setPhishingShieldEnabled(false)
                        val stopIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java).apply {
                            action = "STOP_VPN"
                        }
                        context.startService(stopIntent)
                    }
                }
            )

            SecurityToggleItem(
                title = "Real-Time Protection",
                icon = Icons.Outlined.GppGood,
                checked = isRealTimeEnabled,
                onCheckedChange = { settingsViewModel.toggleRealTimeProtection(context) }
            )

            SecurityToggleItem(
                title = "Biometric Login",
                icon = Icons.Outlined.Fingerprint,
                checked = isBiometricEnabled,
                onCheckedChange = { settingsViewModel.toggleBiometricLogin() }
            )

            SecurityOptionItem(
                title = "Password Change/Reset",
                icon = Icons.Outlined.Lock,
                onClick = { navController.navigate(Route.ResetPassword) }
            )

            SecurityOptionItem(
                title = "Change Email",
                icon = Icons.Outlined.Email,
                onClick = { navController.navigate(Route.ChangeEmail) }
            )

            // Device info — real
            SecurityInfoItem(
                title = "Current Device",
                subtitle = deviceName,
                icon = Icons.Outlined.Devices
            )
        }
    }
}

@Composable
fun SecurityToggleItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = Color(0xFFE2E2E2),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4A5568),
                    uncheckedThumbColor = Color(0xFF7F909F),
                    uncheckedTrackColor = com.protctus.app.ui.theme.SurfaceColor
                )
            )
        }

        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
    }
}

@Composable
fun SecurityOptionItem(
    title: String,
    icon: ImageVector,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "itemScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF222226) else Color.Transparent,
        animationSpec = tween(200), label = "itemBg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = Color(0xFFE2E2E2),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Go",
                tint = Color(0xFF7F909F),
                modifier = Modifier.size(20.dp)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF333333),
                thickness = 1.dp
            )
        }
    }
}

@Composable
fun SecurityInfoItem(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFFE2E2E2),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF7F909F),
                    fontSize = 12.sp
                )
            }
        }
    }
}

