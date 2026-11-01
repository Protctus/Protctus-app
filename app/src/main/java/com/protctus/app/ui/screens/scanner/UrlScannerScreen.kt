package com.protctus.app.ui.screens.scanner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.protctus.edge.phishing.PhishingDetector
import com.protctus.app.neuroshield.engine.ThreatVerdict
import com.protctus.app.neuroshield.engine.VerdictLevel

/**
 * URL Scanner screen.
 *
 * Detection is performed 100% on-device via the [PhishingDetector] pipeline
 * (NeuroShield ONNX lexical model + heuristic engines). No URL is uploaded to
 * any external AI provider or telemetry service by default, keeping browsing
 * data private (privacy-first, offline-first).
 *
 * Verdict thresholds (phishing = positive class):
 *   score >= 0.65 → MALICIOUS   (high confidence, block)
 *   score >= 0.30 → SUSPICIOUS  (grey zone, warn user)
 *   score <  0.30 → SAFE
 */
enum class ScanUiState {
    INPUT, SCANNING, SAFE, SUSPICIOUS, MALICIOUS, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlScannerScreen(sharedUrl: String? = null) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val decodedUrl = remember(sharedUrl) { sharedUrl?.let { java.net.URLDecoder.decode(it, "UTF-8") } }

    // On-device phishing detector (single instance for the screen lifetime).
    val detector = remember { PhishingDetector(context) }
    DisposableEffect(Unit) {
        onDispose { detector.close() }
    }

