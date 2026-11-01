package com.protctus.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.protctus.app.ui.theme.ProtctusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.protctus.app.viewmodel.HomeViewModel
import com.protctus.app.viewmodel.HomeState
import com.protctus.app.utils.SecurityScanResult
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.components.PremiumAdDialog
import com.protctus.app.ui.components.LockedFeatureDialog
import com.protctus.app.data.local.SettingsManager
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun HomeScreen(navController: NavController) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val settingsViewModel: com.protctus.app.viewmodel.SettingsViewModel = hiltViewModel()
    val homeState by homeViewModel.homeState.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // Server-authoritative tier from EntitlementManager (EncryptedSharedPrefs + D1 sync)
    val currentTier by settingsViewModel.currentTier.collectAsState()
    val subscriptionTier = currentTier.weight // 0 = Free, 1 = Premium, 2 = Elite
    val lastAdTimestamp by settingsManager.lastPremiumAdTimestamp.collectAsState(initial = 0L)

    var isVisible by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var showAdPopup by remember { mutableStateOf(false) }
    var paywallFeatureName by remember { mutableStateOf("") }
    var targetTierForPaywall by remember { mutableStateOf(1) }
    
    val scope = rememberCoroutineScope()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> /* OS automatically caches permission outcome */ }

    LaunchedEffect(subscriptionTier, lastAdTimestamp) {
        // Show ad popup every 2-4 days (using 2 days = 172800000ms as minimum)
        val currentTime = System.currentTimeMillis()
        val twoDaysInMillis = 2 * 24 * 60 * 60 * 1000L
        
        if (subscriptionTier == 0 && (currentTime - lastAdTimestamp) > twoDaysInMillis) {
            delay(2000) // Show after 2 seconds of landing
            showAdPopup = true
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        delay(100)
        isVisible = true
        homeViewModel.triggerNewScan(context)
    }

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground,
        bottomBar = { ProtctusBottomNav(navController = navController) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(com.protctus.app.ui.theme.DarkBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(initialOffsetY = { -50 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(700))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_protctus_logo),
                            contentDescription = "Protctus Logo",
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                if (homeState.error != null) {
                    Text("Error: ${homeState.error}", color = Color.Red, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    val result = homeState.result

                    if (homeState.isScanning) {
                        com.protctus.app.ui.components.DashboardSkeleton()
                    } else {
                        DashboardContent(
                            result = result, 
                            isVisible = isVisible, 
                            navController = navController,
                            subscriptionTier = subscriptionTier,
                            onLockedFeatureClick = { name, tier ->
                                paywallFeatureName = name
                                targetTierForPaywall = tier
                                showPaywall = true
                            }
                        )
                    }
                }
            }

            if (showPaywall) {
                LockedFeatureDialog(
                    featureName = paywallFeatureName,
                    onBuyNowClick = {
                        showPaywall = false
                        navController.navigate(com.protctus.app.navigation.Route.GetPremium)
                    },
                    onDismiss = { showPaywall = false }
                )
            }

            if (showAdPopup) {
                PremiumAdDialog(
                    onBuyNowClick = {
                        showAdPopup = false
                        scope.launch {
                            settingsManager.setLastPremiumAdTimestamp(System.currentTimeMillis())
                        }
                        navController.navigate(com.protctus.app.navigation.Route.GetPremium)
                    },
                    onRemindMeLaterClick = {
                        showAdPopup = false
                        scope.launch {
                            settingsManager.setLastPremiumAdTimestamp(System.currentTimeMillis())
                        }
                    },
                    onDismiss = {
                        showAdPopup = false
                        scope.launch {
                            settingsManager.setLastPremiumAdTimestamp(System.currentTimeMillis())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardContent(
    result: SecurityScanResult, 
    isVisible: Boolean, 
    navController: NavController,
    subscriptionTier: Int,
    onLockedFeatureClick: (String, Int) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500)) + fadeIn(tween(500))
    ) {
        SecurityScoreCard(targetScore = result.score)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 150)) + fadeIn(tween(500, delayMillis = 150)),
            modifier = Modifier.weight(1f)
        ) {
            val networkFindings = result.domainFindings[com.protctus.app.utils.SecurityDomain.NETWORK_SECURITY] ?: emptyList()
            val maxNetworkSeverity = networkFindings.minByOrNull { it.severity.ordinal }?.severity
            
            val networkTitle = when (maxNetworkSeverity) {
                com.protctus.app.utils.SecurityFinding.Severity.CRITICAL -> "NETWORK RISK"
                com.protctus.app.utils.SecurityFinding.Severity.HIGH, 
                com.protctus.app.utils.SecurityFinding.Severity.MEDIUM -> "NETWORK WARNING"
                com.protctus.app.utils.SecurityFinding.Severity.LOW,
                com.protctus.app.utils.SecurityFinding.Severity.INFORMATIONAL -> "NETWORK NOTICE"
                null -> "Network Secure"
            }
            
            val networkDesc = networkFindings.firstOrNull()?.title ?: "Connection Verified"
            
            val networkIcon = when (maxNetworkSeverity) {
                com.protctus.app.utils.SecurityFinding.Severity.CRITICAL,
                com.protctus.app.utils.SecurityFinding.Severity.HIGH -> Icons.Outlined.WarningAmber
                com.protctus.app.utils.SecurityFinding.Severity.MEDIUM,
                com.protctus.app.utils.SecurityFinding.Severity.LOW -> Icons.Outlined.Info
                else -> if (result.isVpnActive) Icons.Outlined.VpnKey else Icons.Outlined.VerifiedUser
            }

            val isNetworkPositive = maxNetworkSeverity == null || maxNetworkSeverity == com.protctus.app.utils.SecurityFinding.Severity.LOW || maxNetworkSeverity == com.protctus.app.utils.SecurityFinding.Severity.INFORMATIONAL

            StatusGridCard(
                title = networkTitle,
                statusText = networkDesc,
                icon = networkIcon,
                isPositive = isNetworkPositive
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 300)) + fadeIn(tween(500, delayMillis = 300)),
            modifier = Modifier.weight(1f)
        ) {
            val deviceFindings = result.domainFindings[com.protctus.app.utils.SecurityDomain.DEVICE_SECURITY] ?: emptyList()
            val systemHardening = result.domainFindings[com.protctus.app.utils.SecurityDomain.SYSTEM_HARDENING] ?: emptyList()
            val privacyFindings = result.domainFindings[com.protctus.app.utils.SecurityDomain.PRIVACY_EXPOSURE] ?: emptyList()
            
            val allSystemFindings = deviceFindings + systemHardening + privacyFindings
            val maxDeviceSeverity = allSystemFindings.minByOrNull { it.severity.ordinal }?.severity

            val systemTitle = when (maxDeviceSeverity) {
                com.protctus.app.utils.SecurityFinding.Severity.CRITICAL -> "SYSTEM RISK"
                com.protctus.app.utils.SecurityFinding.Severity.HIGH,
                com.protctus.app.utils.SecurityFinding.Severity.MEDIUM -> "SYSTEM WARNING"
                com.protctus.app.utils.SecurityFinding.Severity.LOW,
                com.protctus.app.utils.SecurityFinding.Severity.INFORMATIONAL -> "SYSTEM NOTICE"
                null -> "System Safe"
            }

            val systemDesc = allSystemFindings.firstOrNull()?.title ?: "OS integrity intact"

            val systemIcon = when (maxDeviceSeverity) {
                com.protctus.app.utils.SecurityFinding.Severity.CRITICAL,
                com.protctus.app.utils.SecurityFinding.Severity.HIGH -> Icons.Outlined.GppBad
                com.protctus.app.utils.SecurityFinding.Severity.MEDIUM,
                com.protctus.app.utils.SecurityFinding.Severity.LOW -> Icons.Outlined.DeveloperMode
                else -> Icons.Outlined.CheckCircle
            }
            
            val isSystemPositive = maxDeviceSeverity == null || maxDeviceSeverity == com.protctus.app.utils.SecurityFinding.Severity.LOW || maxDeviceSeverity == com.protctus.app.utils.SecurityFinding.Severity.INFORMATIONAL

            StatusGridCard(
                title = systemTitle,
                statusText = systemDesc,
                icon = systemIcon,
                isPositive = isSystemPositive
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Phishing VPN Toggle Card
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 300)) + fadeIn(tween(500, delayMillis = 300))
    ) {
        val context = LocalContext.current
        var isVpnRunning by remember(result.isVpnActive) { mutableStateOf(result.isVpnActive) }
        
        val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { activityResult ->
            if (activityResult.resultCode == android.app.Activity.RESULT_OK) {
                val vpnIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
                context.startService(vpnIntent)
                isVpnRunning = true
            } else {
                isVpnRunning = false
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF222226))
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isVpnRunning) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = "VPN",
                        tint = if (isVpnRunning) Color(0xFF10B981) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Phishing Shield",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isVpnRunning) "Active - Real-time protection" else "Disabled - Tap to enable",
                        color = Color(0xFF7F909F),
                        fontSize = 11.sp
                    )
                }
            }

            androidx.compose.material3.Switch(
                checked = isVpnRunning,
                onCheckedChange = { isChecked ->
                        isVpnRunning = isChecked
                        try {
                            if (isChecked) {
                                val intent = android.net.VpnService.prepare(context)
                                if (intent != null) {
                                    // Start activity to get permission via launcher
                                    vpnLauncher.launch(intent)
                                } else {
                                    val vpnIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
                                    context.startService(vpnIntent)
                                }
                            } else {
                                val stopIntent = android.content.Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
                                context.stopService(stopIntent)
                            }
                        } catch (e: Exception) {}
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981)
                )
            )
        }
    }

    // ── Security Findings Section (WHY the score is low) ──
    if (result.findings.isNotEmpty()) {
        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(500, delayMillis = 350))
        ) {
            Text(
                text = "Issues Found (${result.findings.size})",
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        result.findings.forEachIndexed { index, finding ->
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(400, delayMillis = 400 + (index * 80))
                ) + fadeIn(tween(400, delayMillis = 400 + (index * 80)))
            ) {
                FindingCard(finding = finding)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(500, delayMillis = 400))
    ) {
        Text(
            text = stringResource(R.string.security_boosters),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 450)) + fadeIn(tween(500, delayMillis = 450))
    ) {
        BoostersCard(
            navController = navController,
            subscriptionTier = subscriptionTier,
            onLockedFeatureClick = onLockedFeatureClick
        )
    }
}

