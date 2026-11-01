package com.protctus.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protctus.app.viewmodel.DailyQuest
import com.protctus.app.utils.QuestTracker
import kotlinx.coroutines.delay

@Composable
fun QuestGuideOverlay(
    activeQuest: DailyQuest?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isCompleted by remember { mutableStateOf(false) }

    // Check completion state dynamically
    LaunchedEffect(activeQuest) {
        if (activeQuest == null) {
            isCompleted = false
            return@LaunchedEffect
        }
        while (true) {
            if (QuestTracker.isActionCompleted(context, activeQuest.actionTrigger)) {
                isCompleted = true
            }
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = activeQuest != null,
        enter = slideInVertically(initialOffsetY = { -200 }) + fadeIn(tween(400)),
        exit = slideOutVertically(targetOffsetY = { -200 }) + fadeOut(tween(400)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
    ) {
        activeQuest?.let { quest ->
            val bgColor = if (isCompleted) Color(0xFF10B981).copy(alpha = 0.95f) else Color(0xFF2A2B31).copy(alpha = 0.95f)
            val iconColor = if (isCompleted) Color.White else Color(0xFFFACC15)
            val icon = if (isCompleted) Icons.Rounded.TaskAlt else Icons.Rounded.PlayArrow

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Quest Icon",
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCompleted) "Quest Completed!" else "Active Quest",
                            color = if (isCompleted) Color.White else Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = quest.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isCompleted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = quest.subtitle,
                                color = Color(0xFFD1D5DB),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = if (isCompleted) Color.White else Color(0xFF6B7280),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
