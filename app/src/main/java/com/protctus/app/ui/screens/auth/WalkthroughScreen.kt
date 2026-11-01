package com.protctus.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.R
import com.protctus.app.navigation.Route
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

data class OnboardingPage(
    val imageRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val accentColor: Color
)

val onboardingPages = listOf(
    OnboardingPage(
        imageRes = R.drawable.illustration_gamification,
        titleRes = R.string.onboarding_title_1,
        descriptionRes = R.string.onboarding_desc_1,
        accentColor = com.protctus.app.ui.theme.AccentColor
    ),
    OnboardingPage(
        imageRes = R.drawable.illustration_security_on,
        titleRes = R.string.onboarding_title_2,
        descriptionRes = R.string.onboarding_desc_2,
        accentColor = com.protctus.app.ui.theme.AccentColor
    ),
    OnboardingPage(
        imageRes = R.drawable.illustration_privacy_first,
        titleRes = R.string.onboarding_title_3,
        descriptionRes = R.string.onboarding_desc_3,
        accentColor = com.protctus.app.ui.theme.AccentColor
    ),
    OnboardingPage(
        imageRes = R.drawable.illustration_phishing,
        titleRes = R.string.onboarding_title_4,
        descriptionRes = R.string.onboarding_desc_4,
        accentColor = com.protctus.app.ui.theme.AccentColor
    )
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WalkthroughScreen(navController: NavController) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            navController.navigate(Route.BiometricUnlock) {
                popUpTo(Route.Walkthrough) { inclusive = true }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    var showLanguageSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { com.protctus.app.data.local.SettingsManager(context) }
    val appLanguage by settingsManager.appLanguage.collectAsState(initial = "en")

    if (showLanguageSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = com.protctus.app.ui.theme.DarkBackground
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.app_language), 
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge, 
                    color = com.protctus.app.ui.theme.PrimaryText,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp, top = 8.dp)
                )
                
                val languages = listOf(
                    "en" to "English",
                    "hi" to "Hindi (हिंदी)",
                    "kn" to "Kannada (ಕನ್ನಡ)",
                    "bn" to "Bengali (বাংলা)",
                    "ta" to "Tamil (தமிழ்)",
                    "te" to "Telugu (తెలుగు)",
                    "or" to "Odia (ଓଡ଼ିଆ)"
                )
                
                languages.forEach { (code, name) ->
                    Text(
                        text = name,
                        color = if (appLanguage == code) com.protctus.app.ui.theme.PrimaryText else com.protctus.app.ui.theme.SecondaryText,
                        fontWeight = if (appLanguage == code) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    settingsManager.setAppLanguage(code)
                                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                        androidx.core.os.LocaleListCompat.forLanguageTags(code)
                                    )
                                }
                                showLanguageSheet = false
                            }
                            .padding(vertical = 16.dp, horizontal = 24.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(color = com.protctus.app.ui.theme.OutlineColor)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "heroPulse")
    val heroScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroScalePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.protctus.app.ui.theme.DarkBackground)
    ) {
        // Clean Vertical Layout without recursive nested weights causing issues
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Top: Skip Button Action
            Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.ic_translate_premium),
                    contentDescription = "Translate",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(4.dp)
                        .size(26.dp)
                        .clickable { showLanguageSheet = true }
                )

                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.skip),
                    color = com.protctus.app.ui.theme.SecondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { navController.navigate(Route.Onboarding) { popUpTo(Route.Walkthrough) { inclusive = true } } }
                        .padding(4.dp)
                )
            }

            // Middle: Clean Pager scaling vertically naturally
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 16.dp
            ) { page ->
                val pageData = onboardingPages[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absoluteOffset = kotlin.math.abs(pageOffset)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - (absoluteOffset * 0.5f)
                            translationX = pageOffset * 100f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = pageData.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .scale(if (page == pagerState.currentPage) heroScale else 1f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = androidx.compose.ui.res.stringResource(pageData.titleRes),
                        color = com.protctus.app.ui.theme.PrimaryText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        modifier = Modifier.graphicsLayer { translationX = pageOffset * 200f }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = androidx.compose.ui.res.stringResource(pageData.descriptionRes),
                        color = com.protctus.app.ui.theme.SecondaryText,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .graphicsLayer { translationX = pageOffset * 150f }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom UI Stack: Custom Animated Dots & Next Button
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(onboardingPages.size) { iteration ->
                    val isSelected = pagerState.currentPage == iteration
                    val color by animateColorAsState(targetValue = if (isSelected) onboardingPages[iteration].accentColor else com.protctus.app.ui.theme.OutlineColor, label = "dotColor")
                    val size by animateDpAsState(targetValue = if (isSelected) 10.dp else 6.dp, label = "dotSize")

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(size)
                            .clip(CircleShape)
                            .background(color)
                            .align(Alignment.CenterVertically)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isLastPage = pagerState.currentPage == onboardingPages.size - 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(com.protctus.app.ui.theme.AccentColor)
                    .clickable {
                        if (isLastPage) {
                            navController.navigate(Route.Onboarding) { popUpTo(Route.Walkthrough) { inclusive = true } }
                        } else {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isLastPage) androidx.compose.ui.res.stringResource(R.string.next) else androidx.compose.ui.res.stringResource(R.string.next),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
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
    }
}
