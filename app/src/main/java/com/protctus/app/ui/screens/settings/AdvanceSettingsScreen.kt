package com.protctus.app.ui.screens.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import com.protctus.app.data.local.SettingsManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.ui.theme.DarkBackground
import com.protctus.app.ui.theme.SurfaceColor

@Composable
fun AdvanceSettingsScreen(navController: NavController) {
    val interactionSource = remember { MutableInteractionSource() }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val isJuiceJackingEnabled by settingsManager.isJuiceJackingEnabled.collectAsState(initial = false)
    val isUsbProtectionEnabled by settingsManager.isUsbProtectionEnabled.collectAsState(initial = false)
    val isGhostAppDetectorEnabled by settingsManager.isGhostAppDetectorEnabled.collectAsState(initial = false)
    val isHeuristicsEnabled by settingsManager.isHeuristicsEnabled.collectAsState(initial = false)
    val isInvisibleWatcherEnabled by settingsManager.isInvisibleWatcherEnabled.collectAsState(initial = false)

    Scaffold(
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBackground)
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
                    text = stringResource(R.string.advance_settings),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AdvanceToggleItem(
                title = "Juice Jacking Detection",
                icon = Icons.Outlined.BatteryAlert,
                checked = isJuiceJackingEnabled,
                onCheckedChange = { coroutineScope.launch { settingsManager.setJuiceJackingEnabled(it) } }
            )

            AdvanceToggleItem(
                title = "USB Protection",
                icon = Icons.Outlined.Usb,
                checked = isUsbProtectionEnabled,
                onCheckedChange = { coroutineScope.launch { settingsManager.setUsbProtectionEnabled(it) } }
            )

            AdvanceToggleItem(
                title = "Heuristics Malware Engine",
                icon = Icons.Outlined.BugReport,
                checked = isHeuristicsEnabled,
                onCheckedChange = { coroutineScope.launch { settingsManager.setHeuristicsEnabled(it) } }
            )

            AdvanceToggleItem(
                title = "Ghost App Detector (Stalkerware)",
                icon = Icons.Outlined.VisibilityOff,
                checked = isGhostAppDetectorEnabled,
                onCheckedChange = { coroutineScope.launch { settingsManager.setGhostAppDetectorEnabled(it) } }
            )

            AdvanceToggleItem(
                title = "The Invisible Watcher (Anti-Screen Share)",
                icon = Icons.AutoMirrored.Outlined.ScreenShare,
                checked = isInvisibleWatcherEnabled,
                onCheckedChange = { coroutineScope.launch { settingsManager.setInvisibleWatcherEnabled(it) } }
            )
        }
    }
}

@Composable
fun AdvanceToggleItem(
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
                    checkedTrackColor = com.protctus.app.ui.theme.AccentColor,
                    uncheckedThumbColor = Color(0xFF7F909F),
                    uncheckedTrackColor = SurfaceColor
                )
            )
        }

        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
    }
}
