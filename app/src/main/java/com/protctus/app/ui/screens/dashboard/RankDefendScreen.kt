package com.protctus.app.ui.screens.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.viewmodel.HomeViewModel
import com.protctus.app.viewmodel.RewardsState
import com.protctus.app.viewmodel.RewardsViewModel
import androidx.compose.foundation.verticalScroll

@Composable
fun RankDefendScreen(navController: NavController? = null, targetRankId: Int = -1) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val rewardsViewModel: RewardsViewModel = hiltViewModel()
    
    val username by homeViewModel.username.collectAsState()
    val rewardsState by rewardsViewModel.rewardsState.collectAsState()
    val context = LocalContext.current

    // Guard: only show rank detail for unlocked badges
    val rankData = run {
        var baseRank: RankData? = null
        if (rewardsState is RewardsState.Success) {
            val state = rewardsState as RewardsState.Success

            if (targetRankId > 0) {
                // Find the target badge and verify it's unlocked
                val matchedBadge = state.earnedBadges.find { it.rank == targetRankId }
                if (matchedBadge != null) {
                    val nextBadge = rewardsViewModel.allBadges.find { it.rank == targetRankId + 1 }
                    baseRank = RankData(
                        levelNumber = matchedBadge.rank,
                        rankTitle = matchedBadge.title,
                        rankDescription = matchedBadge.rankDescription,
                        themeColor = getThemeColorForRank(matchedBadge.rank),
                        currentXp = state.currentXp,
                        maxXp = nextBadge?.xpRequired ?: matchedBadge.xpRequired,
                        deviceIntegrity = state.currentRank.deviceIntegrity,
                        accountArmor = state.currentRank.accountArmor,
                        networkShield = state.currentRank.networkShield,
                        nextMissionText = state.currentRank.nextMissionText,
                        nextXpReward = state.currentRank.nextXpReward,
                        iconRes = matchedBadge.iconRes,
                        isClaimed = state.currentRank.isClaimed
                    )
                }
            }

            // Fallback to their current rank
            if (baseRank == null) {
                baseRank = state.currentRank
            }
        }

        baseRank
    }

    // Show "Locked" state if badge isn't unlocked or still loading
    if (rankData == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.protctus.app.ui.theme.DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF2A2A2E), Color(0xFF141416))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = "Locked",
                        tint = com.protctus.app.ui.theme.SecondaryText,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.reward_locked),
                    color = com.protctus.app.ui.theme.PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.earn_more_xp_to_unlock_this_reward),
                    color = Color(0xFF7F909F),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (navController != null) {
                    Text(
                        text = stringResource(R.string.go_back),
                        color = com.protctus.app.ui.theme.AccentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { navController.popBackStack() }
                    )
                }
            }
        }
        return
    }

    // ── Main Content ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.protctus.app.ui.theme.DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Back button & Leaderboard ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navController != null) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = com.protctus.app.ui.theme.PrimaryText
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (navController != null) {
                IconButton(onClick = { navController.navigate(com.protctus.app.navigation.Route.Leaderboard) }) {
                    Icon(
                        imageVector = Icons.Outlined.Stars,
                        contentDescription = "Leaderboard",
                        tint = Color(0xFFFACC15)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Header with real username ──
        Text(
            text = "Congrats, $username",
            color = com.protctus.app.ui.theme.PrimaryText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You earned ${rankData.rankTitle}",
            color = com.protctus.app.ui.theme.SecondaryText,
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Shield Arc ──
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            val rawFraction = rankData.currentXp.toFloat() / rankData.maxXp.toFloat()
            val targetFraction = rawFraction.coerceIn(0f, 1f)

            val animatedProgress by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(
                    durationMillis = 2000,
                    easing = FastOutSlowInEasing,
                    delayMillis = 300
                ),
                label = "rankArcSweep"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 5.dp.toPx()

                drawArc(
                    color = com.protctus.app.ui.theme.SurfaceColor,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                drawArc(
                    color = rankData.themeColor,
                    startAngle = 140f,
                    sweepAngle = 260f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val animatedXp by animateIntAsState(
                    targetValue = rankData.currentXp,
                    animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    label = "xpRoll"
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    rankData.themeColor.copy(alpha = 0.15f),
                                    com.protctus.app.ui.theme.SurfaceColor
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (rankData.iconRes != 0) {
                        Image(
                            painter = painterResource(id = rankData.iconRes),
                            contentDescription = "Badge Icon",
                            modifier = Modifier.size(50.dp)
                        )
                    } else {
                        Text(
                            text = "$animatedXp",
                            color = rankData.themeColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$animatedXp / ${rankData.maxXp} XP",
                    color = com.protctus.app.ui.theme.SecondaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = rankData.rankTitle,
                    color = com.protctus.app.ui.theme.PrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Rank Info ──
        Text(
            text = rankData.rankTitle,
            color = com.protctus.app.ui.theme.PrimaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = rankData.rankDescription,
            color = com.protctus.app.ui.theme.SecondaryText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Security Stat Cards (Real data from SecurityScanner) ──

        SecurityStatCard(
            icon = Icons.Rounded.VerifiedUser,
            title = "Device Integrity",
            subtitle = getIntegritySubtitle(rankData.deviceIntegrity),
            percentage = rankData.deviceIntegrity,
            themeColor = rankData.themeColor,
            accentColor = getStatAccentColor(rankData.deviceIntegrity)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecurityStatCardCompact(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Security,
                title = "Account\nArmor",
                percentage = rankData.accountArmor,
                themeColor = rankData.themeColor,
                accentColor = getStatAccentColor(rankData.accountArmor)
            )
            SecurityStatCardCompact(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Wifi,
                title = "Network\nShield",
                percentage = rankData.networkShield,
                themeColor = rankData.themeColor,
                accentColor = getStatAccentColor(rankData.networkShield)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Active Quest / Mission Card ──
        val questIcon = getQuestIcon(rankData.nextMissionText)

        var isVerifying by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            com.protctus.app.ui.theme.SurfaceColor,
                            rankData.themeColor.copy(alpha = 0.08f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
                .clickable(enabled = !rankData.isClaimed && !isVerifying) {
                    isVerifying = true
                    rewardsViewModel.verifyGlobalNextMission(context) { success, message ->
                        isVerifying = false
                        android.widget.Toast
                            .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                            .show()
                    }
                }
                .padding(20.dp)
        ) {
            if (isVerifying) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = rankData.themeColor,
                        strokeWidth = 2.dp
                    )
                }
            } else if (rankData.isClaimed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.all_missions_complete),
                        color = Color(0xFF10B981),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(rankData.themeColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = questIcon,
                            contentDescription = null,
                            tint = rankData.themeColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.active_quest),
                            color = com.protctus.app.ui.theme.PrimaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = rankData.nextMissionText,
                            color = com.protctus.app.ui.theme.SecondaryText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = "Verify",
                        tint = rankData.themeColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Security Stat Card (Full Width) ──
@Composable
private fun SecurityStatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    percentage: Float,
    themeColor: Color,
    accentColor: Color
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing, delayMillis = 500),
        label = "statAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
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
                    Text(text = title, color = com.protctus.app.ui.theme.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${(animatedPercentage * 100).toInt()}%",
                        color = accentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, color = com.protctus.app.ui.theme.SecondaryText, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(com.protctus.app.ui.theme.SurfaceColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPercentage)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.6f), accentColor)
                                )
                            )
                    )
                }
            }
        }
    }
}

