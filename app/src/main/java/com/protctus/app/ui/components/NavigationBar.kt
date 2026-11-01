package com.protctus.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.protctus.app.navigation.Route

// Helper data class for Navigation Items
data class BottomNavItem(
    val title: String,
    val route: Any, // Type-safe Route objects are any
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun ProtctusBottomNav(navController: NavController) {
    // Current route observation to handle active states dynamically
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Define your bottom nav routes
    val items = listOf(
        BottomNavItem("Home", Route.Dashboard, Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("Search", Route.Search, Icons.AutoMirrored.Filled.ManageSearch, Icons.AutoMirrored.Outlined.ManageSearch),
        BottomNavItem("Features", Route.FeaturesShortcut, Icons.Filled.GridView, Icons.Outlined.GridView),
        BottomNavItem("Alerts", Route.Notifications, Icons.Filled.NotificationsNone, Icons.Outlined.NotificationsNone),
        BottomNavItem("Settings", Route.Settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    // Show bottom bar ONLY on these specific screens
    val navBarScreens = listOf("Dashboard", "Rewards", "Search", "ThreatFeed", "DailyQuests", "ThreatMap", "FeaturesShortcut", "LabChapters")
    val showBottomBar = currentRoute != null && navBarScreens.any { currentRoute.contains(it) }

    if (showBottomBar) {
        
        // --- Floating Pill Container ---
        // Isey external padding di hai taaki corners rounded dikhein aur bar flow kare.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp) // External floating padding
                .clip(RoundedCornerShape(32.dp)) // Pill Shape for the Bar (Fix for Sharp Corners!)
                .background(Color(0xFF161618)) // Solid dark background color retained
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp), // Internal padding for content
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val routeName = item.route::class.simpleName ?: ""
                    val isSelected = currentRoute?.contains(routeName) == true

                    // Smooth Background Color Transition for individual pill
                    val animatedBackgroundColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF2D3748) else Color.Transparent,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "pill_color"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp)) // Smaller pill for the individual item
                            .background(animatedBackgroundColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    navController.navigate(item.route) {
                                        popUpTo(Route.Dashboard) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            .padding(horizontal = if (isSelected) 16.dp else 12.dp, vertical = 10.dp)
                            // Smooth Layout Expansion animation for individual item
                            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )

                        // Delayed Fade for Text (Awwwards effect)
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(tween(durationMillis = 200, delayMillis = 100)),
                            exit = fadeOut(tween(durationMillis = 100))
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false // Prevents weird text wrapping jumping
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}