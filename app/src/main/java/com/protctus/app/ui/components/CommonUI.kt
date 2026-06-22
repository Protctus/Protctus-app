package com.protctus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun ProtctusPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Tactile bounce physics: scale down to 95% when held securely without altering UI layout bounds
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f)

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale), // Apply dynamic physics here
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2A2B31), // Exact extracted hex
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A2B31).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        enabled = enabled
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProtctusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    labelString: String? = null // Added for floating labels
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        placeholder = {
            Text(
                text = hint,
                color = Color(0xFF7F909F),
                fontSize = 14.sp
            )
        },
        label = labelString?.let {
            {
                Text(
                    text = it,
                    color = Color(0xFF7F909F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
    )
}

@Composable
fun ScanProgressRing(
    progress: Float, // 0f to 1f
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(300.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        // Outer Dashed Circle
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            drawCircle(
                color = Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f))
                )
            )
            // Draw nodes
            val radius = size.minDimension / 2
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            for (i in 0 until 8) {
                val angle = (i * 45) * (Math.PI / 180)
                val x = center.x + radius * kotlin.math.cos(angle).toFloat()
                val y = center.y + radius * kotlin.math.sin(angle).toFloat()
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }

        // Inner Thick Ring (Background track)
        androidx.compose.foundation.Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 16.dp.toPx()
            drawCircle(
                color = Color(0xFF222225),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // Inner Thick Ring (Progress gradient)
        androidx.compose.foundation.Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 16.dp.toPx()
            drawArc(
                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                    colors = listOf(Color(0xFF4A5568), Color(0xFFA5B4FC), Color.White)
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }

        // Center Content Area
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            centerContent()
        }
    }
}

