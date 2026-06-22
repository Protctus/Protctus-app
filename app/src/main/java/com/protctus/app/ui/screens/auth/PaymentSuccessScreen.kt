package com.protctus.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.PremiumViewModel
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun PaymentSuccessScreen(
    navController: NavController,
    premiumViewModel: PremiumViewModel = hiltViewModel()
) {
    val tierWeight by premiumViewModel.subscriptionTier.collectAsState()
    val tierName = if (tierWeight == 2) "ELITE" else "PREMIUM"
    
    var isVisible by remember { mutableStateOf(false) }

    // Crashlytics/Timber placeholder for "intermittent crashes"
    LaunchedEffect(Unit) {
        try {
            android.util.Log.d("PaymentSuccess", "Screen loaded for tier: $tierName")
            delay(300)
            isVisible = true
        } catch (e: Exception) {
            android.util.Log.e("PaymentSuccess", "CRASH in UI initialization", e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E)),
        contentAlignment = Alignment.Center
    ) {
        // Confetti Canvas
        ConfettiBackground()

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.8f, animationSpec = tween(800, easing = EaseOutBack))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tier Badge
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (tierName == "ELITE") 
                                Brush.linearGradient(listOf(com.protctus.app.ui.theme.PremiumGold, Color(0xFFFF8C00)))
                            else 
                                Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = "Shield",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome to the Inner Circle.",
                    color = Color(0xFF7F909F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You are now $tierName",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Features Summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF161821))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Unlocked Features",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    val features = if (tierName == "ELITE") {
                        listOf("God Mode Protection", "Advanced Heuristics", "Fileless Malware Detection", "Priority Updates")
                    } else {
                        listOf("Real-Time Protection", "Deep Scan Engine", "Phishing Shield VPN", "Dark Web Monitoring")
                    }

                    features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = feature, color = Color(0xFFD0D5DD), fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Email notification trust text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Email, contentDescription = null, tint = Color(0xFF7F909F), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check your email for the official invoice.",
                        color = Color(0xFF7F909F),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (tierName == "ELITE") 
                                Brush.horizontalGradient(listOf(com.protctus.app.ui.theme.PremiumGold, Color(0xFFFF8C00)))
                            else 
                                Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
                        )
                        .clickable {
                            navController.navigate(Route.Dashboard) {
                                popUpTo(Route.PaymentSuccess) { inclusive = true }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start Protecting My Device",
                        color = if (tierName == "ELITE") Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ConfettiBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Generate random particles
    val particles = remember {
        List(40) {
            ConfettiParticle(
                xOffset = Random.nextFloat(), // 0f to 1f multiplier of width
                yOffset = Random.nextFloat() * 2000f, // initial staggering
                color = listOf(
                    Color(0xFF3B82F6),
                    Color(0xFFFF8C00),
                    Color(0xFF4ADE80),
                    Color(0xFF8B5CF6),
                    Color(0xFFFFD700)
                ).random(),
                size = Random.nextFloat() * 10f + 5f,
                speedMultiplier = Random.nextFloat() * 0.5f + 0.8f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { p ->
            // Calculate current Y considering loop
            val currentY = ((offsetY * p.speedMultiplier) + p.yOffset) % height
            drawCircle(
                color = p.color,
                radius = p.size,
                center = Offset(p.xOffset * width, currentY)
            )
        }
    }
}

private data class ConfettiParticle(
    val xOffset: Float,
    val yOffset: Float,
    val color: Color,
    val size: Float,
    val speedMultiplier: Float
)
