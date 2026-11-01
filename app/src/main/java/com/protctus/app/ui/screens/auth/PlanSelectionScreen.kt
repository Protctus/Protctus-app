package com.protctus.app.ui.screens.auth

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.PaymentState
import com.protctus.app.viewmodel.PremiumPlan
import com.protctus.app.viewmodel.PremiumViewModel
import kotlinx.coroutines.delay

@Composable
fun PlanSelectionScreen(
    navController: NavController,
    premiumViewModel: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val paymentState by premiumViewModel.paymentState.collectAsState()
    val paymentError by premiumViewModel.paymentError.collectAsState()
    val isPremium by premiumViewModel.isPremiumUser.collectAsState()

    var selectedTier by remember { mutableStateOf("free") } // "free", "premium", "elite"
    var selectedDuration by remember { mutableStateOf(12) } // 1, 3, 6, 12 months
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // On successful payment, navigate to PaymentSuccess Screen
    LaunchedEffect(paymentState) {
        if (paymentState == PaymentState.SUCCESS) {
            premiumViewModel.resetPaymentState()
            navController.navigate(Route.PaymentSuccess) {
                popUpTo(Route.PlanSelection) { inclusive = true }
            }
        }
    }

    // Payment Error Dialog
    if (paymentState == PaymentState.FAILED && paymentError != null) {
        AlertDialog(
            onDismissRequest = { premiumViewModel.resetPaymentState() },
            containerColor = com.protctus.app.ui.theme.SurfaceColor,
            icon = {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
            },
            title = { Text("Payment Failed", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(paymentError ?: "Please try again.", color = Color(0xFF7F909F)) },
            confirmButton = {
                TextButton(onClick = { premiumViewModel.resetPaymentState() }) {
                    Text("OK", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val isLoading = paymentState == PaymentState.CREATING_ORDER || paymentState == PaymentState.VERIFYING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.protctus.app.ui.theme.DarkBackground,
                        Color(0xFF0A0B0E),
                        com.protctus.app.ui.theme.DarkBackground
                    )
                )
            )
    ) {
        // Subtle background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E3A5F).copy(alpha = 0.15f), Color.Transparent),
                    radius = size.width * 0.6f
                ),
                center = Offset(size.width * 0.3f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF5B21B6).copy(alpha = 0.08f), Color.Transparent),
                    radius = size.width * 0.5f
                ),
                center = Offset(size.width * 0.8f, size.height * 0.7f)
            )
        }

        // ── Close (X) button — top right ──
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2D38))
                .clickable {
                    navController.navigate(Route.Dashboard) {
                        popUpTo(Route.PlanSelection) { inclusive = true }
                    }
                }
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Skip",
                tint = Color(0xFF7F909F),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E3A5F), Color(0xFF3B82F6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Choose Your\nProtection Level",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Start free. Upgrade anytime.",
                        color = Color(0xFF7F909F),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // ── Duration Selector (Only for Premium/Elite) ──
            AnimatedVisibility(
                visible = isVisible && selectedTier != "free",
                enter = fadeIn() + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Select Subscription Duration",
                        color = Color(0xFF7F909F),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E212A))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1, 3, 6, 12).forEach { months ->
                            val isSelected = selectedDuration == months
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF3B82F6) else Color.Transparent)
                                    .clickable { selectedDuration = months }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$months Mo",
                                    color = if (isSelected) Color.White else Color(0xFF7F909F),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ── FREE Plan Card ──
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, 200)) + slideInVertically(tween(500, 200)) { 60 }
            ) {
                PlanCard(
                    tier = "free",
                    title = "Free",
                    price = "₹0",
                    period = "forever",
                    tagText = null,
                    tagColor = Color.Transparent,
                    borderGradient = listOf(Color(0xFF2A2D38), Color(0xFF2A2D38)),
                    isSelected = selectedTier == "free",
                    features = listOf(
                        "Smart Scan" to true,
                        "Security Score Dashboard" to true,
                        "App Permission Auditor" to true,
                        "Gamification & Quests" to true,
                        "Protctus Lab (5 chapters)" to true,
                        "URL Scanner (3/day)" to true,
                        "1 Free Dark Web Scan" to true,
                        "Deep Scan" to false,
                        "Real-Time Protection" to false,
                        "Phishing Shield (VPN)" to false,
                    ),
                    onClick = { selectedTier = "free" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── PREMIUM Plan Card ──
            val premiumBaseMonthly = 49
            val premiumPrice = premiumBaseMonthly * selectedDuration
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, 350)) + slideInVertically(tween(500, 350)) { 60 }
            ) {
                PlanCard(
                    tier = "premium",
                    title = "Premium",
                    price = "₹$premiumPrice",
                    period = if (selectedDuration == 1) "/mo" else "/$selectedDuration mo",
                    tagText = if (selectedDuration == 12) "BEST VALUE" else null,
                    tagColor = Color(0xFF4ADE80),
                    borderGradient = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)),
                    isSelected = selectedTier == "premium",
                    features = listOf(
                        "Everything in Free" to true,
                        "Real-Time Protection" to true,
                        "Deep Scan (YARA + AI)" to true,
                        "Phishing Shield (VPN)" to true,
                        "Dark Web Monitoring" to true,
                        "Stalkerware Scanner" to true,
                        "APK Quarantine" to true,
                        "Unlimited URL Scanner" to true,
                        "All Protctus Lab Chapters" to true,
                        "Ad-Free Experience" to true,
                    ),
                    onClick = { selectedTier = "premium" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── ELITE Plan Card ──
            val eliteBaseMonthly = 99
            val elitePrice = eliteBaseMonthly * selectedDuration
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, 500)) + slideInVertically(tween(500, 500)) { 60 }
            ) {
                PlanCard(
                    tier = "elite",
                    title = "Elite",
                    price = "₹$elitePrice",
                    period = if (selectedDuration == 1) "/mo" else "/$selectedDuration mo",
                    tagText = "GOD MODE",
                    tagColor = com.protctus.app.ui.theme.PremiumGold,
                    borderGradient = listOf(com.protctus.app.ui.theme.PremiumGold, Color(0xFFFF8C00)),
                    isSelected = selectedTier == "elite",
                    features = listOf(
                        "Everything in Premium" to true,
                        "Fileless Malware Detection" to true,
                        "Advanced Heuristic Engine" to true,
                        "USB Threat Protection" to true,
                        "PDF Security Reports" to true,
                        "Priority Signature Updates" to true,
                        "Elite Leaderboard Badge" to true,
                        "Early Access Lab Content" to true,
                    ),
                    onClick = { selectedTier = "elite" }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── CTA Button ──
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, 700))
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                } else {
                    val buttonGradient = when (selectedTier) {
                        "premium" -> Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
                        "elite" -> Brush.horizontalGradient(listOf(com.protctus.app.ui.theme.PremiumGold, Color(0xFFFF8C00)))
                        else -> Brush.horizontalGradient(listOf(Color(0xFF2A2D38), Color(0xFF3A3D48)))
                    }
                    val buttonText = when (selectedTier) {
                        "free" -> "Continue for Free"
                        "premium" -> "Get Premium — ₹$premiumPrice"
                        "elite" -> "Go Elite — ₹$elitePrice"
                        else -> "Continue"
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val buttonScale by animateFloatAsState(if (isPressed) 0.96f else 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(buttonScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(buttonGradient)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                when (selectedTier) {
                                    "free" -> {
                                        navController.navigate(Route.Dashboard) {
                                            popUpTo(Route.PlanSelection) { inclusive = true }
                                        }
                                    }
                                    "premium" -> {
                                        val customPlan = PremiumPlan("premium_custom", "Premium $selectedDuration Mo", "", null, premiumPrice * 100, "INR")
                                        if (activity != null) premiumViewModel.initiatePurchase(customPlan, activity, selectedDuration)
                                    }
                                    "elite" -> {
                                        val customPlan = PremiumPlan("elite_custom", "Elite $selectedDuration Mo", "", null, elitePrice * 100, "INR")
                                        if (activity != null) premiumViewModel.initiatePurchase(customPlan, activity, selectedDuration)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buttonText,
                            color = if (selectedTier == "elite") Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip link for paid plans (they can always upgrade later)
            if (selectedTier != "free") {
                Text(
                    text = "Maybe later — continue for free",
                    color = Color(0xFF4A5568),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            navController.navigate(Route.Dashboard) {
                                popUpTo(Route.PlanSelection) { inclusive = true }
                            }
                        }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlanCard(
    tier: String,
    title: String,
    price: String,
    period: String,
    tagText: String?,
    tagColor: Color,
    borderGradient: List<Color>,
    isSelected: Boolean,
    features: List<Pair<String, Boolean>>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (isPressed) 0.97f else 1f)
    val borderAlpha by animateFloatAsState(if (isSelected) 1f else 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = Brush.linearGradient(borderGradient.map { it.copy(alpha = borderAlpha) }),
                shape = RoundedCornerShape(24.dp)
            )
            .background(
                if (isSelected) com.protctus.app.ui.theme.SurfaceColor
                else com.protctus.app.ui.theme.SurfaceColor.copy(alpha = 0.5f)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = period,
                        color = Color(0xFF7F909F),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tagText != null) {
                        Box(
                            modifier = Modifier
                                .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tagText,
                                color = tagColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Selection radio
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) borderGradient.first() else Color(0xFF3A3D48), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(borderGradient)
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = title, color = Color(0xFF7F909F), fontSize = 14.sp, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(16.dp))

            // Feature list
            features.forEach { (feature, included) ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (included) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        tint = if (included) Color(0xFF4ADE80) else Color(0xFF4A4D58),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = feature,
                        color = if (included) Color(0xFFD0D5DD) else Color(0xFF4A4D58),
                        fontSize = 13.sp,
                        fontWeight = if (included) FontWeight.Normal else FontWeight.Light
                    )
                }
            }
        }
    }
}
