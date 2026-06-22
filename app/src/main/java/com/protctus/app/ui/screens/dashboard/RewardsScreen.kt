package com.protctus.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.viewmodel.RewardItem
import com.protctus.app.viewmodel.RewardsState
import com.protctus.app.viewmodel.RewardsViewModel
import kotlinx.coroutines.delay

@Composable
fun RewardsScreen(navController: NavController) {
    val rewardsViewModel: RewardsViewModel = hiltViewModel()
    val rewardsState by rewardsViewModel.rewardsState.collectAsState()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Scaffold(
        bottomBar = { ProtctusBottomNav(navController = navController) },
        containerColor = Color(0xFF1E1E21)
    ) { innerPadding ->
        when (val state = rewardsState) {
            is RewardsState.Loading -> {
                RewardsSkeleton(modifier = Modifier.padding(innerPadding))
            }
            is RewardsState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Could not load rewards.\n${state.message}",
                        color = Color(0xFF7F909F),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            is RewardsState.Success -> {
                val earnedBadges = state.earnedBadges
                val nextBadges = state.nextBadges
                val earnedCount = earnedBadges.size
                val totalBadges = earnedCount + nextBadges.size
                val upcomingCount = if (totalBadges > 0) totalBadges - earnedCount else 0

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFF1E1E21))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { -40 }, animationSpec = tween(500))
                    ) {
                        Text(
                            text = stringResource(R.string.rewards),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600, delayMillis = 150))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Earned Rewards Card (Clean, NO GLOW)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.earned_rewards),
                                        color = Color(0xFFA0A0AB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Box(
                                        modifier = Modifier.size(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val sweep = if (totalBadges > 0) (earnedCount.toFloat() / totalBadges) * 360f else 0f
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Simple background ring
                                            drawArc(
                                                color = Color(0xFF383840),
                                                startAngle = -90f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                            // Flat progress ring (NO GLOW)
                                            drawArc(
                                                color = Color.White,
                                                startAngle = -90f,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    Text(
                                        text = "$earnedCount/$totalBadges Badges",
                                        color = Color(0xFF6B6C75),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            
                            // Upcoming Rewards Card
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.upcoming_rewards), 
                                        color = Color(0xFFA0A0AB), 
                                        fontSize = 13.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Box(
                                        modifier = Modifier.size(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.5f),
                                                style = Stroke(width = 2.dp.toPx(), pathEffect = pathEffect)
                                            )
                                        }
                                        // A simple custom drawn grid representation
                                        Canvas(modifier = Modifier.size(24.dp)) {
                                            val dotRadius = 1.5.dp.toPx()
                                            val spacing = size.width / 3f
                                            for (i in 0 until 3) {
                                                for (j in 0 until 3) {
                                                    drawCircle(
                                                        color = Color.White,
                                                        radius = dotRadius,
                                                        center = Offset((i + 0.5f) * spacing, (j + 0.5f) * spacing)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = "$upcomingCount badges left", 
                                        color = Color(0xFF6B6C75), 
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600, delayMillis = 300)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(600, delayMillis = 300))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF23242A))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.your_badges),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                BadgeGrid(badges = earnedBadges, navController = navController)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600, delayMillis = 450)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(600, delayMillis = 450))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF23242A))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.next_badges),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                BadgeGrid(badges = nextBadges, navController = navController)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun BadgeGrid(badges: List<RewardItem>, navController: NavController) {
    val itemsPerRow = 3
    val rows = badges.chunked(itemsPerRow)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        rows.forEach { rowBadges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (i in 0 until itemsPerRow) {
                    if (i < rowBadges.size) {
                        BadgeItemView(badge = rowBadges[i], navController = navController, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeItemView(badge: RewardItem, navController: NavController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = badge.unlocked) {
                if (badge.unlocked) {
                    navController.navigate("rank_detail/${badge.rank}")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = badge.iconRes),
            contentDescription = badge.title,
            modifier = Modifier
                .size(72.dp)
                .alpha(if (badge.unlocked) 1f else 0.7f),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = badge.title,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp,
            modifier = Modifier.alpha(if (badge.unlocked) 1f else 0.7f)
        )
    }
}

@Composable
fun RewardsSkeleton(modifier: Modifier = Modifier) {
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
            .background(Color(0xFF1E1E21))
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2B31).copy(alpha = alpha))
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF26262B).copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF26262B).copy(alpha = alpha))
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF23242A).copy(alpha = alpha))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF23242A).copy(alpha = alpha))
        )
    }
}      