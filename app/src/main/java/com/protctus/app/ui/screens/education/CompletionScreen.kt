package com.protctus.app.ui.screens.education

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Premium Completion Screen
 *
 * Shown when a user finishes a quiz or lesson.
 * Features:
 * - Full screen confetti
 * - Celebrating Sentinel Mascot
 * - XP summary card
 * - 3D Continue button
 */
@Composable
fun CompletionScreen(navController: NavController, xpGained: Int = 50) {
    var triggerConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        triggerConfetti = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EduColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Confetti layer
        ConfettiOverlay(isTriggered = triggerConfetti)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = EduDimens.ScreenPadding, vertical = EduDimens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Spacer(modifier = Modifier.weight(0.5f))
            
            // Celebrating Mascot
            Box(modifier = Modifier.scale(scale)) {
                ProtctusMascot(
                    state = MascotState.CELEBRATING,
                    size = EduDimens.MascotSizeLg
                )
            }
            
            Spacer(modifier = Modifier.height(EduDimens.SpacingMassive))
            
            // Title
            AnimatedEntrance(delayMs = 150) {
                Text(
                    text = "Module Complete!",
                    style = EduTypography.H1,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
            
            // XP Summary Card
            AnimatedScaleEntrance(delayMs = 250) {
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(EduDimens.CardPadding),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "XP",
                            tint = EduColors.XpGold,
                            modifier = Modifier.size(EduDimens.IconSizeXl)
                        )
                        Spacer(modifier = Modifier.width(EduDimens.SpacingMd))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Total XP Gained", style = EduTypography.Label)
                            Text(
                                "+$xpGained",
                                style = EduTypography.H1.copy(color = EduColors.XpGold)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Continue CTA
            AnimatedEntrance(delayMs = 400) {
                Edu3DPrimaryButton(
                    text = "CONTINUE",
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
