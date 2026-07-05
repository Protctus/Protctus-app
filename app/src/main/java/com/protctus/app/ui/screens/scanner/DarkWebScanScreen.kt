package com.protctus.app.ui.screens.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.network.BreachDetail
import com.protctus.app.utils.PdfReportGenerator
import com.protctus.app.viewmodel.HomeViewModel
import com.protctus.app.viewmodel.DarkWebViewModel
import com.protctus.app.viewmodel.DarkWebScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DarkWebScanScreen(navController: NavController) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = hiltViewModel()
    val settingsViewModel: com.protctus.app.viewmodel.SettingsViewModel = hiltViewModel()
    val darkWebViewModel: DarkWebViewModel = hiltViewModel()
    
    val profile by settingsViewModel.profile.collectAsState()
    val username by homeViewModel.username.collectAsState()
    val realEmail = profile.email.takeIf { it.isNotBlank() } ?: "unknown"

    val scanState by darkWebViewModel.scanState.collectAsState()
    val scanResult by darkWebViewModel.scanResult.collectAsState()
    var showPromo by remember { mutableStateOf(false) }

    val isAnimating = scanState == DarkWebScanState.SCANNING || scanState == DarkWebScanState.VALIDATING

    val outerRotation = remember { Animatable(0f) }
    val innerRotation = remember { Animatable(360f) }
    val pulseAlpha = remember { Animatable(0.3f) }
    val atomScale = remember { Animatable(0.92f) }

    // Trigger Promo Display briefly after scan completes securely
    LaunchedEffect(scanState) {
        if (scanState == DarkWebScanState.COMPLETED && scanResult?.isError == false) {
            delay(1500)
            showPromo = true
        }
    }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            launch {
                while (true) {
                    outerRotation.animateTo(outerRotation.value + 360f, tween(10000, easing = LinearEasing))
                }
            }
            launch {
                while (true) {
                    innerRotation.animateTo(innerRotation.value - 360f, tween(4000, easing = LinearEasing))
                }
            }
            launch {
                while (true) {
                    pulseAlpha.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
                    pulseAlpha.animateTo(0.3f, tween(1200, easing = FastOutSlowInEasing))
                }
            }
            launch {
                while (true) {
                    atomScale.animateTo(1.05f, tween(1500, easing = FastOutSlowInEasing))
                    atomScale.animateTo(0.92f, tween(1500, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1919))
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.dark_web_scan),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Atom Scanner
            Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val outerRadius = size.width / 2

                    withTransform({ rotate(degrees = outerRotation.value, pivot = center) }) {
                        drawCircle(color = Color.White, radius = outerRadius, center = center,
                            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 30f), 0f)))
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((i * 60 - 90).toDouble())
                            val x = center.x + outerRadius * cos(angle).toFloat()
                            val y = center.y + outerRadius * sin(angle).toFloat()
                            drawCircle(color = Color.White, radius = 12f, center = Offset(x, y))
                        }
                    }

                    val gradientRadius = outerRadius * 0.65f
                    withTransform({ rotate(degrees = innerRotation.value, pivot = center) }) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF636A7D), com.protctus.app.ui.theme.SurfaceColor, Color(0xFF636A7D)),
                                center = center
                            ),
                            radius = gradientRadius, center = center, style = Stroke(width = 40f)
                        )
                    }

                    drawCircle(color = Color(0xFF121212), radius = gradientRadius - 20f, center = center, style = Stroke(width = 4f))

                    val atomRadiusX = 45f; val atomRadiusY = 110f
                    val atomColor = Color(0xFFE8F3F1)

                    withTransform({ scale(scaleX = atomScale.value, scaleY = atomScale.value, pivot = center) }) {
                        for (angle in listOf(30f, 90f, 150f)) {
                            withTransform({ rotate(angle, pivot = center) }) {
                                drawOval(color = atomColor,
                                    topLeft = Offset(center.x - atomRadiusX, center.y - atomRadiusY),
                                    size = Size(atomRadiusX * 2, atomRadiusY * 2),
                                    style = Stroke(width = 12f, cap = StrokeCap.Round))
                            }
                        }
                        drawCircle(color = atomColor, radius = 20f, center = center)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val statusText = when (scanState) {
                DarkWebScanState.IDLE -> "Ready to scan your digital footprint"
                DarkWebScanState.VALIDATING -> "Validating profile settings..."
                DarkWebScanState.SCANNING -> "Querying secure breach records..."
                DarkWebScanState.PAUSED -> "Scan Paused"
                DarkWebScanState.COMPLETED -> if (scanResult?.isError == true) "Network Error" else if (scanResult?.isSafe == true) "Your data is safe" else "Breach detected!"
            }
            val statusColor = if (scanState == DarkWebScanState.COMPLETED) {
                if (scanResult?.isError == true) Color(0xFFF59E0B) else if (scanResult?.isSafe == true) Color(0xFF10B981) else Color(0xFFEF4444)
            } else Color(0xFFB0B0B0)

            Text(text = statusText, color = statusColor, fontSize = 13.sp,
                fontWeight = if (scanState == DarkWebScanState.COMPLETED) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.alpha(if (isAnimating) pulseAlpha.value else 1f),
                textAlign = TextAlign.Center)

            if (scanState == DarkWebScanState.SCANNING || scanState == DarkWebScanState.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                val displayedEmail = scanResult?.scannedEmail?.takeIf { it.isNotBlank() } ?: realEmail
                Text(
                    text = displayedEmail,
                    color = Color(0xFF7F909F),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        when (scanState) {
                            DarkWebScanState.IDLE, DarkWebScanState.COMPLETED -> darkWebViewModel.startScan(context, realEmail)
                            DarkWebScanState.SCANNING, DarkWebScanState.PAUSED -> darkWebViewModel.togglePause()
                            else -> {}
                        }
                    }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (scanState) {
                        DarkWebScanState.IDLE -> "Start Scan"
                        DarkWebScanState.SCANNING -> "Pause Scan"
                        DarkWebScanState.PAUSED -> "Resume Scan"
                        DarkWebScanState.COMPLETED -> "Scan Again"
                        DarkWebScanState.VALIDATING -> "Validating..."
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }

        // Full-screen results overlay
        AnimatedVisibility(
            visible = scanState == DarkWebScanState.COMPLETED,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        darkWebViewModel.resetState()
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .background(Color(0xFF1C1919), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp)
                            .width(40.dp).height(4.dp)
                            .background(Color(0xFF4A5568), RoundedCornerShape(2.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.scan_results), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            val r = scanResult
                            if (r != null) {
                                Text(
                                    text = if (r.isError) "Scan Failed" else if (r.isSafe) "No breaches found" else "${r.breachCount} breach(es) detected",
                                    color = if (r.isError) Color(0xFFF59E0B) else if (r.isSafe) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pdfScope = rememberCoroutineScope()
                            IconButton(onClick = {
                                pdfScope.launch {
                                    scanResult?.let { result ->
                                        val uri = PdfReportGenerator.generateReport(context, result, username)
                                        if (uri != null) {
                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Open Report"))
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download PDF", tint = Color(0xFF7F909F))
                            }

                            IconButton(onClick = { darkWebViewModel.resetState() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF9E9E9E))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2A2B31))

                    val result = scanResult
                    if (result != null) {
                        if (result.isError) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Scan Failed", color = Color(0xFFF59E0B), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    result.errorMessage ?: "A network error occurred. Please try again.",
                                    color = Color(0xFF7F909F), fontSize = 13.sp, textAlign = TextAlign.Center
                                )
                            }
                        } else if (result.isSafe) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(R.string.you_re_safe), color = Color(0xFF10B981), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.your_email_was_not_found_in_any_known_da),
                                    color = Color(0xFF7F909F), fontSize = 13.sp, textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                ScanMetaRow("Scanned Email", result.scannedEmail.ifEmpty { realEmail })
                                ScanMetaRow("Databases Checked", "Live breach records")
                                ScanMetaRow("Risk Level", "LOW", Color(0xFF10B981))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    ScanMetaRow("Scanned Email", result.scannedEmail.ifEmpty { realEmail })
                                    ScanMetaRow("Databases Checked", "Live breach records")
                                    ScanMetaRow("Total Breaches Found", "${result.breachCount}", Color(0xFFEF4444))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.detailed_breach_report), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                items(result.breaches) { breach ->
                                    BreachDetailCard(breach)
                                }
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .background(Color(0xFF2A2B31), RoundedCornerShape(12.dp))
                                            .padding(16.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.AdminPanelSettings,
                                                contentDescription = null,
                                                tint = Color(0xFFE3E3E3),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.recommended_actions), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        listOf(
                                            "Change passwords for all breached accounts immediately",
                                            "Enable Two-Factor Authentication",
                                            "Use unique passwords per service via a password manager",
                                            "Monitor your email inbox for suspicious login alerts"
                                        ).forEach {
                                            Text("• $it", color = Color(0xFF7F909F), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Marketing Promo Popup ──
        AnimatedVisibility(
            visible = showPromo,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.85f, animationSpec = tween(400)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.85f, animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        showPromo = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2D3A), Color(0xFF1C1F2B))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(
                            onClick = { showPromo = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF7F909F), modifier = Modifier.size(18.dp))
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF86A055),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.protect_your_circle),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your friends' data might be exposed too.\nRefer them to Protctus and earn XP rewards!",
                        color = Color(0xFFA0A0B0),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showPromo = false
                            navController.navigate(com.protctus.app.navigation.Route.Referral)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF86A055)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.refer_earn_xp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.not_now),
                        color = Color(0xFF7F909F),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showPromo = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ScanMetaRow(label: String, value: String, valueColor: Color = Color(0xFFE3E3E3)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF7F909F), fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.6f), textAlign = TextAlign.End)
    }
}

@Composable
fun BreachDetailCard(breach: BreachDetail) {
    val riskColor = if (breach.isSensitive) Color(0xFFEF4444) else Color(0xFFF59E0B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2B31), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(riskColor.copy(alpha = 0.12f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(breach.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
            Box(
                modifier = Modifier.background(riskColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(if (breach.isSensitive) "SENSITIVE" else "BREACH", color = riskColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (breach.domain.isNotEmpty()) BreachInfoRow("Domain", breach.domain)
            if (breach.breachDate.isNotEmpty()) BreachInfoRow("Breach Date", breach.breachDate)
            if (breach.pwnCount > 0) BreachInfoRow("Affected Users", "%,d".format(breach.pwnCount))
            if (breach.dataClasses.isNotEmpty()) {
                BreachInfoRow("Exposed Data", breach.dataClasses.take(5).joinToString(", "))
            }
            if (breach.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(breach.description.take(160) + if (breach.description.length > 160) "…" else "",
                    color = Color(0xFF7F909F), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun BreachInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF7F909F), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(0.35f))
        Text(value, color = Color(0xFFE3E3E3), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}