package com.protctus.app.ui.screens.notifications

import com.protctus.app.ui.theme.*

import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.viewmodel.ThreatFeedViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    val viewModel: ThreatFeedViewModel = hiltViewModel()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Threats", "Alerts")
    
    val allThreats by viewModel.threats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.notifications),
                        color = PrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                actions = {
                    Spacer(modifier = Modifier.width(48.dp)) // To center the title
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(52.dp)
                    .background(SurfaceColor, RoundedCornerShape(26.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val tabBgColor by animateColorAsState(
                            targetValue = if (isSelected) AccentColor.copy(alpha = 0.3f) else Color.Transparent,
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            label = "tabBgColor"
                        )
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isSelected) PrimaryText else SecondaryText,
                            animationSpec = tween(400),
                            label = "tabTextColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(26.dp))
                                .background(tabBgColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedTab = index }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = tabTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                    (slideIntoContainer(
                        towards = direction,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(400))) togetherWith
                    (slideOutOfContainer(
                        towards = direction,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(400)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "Tab Transition"
            ) { targetTab ->
                
                // Map the real Threats from DB to proper tabs based on their severity/type
                val displayNotifications = allThreats.filter { threat ->
                    when (targetTab) {
                        0 -> threat.priority.equals("High", ignoreCase = true) || threat.threatType.contains("Malware", ignoreCase = true) || threat.threatType.contains("Spyware", ignoreCase = true) // Threats tab
                        else -> !threat.priority.equals("High", ignoreCase = true) && !threat.threatType.contains("Malware", ignoreCase = true) // Alerts tab
                    }
                }

                val groupedNotifications = displayNotifications.groupBy {
                    val today = Calendar.getInstance()
                    val notificationDate = Calendar.getInstance().apply { timeInMillis = it.timestamp }

                    if (today.get(Calendar.YEAR) == notificationDate.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) == notificationDate.get(Calendar.DAY_OF_YEAR)
                    ) {
                        "TODAY"
                    } else if (today.get(Calendar.YEAR) == notificationDate.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) - notificationDate.get(Calendar.DAY_OF_YEAR) == 1
                    ) {
                        "YESTERDAY"
                    } else {
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        sdf.format(Date(it.timestamp))
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groupedNotifications.forEach { (dateHeader, items) ->
                        item {
                            Text(
                                text = dateHeader,
                                color = AccentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }

                        itemsIndexed(items, key = { _, item -> item.id }) { index, notification ->
                            NotificationItemRow(notification = notification, index = index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemRow(notification: ThreatAlert, index: Int) {
    val timeFormat = SimpleDateFormat("hh.mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(notification.timestamp))

    // Entrance Animation setup with more spring and stagger
    val scale = remember { Animatable(0.2f) }
    val alpha = remember { Animatable(0f) }
    val offsetX = remember { Animatable(180f) } // Extremely dramatic entrance

    LaunchedEffect(notification.id) {
        val delayTime = (index % 12) * 75L // Smoother, wider stagger waterfall
        delay(delayTime)
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f, // Toned down bounce
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing) // Extremely silky fade
            )
        }
        launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f, // Custom bouncy glide
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Colors and Icons mapping dynamically based on ThreatAlert properties
    val typeLower = notification.threatType.lowercase() 
    val priorityLower = notification.priority.lowercase()

    val (iconColor, iconVector) = when {
        typeLower.contains("malware") || typeLower.contains("spyware") || typeLower.contains("trojan") || priorityLower == "high" -> ErrorColor to Icons.Rounded.BugReport
        typeLower.contains("dark web") || typeLower.contains("breach") -> Color(0xFFFF8C00) to Icons.Rounded.Public
        typeLower.contains("scan") || typeLower.contains("deep") -> Color(0xFF00E676) to Icons.Rounded.Shield
        typeLower.contains("smart scan") -> AccentColor to Icons.Rounded.Search
        typeLower.contains("quest") || typeLower.contains("streak") -> Color(0xFFFFD700) to Icons.Rounded.EmojiEvents
        typeLower.contains("network") || typeLower.contains("usb") || typeLower.contains("alert") -> Color(0xFFFF9800) to Icons.Rounded.Warning
        else -> OutlineColor to Icons.Rounded.Info
    }
    
    // Continuous icon pulsing animation - heavier and smoother pulse
    val infiniteTransition = rememberInfiniteTransition(label = "icon Pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f, // Moderately expanded
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing), // Slower, deeper breath
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    // Inner icon slight rotation for extra flair
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f, // More noticeable sway
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconRotation"
    )

    // Icon background color with slight transparency
    val iconBgColor = iconColor.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .alpha(alpha.value) // Applies animated alpha
            .scale(scale.value) // Applies animated scale
            .offset(x = offsetX.value.dp), // Applies animated offset
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(iconScale)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = notification.threatType,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(rotationZ = iconRotation) // Applying rotation inside the icon
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${notification.title} ${notification.description}",
                color = PrimaryText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeString,
                color = SecondaryText,
                fontSize = 13.sp
            )
        }
    }
}

@Preview
@Composable
fun NotificationsScreenPreview() {
    // The Preview won't populate real items directly without Koin mock/LocalContext, 
    // but the frame architecture is preserved perfectly here.
    MaterialTheme {
        // NotificationsScreen(navController = rememberNavController())
    }
}