package com.thisux.droidclaw.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thisux.droidclaw.R
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.GoalStatus
import com.thisux.droidclaw.ui.theme.DroidClawTheme
import kotlinx.coroutines.delay

private val Green = Color(0xFF4CAF50)
private val Blue = Color(0xFF2196F3)
private val Red = Color(0xFFF44336)
private val Gray = Color(0xFF9E9E9E)
private val IconBackground = Color(0xFF1A1A1A)

@Composable
fun OverlayContent() {
    DroidClawTheme {
        val connectionState by ConnectionService.connectionState.collectAsState()
        val goalStatus by ConnectionService.currentGoalStatus.collectAsState()

        var displayStatus by remember { mutableStateOf(goalStatus) }
        LaunchedEffect(goalStatus) {
            displayStatus = goalStatus
            if (goalStatus == GoalStatus.Completed || goalStatus == GoalStatus.Failed) {
                delay(3000)
                displayStatus = GoalStatus.Idle
            }
        }

        val isConnected = connectionState == ConnectionState.Connected

        val ringColor by animateColorAsState(
            targetValue = when {
                !isConnected -> Gray
                displayStatus == GoalStatus.Running -> Red
                displayStatus == GoalStatus.Completed -> Blue
                displayStatus == GoalStatus.Failed -> Gray
                else -> Green
            },
            label = "ringColor"
        )

        val isRunning = isConnected && displayStatus == GoalStatus.Running

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp)
        ) {
            // Background circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(IconBackground)
            )

            if (isRunning) {
                // Spinning progress ring
                val transition = rememberInfiniteTransition(label = "spin")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing)
                    ),
                    label = "rotation"
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = ringColor,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )
            } else {
                // Static colored ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(52.dp),
                    color = ringColor,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )
            }

            // App icon
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "DroidClaw",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        }
    }
}
