package com.protctus.app.ui.screens.settings

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.SettingsViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(navController: NavController) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val appLanguage by settingsViewModel.appLanguage.collectAsState()
    val isDataSharingEnabled by settingsViewModel.isDataSharingEnabled.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current

    // Delete account confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_account_1), color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.this_will_permanently_delete_all_your_lo), color = Color(0xFFA0A0A0)) },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.deleteAccount()
                    showDeleteDialog = false
                    navController.navigate(Route.SignIn) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text(stringResource(R.string.delete), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
            },
            containerColor = com.protctus.app.ui.theme.SurfaceColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = com.protctus.app.ui.theme.DarkBackground
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.app_language), 
                    style = MaterialTheme.typography.titleLarge, 
                    color = Color.White,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp, top = 8.dp)
                )
                
                val languages = listOf(
                    "en" to "English",
                    "hi" to "Hindi (हिंदी)",
                    "kn" to "Kannada (ಕನ್ನಡ)",
                    "bn" to "Bengali (বাংলা)",
                    "ta" to "Tamil (தமிழ்)",
                    "te" to "Telugu (తెలుగు)",
                    "or" to "Odia (ଓଡ଼ିଆ)"
                )
                
                languages.forEach { (code, name) ->
                    Text(
                        text = name,
                        color = if (appLanguage == code) Color.White else Color(0xFFE2E2E2),
                        fontWeight = if (appLanguage == code) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsViewModel.updateLanguage(code)
                                showLanguageSheet = false
                            }
                            .padding(vertical = 16.dp, horizontal = 24.dp)
                    )
                    HorizontalDivider(color = Color(0xFF333333))
                }
            }
        }
    }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    text = stringResource(R.string.preferences),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            PreferenceToggleItem(
                title = stringResource(R.string.push_notifications),
                icon = Icons.Outlined.Notifications,
                checked = settings.notificationsEnabled,
                onCheckedChange = { settingsViewModel.toggleNotifications() }
            )

            PreferenceToggleItem(
                title = stringResource(R.string.data_sharing),
                icon = Icons.Outlined.CloudUpload,
                checked = isDataSharingEnabled,
                onCheckedChange = { settingsViewModel.toggleDataSharing() }
            )

            PreferenceOptionItem(
                title = stringResource(R.string.language),
                icon = Icons.Outlined.Language,
                onClick = { showLanguageSheet = true }
            )

            val coroutineScope = rememberCoroutineScope()

            // Download all data — real export to Downloads
            PreferenceOptionItem(
                title = stringResource(R.string.download_data),
                icon = Icons.Outlined.CloudDownload,
                onClick = {
                    coroutineScope.launch {
                        try {
                            val db = com.protctus.app.data.local.AppDatabase.getInstance(context)
                            val threats = db.threatDao().getAllThreats().firstOrNull() ?: emptyList()
                            val searches = db.recentSearchDao().getRecentSearches().firstOrNull() ?: emptyList()

                            // Export settings + profile as JSON to Downloads
                            val json = JSONObject().apply {
                                val p = JSONObject().apply {
                                    put("displayName", profile.displayName)
                                    put("username", profile.username)
                                    put("email", profile.email)
                                    put("phone", profile.phone)
                                    put("rank", profile.rank)
                                }
                                put("profile", p)

                                val sObj = JSONObject().apply {
                                    put("notificationsEnabled", settings.notificationsEnabled)
                                    put("biometricLoginEnabled", settings.biometricLoginEnabled)
                                    put("twoFactorEnabled", settings.twoFactorEnabled)
                                }
                                put("settings", sObj)

                                val tl = org.json.JSONArray()
                                for (t in threats) {
                                    val tObj = JSONObject().apply {
                                        put("id", t.id)
                                        put("type", t.threatType)
                                        put("title", t.title)
                                        put("description", t.description)
                                        put("timestamp", t.timestamp)
                                        put("priority", t.priority)
                                    }
                                    tl.put(tObj)
                                }
                                put("security_log_threats", tl)

                                val sl = org.json.JSONArray()
                                for (s in searches) {
                                    val searchObj = JSONObject().apply {
                                        put("id", s.id)
                                        put("query", s.query)
                                        put("timestamp", s.timestamp)
                                    }
                                    sl.put(searchObj)
                                }
                                put("search_history", sl)
                            }
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, "protctus_security_export_${System.currentTimeMillis()}.json")
                                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            uri?.let {
                                context.contentResolver.openOutputStream(it)?.use { os ->
                                    os.write(json.toString(2).toByteArray())
                                }
                                Toast.makeText(context, "Security Data exported to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // Delete account — real with confirmation
            PreferenceOptionItem(
                title = stringResource(R.string.delete_account_1),
                icon = Icons.Outlined.DeleteOutline,
                isDestructive = true,
                showDivider = false,
                onClick = { showDeleteDialog = true }
            )
        }
    }
}

@Composable
fun PreferenceToggleItem(
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
fun PreferenceOptionItem(
    title: String,
    icon: ImageVector,
    showDivider: Boolean = true,
    isDestructive: Boolean = false,
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

    val itemColor = if (isDestructive) Color(0xFFEF4444) else Color(0xFFE2E2E2)
    val iconTint = if (isDestructive) Color(0xFFEF4444) else Color.White
    val arrowTint = if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.7f) else Color(0xFF7F909F)

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
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = itemColor,
                fontSize = 15.sp,
                fontWeight = if (isDestructive) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Go",
                tint = arrowTint,
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