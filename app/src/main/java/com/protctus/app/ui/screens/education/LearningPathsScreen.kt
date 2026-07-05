package com.protctus.app.ui.screens.education

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.protctus.app.navigation.Route
import com.protctus.app.ui.screens.education.components.*
import com.protctus.app.ui.screens.education.theme.*

import androidx.hilt.navigation.compose.hiltViewModel
import com.protctus.app.viewmodel.LabChaptersViewModel

data class PathNode(val id: String, val title: String, val isCompleted: Boolean, val isLocked: Boolean)

/**
 * Premium Learning Path Screen
 * 
 * DESIGN:
 * - 3D path nodes with press physics
 * - Sentinel mascot on the active node
 * - S-curve staggering layout
 * - Floating top bar
 * 
 * STRICT: All colors from EduColors. No Color.White.
 */
@Composable
fun LearningPathsScreen(
    navController: NavController,
    viewModel: LabChaptersViewModel = hiltViewModel()
) {
    val nodes by viewModel.nodes.collectAsState()

    Scaffold(
        containerColor = EduColors.Background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Scrollable Path ──
            if (nodes.isEmpty()) {
                // Show loading or empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EduColors.Primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 100.dp, bottom = EduDimens.SpacingMassive)
                ) {
                    itemsIndexed(nodes, key = { _, node -> node.id }) { index, node ->
                        // S-curve offset logic
                        val isLeft = (index / 2) % 2 == 0
                        val offsetAmount = if (index % 2 == 0) 0.dp else 60.dp
                        val finalOffset = if (isLeft) -offsetAmount else offsetAmount
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EduDimens.SpacingLg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(x = finalOffset)
                            ) {
                                AnimatedEntrance(delayMs = index * 50) {
                                    EduPathNode3D(
                                        node = node,
                                        onClick = {
                                            if (!node.isLocked) navController.navigate(Route.MicrolearningFeed(node.id))
                                        }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(EduDimens.SpacingMd))
                                
                                Text(
                                    text = node.title,
                                    style = EduTypography.Label.copy(
                                        color = if (node.isLocked) EduColors.TextDisabled else EduColors.TextPrimary
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // ── Floating Top Bar ──
            EduTopBar(
                onBackClick = { navController.popBackStack() },
                title = "Cyber Path",
                modifier = Modifier
                    .background(EduColors.Background.copy(alpha = 0.95f))
                    .padding(top = innerPadding.calculateTopPadding())
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EduPathNode3D — Duolingo-style 3D circular node
// ═══════════════════════════════════════════════════════════════════

@Composable
fun EduPathNode3D(
    node: PathNode,
    onClick: () -> Unit,
    size: Dp = 85.dp,
    depth: Dp = 8.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val faceColor = when {
        node.isCompleted -> EduColors.Secondary
        node.isLocked -> EduColors.SurfaceDark
        else -> EduColors.Primary
    }

    val depthColor = when {
        node.isCompleted -> EduColors.SecondaryMuted
        node.isLocked -> EduColors.CardBorder
        else -> EduColors.PrimaryMuted
    }
    
    val borderColor = when {
        node.isLocked -> EduColors.CardBorder
        else -> faceColor
    }

    val animatedDepth by animateDpAsState(
        targetValue = if (isPressed && !node.isLocked) 0.dp else depth,
        animationSpec = tween(80),
        label = "node_depth"
    )

    Box(
        modifier = Modifier
            .width(size)
            .height(size + depth)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !node.isLocked,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Depth Layer
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = depth)
                .clip(CircleShape)
                .background(depthColor)
        )

        // Face Layer
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = depth - animatedDepth)
                .clip(CircleShape)
                .background(faceColor)
                .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                node.isLocked -> {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = EduColors.TextDisabled,
                        modifier = Modifier.size(EduDimens.IconSizeLg)
                    )
                }
                node.isCompleted -> {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Completed",
                        tint = EduColors.Background,
                        modifier = Modifier.size(EduDimens.IconSizeXl)
                    )
                }
                else -> {
                    // Active node — Sentinel Mascot inside the node
                    ProtctusMascot(
                        state = MascotState.IDLE,
                        size = size * 0.7f
                    )
                }
            }
        }
    }
}
