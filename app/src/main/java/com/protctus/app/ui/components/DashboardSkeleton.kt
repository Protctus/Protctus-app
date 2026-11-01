package com.protctus.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF242121), // Base placeholder color
            Color(0xFF383434), // Shimmer highlight
            Color(0xFF242121)
        ),
        start = Offset(shimmerTranslate - 300f, shimmerTranslate - 300f),
        end = Offset(shimmerTranslate + 300f, shimmerTranslate + 300f)
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {


        // Main Security Card Skeleton Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature Grid Skeleton Placeholder
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(16.dp)).background(shimmerBrush))
            Box(modifier = Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(16.dp)).background(shimmerBrush))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(16.dp)).background(shimmerBrush))
            Box(modifier = Modifier.weight(1f).height(140.dp).clip(RoundedCornerShape(16.dp)).background(shimmerBrush))
        }
    }
}