    var uiState by remember { mutableStateOf(if (decodedUrl != null) ScanUiState.SCANNING else ScanUiState.INPUT) }
    var currentUrl by remember { mutableStateOf(decodedUrl ?: "") }
    var verdict by remember { mutableStateOf<ThreatVerdict?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    val scanAction = { urlToScan: String ->
        val host = urlToScan.trim().lowercase()
        if (host.isNotEmpty()) {
            // Basic URL-shape validation: must look like a host/URL, reject obvious junk.
            val looksLikeUrl = Patterns.WEB_URL.matcher(urlToScan).matches() ||
                host.contains(".") && host.indexOf(' ') == -1
            if (looksLikeUrl) {
                uiState = ScanUiState.SCANNING
                coroutineScope.launch {
                    try {
                        // ── ON-DEVICE DETECTION (NeuroShield ONNX + heuristics) ──
                        // Run inference off the Main thread (inference + heuristics can take a few ms).
                        val v = withContext(Dispatchers.IO) { detector.getVerdict(urlToScan) }
                        verdict = v

                        uiState = when (v.verdict) {
                            VerdictLevel.PHISHING -> ScanUiState.MALICIOUS
                            VerdictLevel.SUSPICIOUS -> ScanUiState.SUSPICIOUS
                            VerdictLevel.SAFE -> ScanUiState.SAFE
                            VerdictLevel.REQUIRES_NETWORK_VERIFICATION -> ScanUiState.SUSPICIOUS
                        }

                        if (v.verdict == VerdictLevel.PHISHING) {
                            vibrateDevice(context)
                        }
                    } catch (e: Exception) {
                        errorMessage = "Scan failed: ${e.localizedMessage ?: "unknown error"}"
                        uiState = ScanUiState.ERROR
                    }
                }
            } else {
                errorMessage = "Invalid URL or domain format."
                uiState = ScanUiState.ERROR
            }
        } else {
            errorMessage = "Please enter a URL or domain."
            uiState = ScanUiState.ERROR
        }
    }

    LaunchedEffect(decodedUrl) {
        if (!decodedUrl.isNullOrBlank()) {
            scanAction(decodedUrl)
        }
    }

    // Colors
    val radarColor = MaterialTheme.colorScheme.primary
    val maliciousColor = MaterialTheme.colorScheme.error
    val suspiciousColor = Color(0xFFF59E0B) // amber
    val safeColor = MaterialTheme.colorScheme.tertiary
    val bgColor = MaterialTheme.colorScheme.background

    val currentBgColor by animateColorAsState(
        targetValue = when (uiState) {
            ScanUiState.MALICIOUS -> maliciousColor.copy(alpha = 0.15f)
            ScanUiState.SUSPICIOUS -> suspiciousColor.copy(alpha = 0.12f)
            ScanUiState.SAFE -> safeColor.copy(alpha = 0.15f)
            else -> bgColor
        },
        animationSpec = tween(1000), label = "bg_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Header for Input State OR Display of active target URL
            AnimatedVisibility(visible = uiState == ScanUiState.INPUT) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.global_threat_engine),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.url_scanner_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = { currentUrl = it },
                        placeholder = { Text(stringResource(R.string.paste_suspicious_url_here), color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = radarColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            if (currentUrl.isNotBlank()) scanAction(currentUrl)
                        })
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (currentUrl.isNotBlank()) scanAction(currentUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = radarColor),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.initiate_scan), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            AnimatedVisibility(visible = uiState != ScanUiState.INPUT) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.target), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        text = currentUrl,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 60.dp)
                    )
                }
            }

            // Radar/Output Container
            AnimatedVisibility(visible = uiState != ScanUiState.INPUT) {
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (uiState) {
                        ScanUiState.SCANNING -> RadarAnimation(radarColor)
                        ScanUiState.SAFE -> ResultUI(
                            icon = Icons.Rounded.CheckCircle,
                            color = safeColor,
                            title = stringResource(R.string.scan_safe_title),
                            score = (verdict?.confidence ?: 0f) * 100f,
                            reason = verdict?.explanation ?: stringResource(R.string.scan_safe_reason)
                        )
                        ScanUiState.SUSPICIOUS -> ResultUI(
                            icon = Icons.Rounded.Warning,
                            color = suspiciousColor,
                            title = stringResource(R.string.scan_suspicious_title),
                            score = (verdict?.confidence ?: 0f) * 100f,
                            reason = verdict?.explanation ?: stringResource(R.string.scan_suspicious_reason)
                        )
                        ScanUiState.MALICIOUS -> ResultUI(
                            icon = Icons.Rounded.Warning,
                            color = maliciousColor,
                            title = stringResource(R.string.scan_malicious_title),
                            score = (verdict?.confidence ?: 0f) * 100f,
                            reason = verdict?.explanation ?: stringResource(R.string.scan_malicious_reason)
                        )
                        ScanUiState.ERROR -> ErrorUI(errorMessage, onRetry = { scanAction(currentUrl) })
                        else -> {}
                    }
                }
            }

            // Triggered indicators (actionable basis for the verdict)
            val v = verdict
            if (v != null && v.triggeredIndicators.isNotEmpty() &&
                uiState != ScanUiState.SCANNING && uiState != ScanUiState.ERROR) {
                AnimatedVisibility(visible = true) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.detection_basis),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        v.triggeredIndicators.forEach { indicator ->
                            Text(
                                text = "• $indicator",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(bottom = 4.dp)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            AnimatedVisibility(visible = uiState == ScanUiState.SCANNING, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.analyzing_locally),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.on_device_inference),
                        color = radarColor,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = uiState == ScanUiState.SAFE || uiState == ScanUiState.SUSPICIOUS || uiState == ScanUiState.MALICIOUS || uiState == ScanUiState.ERROR) {
                Button(
                    onClick = {
                        uiState = ScanUiState.INPUT
                        currentUrl = ""
                        verdict = null
                        errorMessage = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(0.6f).height(50.dp)
                ) {
                    Text(stringResource(R.string.scan_another), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RadarAnimation(radarColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "sweep"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2

        for (i in 1..4) {
            drawCircle(
                color = radarColor.copy(alpha = pulse * 0.5f),
                radius = radius * (i / 4f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        drawLine(color = radarColor.copy(alpha = 0.3f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 2f)
        drawLine(color = radarColor.copy(alpha = 0.3f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 2f)

        rotate(rotation, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.8f to radarColor.copy(alpha = 0.1f),
                    1.0f to radarColor.copy(alpha = 0.8f),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true
            )
        }
    }
}

@Composable
fun ResultUI(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, score: Float, reason: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = "Risk Score: ${"%.0f".format(score)}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        if (reason.isNotEmpty()) {
            Text(text = reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp))
        }
    }
}

@Composable
fun ErrorUI(error: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Rounded.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(80.dp))
        Text(text = stringResource(R.string.scan_failed), color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(text = error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun vibrateDevice(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
