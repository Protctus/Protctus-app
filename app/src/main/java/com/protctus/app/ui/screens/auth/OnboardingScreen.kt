package com.protctus.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.protctus.app.R
import com.protctus.app.navigation.Route

@Composable
fun OnboardingScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { com.protctus.app.data.local.SettingsManager(context) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        isVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.protctus.app.ui.theme.DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800)) + slideInVertically(initialOffsetY = { -50 })
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_protctus_logo),
                    contentDescription = "Protctus Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(36.dp)
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 200)) + scaleIn(initialScale = 0.95f, animationSpec = tween(800, delayMillis = 200))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.get_started),
                    contentDescription = "Onboarding Illustration",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .heightIn(max = 260.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 400)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 400))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SECURE YOUR\nDIGITAL FUTURE",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 42.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Advance Protection with Protctus",
                        color = com.protctus.app.ui.theme.SecondaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800, delayMillis = 600)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 600))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(com.protctus.app.ui.theme.SurfaceColor)
                            .clickable {
                                coroutineScope.launch {
                                    settingsManager.setHasSeenWalkthrough(true)
                                }
                                navController.navigate(Route.SignUp) {
                                    popUpTo(Route.Onboarding) { inclusive = true }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Get Started",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "v1.0.0 | © PROTCTUS",
                        color = com.protctus.app.ui.theme.SecondaryText.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}