package com.thisux.droidclaw.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thisux.droidclaw.DroidClawApp
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.model.AgentStep
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.GoalStatus
import com.thisux.droidclaw.ui.theme.StatusGreen
import com.thisux.droidclaw.ui.theme.StatusRed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DEFAULT_SUGGESTIONS = listOf(
    "Open WhatsApp and reply to the last message",
    "Take a screenshot and save it",
    "Turn on Do Not Disturb",
    "Search for nearby restaurants on Maps"
)

// Represents a message in the chat timeline
private sealed class ChatItem {
    data class GoalMessage(val text: String) : ChatItem()
    data class StepMessage(val step: AgentStep) : ChatItem()
    data class StatusMessage(val status: GoalStatus, val stepCount: Int) : ChatItem()
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val connectionState by ConnectionService.connectionState.collectAsState()
    val goalStatus by ConnectionService.currentGoalStatus.collectAsState()
    val steps by ConnectionService.currentSteps.collectAsState()
    val currentGoal by ConnectionService.currentGoal.collectAsState()
    val recentGoals by app.settingsStore.recentGoals.collectAsState(initial = emptyList())

    var goalInput by remember { mutableStateOf("") }

    val isConnected = connectionState == ConnectionState.Connected
    val canSend = isConnected && goalStatus != GoalStatus.Running

    val suggestions = remember(recentGoals) {
        val combined = mutableListOf<String>()
        combined.addAll(recentGoals.take(4))
        for (default in DEFAULT_SUGGESTIONS) {
            if (combined.size >= 4) break
            if (default !in combined) combined.add(default)
        }
        combined.take(4)
    }

    // Build chat items: goal bubble → step bubbles → status bubble
    val chatItems = remember(currentGoal, steps, goalStatus) {
        buildList {
            if (currentGoal.isNotEmpty()) {
                add(ChatItem.GoalMessage(currentGoal))
            }
            steps.forEach { add(ChatItem.StepMessage(it)) }
            if (goalStatus == GoalStatus.Running) {
                add(ChatItem.StatusMessage(GoalStatus.Running, steps.size))
            } else if (goalStatus == GoalStatus.Completed || goalStatus == GoalStatus.Failed) {
                add(ChatItem.StatusMessage(goalStatus, steps.size))
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new items arrive
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat area
        if (chatItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "What should I do?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Send a goal to start the agent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
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
                                        onClick = {
                                            val intent = Intent(context, ConnectionService::class.java).apply {
                                                action = ConnectionService.ACTION_SEND_GOAL
                                                putExtra(ConnectionService.EXTRA_GOAL, suggestion)
                                            }
                                            context.startService(intent)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(chatItems, key = { item ->
                    when (item) {
                        is ChatItem.GoalMessage -> "goal_${item.text}"
                        is ChatItem.StepMessage -> "step_${item.step.step}"
                        is ChatItem.StatusMessage -> "status_${item.status}"
                    }
                }) { item ->
                    when (item) {
                        is ChatItem.GoalMessage -> GoalBubble(item.text)
                        is ChatItem.StepMessage -> AgentBubble(item.step)
                        is ChatItem.StatusMessage -> StatusBubble(item.status, item.stepCount)
                    }
                }
            }
        }

        // Input bar pinned at bottom
        InputBar(
            value = goalInput,
            onValueChange = { goalInput = it },
            onSend = {
                if (goalInput.isNotBlank()) {
                    val intent = Intent(context, ConnectionService::class.java).apply {
                        action = ConnectionService.ACTION_SEND_GOAL
                        putExtra(ConnectionService.EXTRA_GOAL, goalInput)
                    }
                    context.startService(intent)
                    goalInput = ""
                }
            },
            onStop = { ConnectionService.instance?.stopGoal() },
            canSend = connectionState == ConnectionState.Connected
                    && goalStatus != GoalStatus.Running
                    && goalInput.isNotBlank(),
            isRunning = goalStatus == GoalStatus.Running,
            isConnected = connectionState == ConnectionState.Connected
        )
    }
}

/** User's goal — right-aligned bubble */
@Composable
private fun GoalBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** Agent step — left-aligned bubble */
@Composable
private fun AgentBubble(step: AgentStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Step number badge
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${step.step}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = step.action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (step.reasoning.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = step.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTime(step.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Status indicator — centered */
@Composable
private fun StatusBubble(status: GoalStatus, stepCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        val (text, color) = when (status) {
            GoalStatus.Running -> "Thinking..." to MaterialTheme.colorScheme.onSurfaceVariant
            GoalStatus.Completed -> "Done — $stepCount steps" to StatusGreen
            GoalStatus.Failed -> "Failed" to StatusRed
            GoalStatus.Idle -> "" to Color.Transparent
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (status == GoalStatus.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = color
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

/** Bottom input bar */
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    canSend: Boolean,
    isRunning: Boolean,
    isConnected: Boolean
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        if (!isConnected) "Not connected"
                        else if (isRunning) "Agent is working..."
                        else "Enter a goal...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = isConnected && !isRunning,
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

            if (isRunning) {
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = StatusRed.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop",
                        tint = StatusRed
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
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

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
