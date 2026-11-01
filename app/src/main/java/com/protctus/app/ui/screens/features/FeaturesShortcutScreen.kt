package com.protctus.app.ui.screens.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.components.LockedFeatureDialog
import com.protctus.app.ui.components.ProBadge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush

@Composable
fun FeaturesShortcutScreen(navController: NavController) {
    val interactionSource = remember { MutableInteractionSource() }
    val settingsViewModel: com.protctus.app.viewmodel.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    
    // Server-authoritative tier from EntitlementManager (EncryptedSharedPrefs + D1 sync)
    val currentTier by settingsViewModel.currentTier.collectAsState()
    val subscriptionTier = currentTier.weight // 0 = Free, 1 = Premium, 2 = Elite
    
    var showPaywall by remember { mutableStateOf(false) }
    var paywallFeatureName by remember { mutableStateOf("") }
    var targetTierForPaywall by remember { mutableStateOf(1) } // Default to Premium

    // Staggered entry animation trigger
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Paywall overlay
    if (showPaywall) {
        LockedFeatureDialog(
            featureName = paywallFeatureName,
            onBuyNowClick = {
                showPaywall = false
                navController.navigate(Route.GetPremium)
            },
            onDismiss = { showPaywall = false }
        )
    }

    Scaffold(
        bottomBar = { ProtctusBottomNav(navController = navController) },
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(com.protctus.app.ui.theme.DarkBackground)
                    .padding(horizontal = 24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFE3E3E3)
                                )
                            }
                        }
                        
                        Text(
                            text = stringResource(R.string.features_shortcut),
                            color = Color(0xFFFFFFFF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Box(modifier = Modifier.size(48.dp)) // Empty box for balancing spacing
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = stringResource(R.string.quick_access),
                        color = Color(0xFFFFFFFF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Row 1 — Smart Scan (Free), Deep Scan (Premium), Rewards (Free)
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 500, delayMillis = 0)
                        ) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 0))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FeatureBlock(modifier = Modifier.weight(1f), title = "Smart Scan",
                                icon = Icons.Outlined.DocumentScanner, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.SmartScan) })
                            
                            FeatureBlock(
                                modifier = Modifier.weight(1f), title = "Deep Scan",
                                icon = Icons.AutoMirrored.Outlined.ManageSearch, iconTint = Color(0xFFE3E3E3),
                                isPro = subscriptionTier < 1,
                                onClick = {
                                    if (subscriptionTier >= 1) {
                                        navController.navigate(Route.DeepScan)
                                    } else {
                                        paywallFeatureName = "Deep Scan"
                                        targetTierForPaywall = 1
                                        showPaywall = true
                                    }
                                }
                            )
                            
                            FeatureBlock(modifier = Modifier.weight(1f), title = "Rewards",
                                icon = Icons.Outlined.WorkspacePremium, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.Rewards) })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row 2 — Threat Map (Free), Dark Web (Premium), Daily Quests (Free)
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 500, delayMillis = 120)
                        ) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 120))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FeatureBlock(modifier = Modifier.weight(1f), title = "Threat Map",
                                icon = Icons.Outlined.TravelExplore, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.ThreatMap) })
                            
                            FeatureBlock(
                                modifier = Modifier.weight(1f), title = "Dark Web",
                                icon = Icons.Outlined.DarkMode, iconTint = Color(0xFFE3E3E3),
                                isPro = subscriptionTier < 1,
                                onClick = {
                                    if (subscriptionTier >= 1) {
                                        navController.navigate(Route.DarkWebScan)
                                    } else {
                                        paywallFeatureName = "Dark Web Scan"
                                        targetTierForPaywall = 1
                                        showPaywall = true
                                    }
                                }
                            )
                            
                            FeatureBlock(modifier = Modifier.weight(1f), title = "Daily Quests",
                                icon = Icons.Outlined.EmojiEvents, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.DailyQuests) })
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Large Feature Blocks — Threat Feed (Free), Referral (Free)
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 500, delayMillis = 240)
                        ) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 240))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LargeFeatureBlock(modifier = Modifier.weight(1f), title = "Threat Feed",
                                icon = Icons.Outlined.DynamicFeed, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.ThreatFeed) })
                            LargeFeatureBlock(modifier = Modifier.weight(1f), title = "Referral",
                                icon = Icons.Outlined.CardGiftcard, iconTint = Color(0xFFE3E3E3),
                                onClick = { navController.navigate(Route.Referral) })
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = stringResource(R.string.protctus_lab),
                        color = Color(0xFFFFFFFF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Row 3 — Advance Settings (Elite), Protctus Lab (Free)
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 500, delayMillis = 360)
                        ) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 360))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LargeFeatureBlock(
                                modifier = Modifier.weight(1f),
                                title = "Advance Settings",
                                icon = Icons.Outlined.Science,
                                iconTint = com.protctus.app.ui.theme.AccentColor,
                                isPro = subscriptionTier < 2,
                                onClick = { 
                                    if (subscriptionTier >= 2) {
                                        navController.navigate(Route.AdvanceSettings)
                                    } else {
                                        paywallFeatureName = "Advance Settings"
                                        targetTierForPaywall = 2
                                        showPaywall = true
                                    }
                                }
                            )
                            LargeFeatureBlock(
                                modifier = Modifier.weight(1f),
                                title = "Protctus Lab",
                                icon = Icons.Outlined.School,
                                iconTint = com.protctus.app.ui.theme.AccentColor,
                                onClick = { navController.navigate(Route.LabChapters) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun FeatureBlock(modifier: Modifier, title: String, icon: ImageVector, iconTint: Color = Color(0xFFE3E3E3), isPro: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f)

    Box(
        modifier = modifier
            .height(120.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2D2E35)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = Color(0xFFE3E3E3), fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 15.sp)
        }

        // PRO badge top-right
        if (isPro) {
            ProBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            )
        }
    }
}

@Composable
fun LargeFeatureBlock(modifier: Modifier, title: String, icon: ImageVector, iconTint: Color = Color(0xFFE3E3E3), isPro: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f)

    Box(
        modifier = modifier
            .height(200.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF2D2E35)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = title, color = Color(0xFFE3E3E3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        // PRO badge top-right
        if (isPro) {
            ProBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 18.dp)
            )
        }
    }
}
