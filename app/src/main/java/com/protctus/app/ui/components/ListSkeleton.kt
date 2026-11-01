package com.protctus.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
fun ListSkeleton(modifier: Modifier = Modifier, itemCount: Int = 3) {
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
            Color(0xFF32343B), // Slightly lighter than Surface placeholder
            Color(0xFF42444D), // Shimmer highlight
            Color(0xFF32343B)
        ),
        start = Offset(shimmerTranslate - 300f, shimmerTranslate - 300f),
        end = Offset(shimmerTranslate + 300f, shimmerTranslate + 300f)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(itemCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(com.protctus.app.ui.theme.SurfaceColor) // Match actual list item background
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(shimmerBrush))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Box(modifier = Modifier.size(160.dp, 14.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.size(100.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
                    }
                }
            }
        }
    }
}
