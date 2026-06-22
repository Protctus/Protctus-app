    package com.protctus.app.ui.screens.features

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.viewmodel.SecurityQuizViewModel
import com.protctus.app.viewmodel.FeedUiState
import com.protctus.app.viewmodel.QuizEvent
import com.protctus.app.viewmodel.CardLockState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MicrolearningScreen(navController: NavController) {
    val viewModel: SecurityQuizViewModel = hiltViewModel()
    val feedState by viewModel.feedState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val userXp by viewModel.userXp.collectAsState()
    val dailyLimitReached by viewModel.dailyLimitReached.collectAsState()

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(com.protctus.app.ui.theme.SurfaceColor, CircleShape)
                        .border(1.dp, com.protctus.app.ui.theme.OutlineColor, CircleShape)
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Active Category Badge
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .background(com.protctus.app.ui.theme.SuccessColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .border(1.dp, com.protctus.app.ui.theme.SuccessColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCategory ?: "All Topics",
                        color = com.protctus.app.ui.theme.SuccessColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // XP Counter
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(16.dp))
                        .border(1.dp, com.protctus.app.ui.theme.OutlineColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = "XP", tint = com.protctus.app.ui.theme.PremiumGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "$userXp XP", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Feed Content ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = feedState) {
                        is FeedUiState.Loading -> {
                            MicrolearningSkeleton()
                        }
                        is FeedUiState.Empty -> {
                            EmptyFeedState()
                        }
                        is FeedUiState.Success -> {
                            val feed = state.data
                            val firstActiveIndex = remember(feed) { 
                                feed.indexOfFirst { it.lockState == CardLockState.ACTIVE }.coerceAtLeast(0) 
                            }
                            val pagerState = rememberPagerState(
                                initialPage = firstActiveIndex,
                                pageCount = { feed.size }
                            )

                            // Event Debouncing via Single Live Event
                            LaunchedEffect(viewModel) {
                                viewModel.quizEvents.collect { event ->
                                    when(event) {
                                        is QuizEvent.NavigateToNext -> {
                                            if (pagerState.currentPage < feed.size - 1) {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    }
                                }
                            }

                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = true,
                                key = { page -> feed[page].card.id } // CS-2.2 fix
                            ) { page ->
                                val cardItem = feed[page]
                                MicrolearningCard(
                                    uiItem = cardItem,
                                    progress = (page + 1).toFloat() / feed.size.coerceAtLeast(1),
                                    onAnswerSubmit = { isCorrect ->
                                        viewModel.submitAnswer(cardItem.card.id, isCorrect)
                                    }
                                )
                            }

                            // Page indicator
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(com.protctus.app.ui.theme.SurfaceColor)
                                    .border(1.dp, com.protctus.app.ui.theme.OutlineColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${feed.size}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) com.protctus.app.ui.theme.SuccessColor.copy(alpha = 0.2f) else com.protctus.app.ui.theme.SurfaceColor,
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) com.protctus.app.ui.theme.SuccessColor else com.protctus.app.ui.theme.SecondaryText,
        label = "chipText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) com.protctus.app.ui.theme.SuccessColor else com.protctus.app.ui.theme.OutlineColor,
        label = "chipBorder"
    )

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyFeedState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = "Offline or Empty",
            tint = com.protctus.app.ui.theme.SecondaryText,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Content Available",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Check your connection or try another category.",
            color = Color(0xFF9CA3AF),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MicrolearningSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2B31).copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2B31).copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(24.dp))
        for (i in 0 until 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i == 2) 0.6f else 1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A2B31).copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2B31).copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2B31).copy(alpha = alpha))
            )
        }
    }
}

