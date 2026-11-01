package com.protctus.app.ui.screens.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Premium Achievements Screen
 *
 * DESIGN:
 * - Grid layout with premium cards
 * - Animated staggered entrance
 *
 * STRICT: All colors from EduColors.
 */
@Composable
fun AchievementsScreen(navController: NavController, viewModel: EducationViewModel = hiltViewModel()) {
    val achievements by viewModel.achievements.collectAsState()

    Scaffold(
        containerColor = EduColors.Background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                title = "Badges & Ranks"
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(EduDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(EduDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(EduDimens.SpacingLg),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(achievements) { index, achievement ->
                    AnimatedEntrance(delayMs = index * 50) {
                        EduCard(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
                            backgroundColor = if (achievement.isUnlocked) EduColors.SurfaceLight else EduColors.SurfaceDark,
                            borderColor = if (achievement.isUnlocked) EduColors.Accent.copy(alpha = 0.5f) else EduColors.CardBorder
                        ) {
                            Column(
                                modifier = Modifier.padding(EduDimens.SpacingMd),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(EduDimens.AvatarSizeLg)
                                        .clip(CircleShape)
                                        .background(
                                            if (achievement.isUnlocked) EduColors.AccentMuted
                                            else EduColors.Background
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (achievement.isUnlocked) Icons.Rounded.MilitaryTech else Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = if (achievement.isUnlocked) EduColors.Accent else EduColors.TextDisabled,
                                        modifier = Modifier.size(EduDimens.IconSizeXl)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                                
                                Text(
                                    text = achievement.title,
                                    style = EduTypography.Label.copy(
                                        color = if (achievement.isUnlocked) EduColors.TextPrimary else EduColors.TextDisabled
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(EduDimens.SpacingXs))
                                Text(
                                    text = achievement.description,
                                    style = EduTypography.Micro,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
