package com.protctus.app.ui.screens.settings

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.protctus.app.R
import com.protctus.app.ui.theme.*
import com.protctus.app.utils.AppVersionResponse
import com.protctus.app.utils.UpdateManager
import kotlinx.coroutines.launch

sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()
    data object Checking : UpdateCheckUiState()
    data object UpToDate : UpdateCheckUiState()
    data class Available(val info: AppVersionResponse) : UpdateCheckUiState()
    data class Error(val message: String) : UpdateCheckUiState()
    data object Downloading : UpdateCheckUiState()
}

/**
 * Builds a Coil ImageLoader with SVG decoder support.
 */
@Composable
private fun rememberSvgImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}

/**
 * Loads an SVG from the assets/ folder using Coil SVG decoder.
 */
@Composable
private fun SvgIllustration(
    assetFileName: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = rememberSvgImageLoader()
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/$assetFileName")
            .build(),
        imageLoader = imageLoader
    )
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun UpdateCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    var uiState by remember { mutableStateOf<UpdateCheckUiState>(UpdateCheckUiState.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        uiState = UpdateCheckUiState.Checking
        updateManager.checkForUpdates(showToastIfUpToDate = false) { result ->
            uiState = when (result) {
                is UpdateManager.UpdateCheckResult.Available -> UpdateCheckUiState.Available(result.info)
                is UpdateManager.UpdateCheckResult.UpToDate -> UpdateCheckUiState.UpToDate
                is UpdateManager.UpdateCheckResult.Error -> UpdateCheckUiState.Error(result.message)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBackground)
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Software Update",
                        color = PrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, "Close", tint = AccentColor, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                Spacer(Modifier.height(20.dp))

                // ── State Content ──
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
                    label = "update_state"
                ) { state ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (state) {
                            is UpdateCheckUiState.Idle,
                            is UpdateCheckUiState.Checking -> CheckingContent()
                            is UpdateCheckUiState.UpToDate -> UpToDateContent()
                            is UpdateCheckUiState.Available -> AvailableContent(
                                info = state.info,
                                onDownload = {
                                    uiState = UpdateCheckUiState.Downloading
                                    updateManager.downloadAndInstall(
                                        state.info.download_url,
                                        state.info.version_name ?: "v${state.info.version_code}"
                                    )
                                }
                            )
                            is UpdateCheckUiState.Error -> ErrorContent(
                                message = state.message,
                                onRetry = {
                                    uiState = UpdateCheckUiState.Checking
                                    scope.launch {
                                        updateManager.checkForUpdates(false) { result ->
                                            uiState = when (result) {
                                                is UpdateManager.UpdateCheckResult.Available ->
                                                    UpdateCheckUiState.Available(result.info)
                                                is UpdateManager.UpdateCheckResult.UpToDate ->
                                                    UpdateCheckUiState.UpToDate
                                                is UpdateManager.UpdateCheckResult.Error ->
                                                    UpdateCheckUiState.Error(result.message)
                                            }
                                        }
                                    }
                                }
                            )
                            is UpdateCheckUiState.Downloading -> DownloadingContent()
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════
//  1. CHECKING
// ═════════════════════════════════════
@Composable
private fun CheckingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SvgIllustration(
            assetFileName = "Upgrading.svg",
            contentDescription = "Checking for updates",
            modifier = Modifier.size(160.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Checking for Updates",
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Connecting to Protctus servers…",
            color = AccentColor,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = AccentColor,
            trackColor = SurfaceColor
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ═════════════════════════════════════
//  2. UP TO DATE
// ═════════════════════════════════════
@Composable
private fun UpToDateContent() {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SvgIllustration(
            assetFileName = "404 Not Found.svg",
            contentDescription = "Up to date",
            modifier = Modifier.size(180.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "You're Up to Date",
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Version $versionName",
            color = SuccessColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(20.dp))

        // Status card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusRow(Icons.Outlined.Shield, "Security Patches", "Current", SuccessColor)
            StatusRow(Icons.Outlined.Memory, "Scan Engine", "Latest", SuccessColor)
            StatusRow(Icons.Outlined.Storage, "Threat Database", "Synced", SuccessColor)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ═════════════════════════════════════
//  3. UPDATE AVAILABLE
// ═════════════════════════════════════
@Composable
private fun AvailableContent(info: AppVersionResponse, onDownload: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SvgIllustration(
            assetFileName = "Upgrading.svg",
            contentDescription = "Update available",
            modifier = Modifier.size(160.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "New Update Available",
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        // Version badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PremiumGold.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.NewReleases, null, tint = PremiumGold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "v${info.version_name ?: info.version_code}",
                color = PremiumGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Release notes
        if (!info.release_notes.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceColor)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Article, null, tint = AccentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("What's New", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(info.release_notes!!, color = AccentColor, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Download button
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceColor,
                contentColor = PrimaryText
            )
        ) {
            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download & Install", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        if (info.is_critical == 1) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, tint = ErrorColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Critical Security Update", color = ErrorColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ═════════════════════════════════════
//  4. DOWNLOADING (uses Upgrading.svg from assets)
// ═════════════════════════════════════
@Composable
private fun DownloadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ── SVG illustration from assets ──
        SvgIllustration(
            assetFileName = "Upgrading.svg",
            contentDescription = "Upgrading",
            modifier = Modifier.size(180.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "Installing Update…",
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(6.dp))
        Text(
            "Check notification bar for download progress",
            color = AccentColor,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Pill-shaped loading indicator
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = AccentColor,
            trackColor = SurfaceColor
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ═════════════════════════════════════
//  5. ERROR
// ═════════════════════════════════════
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SvgIllustration(
            assetFileName = "Bad Gateway.svg",
            contentDescription = "Error",
            modifier = Modifier.size(180.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text("Connection Failed", color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = AccentColor, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF333333))
            )
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = PrimaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ═════════════════════════════════════
//  SHARED: Status Row
// ═════════════════════════════════════
@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = PrimaryText.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(valueColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
