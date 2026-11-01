package com.protctus.app.ui.screens.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

/**
 * Education Content Screen — Shows lesson content BEFORE the quiz.
 *
 * Layout:
 * - Fixed top bar (back, category, progress)
 * - Scrollable lesson content
 * - Fixed bottom CTA ("Continue" / "Start Quiz")
 *
 * STRICT: All colors from EduColors. No Color(). No hardcoded hex.
 */
@Composable
fun EducationContentScreen(
    navController: NavController,
    title: String,
    category: String,
    description: String,
    progress: Float,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EduColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ── Fixed Top Bar ──
        EduTopBar(
            onBackClick = { navController.popBackStack() },
            title = category
        )

        // ── Progress Bar ──
        EduProgressBar(
            progress = progress,
            color = EduColors.Primary,
            modifier = Modifier.padding(horizontal = EduDimens.ScreenPadding)
        )

        Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

        // ── Scrollable Content ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EduDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

            // Category chip
            EduStatusChip(
                text = category,
                color = EduColors.Secondary
            )

            Spacer(modifier = Modifier.height(EduDimens.SpacingXl))

            // Lesson icon
            AnimatedScaleEntrance(delayMs = 100) {
                Box(
                    modifier = Modifier
                        .size(EduDimens.AvatarSizeMd)
                        .clip(CircleShape)
                        .background(EduColors.PrimaryMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = "Lesson",
                        tint = EduColors.Primary,
                        modifier = Modifier.size(EduDimens.IconSizeLg)
                    )
                }
            }

            Spacer(modifier = Modifier.height(EduDimens.SpacingXxl))

            // Lesson Title
            AnimatedEntrance(delayMs = 150) {
                Text(
                    text = title,
                    style = EduTypography.H2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(EduDimens.SpacingXxl))

            // Lesson Content Card
            AnimatedEntrance(delayMs = 250) {
                EduCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = EduColors.SurfaceLight,
                    borderColor = EduColors.CardBorder
                ) {
                    Column(
                        modifier = Modifier.padding(EduDimens.CardPadding)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = EduDimens.SpacingMd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = EduColors.Primary,
                                modifier = Modifier.size(EduDimens.IconSizeSm)
                            )
                            Spacer(modifier = Modifier.width(EduDimens.SpacingSm))
                            Text(
                                text = "Key Concept",
                                style = EduTypography.Label.copy(color = EduColors.Primary)
                            )
                        }

                        Text(
                            text = description,
                            style = EduTypography.BodyLarge,
                            lineHeight = EduTypography.BodyLarge.lineHeight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))
        }

        // ── Fixed Bottom CTA ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EduColors.Background)
                .padding(
                    horizontal = EduDimens.ScreenPadding,
                    vertical = EduDimens.SpacingLg
                )
        ) {
            Edu3DPrimaryButton(
                text = "START QUIZ",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
