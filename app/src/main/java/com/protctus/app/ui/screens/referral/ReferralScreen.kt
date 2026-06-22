package com.protctus.app.ui.screens.referral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.protctus.app.viewmodel.ReferralViewModel

@Composable
fun ReferralScreen(
    navController: NavController,
    referralViewModel: ReferralViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val stats by referralViewModel.stats.collectAsState()

    var applyCode by remember { mutableStateOf("") }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1919))
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(stringResource(R.string.refer_earn), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hero banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2B31), RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    tint = Color(0xFFE3E3E3),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.invite_friends_earn_xp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Both you and your friend earn +10 XP\nwhen they join using your code!",
                    color = Color(0xFF7F909F), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = "Referrals", value = "${stats.totalReferrals}", icon = Icons.Outlined.Group, modifier = Modifier.weight(1f))
            StatCard(title = "XP Earned", value = "+${stats.xpEarned}", icon = Icons.Outlined.StarBorder, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Your referral code
        Text(stringResource(R.string.your_referral_code), color = Color(0xFF7F909F), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2B31), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stats.referralCode.ifEmpty { "Loading..." },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(stats.referralCode))
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = Color(0xFF7F909F))
                }
                IconButton(onClick = {
                    val intent = referralViewModel.getShareIntent(context)
                    context.startActivity(android.content.Intent.createChooser(intent, "Share Referral Code"))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color(0xFF7F909F))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Apply a referral code
        Text(stringResource(R.string.have_a_referral_code), color = Color(0xFF7F909F), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = applyCode,
            onValueChange = { applyCode = it.uppercase().trim() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.enter_code_e_g_ab3xkyz1), color = Color(0xFF4A5568)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF4A5568),
                unfocusedBorderColor = Color(0xFF2A2B31),
                cursorColor = Color.White,
                focusedContainerColor = Color(0xFF2A2B31),
                unfocusedContainerColor = Color(0xFF2A2B31)
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (applyCode.length == 8) {
                    isApplying = true
                    referralViewModel.applyReferralCode(applyCode) { success, msg ->
                        isApplying = false
                        applyMessage = msg
                        if (success) applyCode = ""
                    }
                }
            })
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                isApplying = true
                referralViewModel.applyReferralCode(applyCode) { success, msg ->
                    isApplying = false
                    applyMessage = msg
                    if (success) applyCode = ""
                }
            },
            enabled = applyCode.length == 8 && !isApplying,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A5568),
                disabledContainerColor = Color(0xFF2A2B31)
            )
        ) {
            if (isApplying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.apply_code), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(visible = applyMessage != null, enter = fadeIn(), exit = fadeOut()) {
            applyMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                val isSuccess = it.contains("applied") || it.contains("earned")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(it, color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF2A2B31), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFE3E3E3), modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Color(0xFF7F909F), fontSize = 11.sp)
    }
}
