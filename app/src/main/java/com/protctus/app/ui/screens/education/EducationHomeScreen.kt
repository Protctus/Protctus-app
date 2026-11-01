package com.protctus.app.ui.screens.education

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

import com.protctus.app.viewmodel.LabChaptersViewModel

/**
 * Premium Education Home Screen
 * 
 * DESIGN:
 * - Staggered card entrances
 * - 3D CTA button for "Continue Learning"
 * - Premium stat cards with high-quality icons
 * - No giant mascot wasting space (moved to contextual screens)
 * 
 * STRICT: All colors from EduColors → Color.kt. No Color(). No hex. No glow.
 */
@Composable
fun EducationHomeScreen(
    navController: NavController,
    viewModel: EducationViewModel = hiltViewModel(),
    labViewModel: LabChaptersViewModel = hiltViewModel()
) {
    val streak by viewModel.streak.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val nodes by labViewModel.nodes.collectAsState()

    val activeNode = remember(nodes) {
        nodes.firstOrNull { !it.isLocked && !it.isCompleted } ?: nodes.lastOrNull()
    }

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
            // ── Premium Header ──
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                title = "Education Hub",
                actions = {
                    EduStatBadge(
                        icon = {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = EduColors.StreakFlame,
                                modifier = Modifier.size(EduDimens.IconSizeSm)
                            )
                        },
                        value = "${streak.currentStreak}",
                        modifier = Modifier.clickable { navController.navigate(Route.Streak) }
                    )
                    EduStatBadge(
                        icon = {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = "XP",
                                tint = EduColors.XpGold,
                                modifier = Modifier.size(EduDimens.IconSizeSm)
                            )
                        },
                        value = "${stats.totalXp}",
                        modifier = Modifier.clickable { navController.navigate(Route.Profile) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EduDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(EduDimens.SpacingLg)
            ) {
                // ── Greeting Section ──
                AnimatedEntrance(delayMs = 0) {
                    Column {
                        Text(
                            text = "Good evening, Learner",
                            style = EduTypography.H1
                        )
                        Spacer(modifier = Modifier.height(EduDimens.SpacingXs))
                        Text(
                            text = "Rank: ${stats.rank} • Keep up the momentum!",
                            style = EduTypography.BodyLarge
                        )
                    }
                }

                // ── Learning Path CTA (Most Important) ──
                AnimatedEntrance(delayMs = 100) {
                    EduCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Route.LearningPaths) },
                        backgroundColor = EduColors.SurfaceLight,
                        borderColor = EduColors.PrimaryBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(EduDimens.CardPadding)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(EduDimens.AvatarSizeMd)
                                        .clip(CircleShape)
                                        .background(EduColors.PrimaryMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Map,
                                        contentDescription = null,
                                        tint = EduColors.Primary,
                                        modifier = Modifier.size(EduDimens.IconSizeLg)
                                    )
                                }
                                Spacer(modifier = Modifier.width(EduDimens.SpacingLg))
                                Column {
                                    if (activeNode != null) {
                                        Text(activeNode.title, style = EduTypography.Title)
                                        Spacer(modifier = Modifier.height(EduDimens.SpacingXs))
                                        Text(
                                            if (activeNode.isCompleted) "All modules complete!" else "Up Next", 
                                            style = EduTypography.Body
                                        )
                                    } else {
                                        Text("Loading...", style = EduTypography.Title)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                            
                            // High quality 3D Button for primary action
                            Edu3DPrimaryButton(
                                text = if (activeNode?.isCompleted == true) "REVIEW PATH" else "CONTINUE LEARNING",
                                onClick = { 
                                    if (activeNode != null) navController.navigate(Route.LearningPaths) 
                                },
                                isEnabled = activeNode != null
                            )
                        }
                    }
                }

                // ── Stats Grid ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EduDimens.SpacingLg)
                ) {
                    // Daily Goal Card
                    AnimatedEntrance(delayMs = 200, modifier = Modifier.weight(1f)) {
                        EduCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.85f),
                            onClick = { navController.navigate(Route.DailyGoal) }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(EduDimens.SpacingMd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    EduProgressRing(
                                        progress = dailyGoal.currentProgress.toFloat() / dailyGoal.maxTarget.toFloat(),
                                        size = EduDimens.AvatarSizeLg,
                                        strokeWidth = 8.dp,
                                        color = EduColors.Primary
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${dailyGoal.currentProgress}/${dailyGoal.maxTarget}",
                                            style = EduTypography.Title
                                        )
                                        Text(
                                            "MINS",
                                            style = EduTypography.Micro
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                                Text("Daily Goal", style = EduTypography.Label)
                            }
                        }
                    }
                    
                    // Badges Card
                    AnimatedEntrance(delayMs = 300, modifier = Modifier.weight(1f)) {
                        EduCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.85f),
                            onClick = { navController.navigate(Route.Achievements) }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(EduDimens.SpacingMd)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(EduDimens.AvatarSizeLg)
                                        .clip(CircleShape)
                                        .background(EduColors.AccentMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.MilitaryTech,
                                        contentDescription = null,
                                        tint = EduColors.Accent,
                                        modifier = Modifier.size(EduDimens.IconSizeXl)
                                    )
                                }
                                Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                                Text("Badges & Ranks", style = EduTypography.Label)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(EduDimens.SpacingMassive))
        }
    }
}
