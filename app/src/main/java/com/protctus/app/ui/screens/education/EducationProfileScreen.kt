package com.protctus.app.ui.screens.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Premium Education Profile Screen
 *
 * DESIGN:
 * - Animated Mascot Avatar
 * - Premium stats grid inside a card
 *
 * STRICT: All colors from EduColors.
 */
@Composable
fun EducationProfileScreen(navController: NavController, viewModel: EducationViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        containerColor = EduColors.Background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                title = "Learner Profile"
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EduDimens.ScreenPadding)
            ) {
                Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                
                // Big Avatar / Mascot
                AnimatedScaleEntrance {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(EduColors.SurfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            ProtctusMascot(state = MascotState.HAPPY, size = 100.dp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))
                
                AnimatedEntrance(delayMs = 150) {
                    EduCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(EduDimens.SpacingXl)) {
                            Text("Lifetime Statistics", style = EduTypography.Title)
                            Spacer(modifier = Modifier.height(EduDimens.SpacingXl))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatItem(
                                    icon = Icons.Rounded.Bolt,
                                    color = EduColors.XpGold,
                                    label = "Total XP",
                                    value = "${stats.totalXp}",
                                    modifier = Modifier.weight(1f)
                                )
                                StatItem(
                                    icon = Icons.Rounded.CheckCircle,
                                    color = EduColors.Success,
                                    label = "Modules",
                                    value = "${stats.completedModules}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(EduDimens.SpacingXl))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatItem(
                                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                    color = EduColors.Primary,
                                    label = "Accuracy",
                                    value = "${(stats.accuracy * 100).toInt()}%",
                                    modifier = Modifier.weight(1f)
                                )
                                StatItem(
                                    icon = Icons.Rounded.MilitaryTech,
                                    color = EduColors.Accent,
                                    label = "Rank",
                                    value = stats.rank,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(EduDimens.AvatarSizeMd)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(EduDimens.IconSizeMd)
            )
        }
        Spacer(modifier = Modifier.width(EduDimens.SpacingMd))
        Column {
            Text(value, style = EduTypography.Title)
            Text(label, style = EduTypography.Micro)
        }
    }
}
