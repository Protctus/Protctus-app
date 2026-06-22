package com.protctus.app.ui.screens.leaderboard

import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.protctus.app.ui.theme.ProtctusTheme
import com.protctus.app.viewmodel.LeaderboardViewModel
import com.protctus.app.viewmodel.LeaderboardState
import com.protctus.app.viewmodel.LeaderboardUser

@Composable
fun LeaderboardScreen(
    navController: NavController,
) {
    val viewModel: LeaderboardViewModel = hiltViewModel()
    val state by viewModel.leaderboardState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.protctus.app.ui.theme.DarkBackground)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.leaderboard),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        when (state) {
            is LeaderboardState.Loading -> {
                LeaderboardSkeleton()
            }
            is LeaderboardState.Error -> {
                val errorMsg = (state as LeaderboardState.Error).message
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Transmission Failed:\n$errorMsg",
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is LeaderboardState.Success -> {
                val users = (state as LeaderboardState.Success).topUsers
                val currentUser = users.find { it.isCurrentUser }
                val currentUserRank = users.indexOf(currentUser).let { if (it >= 0) it + 1 else 0 }
                
                // ── Your Stats Card ──
                if (currentUser != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF2A2B31), Color(0xFF1E1F24))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank badge
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF86A055).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$currentUserRank",
                                    color = Color(0xFF86A055),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.your_rank),
                                    color = Color(0xFF7F909F),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = currentUser.username,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lvl ${currentUser.level} • ${currentUser.rankTitle}",
                                    color = Color(0xFF86A055),
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${currentUser.xp}",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.total_xp),
                                    color = Color(0xFF7F909F),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // ── Podium ──
                if (users.size >= 3) {
                    val top3 = users.take(3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        PodiumItem(user = top3[1], rank = 2, size = 76, modifier = Modifier.padding(bottom = 16.dp, end = 12.dp))
                        PodiumItem(user = top3[0], rank = 1, size = 100, isFirst = true, modifier = Modifier.padding(bottom = 32.dp))
                        PodiumItem(user = top3[2], rank = 3, size = 76, modifier = Modifier.padding(bottom = 16.dp, start = 12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    val restOfUsers = if (users.size > 3) users.drop(3) else emptyList()
                    
                    itemsIndexed(restOfUsers) { index, user ->
                        LeaderboardRow(user = user, rank = index + 4)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PodiumItem(user: LeaderboardUser, rank: Int, size: Int, isFirst: Boolean = false, modifier: Modifier = Modifier) {
    val currentBorderColor = if (user.isCurrentUser) Color(0xFF86A055) else if (isFirst) Color(0xFF6B86A9) else Color(0xFF637487)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(100.dp)
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.wrapContentSize()
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .size(size.dp)
                        .border(
                            width = if (isFirst || user.isCurrentUser) 4.dp else 3.dp,
                            color = currentBorderColor,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(com.protctus.app.ui.theme.SurfaceColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size((size / 2).dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(y = 12.dp)
                        .size(26.dp)
                        .background(if (isFirst) Color(0xFFC0C0C0) else Color(0xFF637487), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isFirst) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF6B86A9),
                    modifier = Modifier
                        .size(32.dp)
                        .offset(y = (-20).dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = user.username.split(" ").firstOrNull() ?: "User",
            color = Color.White,
            fontSize = if (isFirst) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFF86A055),
                modifier = Modifier.size(10.dp).padding(end = 4.dp)
            )
            Text(
                text = "${user.xp} XP",
                color = Color(0xFF86A055),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LeaderboardRow(user: LeaderboardUser, rank: Int) {
    val borderColor = if (user.isCurrentUser) Color(0xFF86A055) else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF32333A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Lvl ${user.level} • ${user.rankTitle}",
                color = Color(0xFF7F909F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        
        Text(
            text = "${user.xp} XP",
            color = Color(0xFFA5A5AB),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LeaderboardSkeleton() {
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

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2B31).copy(alpha = alpha))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.width(76.dp).height(120.dp).padding(bottom = 16.dp, end = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2B31).copy(alpha = alpha)))
            Box(modifier = Modifier.width(100.dp).height(160.dp).padding(bottom = 32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2B31).copy(alpha = alpha)))
            Box(modifier = Modifier.width(76.dp).height(120.dp).padding(bottom = 16.dp, start = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2B31).copy(alpha = alpha)))
        }

        Spacer(modifier = Modifier.height(32.dp))

        for(i in 0..4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(com.protctus.app.ui.theme.SurfaceColor.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLeaderboard() {
    ProtctusTheme {
        LeaderboardScreen(navController = rememberNavController())
    }
}