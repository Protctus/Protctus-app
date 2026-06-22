package com.protctus.app.ui.screens.features

import android.os.Parcelable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.protctus.app.data.local.entity.MicrolearningCardData
import com.protctus.app.ui.theme.*
import com.protctus.app.viewmodel.CardLockState
import com.protctus.app.viewmodel.UiCardItem
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class QuizInteractionState : Parcelable {
    object Idle : QuizInteractionState()
    data class Evaluated(val selectedAnswer: String, val isCorrect: Boolean) : QuizInteractionState()
}

@Composable
fun MicrolearningCard(
    uiItem: UiCardItem,
    progress: Float = 0f,
    onAnswerSubmit: (Boolean) -> Unit
) {
    val card = uiItem.card
    var interactionState by rememberSaveable(card.id) { mutableStateOf<QuizInteractionState>(QuizInteractionState.Idle) }
    var showOptions by rememberSaveable(card.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(card.id) {
        if (!showOptions) {
            kotlinx.coroutines.delay(300)
            showOptions = true
        }
    }

    // Full Screen Locked Layout
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(DarkBackground, RoundedCornerShape(32.dp))
                .border(1.dp, SurfaceColor, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Progress Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SuccessColor,
                    trackColor = SurfaceColor
                )
                if (uiItem.lockState == CardLockState.COMPLETED) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", tint = SuccessColor, modifier = Modifier.size(20.dp))
                }
            }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. SCROLLABLE CONTENT AREA (Weight 1f ensures it expands and allows scrolling if content is long)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.title,
                color = PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (card.visualUrl.trim().isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(card.visualUrl.trim())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceColor),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = card.description,
                color = AccentColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        // 2. ANCHORED ACTION CONTAINER (Fixed Height to prevent UI jumps)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp), // Locked height for options + action buttons
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = showOptions,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (card.type) {
                            "INFO" -> {
                                ProfessionalQuizButton(
                                    text = "I UNDERSTAND (+5 XP)",
                                    option = "GOT IT",
                                    correctAnswer = "GOT IT",
                                    interactionState = interactionState,
                                    onClick = {
                                        interactionState = QuizInteractionState.Evaluated("GOT IT", true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAnswerSubmit(true)
                                    }
                                )
                            }
                            "QUIZ_BOOL" -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Use API-provided options; fall back to SAFE/SCAM if empty
                                    val options = card.options.ifEmpty { listOf("SAFE", "SCAM") }
                                    options.forEach { opt ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            ProfessionalQuizButton(
                                                text = opt,
                                                option = opt,
                                                correctAnswer = card.correctAnswer,
                                                interactionState = interactionState,
                                                onClick = {
                                                    val isCorrect = card.correctAnswer.trim().equals(opt.trim(), ignoreCase = true)
                                                    interactionState = QuizInteractionState.Evaluated(opt, isCorrect)
                                                    if (isCorrect) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onAnswerSubmit(isCorrect)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            "QUIZ_MCQ" -> {
                                card.options.take(4).forEach { option ->
                                    ProfessionalQuizButton(
                                        text = option,
                                        option = option,
                                        correctAnswer = card.correctAnswer,
                                        interactionState = interactionState,
                                        onClick = {
                                            val isCorrect = card.correctAnswer.trim().equals(option.trim(), ignoreCase = true)
                                            interactionState = QuizInteractionState.Evaluated(option, isCorrect)
                                            if (isCorrect) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAnswerSubmit(isCorrect)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Status/Retry Area (Always takes space to prevent jumping)
                Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                    if (interactionState is QuizInteractionState.Evaluated) {
                        val state = interactionState as QuizInteractionState.Evaluated
                        if (!state.isCorrect) {
                            Button(
                                onClick = { interactionState = QuizInteractionState.Idle },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(16.dp),
                                border = border(1.dp, ErrorColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TRY AGAIN", color = ErrorColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("EXCELLENT! +5 XP", color = SuccessColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            }
        }
        
        // Overlay for LOCKED states
        if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE || uiItem.lockState == CardLockState.LOCKED_QUOTA) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(DarkBackground.copy(alpha = 0.85f))
                    .clickable {
                        val msg = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE) {
                            "Complete previous modules to unlock."
                        } else {
                            "Daily limit of 10 reached. Come back tomorrow!"
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE) Icons.Rounded.Lock else Icons.Rounded.Timer,
                        contentDescription = "Locked",
                        tint = SecondaryText,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE) "Locked" else "Daily Limit Reached",
                        color = PrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProfessionalQuizButton(
    text: String,
    option: String,
    correctAnswer: String,
    interactionState: QuizInteractionState,
    onClick: () -> Unit
) {
    val isEvaluated = interactionState is QuizInteractionState.Evaluated
    val isSelected = isEvaluated && (interactionState as QuizInteractionState.Evaluated).selectedAnswer == option
    
    // Strict evaluation ignoring case
    val isCorrectOption = correctAnswer.trim().equals(option.trim(), ignoreCase = true)
    
    // Scoped strictly to the selected option
    val isCorrect = isEvaluated && isSelected && isCorrectOption
    val isIncorrect = isEvaluated && isSelected && !isCorrectOption
    val isAnswerHighlight = false // No unselected highlighting

    val borderColor by animateColorAsState(
        targetValue = when {
            isCorrect -> SuccessColor
            isIncorrect -> ErrorColor
            isAnswerHighlight -> SuccessColor.copy(alpha = 0.5f)
            else -> OutlineColor
        }, label = "border"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isCorrect -> SuccessColor.copy(alpha = 0.1f)
            isIncorrect -> ErrorColor.copy(alpha = 0.1f)
            else -> SurfaceColor
        }, label = "bg"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isEvaluated,
                onClick = onClick
            ),
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = if (isEvaluated && !isSelected && !isAnswerHighlight) SecondaryText else PrimaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun border(width: androidx.compose.ui.unit.Dp, color: Color, shape: androidx.compose.ui.graphics.Shape) = 
    androidx.compose.foundation.BorderStroke(width, color)
