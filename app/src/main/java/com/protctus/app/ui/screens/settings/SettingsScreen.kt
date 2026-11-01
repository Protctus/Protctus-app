package com.protctus.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.SettingsViewModel
import com.protctus.app.viewmodel.MainActivityViewModel

@Composable
fun SettingsScreen(navController: NavController) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    val mainViewModel: MainActivityViewModel = androidx.hilt.navigation.compose.hiltViewModel(activity)
    val settings by settingsViewModel.settings.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Update Check Dialog
    if (showUpdateDialog) {
        UpdateCheckDialog(onDismiss = { showUpdateDialog = false })
    }

    Scaffold(
        containerColor = Color(0xFF1C1919)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(56.dp))

                SettingsItem("Edit Profile", Icons.Outlined.ManageAccounts) { navController.navigate(Route.EditProfile) }
                SettingsDivider()

                SettingsItem("Account Security", Icons.Outlined.AdminPanelSettings) { navController.navigate(Route.AccountSecurity) }
                SettingsDivider()

                SettingsToggleItem(
                    title = "Notifications",
                    icon = Icons.Outlined.NotificationsNone,
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { settingsViewModel.toggleNotifications() }
                )
                SettingsDivider()

                SettingsItem("Referral Program", Icons.Outlined.CardGiftcard) { navController.navigate(Route.Referral) }
                SettingsDivider()


                SettingsItem("Premium Features", Icons.Outlined.WorkspacePremium) { navController.navigate(Route.GetPremium) }
                SettingsDivider()

                SettingsItem("Privacy & Preferences", Icons.Outlined.PrivacyTip) { navController.navigate(Route.Preferences) }
                SettingsDivider()

                SettingsItem("Terms of Service", Icons.Outlined.Gavel) { navController.navigate(Route.TermsOfService) }
                SettingsDivider()

                SettingsItem("Privacy Policy", Icons.Outlined.Policy) { navController.navigate(Route.PrivacyPolicy) }
                SettingsDivider()

                SettingsItem("Check for Updates", Icons.Outlined.SystemUpdate) { showUpdateDialog = true }
                SettingsDivider()

                SettingsItem("Help Center", Icons.AutoMirrored.Outlined.HelpOutline) { navController.navigate(Route.HelpCenter) }
                SettingsDivider()
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))

                Button(
                    onClick = {
                        settingsViewModel.logout()
                        navController.navigate(Route.SignIn) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.protctus.app.ui.theme.SurfaceColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.logout), color = Color(0xFFE2E2E2), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector?, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(20.dp))
            } else {
                Spacer(modifier = Modifier.width(44.dp))
            }
            Text(text = title, color = Color(0xFFE2E2E2), fontSize = 16.sp)
        }
        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Navigate", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SettingsToggleItem(title: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Text(text = title, color = Color(0xFFE2E2E2), fontSize = 16.sp)
        }
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
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp, modifier = Modifier.fillMaxWidth().padding(start = 44.dp))
}
