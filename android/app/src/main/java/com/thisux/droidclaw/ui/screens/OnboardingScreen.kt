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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.thisux.droidclaw.connection.PairingApi
import com.thisux.droidclaw.ui.theme.StatusGreen
import com.thisux.droidclaw.util.BatteryOptimization
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) }

    AnimatedContent(targetState = currentStep, label = "onboarding_step") { step ->
        when (step) {
            0 -> OnboardingStepOne(
                onContinue = { currentStep = 1 }
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

private const val CLOUD_SERVER_URL = "wss://tunnel.droidclaw.ai"
private const val CLOUD_PAIRING_BASE = "https://tunnel.droidclaw.ai"

@Composable
private fun OnboardingStepOne(onContinue: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val scope = rememberCoroutineScope()

    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSelfHost by remember { mutableStateOf(false) }

    // Self-host fields
    var selfHostApiKey by remember { mutableStateOf("") }
    var selfHostUrl by remember { mutableStateOf("wss://") }

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

        if (!showSelfHost) {
            // ── OTP Pairing (default) ──
            Text(
                text = "Pair your device to get started",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open droidclaw.ai/dashboard, go to Devices, and enter the 6-digit code shown there",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                label = { Text("6-digit code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = errorMessage != null
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val result = PairingApi.claim(CLOUD_PAIRING_BASE, otpCode)
                        isLoading = false
                        result.onSuccess { response ->
                            app.settingsStore.setApiKey(response.apiKey)
                            app.settingsStore.setServerUrl(response.wsUrl)
                            app.settingsStore.setConnectionMode("cloud")
                            onContinue()
                        }.onFailure { e ->
                            errorMessage = e.message ?: "Pairing failed"
                        }
                    }
                },
                enabled = otpCode.length == 6 && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("Connect", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = { showSelfHost = true }) {
                Text("Self-hosting? Tap here for manual setup")
            }
        } else {
            // ── Self-hosted manual setup ──
            Text(
                text = "Manual Setup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = selfHostUrl,
                onValueChange = { selfHostUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = selfHostApiKey,
                onValueChange = { selfHostApiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        app.settingsStore.setApiKey(selfHostApiKey)
                        app.settingsStore.setServerUrl(selfHostUrl)
                        app.settingsStore.setConnectionMode("selfhosted")
                        onContinue()
                    }
                },
                enabled = selfHostApiKey.isNotBlank() && selfHostUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { showSelfHost = false }) {
                Text("Back to OTP pairing")
            }
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
