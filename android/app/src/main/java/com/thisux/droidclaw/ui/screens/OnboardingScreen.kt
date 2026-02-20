package com.thisux.droidclaw.ui.screens

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thisux.droidclaw.DroidClawApp
import com.thisux.droidclaw.accessibility.DroidClawAccessibilityService
import com.thisux.droidclaw.capture.ScreenCaptureManager
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.ui.theme.StatusGreen
import com.thisux.droidclaw.util.BatteryOptimization
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) }

    val apiKey by app.settingsStore.apiKey.collectAsState(initial = "")
    val serverUrl by app.settingsStore.serverUrl.collectAsState(initial = "wss://tunnel.droidclaw.ai")

    var editingApiKey by remember { mutableStateOf("") }
    var editingServerUrl by remember { mutableStateOf("wss://tunnel.droidclaw.ai") }

    // Sync from datastore when loaded
    var initialized by remember { mutableStateOf(false) }
    if (!initialized && apiKey.isNotEmpty()) {
        editingApiKey = apiKey
        initialized = true
    }
    if (serverUrl != "wss://tunnel.droidclaw.ai" || editingServerUrl == "wss://tunnel.droidclaw.ai") {
        editingServerUrl = serverUrl
    }

    AnimatedContent(targetState = currentStep, label = "onboarding_step") { step ->
        when (step) {
            0 -> OnboardingStepOne(
                apiKey = editingApiKey,
                serverUrl = editingServerUrl,
                onApiKeyChange = { editingApiKey = it },
                onServerUrlChange = { editingServerUrl = it },
                onContinue = {
                    scope.launch {
                        app.settingsStore.setApiKey(editingApiKey)
                        app.settingsStore.setServerUrl(editingServerUrl)
                        currentStep = 1
                    }
                }
            )
            1 -> OnboardingStepTwo(
                onContinue = { currentStep = 2 }
            )
            2 -> OnboardingStepAssistant(
                onContinue = {
                    scope.launch {
                        app.settingsStore.setHasOnboarded(true)
                        val intent = Intent(context, ConnectionService::class.java).apply {
                            action = ConnectionService.ACTION_CONNECT
                        }
                        context.startForegroundService(intent)
                        onComplete()
                    }
                },
                onSkip = {
                    scope.launch {
                        app.settingsStore.setHasOnboarded(true)
                        val intent = Intent(context, ConnectionService::class.java).apply {
                            action = ConnectionService.ACTION_CONNECT
                        }
                        context.startForegroundService(intent)
                        onComplete()
                    }
                }
            )
        }
    }
}

@Composable
private fun OnboardingStepOne(
    apiKey: String,
    serverUrl: String,
    onApiKeyChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Welcome to",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "DroidClaw",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AI-powered Android automation",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            enabled = apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OnboardingStepTwo(onContinue: () -> Unit) {
    val context = LocalContext.current
    val isCaptureAvailable by ScreenCaptureManager.isAvailable.collectAsState()

    var isAccessibilityEnabled by remember {
        mutableStateOf(DroidClawAccessibilityService.isEnabledOnDevice(context))
    }
    var hasCaptureConsent by remember {
        ScreenCaptureManager.restoreConsent(context)
        mutableStateOf(isCaptureAvailable || ScreenCaptureManager.hasConsent())
    }
    var isBatteryExempt by remember {
        mutableStateOf(BatteryOptimization.isIgnoringBatteryOptimizations(context))
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = DroidClawAccessibilityService.isEnabledOnDevice(context)
                ScreenCaptureManager.restoreConsent(context)
                hasCaptureConsent = isCaptureAvailable || ScreenCaptureManager.hasConsent()
                isBatteryExempt = BatteryOptimization.isIgnoringBatteryOptimizations(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureManager.storeConsent(context, result.resultCode, result.data)
            hasCaptureConsent = true
        }
    }

    val allGranted = isAccessibilityEnabled && hasCaptureConsent && isBatteryExempt && hasOverlayPermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Setup Permissions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "DroidClaw needs these permissions to control your device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingChecklistItem(
            label = "Accessibility Service",
            description = "Required to read screen content and perform actions",
            isOk = isAccessibilityEnabled,
            actionLabel = "Enable",
            onAction = { BatteryOptimization.openAccessibilitySettings(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingChecklistItem(
            label = "Screen Capture",
            description = "Required to capture screenshots for visual analysis",
            isOk = hasCaptureConsent,
            actionLabel = "Grant",
            onAction = {
                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(mgr.createScreenCaptureIntent())
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingChecklistItem(
            label = "Battery Optimization",
            description = "Prevents the system from killing the background service",
            isOk = isBatteryExempt,
            actionLabel = "Disable",
            onAction = { BatteryOptimization.requestExemption(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingChecklistItem(
            label = "Overlay Permission",
            description = "Shows agent status indicator over other apps",
            isOk = hasOverlayPermission,
            actionLabel = "Grant",
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            enabled = allGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
        }

        if (!allGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grant all permissions to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingStepAssistant(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    var isDefaultAssistant by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            } else false
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultAssistant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                } else false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Digital Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set DroidClaw as your default digital assistant to invoke it with a long-press on the home button",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingChecklistItem(
            label = "Default Digital Assistant",
            description = "Long-press home to open DroidClaw command panel",
            isOk = isDefaultAssistant,
            actionLabel = "Set",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Get Started", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun OnboardingChecklistItem(
    label: String,
    description: String,
    isOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = if (isOk) "Granted" else "Not granted",
                    tint = if (isOk) StatusGreen else MaterialTheme.colorScheme.error
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isOk) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
