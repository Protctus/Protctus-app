package com.protctus.app.ui.screens.features

import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.data.local.RecentSearch
import com.protctus.app.data.repository.SearchResultItemData
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.viewmodel.SearchViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(navController: NavController) {
    val viewModel: SearchViewModel = hiltViewModel()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    SearchScreenContent(
        navController = navController,
        searchQuery = searchQuery,
        searchResults = searchResults,
        recentSearches = recentSearches,
        isLoading = isLoading,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchTriggered = viewModel::onSearchTriggered,
        onClearRecentSearch = viewModel::clearRecentSearch,
        onClearAllRecentSearches = viewModel::clearAllRecentSearches
    )
}

@Composable
fun SearchScreenContent(
    navController: NavController,
    searchQuery: String,
    searchResults: List<SearchResultItemData>,
    recentSearches: List<RecentSearch>,
    isLoading: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onClearRecentSearch: (Int) -> Unit,
    onClearAllRecentSearches: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Control animations
    var isLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isLaunched = true
    }

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
            item {
                Spacer(modifier = Modifier.height(32.dp))

                AnimatedVisibility(
                    visible = isLaunched,
                    enter = slideInVertically(initialOffsetY = { -50 }) + fadeIn(tween(500))
                ) {
                    Text(
                        text = stringResource(R.string.search),
                        color = Color(0xFFFFFFFF),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Search Bar with Animations
                AnimatedVisibility(
                    visible = isLaunched,
                    enter = scaleIn(initialScale = 0.8f, animationSpec = tween(500, delayMillis = 100)) + fadeIn()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearchTriggered(searchQuery)
                                focusManager.clearFocus()
                            }
                        ),
                        placeholder = {
                            Text(
                                stringResource(R.string.search_for_files_malware_logic),
                                color = Color(0xFF7F909F),
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            AnimatedContent(
                                targetState = searchQuery.isNotEmpty(),
                                transitionSpec = {
                                    scaleIn(tween(200)) togetherWith scaleOut(tween(200))
                                }, label = "SearchIconAnim"
                            ) { isNotEmpty ->
                                if (isNotEmpty) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Image(
                                        painter = painterResource(id = com.protctus.app.R.drawable.search),
                                        contentDescription = "Search",
                                        modifier = Modifier.size(20.dp),
                                        alpha = 0.5f
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color(0xFF4A5568),
                            unfocusedContainerColor = com.protctus.app.ui.theme.SurfaceColor,
                            focusedContainerColor = com.protctus.app.ui.theme.SurfaceColor,
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            cursorColor = Color(0xFF4A5568)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- SEARCH RESULTS STATE ---
            if (searchQuery.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        com.protctus.app.ui.components.ListSkeleton(itemCount = 4)
                    }
                }

                if (!isLoading && searchResults.isEmpty()) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = com.protctus.app.R.drawable.search),
                                    contentDescription = "Empty Search",
                                    modifier = Modifier.size(64.dp),
                                    alpha = 0.3f
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.no_results_found),
                                    color = Color(0xFFFFFFFF),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.try_adjusting_your_search_query),
                                    color = Color(0xFF7F909F),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(searchResults.size) { index ->
                        AnimatedVisibility(
                            visible = !isLoading,
                            enter = slideInVertically(initialOffsetY = { 50 * (index + 1) }) + fadeIn(tween(400))
                        ) {
                            val result = searchResults[index]
                            SearchResultItem(result) {
                                focusManager.clearFocus()
                                onSearchTriggered(searchQuery)
                                navController.navigate(result.route)
                            }
                        }
                    }
                }
            } 
            // --- IDLE STATE (Quick Actions, Recent Searches) ---
            else {
                item {
                    AnimatedVisibility(
                        visible = recentSearches.isNotEmpty(),
                        enter = fadeIn(tween(600)),
                        exit = fadeOut(tween(300))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.recent_searches),
                                    color = Color(0xFFFFFFFF),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.clear_all),
                                    color = Color(0xFF7F909F),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable { onClearAllRecentSearches() }
                                        .padding(4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recentSearches, key = { it.id }) { search ->
                                    SearchChip(
                                        text = search.query,
                                        onClick = { onSearchTriggered(search.query) },
                                        onDelete = { onClearRecentSearch(search.id) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isLaunched,
                        enter = slideInHorizontally(initialOffsetX = { -100 }) + fadeIn(tween(600, delayMillis = 200))
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.quick_actions),
                                color = Color(0xFFFFFFFF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ActionCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Scan For Threat",
                                    icon = Icons.Outlined.Troubleshoot,
                                    onClick = { navController.navigate(Route.SmartScan) }
                                )
                                ActionCard(
                                    modifier = Modifier.weight(1f),
                                    title = "App Settings",
                                    icon = Icons.Outlined.AdminPanelSettings,
                                    onClick = { navController.navigate(Route.Settings) }
                                )
                            }
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isLaunched,
                        enter = slideInHorizontally(initialOffsetX = { 100 }) + fadeIn(tween(600, delayMillis = 300))
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.search_categories),
                                color = Color(0xFFFFFFFF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CategoryCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Threat Logs",
                                    icon = Icons.Outlined.Policy,
                                    onClick = { navController.navigate(Route.ThreatFeed) }
                                )
                                CategoryCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Dashboard Area",
                                    icon = Icons.Outlined.SpaceDashboard,
                                    onClick = { navController.navigate(Route.Dashboard) }
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(item: SearchResultItemData, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    color = Color(0xFF7F909F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SearchChip(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                color = Color(0xFFFFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF7F909F),
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
fun ActionCard(modifier: Modifier = Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    // Hover / pressed animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, tween(150), label = "ActionCardScale")

    Box(
        modifier = modifier
            .scale(scale)
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF374151)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFE3E3E3),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title, 
                color = Color(0xFFFFFFFF), 
                fontSize = 14.sp, 
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CategoryCard(modifier: Modifier = Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, tween(150), label = "CategoryCardScale")

    Box(
        modifier = modifier
            .scale(scale)
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, 
                tint = Color(0xFFE3E3E3),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title, 
                color = Color(0xFFFFFFFF), 
                fontSize = 15.sp, 
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1F)
@Composable
fun SeachScreenPreview() {
    SearchScreenContent(
        navController = NavController(LocalContext.current),
        searchQuery = "",
        searchResults = emptyList(),
        recentSearches = listOf(
            RecentSearch(1, "Malware", System.currentTimeMillis()),
            RecentSearch(2, "Network Logic", System.currentTimeMillis())
        ),
        isLoading = false,
        onSearchQueryChanged = {},
        onSearchTriggered = {},
        onClearRecentSearch = {},
        onClearAllRecentSearches = {}
    )
}
