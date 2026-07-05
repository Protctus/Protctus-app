package com.protctus.app.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.theme.ProtctusTheme
import com.protctus.app.viewmodel.ScanState
import com.protctus.app.viewmodel.ScannerViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SmartScanScreen(navController: NavController, scannerViewModel: ScannerViewModel) {
    val scanState by scannerViewModel.scanState.collectAsState()
    val scanProgress by scannerViewModel.scanProgress.collectAsState()
    val isPaused by scannerViewModel.isPaused.collectAsState()
    val currentScannedItem by scannerViewModel.currentScanningItem.collectAsState()
    val context = LocalContext.current

    val isAnimating = scanState in listOf(ScanState.SCANNING_APPS, ScanState.SCANNING_FILES, ScanState.ANALYZING) && !isPaused

    val infiniteTransition = rememberInfiniteTransition(label = "cyber_core_transition")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isAnimating) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_alpha"
    )

    val orbit1Angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit1"
    )

    val orbit2Angle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = if (isAnimating) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit2"
    )

    val orbit3Angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit3"
    )

    val dashedRingRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "dash_sync"
    )

    LaunchedEffect(Unit) {
        if (scanState == ScanState.IDLE) {
            scannerViewModel.startSmartScan(context)
        }
    }

    LaunchedEffect(scanState) {
        if (scanState == ScanState.COMPLETED) {
            com.protctus.app.utils.QuestTracker.markTaskCompleted(context, "smart_scan")
            
            navController.navigate(Route.ScanResults) {
                popUpTo(Route.SmartScan) { inclusive = true }
            }
        }
    }

    ProtctusTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1919))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
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
                        text = stringResource(R.string.smart_scanner),
                        color = Color.White,
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.height(70.dp))

                // Cybernetic Core Geometry Layout
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val trackColor = Color(0xFF636A7D)
                        val accentColor = Color(0xFFE8F3F1)

                        // Base Core Radii
                        val innerRadius = size.width * 0.25f
                        val middleRadius = size.width * 0.35f
                        val outerRadius = size.width * 0.45f

                        // Slowly revolving Outer and Inner high-tech HUD rings
                        rotate(dashedRingRotate, center) {
                            drawCircle(
                                color = trackColor.copy(alpha = 0.2f),
                                radius = outerRadius,
                                center = center,
                                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
                            )
                        }

                        rotate(-dashedRingRotate * 1.5f, center) {
                            drawCircle(
                                color = trackColor.copy(alpha = 0.3f),
                                radius = innerRadius,
                                center = center,
                                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f))
                            )
                        }

                        // Middle solid guide track
                        drawCircle(
                            color = trackColor.copy(alpha = 0.15f),
                            radius = middleRadius,
                            center = center,
                            style = Stroke(width = 1f)
                        )

                        // Data Packets (Orbiting Glowing Particles)
                        if (isAnimating) {
                            // Particle 1 (Inner Track)
                            val p1Rad = Math.toRadians(orbit1Angle.toDouble())
                            val p1X = center.x + innerRadius * cos(p1Rad).toFloat()
                            val p1Y = center.y + innerRadius * sin(p1Rad).toFloat()
                            drawCircle(color = accentColor, radius = 6f, center = Offset(p1X, p1Y))
                            drawCircle(color = accentColor.copy(alpha = 0.4f), radius = 14f, center = Offset(p1X, p1Y))

                            // Particle 2 (Middle Track)
                            val p2Rad = Math.toRadians(orbit2Angle.toDouble())
                            val p2X = center.x + middleRadius * cos(p2Rad).toFloat()
                            val p2Y = center.y + middleRadius * sin(p2Rad).toFloat()
                            drawCircle(color = accentColor, radius = 8f, center = Offset(p2X, p2Y))
                            drawCircle(color = accentColor.copy(alpha = 0.3f), radius = 18f, center = Offset(p2X, p2Y))

                            // Particle 3 (Outer Track)
                            val p3Rad = Math.toRadians(orbit3Angle.toDouble())
                            val p3X = center.x + outerRadius * cos(p3Rad).toFloat()
                            val p3Y = center.y + outerRadius * sin(p3Rad).toFloat()
                            drawCircle(color = accentColor, radius = 5f, center = Offset(p3X, p3Y))
                            drawCircle(color = accentColor.copy(alpha = 0.5f), radius = 12f, center = Offset(p3X, p3Y))
                        }
                    }

                    // Hardware Memory Chip Central Box
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(com.protctus.app.ui.theme.SurfaceColor.copy(alpha = pulseAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = "System Core",
                            tint = if (isAnimating) Color.White else Color(0xFF636A7D),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(pulseAlpha).padding(horizontal = 24.dp)
                ) {
                    if (isAnimating || isPaused) {
                        Text(
                            text = when (scanState) {
                                ScanState.SCANNING_APPS -> "Scanning System Apps"
                                ScanState.SCANNING_FILES -> "Analyzing File System"
                                ScanState.ANALYZING -> "Compiling Security Report"
                                else -> "Initializing Defense Core"
                            },
                            color = Color(0xFFE8F3F1),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processing: $currentScannedItem",
                            color = Color(0xFF636A7D),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.core_offline_awaiting_sequence),
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Shared Control Button
                Row(
                    modifier = Modifier
                        .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (scanState in listOf(ScanState.SCANNING_APPS, ScanState.SCANNING_FILES, ScanState.ANALYZING)) {
                                scannerViewModel.togglePause()
                            } else if (scanState == ScanState.IDLE) {
                                scannerViewModel.startSmartScan(context)
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

                        if (isAnimating) {
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

                    val buttonText = when {
                        scanState == ScanState.IDLE -> "Start Engine"
                        isAnimating -> "Pause Engine"
                        isPaused -> "Resume Scan"
                        else -> "Processing..."
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
        }
    }
}