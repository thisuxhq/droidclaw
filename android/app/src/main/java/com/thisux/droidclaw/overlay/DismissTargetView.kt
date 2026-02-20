package com.thisux.droidclaw.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class DismissTargetView(private val service: LifecycleService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null

    private val density = service.resources.displayMetrics.density
    private var targetCenterX = 0f
    private var targetCenterY = 0f
    private var targetRadiusPx = 36f * density

    private val savedStateOwner = object : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = service.lifecycle
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performRestore(null) }
    }

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }

    fun show() {
        if (composeView != null) return

        // Compute target coordinates synchronously before showing the view
        val metrics = windowManager.currentWindowMetrics
        val screenWidth = metrics.bounds.width().toFloat()
        val screenHeight = metrics.bounds.height().toFloat()
        targetCenterX = screenWidth / 2f
        // The circle is 56dp from bottom edge + 36dp (half of 72dp circle)
        targetCenterY = screenHeight - (56f + 36f) * density

        val view = ComposeView(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent { DismissTargetContent() }
        }
        composeView = view
        windowManager.addView(view, layoutParams)
    }

    fun hide() {
        composeView?.let { windowManager.removeView(it) }
        composeView = null
    }

    fun destroy() = hide()

    fun isOverTarget(rawX: Float, rawY: Float): Boolean {
        if (composeView == null) return false
        val dx = rawX - targetCenterX
        val dy = rawY - targetCenterY
        // Use generous hit radius (1.5x visual radius) for easier targeting
        val hitRadius = targetRadiusPx * 1.5f
        return (dx * dx + dy * dy) <= (hitRadius * hitRadius)
    }
}

@Composable
private fun DismissTargetContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 56.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xCC333333)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
