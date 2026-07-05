package com.protctus.app.ui.screens.features

import android.os.Parcelable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*
import com.protctus.app.viewmodel.CardLockState
import com.protctus.app.viewmodel.UiCardItem
import kotlinx.parcelize.Parcelize

/**
 * Protctus Quiz Card — Premium Immersive Quiz Experience
 *
 * DESIGN:
 * - The Sentinel mascot reacts to your answers (thinking → happy/sad)
 * - 3D depth buttons that feel physical and pressable
 * - Question is ALWAYS visible — NEVER in a scroll container
 * - Options are ALWAYS visible — anchored, never scroll away
 * - Dramatic correct/wrong feedback with scale + shake animations
 *
 * STRICT: All colors from EduColors → Color.kt. No Color(). No hex. No glow.
 */

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
    var interactionState by rememberSaveable(card.id) {
        mutableStateOf<QuizInteractionState>(QuizInteractionState.Idle)
    }
    val haptics = LocalHapticFeedback.current

    // Stagger options entrance
    var showOptions by rememberSaveable(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id) {
        if (!showOptions) {
            kotlinx.coroutines.delay(350)
            showOptions = true
        }
    }

    // Mascot state
    val mascotState = when (interactionState) {
        is QuizInteractionState.Evaluated -> {
            if ((interactionState as QuizInteractionState.Evaluated).isCorrect)
                MascotState.HAPPY else MascotState.SAD
        }
        else -> when (card.type) {
            "QUIZ_MCQ", "QUIZ_BOOL" -> MascotState.THINKING
            else -> MascotState.IDLE
        }
    }

    // Wrong answer shake
    val isWrong = interactionState is QuizInteractionState.Evaluated &&
            !(interactionState as QuizInteractionState.Evaluated).isCorrect

    // Correct answer celebration scale
    val isCorrect = interactionState is QuizInteractionState.Evaluated &&
            (interactionState as QuizInteractionState.Evaluated).isCorrect
    val celebrationScale by animateFloatAsState(
        targetValue = if (isCorrect) 1f else 1f,
        animationSpec = if (isCorrect) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ) else tween(0),
        label = "celebration"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main Quiz Layout ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = EduDimens.SpacingLg, vertical = EduDimens.SpacingSm)
                .background(EduColors.SurfaceLight, RoundedCornerShape(EduShapes.RadiusXl))
                .clip(RoundedCornerShape(EduShapes.RadiusXl))
                .padding(
                    start = EduDimens.CardPadding,
                    end = EduDimens.CardPadding,
                    top = EduDimens.SpacingXl,
                    bottom = EduDimens.SpacingLg
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══════════════════════════════════════
            // FIXED: Progress Bar (never scrolls)
            // ═══════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EduProgressBar(
                    progress = progress,
                    color = EduColors.Success,
                    trackColor = EduColors.SurfaceDark,
                    height = 10.dp,
                    modifier = Modifier.weight(1f)
                )
                if (uiItem.lockState == CardLockState.COMPLETED) {
                    Spacer(modifier = Modifier.width(EduDimens.SpacingMd))
                    Icon(
                        Icons.Rounded.VerifiedUser,
                        contentDescription = "Completed",
                        tint = EduColors.Success,
                        modifier = Modifier.size(EduDimens.IconSizeMd)
                    )
                }
            }

            Spacer(modifier = Modifier.height(EduDimens.SpacingXl))

            // ═══════════════════════════════════════
            // FIXED: Category Chip
            // ═══════════════════════════════════════
            EduStatusChip(
                text = card.category,
                color = EduColors.Primary
            )

            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

            // ═══════════════════════════════════════
            // FIXED: Question (NEVER scrolls)
            // ═══════════════════════════════════════
            Text(
                text = card.title,
                style = EduTypography.H2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Question: ${card.title}" },
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(EduDimens.SpacingLg))

            // ═══════════════════════════════════════
            // Sentinel Mascot — reacts to answers
            // ═══════════════════════════════════════
            AnimatedScaleEntrance(delayMs = 100) {
                ProtctusMascot(
                    state = mascotState,
                    size = EduDimens.MascotSizeMd,
                    modifier = Modifier.scale(celebrationScale)
                )
            }

            // ═══════════════════════════════════════
            // FLEXIBLE SPACER
            // ═══════════════════════════════════════
            Spacer(modifier = Modifier.weight(1f))

            // ═══════════════════════════════════════
            // FIXED: 3D Options (NEVER scrolls)
            // ═══════════════════════════════════════
            AnimatedVisibility(
                visible = showOptions,
                enter = fadeIn(tween(350)) + slideInVertically(
                    animationSpec = tween(450, easing = FastOutSlowInEasing),
                    initialOffsetY = { 40 }
                )
            ) {
                ShakeOnTrigger(trigger = isWrong) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(EduDimens.SpacingSm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (card.type) {
                            "INFO" -> {
                                // Educational content — single confirmation
                                Edu3DPrimaryButton(
                                    text = "GOT IT  (+5 XP)",
                                    onClick = {
                                        interactionState = QuizInteractionState.Evaluated("GOT IT", true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAnswerSubmit(true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            "QUIZ_BOOL" -> {
                                val options = card.options.ifEmpty { listOf("SAFE", "SCAM") }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(EduDimens.SpacingMd),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    options.forEach { opt ->
                                        val isCorrectAnswer = card.correctAnswer.trim()
                                            .equals(opt.trim(), ignoreCase = true)
                                        val isEvaluated = interactionState is QuizInteractionState.Evaluated
                                        val isSelected = isEvaluated &&
                                                (interactionState as QuizInteractionState.Evaluated).selectedAnswer == opt

                                        Box(modifier = Modifier.weight(1f)) {
                                            Edu3DButton(
                                                text = opt,
                                                isCorrect = when {
                                                    isEvaluated && isSelected -> isCorrectAnswer
                                                    isEvaluated && isCorrectAnswer -> true
                                                    else -> null
                                                },
                                                isEnabled = !isEvaluated,
                                                onClick = {
                                                    interactionState = QuizInteractionState.Evaluated(opt, isCorrectAnswer)
                                                    if (isCorrectAnswer) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onAnswerSubmit(isCorrectAnswer)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            "QUIZ_MCQ" -> {
                                card.options.take(4).forEach { option ->
                                    val isCorrectAnswer = card.correctAnswer.trim()
                                        .equals(option.trim(), ignoreCase = true)
                                    val isEvaluated = interactionState is QuizInteractionState.Evaluated
                                    val isSelected = isEvaluated &&
                                            (interactionState as QuizInteractionState.Evaluated).selectedAnswer == option

                                    Edu3DButton(
                                        text = option,
                                        isCorrect = when {
                                            isEvaluated && isSelected -> isCorrectAnswer
                                            isEvaluated && isCorrectAnswer -> true
                                            else -> null
                                        },
                                        isEnabled = !isEvaluated,
                                        onClick = {
                                            interactionState = QuizInteractionState.Evaluated(option, isCorrectAnswer)
                                            if (isCorrectAnswer) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAnswerSubmit(isCorrectAnswer)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // FIXED HEIGHT: Result Feedback
            // Prevents layout jumps after answering
            // ═══════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = interactionState is QuizInteractionState.Evaluated,
                    enter = fadeIn(tween(200)) + scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    if (interactionState is QuizInteractionState.Evaluated) {
                        val state = interactionState as QuizInteractionState.Evaluated
                        if (!state.isCorrect) {
                            Edu3DButton(
                                text = "TRY AGAIN",
                                faceColor = EduColors.ErrorMuted,
                                depthColor = EduColors.Error,
                                textColor = EduColors.Error,
                                onClick = { interactionState = QuizInteractionState.Idle },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Rounded.Bolt,
                                    contentDescription = "XP earned",
                                    tint = EduColors.XpGold,
                                    modifier = Modifier.size(EduDimens.IconSizeLg)
                                )
                                Spacer(modifier = Modifier.width(EduDimens.SpacingSm))
                                Text(
                                    "CORRECT! +5 XP",
                                    style = EduTypography.Title.copy(color = EduColors.Success)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // Locked Overlay
        // ═══════════════════════════════════════
        if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE || uiItem.lockState == CardLockState.LOCKED_QUOTA) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = EduDimens.SpacingLg, vertical = EduDimens.SpacingSm)
                    .clip(RoundedCornerShape(EduShapes.RadiusXl))
                    .background(EduColors.Overlay)
                    .semantics {
                        contentDescription = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE) {
                            "Locked. Complete previous modules."
                        } else {
                            "Daily limit reached."
                        }
                    }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(EduDimens.SpacingLg)
                ) {
                    ProtctusMascot(
                        state = MascotState.SAD,
                        size = EduDimens.MascotSizeMd
                    )

                    Icon(
                        imageVector = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE)
                            Icons.Rounded.Lock else Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = EduColors.TextSecondary,
                        modifier = Modifier.size(EduDimens.IconSizeXl)
                    )
                    Text(
                        text = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE)
                            "Locked" else "Daily Limit Reached",
                        style = EduTypography.H2,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (uiItem.lockState == CardLockState.LOCKED_SEQUENCE)
                            "Complete previous modules first" else "Return tomorrow for more lessons",
                        style = EduTypography.Body,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
