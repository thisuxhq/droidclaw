package com.thisux.droidclaw.overlay

import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thisux.droidclaw.DroidClawApp
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.GoalStatus
import com.thisux.droidclaw.ui.theme.DroidClawTheme

class CommandPanelOverlay(
    private val service: LifecycleService,
    private val onSubmitGoal: (String) -> Unit,
    private val onStartVoice: () -> Unit,
    private val onDismiss: () -> Unit
) {
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
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    fun show() {
        if (composeView != null) return
        val view = ComposeView(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                CommandPanelContent(
                    onSubmitGoal = { goal ->
                        hide()
                        onSubmitGoal(goal)
                        onDismiss()
                    },
                    onStartVoice = {
                        hide()
                        onStartVoice()
                    },
                    onDismiss = {
                        hide()
                        onDismiss()
                    }
                )
            }
        }
        windowManager.addView(view, layoutParams)
        composeView = view
    }

    fun hide() {
        composeView?.let { windowManager.removeView(it) }
        composeView = null
    }

    fun isShowing() = composeView != null

    fun destroy() = hide()
}

private val DEFAULT_SUGGESTIONS = listOf(
    "Open WhatsApp and reply to the last message",
    "Take a screenshot and save it",
    "Turn on Do Not Disturb",
    "Search for nearby restaurants on Maps"
)

@Composable
private fun CommandPanelContent(
    onSubmitGoal: (String) -> Unit,
    onStartVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    DroidClawTheme {
        val context = LocalContext.current
        val app = context.applicationContext as DroidClawApp
        val recentGoals by app.settingsStore.recentGoals.collectAsState(initial = emptyList())

        val connectionState by ConnectionService.connectionState.collectAsState()
        val goalStatus by ConnectionService.currentGoalStatus.collectAsState()
        val isConnected = connectionState == ConnectionState.Connected
        val canSend = isConnected && goalStatus != GoalStatus.Running

        var goalInput by remember { mutableStateOf("") }

        // Auto-dismiss if a goal starts running
        LaunchedEffect(goalStatus) {
            if (goalStatus == GoalStatus.Running) {
                onDismiss()
            }
        }

        // Build suggestion list: recent goals first, fill remaining with defaults
        val suggestions = remember(recentGoals) {
            val combined = mutableListOf<String>()
            combined.addAll(recentGoals.take(4))
            for (default in DEFAULT_SUGGESTIONS) {
                if (combined.size >= 4) break
                if (default !in combined) combined.add(default)
            }
            combined.take(4)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim - tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
            )

            // Bottom card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume clicks so they don't reach scrim */ },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Handle bar
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            .align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = "What can I help with?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 2x2 suggestion grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in suggestions.chunked(2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (suggestion in row) {
                                    SuggestionCard(
                                        text = suggestion,
                                        enabled = canSend,
                                        onClick = { onSubmitGoal(suggestion) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Text input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sendEnabled = canSend && goalInput.isNotBlank()

                        TextField(
                            value = goalInput,
                            onValueChange = { goalInput = it },
                            placeholder = {
                                Text(
                                    if (!isConnected) "Not connected"
                                    else "Enter a goal...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = canSend,
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )

                        IconButton(
                            onClick = { onStartVoice() },
                            enabled = canSend,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (canSend)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Voice",
                                tint = if (canSend)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (goalInput.isNotBlank()) onSubmitGoal(goalInput)
                            },
                            enabled = sendEnabled,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (sendEnabled)
                                    MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (sendEnabled)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
