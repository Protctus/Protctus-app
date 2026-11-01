package com.protctus.app.ui.screens.education

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Premium Streak Screen
 *
 * DESIGN:
 * - Breathing flame animation
 * - Premium layout
 *
 * STRICT: All colors from EduColors.
 */
@Composable
fun StreakScreen(navController: NavController, viewModel: EducationViewModel = hiltViewModel()) {
    val streak by viewModel.streak.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_scale"
    )

    Scaffold(
        containerColor = EduColors.Background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            EduTopBar(
                onBackClick = { navController.popBackStack() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EduDimens.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(EduColors.StreakFlame.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = EduColors.StreakFlame,
                        modifier = Modifier.size(160.dp)
                    )
                    Text(
                        text = "${streak.currentStreak}",
                        style = EduTypography.H1.copy(color = EduColors.Background),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 25.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))
                
                AnimatedEntrance(delayMs = 100) {
                    Text(
                        text = "${streak.currentStreak} Day Streak!",
                        style = EduTypography.H1,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                
                AnimatedEntrance(delayMs = 200) {
                    Text(
                        text = "You are on fire! Keep learning every day to maintain your streak.",
                        style = EduTypography.BodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = EduDimens.SpacingLg)
                    )
                }
            }
        }
    }
}
