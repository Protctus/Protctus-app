package com.protctus.app.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.ui.theme.ProtctusTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.protctus.app.viewmodel.DeepScanViewModel
import com.protctus.app.viewmodel.DeepScanState as VmDeepScanState
import com.protctus.app.viewmodel.Recommendation

enum class DeepScanState {
    IDLE, SCANNING, PAUSED, COMPLETED
}

@Composable
fun DeepScanScreen(navController: NavController) {
    val viewModel: DeepScanViewModel = hiltViewModel()
    val vmState by viewModel.scanState.collectAsState()
    val context = LocalContext.current

    // Permission launcher for Android < 11
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.runPreScanProfiler()
        } else {
            android.widget.Toast.makeText(context, "Permission denied. Deep Scan cannot proceed.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Settings launcher for Android 11+ (MANAGE_EXTERNAL_STORAGE)
    val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (viewModel.hasFilePermission()) {
            viewModel.runPreScanProfiler()
        } else {
            android.widget.Toast.makeText(context, "All Files Access not granted.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Bridge ViewModel state to the local UI enum so all existing UI code works unchanged
    val scanState = when (vmState) {
        is VmDeepScanState.Idle -> DeepScanState.IDLE
        is VmDeepScanState.Profiling -> DeepScanState.SCANNING
        is VmDeepScanState.ProfileReady -> DeepScanState.IDLE
        is VmDeepScanState.Scanning -> DeepScanState.SCANNING
        is VmDeepScanState.Paused -> DeepScanState.PAUSED
        is VmDeepScanState.Completed -> DeepScanState.COMPLETED
    }

    // Extract real progress data from ViewModel
    val scanProgress = (vmState as? VmDeepScanState.Scanning)?.progress ?: 0f
    val currentItem = (vmState as? VmDeepScanState.Scanning)?.currentItem ?: ""
    val threatsFound = (vmState as? VmDeepScanState.Scanning)?.threatsFound ?: 0
    val completedThreats = (vmState as? VmDeepScanState.Completed)?.threats ?: emptyList()
    val totalScanned = (vmState as? VmDeepScanState.Completed)?.totalScanned ?: 0
    val systemProfile = (vmState as? VmDeepScanState.ProfileReady)?.profile

    var showWarningDialog by remember { mutableStateOf(false) }

    // Auto-show warning dialog when profiler finishes with real system data
    // And mark quest complete when scan finishes
    LaunchedEffect(vmState) {
        if (vmState is VmDeepScanState.ProfileReady) {
            showWarningDialog = true
        } else if (vmState is VmDeepScanState.Completed) {
            com.protctus.app.utils.QuestTracker.markTaskCompleted(context, "deep_scan")
        }
    }

    val isAnimating = scanState == DeepScanState.SCANNING

    val infiniteTransition = rememberInfiniteTransition(label = "scan_transition")
    
    // Smooth pulsing alpha for text
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isAnimating) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_alpha"
    )

    // Ripple 1
    val ripple1Radius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ripple_1"
    )
    
    // Ripple 2
    val ripple2Radius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, delayMillis = 1250, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ripple_2"
    )

    ProtctusTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1919)) // Exactly from DarkWebScanScreen Background
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar exactly like DarkWebScanScreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.deep_scan),
                        color = Color.White,
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.height(70.dp))

                // Elegant Smooth Ripple Animation
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val maxRadius = size.width / 2f
                        val coreRadius = 50f
                        
                        val accentColor = Color(0xFFE8F3F1)
                        val trackColor = Color(0xFF636A7D)

                        // Smooth Ripples
                        if (isAnimating) {
                            drawCircle(
                                color = trackColor.copy(alpha = (1f - ripple1Radius) * 0.4f),
                                radius = coreRadius + (maxRadius - coreRadius) * ripple1Radius,
                                center = center,
                                style = Stroke(width = 4f)
                            )
                            drawCircle(
                                color = trackColor.copy(alpha = (1f - ripple2Radius) * 0.4f),
                                radius = coreRadius + (maxRadius - coreRadius) * ripple2Radius,
                                center = center,
                                style = Stroke(width = 4f)
                            )
                        }

                        // Soft Ambient Glow around Center
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(trackColor.copy(alpha = 0.2f), Color.Transparent),
                                center = center,
                                radius = maxRadius * 0.6f
                            ),
                            radius = maxRadius * 0.6f,
                            center = center
                        )

                        // Center Static Ring
                        drawCircle(
                            color = trackColor.copy(alpha = 0.5f),
                            radius = coreRadius * 1.5f,
                            center = center,
                            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                    }

                    // Central Icon Box with Sweep Effect
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(com.protctus.app.ui.theme.SurfaceColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = Color(0xFF636A7D),
                            modifier = Modifier.size(48.dp)
                        )

                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                // Status text exactly matching DarkWebScanScreen styling
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(if (isAnimating) pulseAlpha else 1f)
                ) {
                    if (scanState == DeepScanState.SCANNING) {
                        // Show real scan progress from ViewModel
                        Text(
                            text = "Deep Scanning — ${scanProgress.toInt()}%",
                            color = Color(0xFFE8F3F1),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentItem.let { if (it.length > 45) "..." + it.takeLast(42) else it },
                            color = Color(0xFF636A7D),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (threatsFound > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$threatsFound threat(s) found",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        val statusText = when (scanState) {
                            DeepScanState.IDLE -> "Ready to deeply scan your device"
                            DeepScanState.PAUSED -> "Scan Paused"
                            DeepScanState.COMPLETED -> "Analysis Complete. Check Results."
                            else -> ""
                        }
                        Text(
                            text = statusText,
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Start Button identically matching DarkWebScanScreen button style
                Row(
                    modifier = Modifier
                        .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (scanState == DeepScanState.IDLE) {
                                // Check permissions before profiling
                                if (viewModel.hasFilePermission()) {
                                    viewModel.runPreScanProfiler()
                                } else {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            settingsLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            settingsLauncher.launch(intent)
                                        }
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                }
                            } else {
                                when (scanState) {
                                    DeepScanState.SCANNING -> viewModel.togglePause()
                                    DeepScanState.PAUSED -> viewModel.togglePause()
                                    DeepScanState.COMPLETED -> {
                                        viewModel.cancelScan()
                                        showWarningDialog = true
                                    }
                                    else -> viewModel.cancelScan()
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(
                            color = Color.White,
                            style = Stroke(width = 3f)
                        )

                        if (scanState == DeepScanState.SCANNING) {
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.35f, size.height * 0.3f),
                                end = Offset(size.width * 0.35f, size.height * 0.7f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.65f, size.height * 0.3f),
                                end = Offset(size.width * 0.65f, size.height * 0.7f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        } else {
                            val path = Path().apply {
                                moveTo(size.width * 0.4f, size.height * 0.35f)
                                lineTo(size.width * 0.65f, size.height * 0.5f)
                                lineTo(size.width * 0.4f, size.height * 0.65f)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(width = 3f, join = StrokeJoin.Round)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    val buttonText = when (scanState) {
                        DeepScanState.IDLE -> "Start Scan"
                        DeepScanState.SCANNING -> "Pause Scan"
                        DeepScanState.PAUSED -> "Resume Scan"
                        DeepScanState.COMPLETED -> "Scan Again"
                    }

                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Results Dialog matching DarkWebScanScreen
            AnimatedVisibility(
                visible = scanState == DeepScanState.COMPLETED,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.cancelScan() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.85f)
                            .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.scan_results),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Dialog",
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.cancelScan() }
                                    .padding(4.dp)
                            )
                        }

                        // Real threat results from DeepScannerService
                        if (completedThreats.isEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Critical\nThreats:",
                                    color = Color(0xFFE5E5E5),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.35f),
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = stringResource(R.string.no_deep_system_infections_found),
                                    color = Color(0xFF10B981),
                                    fontStyle = FontStyle.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.65f),
                                    lineHeight = 18.sp
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "High-Risk\nModules:",
                                    color = Color(0xFFE5E5E5),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.35f),
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = stringResource(R.string.zero_rootkits_or_suspicious_modules),
                                    color = Color(0xFF10B981),
                                    fontStyle = FontStyle.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.65f),
                                    lineHeight = 18.sp
                                )
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Critical\nThreats:",
                                    color = Color(0xFFE5E5E5),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.35f),
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "${completedThreats.size} threat(s) detected across $totalScanned items",
                                    color = Color(0xFFEF4444),
                                    fontStyle = FontStyle.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(0.65f),
                                    lineHeight = 18.sp
                                )
                            }

                            // List each threat
                            completedThreats.forEach { threat ->
                                HorizontalDivider(color = Color(0xFF38383A), thickness = 1.dp)
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = threat.appName,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(0.7f)
                                        )
                                        Text(
                                            text = threat.riskScore,
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = threat.threatType,
                                        color = Color(0xFFF59E0B),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = threat.detailedReason,
                                        color = Color(0xFF636A7D),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Warning Dialog with Real System Profile Data
             AnimatedVisibility(
                visible = showWarningDialog,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showWarningDialog = false
                            viewModel.cancelScan()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.performance_warning),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Real system telemetry from profiler
                        if (systemProfile != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ProfileInfoRow("Device", systemProfile.deviceModel)
                                ProfileInfoRow("CPU Load", "${systemProfile.cpuUsagePercent}%")
                                ProfileInfoRow("RAM", "${systemProfile.availableRamMb}MB free / ${systemProfile.totalRamMb}MB")
                                ProfileInfoRow("Battery", "${systemProfile.batteryLevel}%${if (systemProfile.isCharging) " ⚡" else ""}")
                                ProfileInfoRow("Apps to Scan", "${systemProfile.totalAppsToScan}")
                                ProfileInfoRow("Files to Scan", "${systemProfile.totalFilesToScan}")
                                ProfileInfoRow("Est. Time", "~${systemProfile.estimatedTimeSeconds / 60}m ${systemProfile.estimatedTimeSeconds % 60}s")
                                ProfileInfoRow("Battery Drain", "~${systemProfile.expectedBatteryDrainPercent}%")
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Recommendation banner
                            val recColor = when (systemProfile.recommendation) {
                                Recommendation.SAFE_TO_RUN -> Color(0xFF10B981)
                                Recommendation.PLUG_IN_CHARGER -> Color(0xFFF59E0B)
                                Recommendation.FREE_UP_RAM -> Color(0xFFEF4444)
                            }
                            Text(
                                text = systemProfile.recommendationMessage,
                                color = recColor,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.deep_scan_takes_a_long_time_and_consumes),
                                color = Color(0xFF9E9E9E),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF38383A), thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(top = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        showWarningDialog = false
                                        viewModel.cancelScan()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxSize()
                                    .background(Color(0xFF38383A))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        showWarningDialog = false
                                        viewModel.startDeepScan()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.continue_btn),
                                    color = Color(0xFFE5E5E5),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF7F909F), fontSize = 12.sp)
        Text(
            value,
            color = Color(0xFFE3E3E3),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.6f),
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1F)
@Composable
fun DeepScanScreenPreview() {
    DeepScanScreen(navController = NavController(LocalContext.current))
}
