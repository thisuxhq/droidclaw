package com.thisux.droidclaw.overlay

import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

private val CrimsonGlow = Color(0xFFC62828)

class VignetteOverlay(private val service: LifecycleService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null

    private val savedStateOwner = object : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = service.lifecycle
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performRestore(null) }
    }

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    fun show() {
        if (composeView != null) return
        val view = ComposeView(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent { VignetteContent() }
        }
        composeView = view
        windowManager.addView(view, layoutParams)
    }

    fun hide() {
        composeView?.let { windowManager.removeView(it) }
        composeView = null
    }

    fun destroy() = hide()
}

@Composable
private fun VignetteContent() {
    val transition = rememberInfiniteTransition(label = "vignettePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vignetteAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawVignette(alpha)
    }
}

private fun DrawScope.drawVignette(alpha: Float) {
    val edgeColor = CrimsonGlow.copy(alpha = 0.4f * alpha)
    val glowWidth = size.minDimension * 0.35f

    // Top edge
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(edgeColor, Color.Transparent),
            startY = 0f,
            endY = glowWidth
        )
    )
    // Bottom edge
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, edgeColor),
            startY = size.height - glowWidth,
            endY = size.height
        )
    )
    // Left edge
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(edgeColor, Color.Transparent),
            startX = 0f,
            endX = glowWidth
        )
    )
    // Right edge
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, edgeColor),
            startX = size.width - glowWidth,
            endX = size.width
        )
    )
}
