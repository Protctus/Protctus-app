package com.protctus.app.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.google.firebase.auth.FirebaseAuth
import com.protctus.app.navigation.Route
import kotlinx.coroutines.delay
import kotlin.math.cos 
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun SplashRouter(
    navController: NavController,
    authViewModel: com.protctus.app.viewmodel.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val isPreview = LocalInspectionMode.current
    val splashDest by authViewModel.splashDestination.collectAsState()

    val context = LocalContext.current
    val settingsManager = remember { com.protctus.app.data.local.SettingsManager(context) }
    val hasSeenWalkthrough by settingsManager.hasSeenWalkthrough.collectAsState(initial = false)

    var stage by remember { mutableStateOf(0) }
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            authViewModel.determineSplashDestination()
        }
        
        if (isPreview) {
            delay(500); stage = 1; delay(1500); stage = 2; delay(1000); stage = 3
            return@LaunchedEffect
        }
        delay(300)
        stage = 1
        
        delay(1500) // Circle Reveal and Smooth Logo Fade Duration
        stage = 2
        delay(500) // Transition Pause
        stage = 3 // Expand scale
        delay(800) // Smooth transition full scale duration
    }

    LaunchedEffect(splashDest, stage) {
        if (stage == 3 && !hasNavigated && splashDest != com.protctus.app.viewmodel.SplashDestination.Idle) {
            hasNavigated = true
            when (splashDest) {
                is com.protctus.app.viewmodel.SplashDestination.Username -> {
                    navController.navigate(Route.Username) { popUpTo(Route.Splash) { inclusive = true } }
                }
                is com.protctus.app.viewmodel.SplashDestination.BiometricUnlock -> {
                    navController.navigate(Route.BiometricUnlock) { popUpTo(Route.Splash) { inclusive = true } }
                }
                is com.protctus.app.viewmodel.SplashDestination.Dashboard -> {
                    navController.navigate(Route.Dashboard) { popUpTo(Route.Splash) { inclusive = true } }
                }
                is com.protctus.app.viewmodel.SplashDestination.Onboarding -> {
                    if (hasSeenWalkthrough) {
                        navController.navigate(Route.SignUp) { popUpTo(Route.Splash) { inclusive = true } }
                    } else {
                        navController.navigate(Route.Walkthrough) { popUpTo(Route.Splash) { inclusive = true } }
                    }
                }
                else -> { hasNavigated = false }
            }
        }
    }

    var typeProgress by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        if (!isPreview) {
            delay(300)
            androidx.compose.animation.core.animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            ) { value, _ ->
                typeProgress = value
            }
        } else {
            typeProgress = 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.protctus.app.ui.theme.DarkBackground)
    ) {
        val logoAlpha by animateFloatAsState(
            targetValue = if (stage in 1..2) 1f else 0f,
            animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "logoAlpha"
        )
        
        val textAlpha by animateFloatAsState(
            targetValue = if (stage in 1..2) 1f else 0f,
            animationSpec = tween(800), label = "text"
        )

        val floatAnim = rememberInfiniteTransition(label = "float")
        val floatY by floatAnim.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(3500, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "fy"
        )
        val rotOffset by floatAnim.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(4500, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "rot"
        )

        val brandPathData = "M537.2,155.3C536.7,155.4 536.5,156 536.7,156.6C536.9,157.1 537.1,157.1 542.7,157.2C549.1,157.3 550.4,157.7 552.7,160C557.5,164.8 556.3,172.8 550.3,175.7C548.2,176.8 547.6,176.8 542.4,176.8C537.2,176.8 536.9,176.9 536.7,177.5C536.2,178.7 537.4,178.9 544.1,178.8C549.3,178.7 551.4,178 553.8,175.8C560.8,169.3 558,158.2 548.8,155.6C547.8,155.3 538.3,155.1 537.2,155.3M579.1,155.3L578.3,155.4 578.2,156.1C578.1,157.1 578.3,157.2 583.1,157.2C585.3,157.2 587.7,157.2 588.6,157.3C599.5,158.7 600.4,173.8 589.8,176.6C589,176.8 587.8,176.8 584.8,176.9C582.6,176.9 580.2,176.9 579.5,177L578.3,177.1 578.3,177.8L578.3,178.6 579.4,178.7C580.1,178.7 581.5,178.8 582.5,178.8L584.5,178.8 585.7,180.6C586.4,181.6 588.2,184.2 589.7,186.4C591.3,188.6 593.2,191.2 593.9,192.3C597.5,197.5 597.4,197.3 598.2,196.8C598.6,196.6 598.8,196.4 598.8,196.3C598.8,196 597.6,194 596.8,192.8C596.4,192.3 595.1,190.6 594,188.9C592.8,187.3 591.3,185.1 590.6,184.1C586.3,178.1 586.7,178.9 588.2,178.7C601.6,177.4 603.3,159.2 590.4,155.6C589.4,155.3 580.9,155.1 579.1,155.3M665.2,155.3C664.9,155.5 664.8,156.8 665.1,157C665.7,157.4 696.3,157.3 696.8,157C697,156.8 697.1,155.9 696.8,155.5C696.6,155.2 665.6,155 665.2,155.3M725.6,155.4C707.5,157.2 700.2,178.9 713.4,191.5C721,198.7 733.9,198.7 741.6,191.6L742.7,190.5 742.4,190.1C741.9,188.9 741.8,188.9 739.3,190.9C725.4,201.7 705.9,189.8 709,172.5C710.5,164.3 717.9,157.8 726.3,157.4C731.8,157.1 734.8,158.2 740.6,162.3L741.8,163.2 742.1,162.7C742.9,161.9 742.6,161.4 739.8,159.3C735.8,156.4 730.5,154.9 725.6,155.4M751.1,155.3C750.8,155.5 750.7,156.5 751,156.9C751.3,157.2 770.9,157.4 778.9,157.2L782.8,157.1 782.8,156.3C782.8,155.8 782.8,155.5 782.6,155.4C782.2,155.1 751.6,155.1 751.1,155.3M824.4,155.3C823.2,155.4 823.3,154.5 823.2,170.3C823.1,186.3 823.1,185.3 821.8,187.9C817,196.9 804.1,197.4 798.6,188.8C796.5,185.6 796.5,185.6 796.5,169.4C796.5,154.2 796.6,155.4 795.2,155.5L794.4,155.6 794.4,167.8C794.3,183.8 794.4,184.8 796.2,188.6C802.3,200.8 820.4,199.3 824.5,186.2C825.3,183.9 825.3,183 825.3,169.7L825.3,157.2 840.1,157.2C860.9,157.2 860.8,157.2 866.6,161.5C867.3,162 867.3,162 867.7,161.8C870,160.2 863,156 857.2,155.3C855.7,155.2 826,155.1 824.4,155.3M531.5,155.5C531.3,155.6 531.3,158.1 531.3,176.1C531.3,199 531.1,197.2 532.5,196.9L533.1,196.8 533.2,194.3C533.4,191.7 533.3,155.7 533.2,155.6C533,155.4 531.7,155.3 531.5,155.5M573.1,155.6C572.8,155.8 572.5,188.7 572.7,194C572.9,196.8 573.6,197.8 574.6,196.7C575,196.2 575,156.1 574.5,155.7C574.1,155.3 573.5,155.2 573.1,155.6M631.2,155.5C618.2,157.6 610.3,170.8 614.7,183C621.7,202.7 649.6,201.1 654.7,180.8C658.2,166.9 645.4,153.2 631.2,155.5M637.2,157.5C649.5,159 656.9,173.4 651.2,184.6C646.3,194.3 633.7,198 624.7,192.4C616.9,187.6 613.7,179.1 616.4,170.4C618.3,164 624.3,158.7 630.7,157.7C631.4,157.6 632,157.4 632.2,157.4C632.8,157.3 635.9,157.4 637.2,157.5M680,161C679.8,161.4 679.7,195.4 679.9,196.3C680,196.9 680.6,197.1 681.4,196.8L682,196.5 682,178.8C681.9,161.5 681.9,161.1 681.6,160.9C681.1,160.5 680.2,160.6 680,161M766,160.9C765.7,161.2 765.5,189.4 765.7,194.1C765.9,197.1 766,197.2 767.2,196.8L767.8,196.7 767.8,178.9C767.8,162 767.8,161.2 767.5,160.9C767.1,160.6 766.3,160.6 766,160.9M842.6,163.1C842.2,163.6 842.1,165.4 842.4,166.8C843.1,169.9 845.1,172 848.9,173.8C850.4,174.5 851,174.7 855.8,176.4C864,179.3 866.7,181.6 867.3,185.8C867.9,189.6 866.1,192.1 861.6,193.9C856.3,196.1 849.5,195 844.2,191.2C841.9,189.6 841.5,189.5 840.9,190.4C840.3,191.3 845.6,194.9 849.2,196C859.5,199.1 869.5,194.7 869.5,186.8C869.5,181.3 864.9,177.1 855.4,174.2C847.5,171.7 844.5,169.2 844.2,164.9C844.1,163 843.4,162.2 842.6,163.1"
        val brandPathCompose = remember {
            androidx.core.graphics.PathParser.createPathFromPathData(brandPathData).asComposePath()
        }

        // Floating Logo & Geometric Tracing
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // 1. Draw drifting background abstract shapes
            if (stage < 3) {
                withTransform({ translate(w * 0.2f, h * 0.15f + floatY); rotate(-15f + rotOffset) }) {
                    val path = Path().apply { moveTo(0f, -30f); lineTo(30f, 25f); lineTo(-30f, 25f); close() }
                    drawPath(path, Color(0xFFFFD54F))
                }
                withTransform({ translate(w * 0.15f, h * 0.35f - floatY); rotate(10f - rotOffset) }) {
                    drawRect(Color(0xFFFF7043), topLeft = Offset(-20f, -20f), size = Size(40f, 40f))
                }
                withTransform({ translate(w * 0.25f, h * 0.48f + floatY * 0.5f); rotate(-45f + rotOffset) }) {
                    drawArc(Color(0xFF29B6F6), 0f, -90f, useCenter = true, topLeft = Offset(-25f, -25f), size = Size(50f, 50f))
                }
                withTransform({ translate(w * 0.18f, h * 0.62f - floatY); rotate(30f + rotOffset) }) {
                    drawArc(Color(0xFFAB47BC), 45f, 270f, useCenter = false, topLeft = Offset(-20f, -20f), size = Size(40f, 40f), style = Stroke(10f, cap = StrokeCap.Round))
                }
                withTransform({ translate(w * 0.22f, h * 0.82f + floatY); rotate(-20f - rotOffset) }) {
                    val path = Path().apply { moveTo(0f, -25f); lineTo(25f, 15f); lineTo(-15f, 15f); close() }
                    drawPath(path, Color(0xFF26C6DA))
                }
                withTransform({ translate(w * 0.78f, h * 0.12f - floatY); rotate(rotOffset) }) {
                    drawCircle(Color(0xFF546E7A), radius = 22f, style = Stroke(12f))
                }
                withTransform({ translate(w * 0.85f, h * 0.25f + floatY); rotate(30f + rotOffset) }) {
                    drawRect(Color(0xFF37474F), topLeft = Offset(-12f, -35f), size = Size(24f, 70f))
                }
                withTransform({ translate(w * 0.75f, h * 0.46f - floatY); rotate(15f - rotOffset) }) {
                    drawRect(Color(0xFFF06292), topLeft = Offset(-22f, -22f), size = Size(44f, 44f))
                }
                withTransform({ translate(w * 0.82f, h * 0.68f + floatY); rotate(-rotOffset) }) {
                    drawCircle(Color(0xFF26C6DA), radius = 18f, style = Stroke(10f))
                }
                withTransform({ translate(w * 0.75f, h * 0.85f - floatY); rotate(45f + rotOffset) }) {
                    drawArc(Color(0xFFEF5350), 0f, -90f, useCenter = true, topLeft = Offset(-25f, -25f), size = Size(50f, 50f))
                }
            }
                // Animated drawing of SVG
                if (logoAlpha > 0f) {
                    val logoWidth = 340f
                    val logoHeight = 50f
                    
                    withTransform({
                        // Center and accommodate floating y-shift
                        val jumpY = if (stage == 3) -floatY * 10f else 0f
                        translate(cx - logoWidth / 2f, cy - logoHeight / 2f + jumpY)
                        
                        // Sweep reveal the logo mimicking a 'typing' progression
                        clipRect(
                            left = 0f,
                            top = 0f,
                            right = logoWidth * typeProgress,
                            bottom = logoHeight
                        )
                        
                        // Align raw coordinates
                        translate(-531f, -155f)
                    }) {
                        drawPath(
                            path = brandPathCompose,
                            color = Color.White.copy(alpha = logoAlpha),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }
                }
        } // End of Canvas

        // Restored "MADE IN भारत" text with Indian flag
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .graphicsLayer { alpha = textAlpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.made_in),
                color = com.protctus.app.ui.theme.AccentColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            val indiaGradient = Brush.horizontalGradient(
                colors = listOf(Color(0xFFFF9933), Color(0xFFFFFFFF), Color(0xFF138808))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.str_843618),
                    style = TextStyle(
                        brush = indiaGradient,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    modifier = Modifier.graphicsLayer { shadowElevation = 8f }
                )
                Spacer(modifier = Modifier.width(16.dp))
                // RealisticWavingIndianFlag(modifier = Modifier.width(46.dp).height(30.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SplashRouterPreview() {
    val viewModelStoreOwner = remember {
        object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore = androidx.lifecycle.ViewModelStore()
        }
    }
    CompositionLocalProvider(
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides viewModelStoreOwner
    ) {
        SplashRouter(
            navController = androidx.navigation.compose.rememberNavController()
        )
    }
}