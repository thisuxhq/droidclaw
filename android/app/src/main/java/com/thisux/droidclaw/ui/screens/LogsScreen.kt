package com.thisux.droidclaw.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.model.GoalStatus

@Composable
fun LogsScreen() {
    val steps by ConnectionService.currentSteps.collectAsState()
    val goalStatus by ConnectionService.currentGoalStatus.collectAsState()
    val currentGoal by ConnectionService.currentGoal.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Logs", style = MaterialTheme.typography.headlineMedium)

        if (currentGoal.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentGoal,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (goalStatus) {
                        GoalStatus.Running -> "Running"
                        GoalStatus.Completed -> "Completed"
                        GoalStatus.Failed -> "Failed"
                        GoalStatus.Idle -> "Idle"
                    },
                    color = when (goalStatus) {
                        GoalStatus.Running -> Color(0xFFFFC107)
                        GoalStatus.Completed -> Color(0xFF4CAF50)
                        GoalStatus.Failed -> MaterialTheme.colorScheme.error
                        GoalStatus.Idle -> Color.Gray
                    }
                )
            }
        }

        if (steps.isEmpty()) {
            Text(
                text = "No steps recorded yet. Submit a goal to see agent activity here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(steps) { step ->
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Step ${step.step}: ${step.action}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (expanded && step.reasoning.isNotEmpty()) {
                                Text(
                                    text = step.reasoning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
