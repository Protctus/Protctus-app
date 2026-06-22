package com.protctus.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.protctus.app.ui.theme.ProtctusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun ContactUsScreen(navController: NavController) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("FAQ", "Contact Us")

    ProtctusTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(com.protctus.app.ui.theme.DarkBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.help_center),
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(26.dp)) // Spacer to match back button visually
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF38383A))
                    )
                },
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color.White,
                            height = 2.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) Color.White else Color(0xFF8E8E93)
                            )
                        },
                        interactionSource = remember { MutableInteractionSource() }
                    )
                }
            }

            var showTicketDialog by remember { mutableStateOf(false) }
            var showCheckTicketDialog by remember { mutableStateOf(false) }

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "Tab Transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> FAQTabContent(onShowTicketDialog = { showTicketDialog = true })
                    1 -> ContactUsTabContent(
                        onReportClick = { showTicketDialog = true },
                        onCheckStatusClick = { showCheckTicketDialog = true }
                    )
                }
            }

            if (showTicketDialog) {
                SupportTicketDialog(onDismiss = { showTicketDialog = false })
            }
            if (showCheckTicketDialog) {
                CheckTicketStatusDialog(onDismiss = { showCheckTicketDialog = false })
            }
        }
    }
}

@Composable
fun FAQTabContent(onShowTicketDialog: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedChipIndex by remember { mutableIntStateOf(0) }
    val chips = listOf("All", "Scans", "Privacy", "Account")

    val faqItems = listOf(
        FAQData(
            "How does Protctus protect my device?",
            "We use advanced heuristic scanning and real-time monitoring to detect anomalies, malware, and unauthorized access attempts instantly.",
            "All"
        ),
        FAQData(
            "What is a Smart Scan vs Deep Scan?",
            "Smart Scan quickly checks running processes and common vulnerable areas. Deep Scan thoroughly analyzes every file on your device for hidden threats.",
            "Scans"
        ),
        FAQData(
            "How is my data kept private?",
            "All security analysis happens directly on your device. We do not upload your personal files or browsing history to our servers.",
            "Privacy"
        ),
        FAQData(
            "What do I do if I find a vulnerability?",
            "Switch to the 'Contact Us' tab and use 'Report a Problem' feature to notify our security team directly.",
            "Account"
        )
    )

    val baseFiltered = if (selectedChipIndex == 0) faqItems else faqItems.filter { it.category == chips[selectedChipIndex] }
    val filteredItems = if (searchQuery.isBlank()) {
        baseFiltered
    } else {
        baseFiltered.filter {
            it.question.contains(searchQuery, ignoreCase = true) || 
            it.answer.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedEntranceItem(index = 0) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(chips) { index, chip ->
                    val isSelected = selectedChipIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF3A3A3C) else com.protctus.app.ui.theme.SurfaceColor)
                            .clickable { selectedChipIndex = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            color = if (isSelected) Color.White else Color(0xFFD1D1D6),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        AnimatedEntranceItem(index = 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_for_help),
                                    color = Color(0xFF8E8E93),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
        }

        AnimatedContent(
            targetState = filteredItems.isEmpty(),
            label = "empty_state",
            modifier = Modifier.weight(1f)
        ) { isEmpty ->
            if (isEmpty) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "No help found for '$searchQuery'",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Can't find what you're looking for?\nSubmit a ticket to our support team.",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onShowTicketDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.submit_an_issue_ticket), color = Color.White)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(filteredItems, key = { _, item -> item.question }) { index, item ->
                        AnimatedEntranceItem(index = index + 2) {
                            ExpandableFAQCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactUsTabContent(onReportClick: () -> Unit, onCheckStatusClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AnimatedEntranceItem(index = 0) {
            TicketStatusBanner(onClick = onCheckStatusClick)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedEntranceItem(index = 1) {
            ExpandableContactCard(
                icon = {
                    Icon(Icons.Rounded.HeadsetMic, null, tint = Color.White, modifier = Modifier.size(22.dp))
                },
                title = "Contact Support",
                isInitiallyExpanded = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.ChatBubbleOutline, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "If you face issues, reach our support team directly."
                    )
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.Email, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "support@protctus.com"
                    )
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.Phone, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "Toll-Free: 1-800-PROTCTUS"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactDetailRow(
                            icon = { Icon(Icons.Rounded.Schedule, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                            text = "Available: Mon-Sat, 9 AM - 7 PM"
                        )
                        Icon(Icons.Rounded.Schedule, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedEntranceItem(index = 2) {
            ExpandableContactCard(
                icon = {
                    Icon(Icons.Rounded.PlayCircleOutline, null, tint = Color.White, modifier = Modifier.size(22.dp))
                },
                title = "Threat Remediation Guides"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.PlayCircleOutline, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "How to isolate compromised applications."
                    )
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.PlayCircleOutline, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "Configuring real-time protection protocols."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedEntranceItem(index = 3) {
            ExpandableContactCard(
                icon = {
                    Icon(Icons.Rounded.BugReport, null, tint = Color.White, modifier = Modifier.size(22.dp))
                },
                title = "Report a Threat / Vulnerability"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ContactDetailRow(
                        icon = { Icon(Icons.Rounded.BugReport, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "Report critical zero-day vulnerabilities or false positives directly to our malware analysis team."
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = onReportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.report_now), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedEntranceItem(index = 4) {
            ExpandableContactCard(
                icon = {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = Color.White, modifier = Modifier.size(22.dp))
                },
                title = "Security & Privacy Forums"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ContactDetailRow(
                        icon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp)) },
                        text = "Join discussions on emerging threats, privacy protocols, and community-driven mitigation strategies."
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableFAQCard(item: FAQData) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.question,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.answer,
                color = Color(0xFFD1D1D6),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ExpandableContactCard(
    icon: @Composable () -> Unit,
    title: String,
    isInitiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(isInitiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.protctus.app.ui.theme.SurfaceColor)
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }

        if (expanded) {
            content()
        }
    }
}

@Composable
fun ContactDetailRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFFD1D1D6),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

data class FAQData(
    val question: String,
    val answer: String,
    val category: String
)

@Composable
fun TicketStatusBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFF2B5876), Color(0xFF4E4376))
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.check_ticket_status),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.track_your_support_requests_and_read_adm),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ConfirmationNumber,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedEntranceItem(index: Int, content: @Composable () -> Unit) {
    // Heavy, incredibly bouncy entrance states
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    val offsetX = remember { Animatable(180f) }
    val rotation = remember { Animatable(15f) } 

    LaunchedEffect(Unit) {
        val delayTime = (index * 120).toLong() // Slower stagger
        delay(delayTime)
        
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.45f, // More bouncy
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessVeryLow
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .alpha(alpha.value)
            .scale(scale.value)
            .offset(x = offsetX.value.dp)
            .graphicsLayer(rotationZ = rotation.value)
    ) {
        content()
    }
}

@Composable
fun SupportTicketDialog(onDismiss: () -> Unit) {
    var ticketText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var successTicketId by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    // Robust Firestore Init
    val firestore = remember {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus")
        } catch (e: Exception) {
            // Fallback to default database if named instance "protctus" fails
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isSubmitting, dismissOnClickOutside = !isSubmitting)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            AnimatedContent(
                targetState = successTicketId != null,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                },
                label = "dialog_state"
            ) { isSuccess ->
                if (isSuccess) {
                    // Success View
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF00C853).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.ticket_generated), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.your_ticket_id_is), color = Color(0xFF8E8E93), fontSize = 14.sp)
                        Text(successTicketId!!, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.done_1), color = Color.White)
                        }
                    }
                } else {
                    // Input View
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.submit_a_ticket), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = ticketText,
                            onValueChange = { ticketText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text(stringResource(R.string.describe_your_issue_in_detail), color = Color(0xFF8E8E93)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color(0xFF38383A),
                                cursorColor = Color.White
                            ),
                            enabled = !isSubmitting
                        )
                        
                        if (errorMsg != null) {
                            Text(errorMsg!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text = stringResource(R.string.cancel),
                                color = Color(0xFF8E8E93),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable(enabled = !isSubmitting) { onDismiss() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            Button(
                                onClick = {
                                    if (ticketText.isBlank()) {
                                        errorMsg = "Please describe your issue."
                                        return@Button
                                    }
                                    isSubmitting = true
                                    errorMsg = null
                                    
                                    val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                                    val newTicketId = "PRT-${UUID.randomUUID().toString().take(6).uppercase()}"
                                    val timestamp = System.currentTimeMillis()
                                    val docData = hashMapOf(
                                        "ticketId" to newTicketId,
                                        "issue" to ticketText,
                                        "user_email" to userEmail,
                                        "timestamp" to timestamp,
                                        "status" to "open"
                                    )

                                    // Sync to D1 Worker Only (No Firestore)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val json = org.json.JSONObject().apply {
                                                put("ticket_id", newTicketId)
                                                put("issue", ticketText)
                                                put("user_email", userEmail)
                                                put("timestamp", timestamp)
                                            }.toString()

                                            val request = okhttp3.Request.Builder()
                                                .url("https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev/api/tickets")
                                                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                                                .build()

                                            val response = com.protctus.app.network.SecureHttpClient.default.newCall(request).execute()
                                            
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isSubmitting = false
                                                if (response.isSuccessful) {
                                                    successTicketId = newTicketId
                                                    Toast.makeText(context, "Ticket submitted successfully!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    errorMsg = "Submission failed (Server error)"
                                                    Toast.makeText(context, "Submission failed!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SupportTicket", "Worker sync failed: ${e.message}")
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isSubmitting = false
                                                errorMsg = "Submission failed: ${e.message}"
                                            }
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.submit), color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckTicketStatusDialog(onDismiss: () -> Unit) {
    var ticketId by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resultData by remember { mutableStateOf<Map<String, Any>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Robust Firestore Init
    val firestore = remember {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus")
        } catch (e: Exception) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            AnimatedContent(
                targetState = resultData != null,
                label = "status_view"
            ) { hasResult ->
                if (hasResult) {
                    val data = resultData!!
                    val status = (data["status"] as? String) ?: "Open"
                    val issue = data["issue"] as? String ?: "Unknown issue"
                    val adminReply = data["admin_reply"] as? String
                    
                    val statusColor = when(status.lowercase()) {
                        "resolved" -> Color(0xFF00C853)
                        "in_progress" -> Color(0xFFFFC107)
                        else -> Color(0xFF3B82F6)
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ConfirmationNumber, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ticket_details),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(stringResource(R.string.status), color = Color(0xFF8E8E93), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(status.uppercase().replace("_", " "), color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.your_issue), color = Color(0xFF8E8E93), fontSize = 12.sp)
                        Text(issue, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        
                        if (!adminReply.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2A2A2C))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Info, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.admin_reply), color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(adminReply, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.close), color = Color.White)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.check_ticket_status), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.enter_your_ticket_id_below_to_track_it), color = Color(0xFF8E8E93), fontSize = 14.sp, textAlign = TextAlign.Center)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = ticketId,
                            onValueChange = { ticketId = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.e_g_prt_a1b2c3), color = Color(0xFF8E8E93)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF38383A),
                                cursorColor = Color.White
                            ),
                            singleLine = true,
                            enabled = !isChecking
                        )
                        
                        if (errorMsg != null) {
                            Text(errorMsg!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text = stringResource(R.string.cancel),
                                color = Color(0xFF8E8E93),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable(enabled = !isChecking) { onDismiss() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            Button(
                                onClick = {
                                    if (ticketId.isBlank()) {
                                        errorMsg = "Please enter a valid Ticket ID."
                                        return@Button
                                    }
                                    isChecking = true
                                    errorMsg = null
                                    
                                    // Fetch Status from D1 Worker
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val request = okhttp3.Request.Builder()
                                                .url("https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev/api/tickets/$ticketId")
                                                .get()
                                                .build()

                                            val response = com.protctus.app.network.SecureHttpClient.default.newCall(request).execute()
                                            val body = response.body?.string()

                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isChecking = false
                                                if (response.isSuccessful && body != null) {
                                                    val json = org.json.JSONObject(body)
                                                    val map = mutableMapOf<String, Any>()
                                                    val keys = json.keys()
                                                    while (keys.hasNext()) {
                                                        val key = keys.next()
                                                        map[key] = json.get(key)
                                                    }
                                                    resultData = map
                                                } else {
                                                    errorMsg = "Ticket not found or server error."
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SupportTicket", "Status check failed: ${e.message}")
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isChecking = false
                                                errorMsg = "Error: ${e.message}"
                                            }
                                        }
                                    }
                                },
                                enabled = !isChecking,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.check_status), color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1C)
@Composable
fun ContactUsScreenPreview() {
    ContactUsScreen(navController = NavController(LocalContext.current))
}