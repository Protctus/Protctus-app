package com.protctus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable paywall bottom sheet shown when a free user taps a locked premium feature.
 * Supports both Premium and Elite tiers with dynamic theme switching.
 */
@Composable
fun PremiumPaywallSheet(
    featureName: String,
    isEliteRequired: Boolean = false,
    onUpgradeClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = if (isEliteRequired) Color(0xFFFFD700) else Color(0xFF3B82F6)
    val secondaryColor = if (isEliteRequired) Color(0xFFFFA000) else Color(0xFF8B5CF6)
    val tierName = if (isEliteRequired) "Elite" else "Premium"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            com.protctus.app.ui.theme.SurfaceColor,
                            com.protctus.app.ui.theme.DarkBackground
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume clicks on the sheet body
                )
                .padding(horizontal = 28.dp, vertical = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle indicator
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3A3D48))
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Icon with gradient glow
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.2f), secondaryColor.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEliteRequired) Icons.Outlined.Star else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "$featureName is an\n$tierName Feature",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isEliteRequired) {
                        "Upgrade to Elite to unlock enterprise-grade protection including the Invisible Hydration Engine and USB Threat protection."
                    } else {
                        "Upgrade to unlock $featureName and get full protection with Deep Scan, Real-Time Protection, and Phishing Shield."
                    },
                    color = Color(0xFF7F909F),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Upgrade button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val btnScale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "btn_scale")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(btnScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(primaryColor, secondaryColor)
                            )
                        )
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onUpgradeClick),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isEliteRequired) Icons.Outlined.Star else Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isEliteRequired) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Upgrade to $tierName",
                            color = if (isEliteRequired) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Price hint
                Text(
                    text = if (isEliteRequired) "Starting at ₹799/year" else "Starting at ₹349/year",
                    color = Color(0xFF4A5568),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Small "PRO" or "ELITE" badge to overlay on feature tiles.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier, isElite: Boolean = false) {
    val iconColor = if (isElite) Color(0xFFFFD700) else Color(0xFFFBBF24)

    Box(
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isElite) Icons.Outlined.Star else Icons.Outlined.WorkspacePremium,
            contentDescription = if (isElite) "Elite" else "Premium",
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
