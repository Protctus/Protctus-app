package com.protctus.app.ui.screens.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Premium Daily Goals Screen
 *
 * DESIGN:
 * - Premium Top Bar
 * - Huge central animated progress ring
 * - Premium challenge card
 *
 * STRICT: All colors from EduColors.
 */
@Composable
fun DailyGoalsScreen(navController: NavController, viewModel: EducationViewModel = hiltViewModel()) {
    val dailyGoal by viewModel.dailyGoal.collectAsState()

    Scaffold(
        containerColor = EduColors.Background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                title = "Daily Goals"
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EduDimens.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                
                AnimatedScaleEntrance {
                    Box(contentAlignment = Alignment.Center) {
                        EduProgressRing(
                            progress = dailyGoal.currentProgress.toFloat() / dailyGoal.maxTarget.toFloat(),
                            size = 240.dp,
                            strokeWidth = 20.dp,
                            color = EduColors.Primary,
                            backgroundColor = EduColors.SurfaceDark
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${dailyGoal.currentProgress}/${dailyGoal.maxTarget}", style = EduTypography.H1)
                            Spacer(modifier = Modifier.height(EduDimens.SpacingXs))
                            Text("MODULES", style = EduTypography.Label)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))

                AnimatedEntrance(delayMs = 150) {
                    EduCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(EduDimens.CardPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = EduColors.Primary,
                                    modifier = Modifier.size(EduDimens.IconSizeLg)
                                )
                                Spacer(modifier = Modifier.width(EduDimens.SpacingMd))
                                Text("Daily Challenge", style = EduTypography.Title)
                            }
                            
                            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))
                            
                            Text(
                                "Complete ${dailyGoal.maxTarget} learning modules today to earn bonus XP and maintain your streak.",
                                style = EduTypography.BodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
