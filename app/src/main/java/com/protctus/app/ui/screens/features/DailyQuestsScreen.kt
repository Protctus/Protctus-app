package com.protctus.app.ui.screens.features
import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import com.protctus.app.navigation.Route
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.viewmodel.DailyQuest
import com.protctus.app.viewmodel.DailyQuestsViewModel
import com.protctus.app.viewmodel.QuestsState
import com.protctus.app.viewmodel.XpGainEvent
import kotlinx.coroutines.delay

@Composable
fun DailyQuestsScreen(navController: NavController, viewModel: DailyQuestsViewModel = hiltViewModel()) {
    var isListVisible by remember { mutableStateOf(false) }
    val questsState by viewModel.questsState.collectAsState()

    // XP animation overlay state
    var xpPopup by remember { mutableStateOf<XpGainEvent?>(null) }
    var showXpPopup by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isListVisible = true
                viewModel.fetchQuests()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Listen for XP gain events
    LaunchedEffect(Unit) {
        viewModel.xpGainEvent.collect { event ->
            xpPopup = event
            showXpPopup = true
            delay(2500)
            showXpPopup = false
            delay(500)
            xpPopup = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { ProtctusBottomNav(navController = navController) },
            containerColor = com.protctus.app.ui.theme.DarkBackground
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(com.protctus.app.ui.theme.DarkBackground)
                    .padding(horizontal = 24.dp)
            ) {
                when (questsState) {
                    is QuestsState.Loading -> {
                        item {
                            DailyQuestsSkeleton()
                        }
                    }

                    is QuestsState.Error -> {
                        item {
                            val errorMessage = (questsState as QuestsState.Error).message
                            Text(
                                text = "Error loading quests: $errorMessage",
                                color = Color.Red,
                                modifier = Modifier.fillMaxWidth().padding(40.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is QuestsState.Success -> {
                        val allQuests = (questsState as QuestsState.Success).quests
                        val completedQuests = allQuests.filter { it.isCompleted }
                        val upcomingQuests = allQuests.filter { !it.isCompleted }

                        val totalCompletedXp = completedQuests.sumOf { it.points }
                        val goalXp = allQuests.sumOf { it.points }.coerceAtLeast(1)
                        val progressFraction = (totalCompletedXp.toFloat() / goalXp.toFloat()).coerceIn(0f, 1f)

                        item {
                            Spacer(modifier = Modifier.height(32.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.daily_quests),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.weight(1f).padding(end = 48.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$totalCompletedXp / $goalXp XP Earned Today",
                                        color = Color(0xFF7F909F),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(com.protctus.app.ui.theme.SurfaceColor)
                                    ) {
                                        val animatedFraction by animateFloatAsState(
                                            targetValue = progressFraction,
                                            animationSpec = tween(1500, easing = FastOutSlowInEasing),
                                            label = "questProgress"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedFraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(24.dp))
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(com.protctus.app.ui.theme.SurfaceColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = com.protctus.app.R.drawable.star),
                                        contentDescription = "Star",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            // Display our distinct 5 daily tasks directly
                        }

                        // Upcoming (claimable) quests
                        items(upcomingQuests.size) { index ->
                            val quest = upcomingQuests[index]
                            AnimatedVisibility(
                                visible = isListVisible,
                                enter = slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(500, delayMillis = index * 100)
                                ) + fadeIn(tween(500, delayMillis = index * 100))
                            ) {
                                val context = LocalContext.current
                                ClaimableQuestItem(
                                    quest = quest,
                                    onStart = { q ->
                                        com.protctus.app.utils.GlobalQuestManager.setActiveQuest(q)
                                        val route = com.protctus.app.utils.QuestActionRegistry.getRouteForTrigger(q.actionTrigger)
                                        navController.navigate(route)
                                    },
                                    onClaim = { q ->
                                        viewModel.completeQuest(q) { _, _ -> }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Completed quests section
                        if (completedQuests.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Completed (${completedQuests.size})",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            items(completedQuests.size) { index ->
                                val quest = completedQuests[index]
                                AnimatedVisibility(
                                    visible = isListVisible,
                                    enter = fadeIn(tween(500, delayMillis = (upcomingQuests.size + index) * 100))
                                ) {
                                    CompletedQuestItem(quest = quest)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }

        // ── XP Gain Animation Overlay ──
        AnimatedVisibility(
            visible = showXpPopup,
            enter = slideInVertically(initialOffsetY = { -100 }) + fadeIn(tween(400)),
            exit = slideOutVertically(targetOffsetY = { -100 }) + fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) {
            xpPopup?.let { event ->
                val popupScale by animateFloatAsState(
                    targetValue = if (showXpPopup) 1f else 0.8f,
                    animationSpec = tween(300),
                    label = "xpPopupScale"
                )
                Box(
                    modifier = Modifier
                        .scale(popupScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.protctus.app.ui.theme.SurfaceColor)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+${event.xpAmount} XP",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = event.questTitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClaimableQuestItem(quest: DailyQuest, onStart: (DailyQuest) -> Unit, onClaim: (DailyQuest) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isReady = com.protctus.app.utils.QuestTracker.isActionCompleted(context, quest.actionTrigger) || quest.isReadyToClaim

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(enabled = !quest.isClaiming && !quest.isCompleted) { 
                if (isReady) onClaim(quest) else onStart(quest)
            }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quest.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = quest.subtitle,
                    color = Color(0xFF7F909F),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (quest.isClaiming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFACC15),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isReady) "CLAIM" else "START",
                        color = Color(0xFFFACC15),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFACC15).copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "+${quest.points} XP",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CompletedQuestItem(quest: DailyQuest) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor.copy(alpha = 0.5f))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quest.title,
                    color = Color(0xFF7F909F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = quest.subtitle,
                    color = Color(0xFF4A4B50),
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.done),
                    color = Color(0xFF4ADE80),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${quest.points} XP",
                    color = Color(0xFF4ADE80).copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DailyQuestsSkeleton(modifier: Modifier = Modifier) {
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

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(40.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2B31).copy(alpha = alpha))
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2B31).copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2B31).copy(alpha = alpha))
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2B31).copy(alpha = alpha))
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(com.protctus.app.ui.theme.SurfaceColor.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

