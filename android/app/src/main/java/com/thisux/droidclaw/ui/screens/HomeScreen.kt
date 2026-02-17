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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.GoalStatus

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val connectionState by ConnectionService.connectionState.collectAsState()
    val goalStatus by ConnectionService.currentGoalStatus.collectAsState()
    val steps by ConnectionService.currentSteps.collectAsState()
    val currentGoal by ConnectionService.currentGoal.collectAsState()
    val errorMessage by ConnectionService.errorMessage.collectAsState()

    var goalInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionState) {
                            ConnectionState.Connected -> Color(0xFF4CAF50)
                            ConnectionState.Connecting -> Color(0xFFFFC107)
                            ConnectionState.Error -> Color(0xFFF44336)
                            ConnectionState.Disconnected -> Color.Gray
                        }
                    )
            )
            Text(
                text = when (connectionState) {
                    ConnectionState.Connected -> "Connected to server"
                    ConnectionState.Connecting -> "Connecting..."
                    ConnectionState.Error -> errorMessage ?: "Connection error"
                    ConnectionState.Disconnected -> "Disconnected"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connect/Disconnect button
        Button(
            onClick = {
                val intent = Intent(context, ConnectionService::class.java).apply {
                    action = if (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error) {
                        ConnectionService.ACTION_CONNECT
                    } else {
                        ConnectionService.ACTION_DISCONNECT
                    }
                }
                context.startForegroundService(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (connectionState) {
                    ConnectionState.Disconnected, ConnectionState.Error -> "Connect"
                    else -> "Disconnect"
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Goal Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = it },
                label = { Text("Enter a goal...") },
                modifier = Modifier.weight(1f),
                enabled = connectionState == ConnectionState.Connected && goalStatus != GoalStatus.Running,
                singleLine = true
            )
            Button(
                onClick = {
                    if (goalInput.isNotBlank()) {
                        val intent = Intent(context, ConnectionService::class.java).apply {
                            action = ConnectionService.ACTION_SEND_GOAL
                            putExtra(ConnectionService.EXTRA_GOAL, goalInput)
                        }
                        context.startService(intent)
                        goalInput = ""
                    }
                },
                enabled = connectionState == ConnectionState.Connected
                    && goalStatus != GoalStatus.Running
                    && goalInput.isNotBlank()
            ) {
                Text("Run")
            }
        }

        if (currentGoal.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Goal: $currentGoal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step Log
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(steps) { step ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Step ${step.step}: ${step.action}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (step.reasoning.isNotEmpty()) {
                            Text(
                                text = step.reasoning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Goal Status
        if (goalStatus == GoalStatus.Completed || goalStatus == GoalStatus.Failed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (goalStatus == GoalStatus.Completed) {
                    "Goal completed (${steps.size} steps)"
                } else {
                    "Goal failed"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (goalStatus == GoalStatus.Completed) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
