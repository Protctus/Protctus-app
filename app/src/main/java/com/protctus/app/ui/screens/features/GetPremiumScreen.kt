package com.protctus.app.ui.screens.features

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.components.ProtctusBottomNav
import com.protctus.app.ui.components.ProtctusPrimaryButton
import androidx.hilt.navigation.compose.hiltViewModel
import com.protctus.app.viewmodel.PremiumViewModel
import com.protctus.app.viewmodel.PremiumPlan
import com.protctus.app.viewmodel.PaymentState

@Composable
fun GetPremiumScreen(
    navController: NavController,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val plans by viewModel.plans.collectAsState()
    val paymentState by viewModel.paymentState.collectAsState()
    val paymentError by viewModel.paymentError.collectAsState()
    val isPremium by viewModel.isPremiumUser.collectAsState()
    var selectedPlan by remember { mutableStateOf<PremiumPlan?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    // ── Payment Success Dialog ──
    if (paymentState == PaymentState.SUCCESS) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetPaymentState()
                navController.popBackStack()
            },
            containerColor = com.protctus.app.ui.theme.SurfaceColor,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(56.dp)
                )
            },
            title = {
                Text(
                    text = "Welcome to Premium!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "All premium features are now unlocked. Your device is fully protected.",
                    color = Color(0xFF7F909F),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetPaymentState()
                    navController.popBackStack()
                }) {
                    Text("Continue", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Payment Error Dialog ──
    if (paymentState == PaymentState.FAILED && paymentError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.resetPaymentState() },
            containerColor = com.protctus.app.ui.theme.SurfaceColor,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Payment Failed",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = paymentError ?: "Something went wrong. Please try again.",
                    color = Color(0xFF7F909F),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resetPaymentState() }) {
                    Text("Try Again", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                }
            }
        )
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
                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { navController.popBackStack() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFFFFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── Already Premium State ──
                if (isPremium) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkspacePremium,
                            contentDescription = "Premium",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "You're Premium!",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All premium features are unlocked.\nThank you for supporting Protctus!",
                            color = Color(0xFF7F909F),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // ── Purchase Flow ──
                    Text(
                        text = stringResource(R.string.get_premium),
                        color = Color(0xFFFFFFFF),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Unlock all the power of this mobile tool and\nenjoy digital experience like never before!",
                        color = Color(0xFF7F909F),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Dynamic Plan Cards purely from Firebase Backend
                    if (plans.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF4A5568))
                        }
                    } else {
                        plans.forEach { plan ->
                            val isSelected = selectedPlan?.id == plan.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) com.protctus.app.ui.theme.SurfaceColor else Color.Transparent)
                                    .border(
                                        1.dp, 
                                        if (isSelected) Color.Transparent else Color(0xFF374151), 
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable { selectedPlan = plan }
                                    .padding(horizontal = 24.dp, vertical = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(plan.title, color = Color(0xFFFFFFFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (!plan.badgeText.isNullOrEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF4ADE80).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(plan.badgeText, color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(plan.subtitle, color = Color(0xFF7F909F), fontSize = 13.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // ── Checkout Button ──
                    val isLoading = paymentState == PaymentState.CREATING_ORDER || paymentState == PaymentState.VERIFYING
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        } else {
                            ProtctusPrimaryButton(
                                text = if (selectedPlan != null) "Proceed to Checkout" else "Select a Plan",
                                onClick = { 
                                    if (selectedPlan != null && activity != null) {
                                        viewModel.initiatePurchase(selectedPlan!!, activity)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF7F909F))) {
                            append("By placing this order, you agree to the ")
                        }
                        pushLink(androidx.compose.ui.text.LinkAnnotation.Clickable("TOS") {
                            navController.navigate(Route.TermsOfService)
                        })
                        withStyle(style = SpanStyle(color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)) {
                            append("Terms of Service")
                        }
                        pop()
                        withStyle(style = SpanStyle(color = Color(0xFF7F909F))) {
                            append(" and ")
                        }
                        pushLink(androidx.compose.ui.text.LinkAnnotation.Clickable("PRIVACY") {
                            navController.navigate(Route.PrivacyPolicy)
                        })
                        withStyle(style = SpanStyle(color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)) {
                            append("Privacy Policy")
                        }
                        pop()
                        withStyle(style = SpanStyle(color = Color(0xFF7F909F))) {
                            append(". Subscription automatically renews unless auto-renew is turned off at least 24-hours before the end of the current period.")
                        }
                    }
                    
                    Text(
                        text = annotatedString,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