// ── Security Stat Card (Compact — for the 2-column row) ──
@Composable
private fun SecurityStatCardCompact(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    percentage: Float,
    themeColor: Color,
    accentColor: Color
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing, delayMillis = 700),
        label = "compactStatAnim"
    )

    Box(
        modifier = modifier
            .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${(animatedPercentage * 100).toInt()}%",
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                color = com.protctus.app.ui.theme.PrimaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(com.protctus.app.ui.theme.SurfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPercentage)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.6f), accentColor)
                            )
                        )
                )
            }
        }
    }
}

// ── Helpers ──

private fun getThemeColorForRank(rank: Int): Color {
    return when (rank) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF59E0B)
        3 -> Color(0xFF10B981)
        4 -> Color(0xFF3B82F6)
        5 -> com.protctus.app.ui.theme.AccentColor
        6 -> Color(0xFFEC4899)
        7 -> Color(0xFFD946EF)
        8 -> Color(0xFFEAB308)
        9 -> Color(0xFF06B6D4)
        else -> com.protctus.app.ui.theme.SecondaryText
    }
}

private fun getStatAccentColor(score: Float): Color {
    return when {
        score >= 0.8f -> Color(0xFF10B981) // Green — Excellent
        score >= 0.5f -> Color(0xFFF59E0B) // Amber — Moderate
        else -> Color(0xFFEF4444)           // Red — Critical
    }
}

private fun getIntegritySubtitle(score: Float): String {
    return when {
        score >= 0.9f -> "Excellent — No threats detected"
        score >= 0.7f -> "Warning — Minor issues found"
        score >= 0.5f -> "At Risk — Action recommended"
        else -> "Critical — Immediate review needed"
    }
}

private fun getQuestIcon(missionText: String): ImageVector {
    val lower = missionText.lowercase()
    return when {
        lower.contains("scan") -> Icons.Outlined.DocumentScanner
        lower.contains("share") || lower.contains("referral") -> Icons.Outlined.Share
        lower.contains("quiz") || lower.contains("learn") -> Icons.Outlined.School
        lower.contains("password") || lower.contains("lock") -> Icons.Outlined.HealthAndSafety
        lower.contains("wifi") || lower.contains("network") -> Icons.Outlined.Wifi
        lower.contains("update") -> Icons.Outlined.SystemUpdate
        lower.contains("phishing") || lower.contains("shield") -> Icons.Outlined.Security
        lower.contains("patrol") || lower.contains("check") -> Icons.Outlined.VerifiedUser
        lower.contains("threat") || lower.contains("feed") -> Icons.Outlined.DynamicFeed
        lower.contains("dark") || lower.contains("web") -> Icons.Outlined.DarkMode
        else -> Icons.Outlined.RocketLaunch
    }
}

// Legacy StatBarRow kept for backwards compatibility if used elsewhere
@Composable
fun StatBarRow(title: String, targetPercentage: Float, color: Color, themeColor: Color, alignEnd: Boolean = false) {
    val animatedPercentage by animateFloatAsState(
        targetValue = targetPercentage.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing,
            delayMillis = 500
        ),
        label = "statBarSweep"
    )

    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = color, fontSize = 12.sp)
            Text(text = "${(animatedPercentage * 100).toInt()}%", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedPercentage)
                    .height(4.dp)
                    .background(themeColor, RoundedCornerShape(2.dp))
            )
        }
    }
}