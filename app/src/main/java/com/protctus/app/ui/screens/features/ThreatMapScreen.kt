package com.protctus.app.ui.screens.features

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.R
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.theme.*
import com.protctus.app.viewmodel.ThreatMapUiState
import com.protctus.app.viewmodel.ThreatMapViewModel

@Composable
fun ThreatMapScreen(
    navController: NavController,
    viewModel: ThreatMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var isFullScreen by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "RefreshRotation"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        com.protctus.app.utils.QuestTracker.markTaskCompleted(context, "threat_map")
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                CyberMapWebView(modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = { isFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                        .background(Color(0x88000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }

    Scaffold(
        bottomBar = { ProtctusBottomNav(navController = navController) },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ─── Header ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.global_threat_map),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 48.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Native Cybermap Canvas ───
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A)) // Dark space
                ) {
                    if (!isFullScreen) {
                        CyberMapWebView(modifier = Modifier.fillMaxSize())
                    } else {
                        // Placeholder while fullscreen
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                    IconButton(
                        onClick = { isFullScreen = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color(0x88000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }

            // ─── Summary Header ───
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.live_threat_summary),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.refreshMetrics() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentColor,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ─── State Handling ───
            when (val state = uiState) {
                is ThreatMapUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentColor)
                        }
                    }
                }
                is ThreatMapUiState.Error -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = ErrorColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is ThreatMapUiState.Success -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiveStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Active Attacks",
                                value = state.metrics.activeAttacks.toString(),
                                valueColor = ErrorColor
                            )
                            LiveStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Monitored Nodes",
                                value = state.metrics.monitoredNodes.toString(),
                                valueColor = SuccessColor
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiveStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Top Target",
                                value = state.metrics.topTarget,
                                valueColor = Color.White
                            )
                            LiveStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Top Method",
                                value = state.metrics.topType,
                                valueColor = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LiveStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceColor)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = label,
                color = AccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CyberMapWebView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Allow transparency so our app's dark background shows through if needed
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                }
                
                // Crucial for WebGL/Three.js maps
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                
                // Load the built Vite app from Android assets
                loadUrl("file:///android_asset/cybermap/index.html")
            }
        }
    )
}