@Composable
fun FindingCard(finding: com.protctus.app.utils.SecurityFinding) {
    val context = LocalContext.current

    val severityColor = when (finding.severity) {
        com.protctus.app.utils.SecurityFinding.Severity.CRITICAL -> Color(0xFFFF4C4C)
        com.protctus.app.utils.SecurityFinding.Severity.HIGH -> Color(0xFFFF8C42)
        com.protctus.app.utils.SecurityFinding.Severity.MEDIUM -> Color(0xFFFFC107)
        com.protctus.app.utils.SecurityFinding.Severity.LOW -> Color(0xFF4DB6AC)
        com.protctus.app.utils.SecurityFinding.Severity.INFORMATIONAL -> Color(0xFF9E9E9E)
    }

    val categoryIcon = when (finding.domain) {
        com.protctus.app.utils.SecurityDomain.NETWORK_SECURITY -> Icons.Outlined.Wifi
        com.protctus.app.utils.SecurityDomain.APPLICATION_RISK,
        com.protctus.app.utils.SecurityDomain.MALWARE_EXPOSURE -> Icons.Outlined.Apps
        com.protctus.app.utils.SecurityDomain.PRIVACY_EXPOSURE -> Icons.Outlined.Visibility
        else -> Icons.Outlined.PhonelinkLock
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1B1F))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Severity indicator dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(severityColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = finding.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.description,
                color = Color(0xFF8E8F93),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "EVIDENCE: ${finding.evidence}",
                color = Color(0xFFB0BEC5),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "FIX: ${finding.fix}",
                color = severityColor.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
            )

            // Render FIX button if action is present
            if (finding.intentAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(severityColor.copy(alpha = 0.15f))
                        .clickable {
                            try {
                                val intent = android.content.Intent(finding.intentAction)
                                if (finding.intentUri != null) {
                                    intent.data = android.net.Uri.parse(finding.intentUri)
                                }
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FIX NOW",
                        color = severityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Confidence badge
        Text(
            text = finding.confidence.name,
            color = severityColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(severityColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SecurityScoreCard(targetScore: Int) {
    // Number rolling animation
    val animatedScore = remember { androidx.compose.animation.core.Animatable(0f) }

    // Circular progress sweeping animation
    val animatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(targetScore) {
        if (targetScore > 0) {
            animatedScore.snapTo(0f)
            animatedProgress.snapTo(0f)

            launch {
                animatedScore.animateTo(
                    targetValue = targetScore.toFloat(),
                    animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                )
            }
            launch {
                animatedProgress.animateTo(
                    targetValue = targetScore / 100f,
                    animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E1F24), Color(0xFF151619)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3A3D46), Color.Transparent)
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(28.dp)
    ) {
        // Subtle ambient glow behind the score
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(140.dp)
                .offset(x = 10.dp, y = (-10).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF10B981).copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Your Security\nScore:",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${animatedScore.value.toInt()}",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = stringResource(R.string.score_100),
                        color = Color(0xFF7F909F),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.security_rank_defends),
                    color = Color(0xFF7F909F),
                    fontSize = 10.sp
                )
            }

            // Animated Circular Progress Bar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    // Background track
                    drawArc(
                        color = com.protctus.app.ui.theme.SurfaceColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Animated foreground track
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF10B981))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress.value,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = "Shield",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun StatusGridCard(title: String, statusText: String, icon: ImageVector, isPositive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "threatPulse")

    val pulseScale by if (!isPositive) infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    ) else remember { mutableStateOf(1f) }

    val pulseColor by if (!isPositive) infiniteTransition.animateColor(
        initialValue = Color.White,
        targetValue = Color(0xFFFF4C4C), // Bright Red Warning
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseColor"
    ) else remember { mutableStateOf(Color.White) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF222226))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            color = if (!isPositive) pulseColor else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = pulseColor,
            modifier = Modifier
                .size(24.dp)
                .scale(pulseScale)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = statusText,
            color = Color(0xFF7F909F),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun BoostersCard(navController: NavController, subscriptionTier: Int, onLockedFeatureClick: (String, Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF222226))
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoosterItem(title = "Smart Scan", icon = Icons.Outlined.DocumentScanner, onClick = { navController.navigate(com.protctus.app.navigation.Route.SmartScan) })

        BoosterItem(
            title = "Deep Scan", 
            icon = Icons.AutoMirrored.Outlined.ManageSearch, 
            onClick = { 
                if (subscriptionTier >= 1) {
                    navController.navigate(com.protctus.app.navigation.Route.DeepScan)
                } else {
                    onLockedFeatureClick("Deep Scan", 1)
                }
            }
        )

        BoosterItem(title = "Phishing Scan", icon = Icons.Outlined.Public, onClick = { navController.navigate(com.protctus.app.navigation.Route.UrlScanner()) })
        BoosterItem(title = "Rankings", icon = Icons.Outlined.Leaderboard, onClick = { navController.navigate(com.protctus.app.navigation.Route.Leaderboard) })
    }
}

@Composable
fun BoosterItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "boosterScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFE3E3E3),
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = Color(0xFF7F909F),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}



@Preview(showBackground = true, device = "id:pixel_6_pro")
@Composable
fun PreviewHomeScreen() {
    ProtctusTheme {
        HomeScreen(navController = rememberNavController())
    }
}