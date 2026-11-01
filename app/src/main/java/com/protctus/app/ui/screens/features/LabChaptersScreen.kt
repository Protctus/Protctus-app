package com.protctus.app.ui.screens.features

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.theme.*
import com.protctus.app.viewmodel.LabChaptersViewModel

@Composable
fun LabChaptersScreen(navController: NavController) {
    val viewModel: LabChaptersViewModel = hiltViewModel()
    val nodes by viewModel.nodes.collectAsState()
    val categories = nodes.map { it.id }

    Scaffold(
        bottomBar = { ProtctusBottomNav(navController = navController) },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Close Button - Apple Style Glassy Button
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .size(32.dp)
                    .background(Color(0xFF2A2B31), CircleShape)
                    .clickable { navController.popBackStack() }
                    .align(Alignment.TopEnd)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(18.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 60.dp, start = 24.dp, end = 24.dp, bottom = 40.dp)
            ) {
                // Hero Section with Gamified Stats
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        ambientColor = AccentColor.copy(alpha = 0.4f),
                                        spotColor = AccentColor.copy(alpha = 0.4f)
                                    )
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(AccentColor, Color(0xFF2E639A))
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.School,
                                    contentDescription = "Education",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            
                            // Gamified Stats
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GamifiedStatBadge("12", "Streak", Color(0xFFF59E0B))
                                GamifiedStatBadge("450", "XP", PremiumGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Academy",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Master cybersecurity fundamentals through interactive missions.",
                            color = Color(0xFFA1A1A6),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 24.sp
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Generic "Start Full Course"
                item {
                    StartLearningButton(
                        text = "Resume Mission",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = {
                            navController.navigate(Route.MicrolearningFeed(categoryId = null))
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "LEARNING PATHS",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                    )
                }

                // Dynamic Topics Grid
                if (categories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentColor)
                        }
                    }
                } else {
                    val chunked = categories.chunked(2)
                    items(chunked) { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (category in rowCategories) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PremiumCategoryCard(
                                        title = category,
                                        onClick = {
                                            navController.navigate(Route.MicrolearningFeed(categoryId = category))
                                        }
                                    )
                                }
                            }
                            // Fill empty space if odd number of categories
                            if (rowCategories.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GamifiedStatBadge(value: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color(0xFFA1A1A6), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PremiumCategoryCard(
    title: String,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .aspectRatio(1f) // Square card
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                )
            )
            .border(1.dp, Color(0xFF38383A), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(AccentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun TopicListRow(
    title: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(16.dp)
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        isLast -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val haptics = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "scale")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .scale(scale)
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF1C1C1E))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        val up = waitForUpOrCancellation()
                        isPressed = false
                        if (up != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    }
                }
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = "Forward",
                tint = Color(0xFF636366),
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Divider (Apple style, inset)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp) // align with text
                        .height(0.5.dp)
                        .background(Color(0xFF38383A))
                )
            }
        }
    }
}

@Composable
private fun StartLearningButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onClick()
                    }
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
