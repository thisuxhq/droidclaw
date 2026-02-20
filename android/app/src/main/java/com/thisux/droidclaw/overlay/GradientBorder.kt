package com.thisux.droidclaw.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val GradientColors = listOf(
    Color(0xFFC62828), // crimson red
    Color(0xFFEF5350), // crimson light
    Color(0xFFFFB300), // golden accent
    Color(0xFFEF5350), // crimson light
    Color(0xFFC62828), // crimson red (loop)
)

@Composable
fun GradientBorder() {
    val transition = rememberInfiniteTransition(label = "gradientRotation")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    val borderWidth = with(LocalDensity.current) { 4.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val shiftedColors = shiftColors(GradientColors, offset)

        // Top edge
        drawRect(
            brush = Brush.horizontalGradient(shiftedColors),
            topLeft = Offset.Zero,
            size = Size(w, borderWidth)
        )

        // Bottom edge
        drawRect(
            brush = Brush.horizontalGradient(shiftedColors.reversed()),
            topLeft = Offset(0f, h - borderWidth),
            size = Size(w, borderWidth)
        )

        // Left edge
        drawRect(
            brush = Brush.verticalGradient(shiftedColors),
            topLeft = Offset.Zero,
            size = Size(borderWidth, h)
        )

        // Right edge
        drawRect(
            brush = Brush.verticalGradient(shiftedColors.reversed()),
            topLeft = Offset(w - borderWidth, 0f),
            size = Size(borderWidth, h)
        )
    }
}

private fun shiftColors(colors: List<Color>, offset: Float): List<Color> {
    if (colors.size < 2) return colors
    val n = colors.size
    val shift = (offset * n).toInt() % n
    return colors.subList(shift, n) + colors.subList(0, shift)
}
