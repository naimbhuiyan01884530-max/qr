package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

@Composable
fun ScannerReticleOverlay(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "laser_sweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val reticleSize = (size.minDimension * 0.68f).coerceIn(180f, 760f)
        val left = (size.width - reticleSize) / 2f
        val top = (size.height - reticleSize) / 2f - size.height * 0.04f
        val right = left + reticleSize
        val bottom = top + reticleSize
        val cornerRadius = 24.dp.toPx()

        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }

        // Dim outer studio frame around the optical reticle
        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.56f))
        }

        // Subtle inner border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(left, top),
            size = Size(reticleSize, reticleSize),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.dp.toPx())
        )

        // Four precision corner brackets
        val bracketLen = reticleSize * 0.14f
        val strokeWidth = 4.dp.toPx()
        val bracketColor = if (isActive) primaryColor else Color.White.copy(alpha = 0.6f)

        // Top-left
        drawLine(bracketColor, Offset(left, top + cornerRadius), Offset(left, top + bracketLen), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius, top), Offset(left + bracketLen, top), strokeWidth, StrokeCap.Round)
        drawArc(
            color = bracketColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Top-right
        drawLine(bracketColor, Offset(right, top + cornerRadius), Offset(right, top + bracketLen), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right - cornerRadius, top), Offset(right - bracketLen, top), strokeWidth, StrokeCap.Round)
        drawArc(
            color = bracketColor,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - cornerRadius * 2, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Bottom-left
        drawLine(bracketColor, Offset(left, bottom - cornerRadius), Offset(left, bottom - bracketLen), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius, bottom), Offset(left + bracketLen, bottom), strokeWidth, StrokeCap.Round)
        drawArc(
            color = bracketColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, bottom - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Bottom-right
        drawLine(bracketColor, Offset(right, bottom - cornerRadius), Offset(right, bottom - bracketLen), strokeWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right - cornerRadius, bottom), Offset(right - bracketLen, bottom), strokeWidth, StrokeCap.Round)
        drawArc(
            color = bracketColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(right - cornerRadius * 2, bottom - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Animated laser sweep line inside reticle
        if (isActive) {
            val inset = 14.dp.toPx()
            val laserY = top + inset + (reticleSize - inset * 2) * sweepProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor,
                        primaryColor.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    startX = left + inset,
                    endX = right - inset
                ),
                start = Offset(left + inset, laserY),
                end = Offset(right - inset, laserY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
