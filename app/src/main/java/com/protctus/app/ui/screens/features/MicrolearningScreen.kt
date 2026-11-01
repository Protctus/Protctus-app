package com.protctus.app.ui.screens.features

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.screens.education.EducationContentScreen
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*
import com.protctus.app.viewmodel.CardLockState
import com.protctus.app.viewmodel.FeedUiState
import com.protctus.app.viewmodel.QuizEvent
import com.protctus.app.viewmodel.SecurityQuizViewModel

/**
 * Premium Microlearning Session Screen
 *
 * The container for the education + quiz flow:
 * - INFO cards → Content view (read lesson, then continue)
 * - QUIZ cards → Interactive quiz with 3D buttons + Sentinel mascot
 *
 * Premium features:
 * - Immersive full-screen card pager
 * - Top bar with XP badge and streak indicator
 * - Smooth spring-based page transitions
 * - Premium skeleton loading state with Sentinel mascot
 *
 * STRICT: All colors from EduColors → Color.kt. No Color(). No hex.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MicrolearningScreen(navController: NavController) {
    val viewModel: SecurityQuizViewModel = hiltViewModel()
    val feedState by viewModel.feedState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val userXp by viewModel.userXp.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EduColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ── Premium Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = EduDimens.ScreenPadding,
                    vertical = EduDimens.SpacingLg
                )
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f),
                actions = {
                    // XP Badge
                    EduStatBadge(
                        icon = {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = "XP",
                                tint = EduColors.XpGold,
                                modifier = Modifier.size(EduDimens.IconSizeMd)
                            )
                        },
                        value = "$userXp"
                    )
                }
            )
        }

        // ── Main Content ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = feedState) {
                is FeedUiState.Loading -> {
                    PremiumLoadingSkeleton()
                }

                is FeedUiState.Empty -> {
                    PremiumEmptyState()
                }

                is FeedUiState.Success -> {
                    val feed = state.data
                    val firstActiveIndex = remember(feed) {
                        feed.indexOfFirst { it.lockState == CardLockState.ACTIVE }
                            .coerceAtLeast(0)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = firstActiveIndex,
                        pageCount = { feed.size }
                    )

                    // Handle auto-navigation on correct answer
                    LaunchedEffect(viewModel) {
                        viewModel.quizEvents.collect { event ->
                            when (event) {
                                is QuizEvent.NavigateToNext -> {
                                    if (pagerState.currentPage < feed.size - 1) {
                                        pagerState.animateScrollToPage(
                                            page = pagerState.currentPage + 1,
                                            animationSpec = spring(
                                                dampingRatio = 0.8f,
                                                stiffness = 100f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        key = { page -> feed[page].card.id }
                    ) { page ->
                        val cardItem = feed[page]
                        val cardProgress = (page + 1).toFloat() / feed.size.coerceAtLeast(1)

                        when (cardItem.card.type) {
                            "INFO" -> {
                                // Education Content Screen — show lesson before quiz
                                EducationContentScreen(
                                    navController = navController,
                                    title = cardItem.card.title,
                                    category = cardItem.card.category,
                                    description = cardItem.card.description,
                                    progress = cardProgress,
                                    onContinue = {
                                        viewModel.submitAnswer(cardItem.card.id, true)
                                    }
                                )
                            }

                            else -> {
                                // Quiz Screen — interactive 3D quiz
                                MicrolearningCard(
                                    uiItem = cardItem,
                                    progress = cardProgress,
                                    onAnswerSubmit = { isCorrect ->
                                        viewModel.submitAnswer(cardItem.card.id, isCorrect)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Premium Empty State — Sentinel mascot looking sad
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PremiumEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(EduDimens.SpacingHuge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedScaleEntrance {
            ProtctusMascot(
                state = MascotState.SAD,
                size = EduDimens.MascotSizeLg
            )
        }

        Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))

        AnimatedEntrance(delayMs = 200) {
            Text(
                text = "No Lessons Available",
                style = EduTypography.H1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

        AnimatedEntrance(delayMs = 350) {
            Text(
                text = "Check your connection or try another path.\nThe cyber-realm is currently quiet.",
                style = EduTypography.Body,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Premium Loading Skeleton — Animated shimmer with mascot
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PremiumLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(EduDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skeleton card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(EduShapes.RadiusXl))
                .background(EduColors.SurfaceLight.copy(alpha = alpha))
                .padding(EduDimens.CardPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Sentinel mascot animating
                ProtctusMascot(
                    state = MascotState.THINKING,
                    size = EduDimens.MascotSizeMd
                )

                Spacer(modifier = Modifier.height(EduDimens.SpacingXxl))

                // Skeleton progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(EduColors.SurfaceDark)
                )

                Spacer(modifier = Modifier.height(EduDimens.SpacingXxl))

                // Skeleton title
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(24.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(EduShapes.RadiusSm))
                        .background(EduColors.SurfaceDark)
                )

                Spacer(modifier = Modifier.height(EduDimens.SpacingHuge))

                // Skeleton buttons
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EduDimens.ButtonHeight)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(EduShapes.RadiusLg))
                            .background(EduColors.SurfaceDark)
                    )
                    Spacer(modifier = Modifier.height(EduDimens.SpacingMd))
                }
            }
        }
    }
}
