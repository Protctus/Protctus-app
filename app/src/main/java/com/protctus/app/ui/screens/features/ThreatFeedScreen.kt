package com.protctus.app.ui.screens.features

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.R
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.theme.DarkBackground
import com.protctus.app.ui.theme.OutlineColor
import com.protctus.app.ui.theme.PrimaryText
import com.protctus.app.ui.theme.SecondaryText
import com.protctus.app.ui.theme.SurfaceColor
import com.protctus.app.viewmodel.ThreatFeedViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ThreatFeedScreen(navController: NavController) {
    val viewModel: ThreatFeedViewModel = hiltViewModel()
    val threats by viewModel.threats.collectAsState()
    
    // Launch Animation triggers
    var isLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isLaunched = true
    }

    Scaffold(
        bottomBar = { ProtctusBottomNav(navController = navController) },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground),
            contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                AnimatedVisibility(
                    visible = isLaunched,
                    enter = slideInVertically(initialOffsetY = { -50 }) + fadeIn(tween(500))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(R.string.threat_feed),
                            color = PrimaryText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Monitoring and mitigating active risks on your device.",
                            color = SecondaryText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = isLaunched,
                    enter = fadeIn(tween(500, delayMillis = 200))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recent_blocked_attempts),
                            color = PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (threats.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearAllThreats() },
                                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.clear_all), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            if (threats.isEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = isLaunched,
                        enter = fadeIn(tween(800))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceColor)
                                .border(1.dp, OutlineColor, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF22C55E).copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.no_threats_logged), 
                                    color = PrimaryText, 
                                    fontSize = 18.sp, 
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.your_device_is_completely_clean), 
                                    color = SecondaryText,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(threats, key = { _, threat -> threat.id }) { index, threat ->
                    AnimatedVisibility(
                        visible = isLaunched,
                        enter = slideInHorizontally(initialOffsetX = { 200 }) + fadeIn(tween(400, delayMillis = index * 100)),
                        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                    ) {
                        ThreatFeedItem(
                            threat = threat, 
                            onDelete = { viewModel.deleteThreat(threat.id) },
                            navController = navController
                        )
                    }
                }
            }

            if (threats.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    AnimatedVisibility(
                        visible = isLaunched,
                        enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(tween(500, delayMillis = 300))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceColor)
                                .border(1.dp, OutlineColor, RoundedCornerShape(24.dp))
                                .padding(vertical = 20.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${threats.size} total threats securely isolated.",
                                color = SecondaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatFeedItem(threat: ThreatAlert, onDelete: () -> Unit, navController: NavController) {
    val date = Date(threat.timestamp)
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateString = formatter.format(date)
    
    // Safely parse the hex color string from DB
    val colorPrimary = try {
        Color(android.graphics.Color.parseColor("#${threat.priorityColorHex.removePrefix("#")}"))
    } catch(e: Exception) {
        Color(0xFFEF4444) // Fallback Red
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = Color(0xFFEF4444)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.copy(alpha = 0.2f))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) {
        val isStalkerware = threat.threatType.startsWith("Stalkerware:")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceColor)
                .border(1.dp, OutlineColor, RoundedCornerShape(24.dp))
                .then(
                    if (isStalkerware) {
                        Modifier.clickable {
                            val packageName = threat.threatType.substringAfter("Stalkerware:")
                            navController.navigate(com.protctus.app.navigation.Route.ThreatResolution(packageName, threat.title, threat.description))
                        }
                    } else Modifier
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colorPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Alert",
                        tint = colorPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = threat.title, 
                            color = PrimaryText, 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = dateString, 
                            color = SecondaryText, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = threat.description,
                        color = SecondaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .background(colorPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = threat.priority.uppercase(), 
                            color = colorPrimary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
