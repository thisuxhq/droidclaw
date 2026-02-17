# DroidClaw Android App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the DroidClaw Android companion app — a Jetpack Compose app that connects to the Hono server via WebSocket, captures accessibility trees and screenshots, executes gestures, and lets users submit goals from the phone.

**Architecture:** Three-layer architecture (Accessibility → Connection → UI). The AccessibilityService captures screen trees and executes gestures. A foreground ConnectionService manages the Ktor WebSocket. Compose UI with bottom nav (Home/Settings/Logs) observes service state via companion-object StateFlows.

**Tech Stack:** Kotlin, Jetpack Compose, Ktor Client WebSocket, kotlinx.serialization, DataStore Preferences, MediaProjection API, AccessibilityService API.

---

## Existing Project State

The Android project is a fresh Compose scaffold:
- `android/app/build.gradle.kts` — Compose app with AGP 9.0.1, Kotlin 2.0.21, compileSdk 36, minSdk 24
- `android/gradle/libs.versions.toml` — version catalog with basic Compose + lifecycle deps
- `android/app/src/main/java/com/thisux/droidclaw/MainActivity.kt` — Hello World Compose activity
- `android/app/src/main/java/com/thisux/droidclaw/ui/theme/` — Default Material 3 theme (Color, Type, Theme)
- Shared TypeScript types in `packages/shared/src/types.ts` and `packages/shared/src/protocol.ts` define the data models and WebSocket protocol the Android app must mirror.

---

### Task 1: Add Dependencies & Build Config

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/build.gradle.kts` (root)
- Modify: `android/app/build.gradle.kts`

**Step 1: Add version catalog entries**

Add to `android/gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.0.1"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.0.21"
composeBom = "2024.09.00"
ktor = "3.1.1"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
datastore = "1.1.1"
lifecycleService = "2.8.7"
navigationCompose = "2.8.5"
composeIconsExtended = "1.7.6"

[libraries]
# ... keep existing entries ...
ktor-client-cio = { group = "io.ktor", name = "ktor-client-cio", version.ref = "ktor" }
ktor-client-websockets = { group = "io.ktor", name = "ktor-client-websockets", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycleService" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
compose-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "composeIconsExtended" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Step 2: Add serialization plugin to root build.gradle.kts**

In `android/build.gradle.kts`, add:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

**Step 3: Add plugin and dependencies to app build.gradle.kts**

In `android/app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ... android block stays the same, but fix compileSdk ...
android {
    namespace = "com.thisux.droidclaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thisux.droidclaw"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // ... rest stays the same ...
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Existing
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // New - Ktor WebSocket
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // New - Serialization
    implementation(libs.kotlinx.serialization.json)

    // New - Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // New - DataStore
    implementation(libs.datastore.preferences)

    // New - Lifecycle service
    implementation(libs.lifecycle.service)

    // New - Navigation
    implementation(libs.navigation.compose)
    implementation(libs.compose.icons.extended)

    // Test deps stay the same
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

**Step 4: Sync and verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/gradle/libs.versions.toml android/build.gradle.kts android/app/build.gradle.kts
git commit -m "feat(android): add Ktor, serialization, DataStore, navigation dependencies"
```

---

### Task 2: Data Models

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/model/UIElement.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/model/Protocol.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/model/AppState.kt`

These mirror `packages/shared/src/types.ts` and `packages/shared/src/protocol.ts`.

**Step 1: Create UIElement.kt**

```kotlin
package com.thisux.droidclaw.model

import kotlinx.serialization.Serializable

@Serializable
data class UIElement(
    val id: String = "",
    val text: String = "",
    val type: String = "",
    val bounds: String = "",
    val center: List<Int> = listOf(0, 0),
    val size: List<Int> = listOf(0, 0),
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = false,
    val checked: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val scrollable: Boolean = false,
    val longClickable: Boolean = false,
    val password: Boolean = false,
    val hint: String = "",
    val action: String = "read",
    val parent: String = "",
    val depth: Int = 0
)
```

**Step 2: Create Protocol.kt**

```kotlin
package com.thisux.droidclaw.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Device → Server messages
@Serializable
data class AuthMessage(
    val type: String = "auth",
    val apiKey: String,
    val deviceInfo: DeviceInfoMsg? = null
)

@Serializable
data class DeviceInfoMsg(
    val model: String,
    val androidVersion: String,
    val screenWidth: Int,
    val screenHeight: Int
)

@Serializable
data class ScreenResponse(
    val type: String = "screen",
    val requestId: String,
    val elements: List<UIElement>,
    val screenshot: String? = null,
    val packageName: String? = null
)

@Serializable
data class ResultResponse(
    val type: String = "result",
    val requestId: String,
    val success: Boolean,
    val error: String? = null,
    val data: String? = null
)

@Serializable
data class GoalMessage(
    val type: String = "goal",
    val text: String
)

@Serializable
data class PongMessage(
    val type: String = "pong"
)

// Server → Device messages (parsed via discriminator)
@Serializable
data class ServerMessage(
    val type: String,
    val requestId: String? = null,
    val deviceId: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val goal: String? = null,
    val success: Boolean? = null,
    val stepsUsed: Int? = null,
    val step: Int? = null,
    val action: JsonObject? = null,
    val reasoning: String? = null,
    val screenHash: String? = null,
    // Action-specific fields
    val x: Int? = null,
    val y: Int? = null,
    val x1: Int? = null,
    val y1: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val duration: Int? = null,
    val text: String? = null,
    val packageName: String? = null,
    val url: String? = null,
    val code: Int? = null
)
```

**Step 3: Create AppState.kt**

```kotlin
package com.thisux.droidclaw.model

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error
}

enum class GoalStatus {
    Idle,
    Running,
    Completed,
    Failed
}

data class AgentStep(
    val step: Int,
    val action: String,
    val reasoning: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GoalSession(
    val sessionId: String,
    val goal: String,
    val steps: List<AgentStep>,
    val status: GoalStatus,
    val timestamp: Long = System.currentTimeMillis()
)
```

**Step 4: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/model/
git commit -m "feat(android): add data models (UIElement, Protocol, AppState)"
```

---

### Task 3: DataStore Settings

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/data/SettingsStore.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/DroidClawApp.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (add Application class)

**Step 1: Create SettingsStore.kt**

```kotlin
package com.thisux.droidclaw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val SERVER_URL = stringPreferencesKey("server_url")
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
}

class SettingsStore(private val context: Context) {

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.API_KEY] ?: ""
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.SERVER_URL] ?: "wss://localhost:8080"
    }

    val deviceName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.DEVICE_NAME] ?: android.os.Build.MODEL
    }

    val autoConnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.AUTO_CONNECT] ?: false
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[SettingsKeys.API_KEY] = value }
    }

    suspend fun setServerUrl(value: String) {
        context.dataStore.edit { it[SettingsKeys.SERVER_URL] = value }
    }

    suspend fun setDeviceName(value: String) {
        context.dataStore.edit { it[SettingsKeys.DEVICE_NAME] = value }
    }

    suspend fun setAutoConnect(value: Boolean) {
        context.dataStore.edit { it[SettingsKeys.AUTO_CONNECT] = value }
    }
}
```

**Step 2: Create DroidClawApp.kt**

```kotlin
package com.thisux.droidclaw

import android.app.Application
import com.thisux.droidclaw.data.SettingsStore

class DroidClawApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
    }
}
```

**Step 3: Register Application class in AndroidManifest.xml**

Add `android:name=".DroidClawApp"` to the `<application>` tag:

```xml
<application
    android:name=".DroidClawApp"
    android:allowBackup="true"
    ...>
```

**Step 4: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/data/ android/app/src/main/java/com/thisux/droidclaw/DroidClawApp.kt android/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add DataStore settings and Application class"
```

---

### Task 4: Accessibility Service + ScreenTreeBuilder

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/accessibility/DroidClawAccessibilityService.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/accessibility/ScreenTreeBuilder.kt`
- Create: `android/app/src/main/res/xml/accessibility_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` (add service declaration)

**Step 1: Create accessibility_config.xml**

Create `android/app/src/main/res/xml/accessibility_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewFocused"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRequestEnhancedWebAccessibility"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:settingsActivity="com.thisux.droidclaw.MainActivity" />
```

**Step 2: Create ScreenTreeBuilder.kt**

```kotlin
package com.thisux.droidclaw.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.thisux.droidclaw.model.UIElement
import java.security.MessageDigest

object ScreenTreeBuilder {

    fun capture(rootNode: AccessibilityNodeInfo?): List<UIElement> {
        if (rootNode == null) return emptyList()
        val elements = mutableListOf<UIElement>()
        walkTree(rootNode, elements, depth = 0, parentDesc = "")
        return elements
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        elements: MutableList<UIElement>,
        depth: Int,
        parentDesc: String
    ) {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            val displayText = text.ifEmpty { contentDesc }

            val isInteractive = node.isClickable || node.isLongClickable ||
                node.isEditable || node.isScrollable || node.isFocusable

            if (isInteractive || displayText.isNotEmpty()) {
                val centerX = (rect.left + rect.right) / 2
                val centerY = (rect.top + rect.bottom) / 2
                val width = rect.width()
                val height = rect.height()

                val action = when {
                    node.isEditable -> "type"
                    node.isScrollable -> "scroll"
                    node.isLongClickable -> "longpress"
                    node.isClickable -> "tap"
                    else -> "read"
                }

                elements.add(
                    UIElement(
                        id = viewId,
                        text = displayText,
                        type = className.substringAfterLast("."),
                        bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                        center = listOf(centerX, centerY),
                        size = listOf(width, height),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        enabled = node.isEnabled,
                        checked = node.isChecked,
                        focused = node.isFocused,
                        selected = node.isSelected,
                        scrollable = node.isScrollable,
                        longClickable = node.isLongClickable,
                        password = node.isPassword,
                        hint = node.hintText?.toString() ?: "",
                        action = action,
                        parent = parentDesc,
                        depth = depth
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    walkTree(child, elements, depth + 1, className)
                } finally {
                    child.recycle()
                }
            }
        } catch (_: Exception) {
            // Node may have been recycled during traversal
        }
    }

    fun computeScreenHash(elements: List<UIElement>): String {
        val digest = MessageDigest.getInstance("MD5")
        for (el in elements) {
            digest.update("${el.id}|${el.text}|${el.center}".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(12)
    }
}
```

**Step 3: Create DroidClawAccessibilityService.kt**

```kotlin
package com.thisux.droidclaw.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.thisux.droidclaw.model.UIElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class DroidClawAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DroidClawA11y"
        val isRunning = MutableStateFlow(false)
        val lastScreenTree = MutableStateFlow<List<UIElement>>(emptyList())
        var instance: DroidClawAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        instance = this
        isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We capture on-demand via getScreenTree(), not on every event
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
        instance = null
        isRunning.value = false
    }

    /**
     * Capture current screen tree with retry for null rootInActiveWindow.
     * Returns empty list if root is still null after retries (server uses vision fallback).
     */
    fun getScreenTree(): List<UIElement> {
        val delays = longArrayOf(50, 100, 200)
        for (delayMs in delays) {
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    val elements = ScreenTreeBuilder.capture(root)
                    lastScreenTree.value = elements
                    return elements
                } finally {
                    root.recycle()
                }
            }
            runBlocking { delay(delayMs) }
        }
        Log.w(TAG, "rootInActiveWindow null after retries")
        return emptyList()
    }

    /**
     * Find node closest to given coordinates.
     */
    fun findNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeAtRecursive(root, x, y)
    }

    private fun findNodeAtRecursive(
        node: AccessibilityNodeInfo,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        if (!rect.contains(x, y)) {
            node.recycle()
            return null
        }

        // Check children (deeper = more specific)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeAtRecursive(child, x, y)
            if (found != null) {
                node.recycle()
                return found
            }
        }

        // This node contains the point and no child does
        return if (node.isClickable || node.isLongClickable || node.isEditable) {
            node
        } else {
            node.recycle()
            null
        }
    }
}
```

**Step 4: Add service declaration + permissions to AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".DroidClawApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.DroidClaw">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.DroidClaw">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".accessibility.DroidClawAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_config" />
        </service>

        <service
            android:name=".connection.ConnectionService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />

    </application>
</manifest>
```

**Step 5: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/accessibility/ android/app/src/main/res/xml/accessibility_config.xml android/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add AccessibilityService and ScreenTreeBuilder"
```

---

### Task 5: GestureExecutor

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/accessibility/GestureExecutor.kt`

**Step 1: Create GestureExecutor.kt**

This implements the node-first strategy: try `performAction()` on accessibility nodes first, fall back to `dispatchGesture()` with coordinates.

```kotlin
package com.thisux.droidclaw.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.thisux.droidclaw.model.ServerMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ActionResult(val success: Boolean, val error: String? = null, val data: String? = null)

class GestureExecutor(private val service: DroidClawAccessibilityService) {

    companion object {
        private const val TAG = "GestureExecutor"
    }

    suspend fun execute(msg: ServerMessage): ActionResult {
        return try {
            when (msg.type) {
                "tap" -> executeTap(msg.x ?: 0, msg.y ?: 0)
                "type" -> executeType(msg.text ?: "")
                "enter" -> executeEnter()
                "back" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "notifications" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "longpress" -> executeLongPress(msg.x ?: 0, msg.y ?: 0)
                "swipe" -> executeSwipe(
                    msg.x1 ?: 0, msg.y1 ?: 0,
                    msg.x2 ?: 0, msg.y2 ?: 0,
                    msg.duration ?: 300
                )
                "launch" -> executeLaunch(msg.packageName ?: "")
                "clear" -> executeClear()
                "clipboard_set" -> executeClipboardSet(msg.text ?: "")
                "clipboard_get" -> executeClipboardGet()
                "paste" -> executePaste()
                "open_url" -> executeOpenUrl(msg.url ?: "")
                "switch_app" -> executeLaunch(msg.packageName ?: "")
                "keyevent" -> executeKeyEvent(msg.code ?: 0)
                "open_settings" -> executeOpenSettings()
                "wait" -> executeWait(msg.duration ?: 1000)
                else -> ActionResult(false, "Unknown action: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action ${msg.type} failed", e)
            ActionResult(false, e.message)
        }
    }

    private suspend fun executeTap(x: Int, y: Int): ActionResult {
        // Try node-first
        val node = service.findNodeAt(x, y)
        if (node != null) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return ActionResult(true)
                }
            } finally {
                node.recycle()
            }
        }
        // Fallback to gesture
        return dispatchTapGesture(x, y)
    }

    private suspend fun executeType(text: String): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused editable node found")
    }

    private fun executeEnter(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                // Try IME action first
                if (focused.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        // Fallback: global key event
        return ActionResult(
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK).not(), // placeholder
            "Enter key fallback not available via accessibility"
        )
    }

    private fun executeGlobalAction(action: Int): ActionResult {
        val success = service.performGlobalAction(action)
        return ActionResult(success, if (!success) "Global action failed" else null)
    }

    private suspend fun executeLongPress(x: Int, y: Int): ActionResult {
        // Try node-first
        val node = service.findNodeAt(x, y)
        if (node != null) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return ActionResult(true)
                }
            } finally {
                node.recycle()
            }
        }
        // Fallback: gesture hold at point
        return dispatchSwipeGesture(x, y, x, y, 1000)
    }

    private suspend fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): ActionResult {
        return dispatchSwipeGesture(x1, y1, x2, y2, duration)
    }

    private fun executeLaunch(packageName: String): ActionResult {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(false, "Package not found: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return ActionResult(true)
    }

    private fun executeClear(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused editable node to clear")
    }

    private fun executeClipboardSet(text: String): ActionResult {
        val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("droidclaw", text)
        clipboard.setPrimaryClip(clip)
        return ActionResult(true)
    }

    private fun executeClipboardGet(): ActionResult {
        val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        return ActionResult(true, data = text)
    }

    private fun executePaste(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                if (focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused node to paste into")
    }

    private fun executeOpenUrl(url: String): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return ActionResult(true)
    }

    private fun executeKeyEvent(code: Int): ActionResult {
        // AccessibilityService doesn't have direct keyevent dispatch
        // Use instrumentation or shell command via Runtime
        return try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", code.toString()))
            ActionResult(true)
        } catch (e: Exception) {
            ActionResult(false, "keyevent failed: ${e.message}")
        }
    }

    private fun executeOpenSettings(): ActionResult {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return ActionResult(true)
    }

    private suspend fun executeWait(duration: Int): ActionResult {
        kotlinx.coroutines.delay(duration.toLong())
        return ActionResult(true)
    }

    // --- Gesture Helpers ---

    private suspend fun dispatchTapGesture(x: Int, y: Int): ActionResult {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    private suspend fun dispatchSwipeGesture(
        x1: Int, y1: Int, x2: Int, y2: Int, duration: Int
    ): ActionResult {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): ActionResult =
        suspendCancellableCoroutine { cont ->
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult(true))
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult(false, "Gesture cancelled"))
                    }
                },
                null
            )
        }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }
}
```

**Step 2: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/accessibility/GestureExecutor.kt
git commit -m "feat(android): add GestureExecutor with node-first strategy"
```

---

### Task 6: Screen Capture (MediaProjection)

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/capture/ScreenCaptureManager.kt`

**Step 1: Create ScreenCaptureManager.kt**

```kotlin
package com.thisux.droidclaw.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCapture"
        const val REQUEST_CODE = 1001
        val isAvailable = MutableStateFlow(false)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = DisplayMetrics.DENSITY_DEFAULT

    fun initialize(resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Scale down for capture
        val scale = 720f / screenWidth
        val captureWidth = 720
        val captureHeight = (screenHeight * scale).toInt()

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "DroidClaw",
            captureWidth, captureHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                release()
            }
        }, null)

        isAvailable.value = true
        Log.i(TAG, "Screen capture initialized: ${captureWidth}x${captureHeight}")
    }

    fun capture(): ByteArray? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop padding
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            if (cropped != bitmap) bitmap.recycle()

            // Check for secure window (all black)
            if (isBlackFrame(cropped)) {
                cropped.recycle()
                Log.w(TAG, "Detected FLAG_SECURE (black frame)")
                return null
            }

            // Compress to JPEG
            val stream = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            cropped.recycle()
            stream.toByteArray()
        } finally {
            image.close()
        }
    }

    private fun isBlackFrame(bitmap: Bitmap): Boolean {
        // Sample 4 corners + center
        val points = listOf(
            0 to 0,
            bitmap.width - 1 to 0,
            0 to bitmap.height - 1,
            bitmap.width - 1 to bitmap.height - 1,
            bitmap.width / 2 to bitmap.height / 2
        )
        return points.all { (x, y) -> bitmap.getPixel(x, y) == android.graphics.Color.BLACK }
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        isAvailable.value = false
    }
}
```

**Step 2: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/capture/
git commit -m "feat(android): add ScreenCaptureManager with MediaProjection"
```

---

### Task 7: ReliableWebSocket (Ktor)

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/connection/ReliableWebSocket.kt`

**Step 1: Create ReliableWebSocket.kt**

```kotlin
package com.thisux.droidclaw.connection

import android.util.Log
import com.thisux.droidclaw.model.AuthMessage
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.DeviceInfoMsg
import com.thisux.droidclaw.model.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReliableWebSocket(
    private val scope: CoroutineScope,
    private val onMessage: suspend (ServerMessage) -> Unit
) {
    companion object {
        private const val TAG = "ReliableWS"
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val outbound = Channel<String>(Channel.BUFFERED)
    private var connectionJob: Job? = null
    private var client: HttpClient? = null
    private var backoffMs = 1000L
    private var shouldReconnect = true

    var deviceId: String? = null
        private set

    fun connect(serverUrl: String, apiKey: String, deviceInfo: DeviceInfoMsg) {
        shouldReconnect = true
        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (shouldReconnect && isActive) {
                try {
                    _state.value = ConnectionState.Connecting
                    _errorMessage.value = null
                    connectOnce(serverUrl, apiKey, deviceInfo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed: ${e.message}")
                    _state.value = ConnectionState.Error
                    _errorMessage.value = e.message
                }
                if (shouldReconnect && isActive) {
                    Log.i(TAG, "Reconnecting in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    private suspend fun connectOnce(serverUrl: String, apiKey: String, deviceInfo: DeviceInfoMsg) {
        val httpClient = HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 30_000
            }
        }
        client = httpClient

        // Convert wss:// to proper URL path
        val wsUrl = serverUrl.trimEnd('/') + "/ws/device"

        httpClient.webSocket(wsUrl) {
            // Auth handshake
            val authMsg = AuthMessage(apiKey = apiKey, deviceInfo = deviceInfo)
            send(Frame.Text(json.encodeToString(authMsg)))
            Log.i(TAG, "Sent auth message")

            // Wait for auth response
            val authFrame = incoming.receive() as? Frame.Text
                ?: throw Exception("Expected text frame for auth response")

            val authResponse = json.decodeFromString<ServerMessage>(authFrame.readText())
            when (authResponse.type) {
                "auth_ok" -> {
                    deviceId = authResponse.deviceId
                    _state.value = ConnectionState.Connected
                    _errorMessage.value = null
                    backoffMs = 1000L // Reset backoff on success
                    Log.i(TAG, "Authenticated, deviceId=$deviceId")
                }
                "auth_error" -> {
                    shouldReconnect = false // Don't retry auth errors
                    _state.value = ConnectionState.Error
                    _errorMessage.value = authResponse.message ?: "Authentication failed"
                    close()
                    return@webSocket
                }
                else -> {
                    throw Exception("Unexpected auth response: ${authResponse.type}")
                }
            }

            // Launch outbound sender
            val senderJob = launch {
                for (msg in outbound) {
                    send(Frame.Text(msg))
                }
            }

            // Read incoming messages
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val msg = json.decodeFromString<ServerMessage>(text)
                            onMessage(msg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse message: ${e.message}")
                        }
                    }
                }
            } finally {
                senderJob.cancel()
            }
        }

        httpClient.close()
        client = null
        _state.value = ConnectionState.Disconnected
    }

    fun send(message: String) {
        outbound.trySend(message)
    }

    inline fun <reified T> sendTyped(message: T) {
        send(json.encodeToString(message))
    }

    fun disconnect() {
        shouldReconnect = false
        connectionJob?.cancel()
        connectionJob = null
        client?.close()
        client = null
        _state.value = ConnectionState.Disconnected
        _errorMessage.value = null
        deviceId = null
    }
}
```

**Step 2: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/connection/ReliableWebSocket.kt
git commit -m "feat(android): add ReliableWebSocket with Ktor, reconnect, auth handshake"
```

---

### Task 8: CommandRouter

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/connection/CommandRouter.kt`

**Step 1: Create CommandRouter.kt**

```kotlin
package com.thisux.droidclaw.connection

import android.util.Base64
import android.util.Log
import com.thisux.droidclaw.accessibility.DroidClawAccessibilityService
import com.thisux.droidclaw.accessibility.GestureExecutor
import com.thisux.droidclaw.accessibility.ScreenTreeBuilder
import com.thisux.droidclaw.capture.ScreenCaptureManager
import com.thisux.droidclaw.model.AgentStep
import com.thisux.droidclaw.model.GoalStatus
import com.thisux.droidclaw.model.PongMessage
import com.thisux.droidclaw.model.ResultResponse
import com.thisux.droidclaw.model.ScreenResponse
import com.thisux.droidclaw.model.ServerMessage
import kotlinx.coroutines.flow.MutableStateFlow

class CommandRouter(
    private val webSocket: ReliableWebSocket,
    private val captureManager: ScreenCaptureManager?
) {
    companion object {
        private const val TAG = "CommandRouter"
    }

    val currentGoalStatus = MutableStateFlow(GoalStatus.Idle)
    val currentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val currentGoal = MutableStateFlow("")
    val currentSessionId = MutableStateFlow<String?>(null)

    private var gestureExecutor: GestureExecutor? = null

    fun updateGestureExecutor() {
        val svc = DroidClawAccessibilityService.instance
        gestureExecutor = if (svc != null) GestureExecutor(svc) else null
    }

    suspend fun handleMessage(msg: ServerMessage) {
        Log.d(TAG, "Handling: ${msg.type}")

        when (msg.type) {
            "get_screen" -> handleGetScreen(msg.requestId!!)
            "ping" -> webSocket.sendTyped(PongMessage())

            // Action commands — all have requestId
            "tap", "type", "enter", "back", "home", "notifications",
            "longpress", "swipe", "launch", "clear", "clipboard_set",
            "clipboard_get", "paste", "open_url", "switch_app",
            "keyevent", "open_settings", "wait" -> handleAction(msg)

            // Goal lifecycle
            "goal_started" -> {
                currentSessionId.value = msg.sessionId
                currentGoal.value = msg.goal ?: ""
                currentGoalStatus.value = GoalStatus.Running
                currentSteps.value = emptyList()
                Log.i(TAG, "Goal started: ${msg.goal}")
            }
            "step" -> {
                val step = AgentStep(
                    step = msg.step ?: 0,
                    action = msg.action?.toString() ?: "",
                    reasoning = msg.reasoning ?: ""
                )
                currentSteps.value = currentSteps.value + step
                Log.d(TAG, "Step ${step.step}: ${step.reasoning}")
            }
            "goal_completed" -> {
                currentGoalStatus.value = if (msg.success == true) GoalStatus.Completed else GoalStatus.Failed
                Log.i(TAG, "Goal completed: success=${msg.success}, steps=${msg.stepsUsed}")
            }

            else -> Log.w(TAG, "Unknown message type: ${msg.type}")
        }
    }

    private fun handleGetScreen(requestId: String) {
        updateGestureExecutor()
        val svc = DroidClawAccessibilityService.instance
        val elements = svc?.getScreenTree() ?: emptyList()
        val packageName = try {
            svc?.rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) { null }

        // Optionally include screenshot
        var screenshot: String? = null
        if (elements.isEmpty()) {
            // Vision fallback: capture screenshot
            val bytes = captureManager?.capture()
            if (bytes != null) {
                screenshot = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }

        val response = ScreenResponse(
            requestId = requestId,
            elements = elements,
            screenshot = screenshot,
            packageName = packageName
        )
        webSocket.sendTyped(response)
    }

    private suspend fun handleAction(msg: ServerMessage) {
        updateGestureExecutor()
        val executor = gestureExecutor
        if (executor == null) {
            webSocket.sendTyped(
                ResultResponse(
                    requestId = msg.requestId!!,
                    success = false,
                    error = "Accessibility service not running"
                )
            )
            return
        }

        val result = executor.execute(msg)
        webSocket.sendTyped(
            ResultResponse(
                requestId = msg.requestId!!,
                success = result.success,
                error = result.error,
                data = result.data
            )
        )
    }

    fun reset() {
        currentGoalStatus.value = GoalStatus.Idle
        currentSteps.value = emptyList()
        currentGoal.value = ""
        currentSessionId.value = null
    }
}
```

**Step 2: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/connection/CommandRouter.kt
git commit -m "feat(android): add CommandRouter for dispatching server commands"
```

---

### Task 9: ConnectionService (Foreground Service)

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/connection/ConnectionService.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/util/DeviceInfo.kt`

**Step 1: Create DeviceInfo.kt**

```kotlin
package com.thisux.droidclaw.util

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.thisux.droidclaw.model.DeviceInfoMsg

object DeviceInfoHelper {
    fun get(context: Context): DeviceInfoMsg {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return DeviceInfoMsg(
            model = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
    }
}
```

**Step 2: Create ConnectionService.kt**

```kotlin
package com.thisux.droidclaw.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.thisux.droidclaw.DroidClawApp
import com.thisux.droidclaw.MainActivity
import com.thisux.droidclaw.R
import com.thisux.droidclaw.capture.ScreenCaptureManager
import com.thisux.droidclaw.model.ConnectionState
import com.thisux.droidclaw.model.GoalMessage
import com.thisux.droidclaw.model.GoalStatus
import com.thisux.droidclaw.model.AgentStep
import com.thisux.droidclaw.util.DeviceInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConnectionService : LifecycleService() {

    companion object {
        private const val TAG = "ConnectionSvc"
        private const val CHANNEL_ID = "droidclaw_connection"
        private const val NOTIFICATION_ID = 1

        val connectionState = MutableStateFlow(ConnectionState.Disconnected)
        val currentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
        val currentGoalStatus = MutableStateFlow(GoalStatus.Idle)
        val currentGoal = MutableStateFlow("")
        val errorMessage = MutableStateFlow<String?>(null)
        var instance: ConnectionService? = null

        const val ACTION_CONNECT = "com.thisux.droidclaw.CONNECT"
        const val ACTION_DISCONNECT = "com.thisux.droidclaw.DISCONNECT"
        const val ACTION_SEND_GOAL = "com.thisux.droidclaw.SEND_GOAL"
        const val EXTRA_GOAL = "goal_text"
    }

    private var webSocket: ReliableWebSocket? = null
    private var commandRouter: CommandRouter? = null
    private var captureManager: ScreenCaptureManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_CONNECT -> {
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                connect()
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
            ACTION_SEND_GOAL -> {
                val goal = intent.getStringExtra(EXTRA_GOAL) ?: return START_NOT_STICKY
                sendGoal(goal)
            }
        }

        return START_NOT_STICKY
    }

    private fun connect() {
        lifecycleScope.launch {
            val app = application as DroidClawApp
            val apiKey = app.settingsStore.apiKey.first()
            val serverUrl = app.settingsStore.serverUrl.first()

            if (apiKey.isBlank() || serverUrl.isBlank()) {
                connectionState.value = ConnectionState.Error
                errorMessage.value = "API key or server URL not configured"
                stopSelf()
                return@launch
            }

            captureManager = ScreenCaptureManager(this@ConnectionService)

            val ws = ReliableWebSocket(lifecycleScope) { msg ->
                commandRouter?.handleMessage(msg)
            }
            webSocket = ws

            val router = CommandRouter(ws, captureManager)
            commandRouter = router

            // Forward state
            launch {
                ws.state.collect { state ->
                    connectionState.value = state
                    updateNotification(
                        when (state) {
                            ConnectionState.Connected -> "Connected to server"
                            ConnectionState.Connecting -> "Connecting..."
                            ConnectionState.Error -> "Connection error"
                            ConnectionState.Disconnected -> "Disconnected"
                        }
                    )
                }
            }
            launch {
                ws.errorMessage.collect { errorMessage.value = it }
            }
            launch {
                router.currentSteps.collect { currentSteps.value = it }
            }
            launch {
                router.currentGoalStatus.collect { currentGoalStatus.value = it }
            }
            launch {
                router.currentGoal.collect { currentGoal.value = it }
            }

            // Acquire wake lock during active connection
            acquireWakeLock()

            val deviceInfo = DeviceInfoHelper.get(this@ConnectionService)
            ws.connect(serverUrl, apiKey, deviceInfo)
        }
    }

    private fun sendGoal(text: String) {
        webSocket?.sendTyped(GoalMessage(text = text))
    }

    private fun disconnect() {
        webSocket?.disconnect()
        webSocket = null
        commandRouter?.reset()
        commandRouter = null
        captureManager?.release()
        captureManager = null
        releaseWakeLock()
        connectionState.value = ConnectionState.Disconnected
    }

    override fun onDestroy() {
        disconnect()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DroidClaw Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when DroidClaw is connected to the server"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ConnectionService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DroidClaw")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Disconnect", disconnectIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // --- Wake Lock ---

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DroidClaw::ConnectionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
```

**Step 3: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/connection/ConnectionService.kt android/app/src/main/java/com/thisux/droidclaw/util/DeviceInfo.kt
git commit -m "feat(android): add ConnectionService foreground service and DeviceInfo helper"
```

---

### Task 10: UI — Navigation + HomeScreen

**Files:**
- Modify: `android/app/src/main/java/com/thisux/droidclaw/MainActivity.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/ui/screens/HomeScreen.kt`

**Step 1: Create HomeScreen.kt**

```kotlin
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

        // Current goal
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
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
```

**Step 2: Rewrite MainActivity.kt with bottom nav**

```kotlin
package com.thisux.droidclaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thisux.droidclaw.ui.screens.HomeScreen
import com.thisux.droidclaw.ui.screens.LogsScreen
import com.thisux.droidclaw.ui.screens.SettingsScreen
import com.thisux.droidclaw.ui.theme.DroidClawTheme

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "Home")
    data object Settings : Screen("settings", "Settings")
    data object Logs : Screen("logs", "Logs")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidClawTheme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Settings, Screen.Logs)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                when (screen) {
                                    is Screen.Home -> Icons.Filled.Home
                                    is Screen.Settings -> Icons.Filled.Settings
                                    is Screen.Logs -> Icons.Filled.History
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Logs.route) { LogsScreen() }
        }
    }
}
```

**Step 3: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/ui/screens/HomeScreen.kt android/app/src/main/java/com/thisux/droidclaw/MainActivity.kt
git commit -m "feat(android): add HomeScreen with goal input and bottom nav"
```

---

### Task 11: UI — SettingsScreen

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/ui/screens/SettingsScreen.kt`
- Create: `android/app/src/main/java/com/thisux/droidclaw/util/BatteryOptimization.kt`

**Step 1: Create BatteryOptimization.kt**

```kotlin
package com.thisux.droidclaw.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestExemption(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
```

**Step 2: Create SettingsScreen.kt**

```kotlin
package com.thisux.droidclaw.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thisux.droidclaw.DroidClawApp
import com.thisux.droidclaw.accessibility.DroidClawAccessibilityService
import com.thisux.droidclaw.capture.ScreenCaptureManager
import com.thisux.droidclaw.util.BatteryOptimization
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DroidClawApp
    val scope = rememberCoroutineScope()

    val apiKey by app.settingsStore.apiKey.collectAsState(initial = "")
    val serverUrl by app.settingsStore.serverUrl.collectAsState(initial = "wss://localhost:8080")

    var editingApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editingServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    val isAccessibilityEnabled by DroidClawAccessibilityService.isRunning.collectAsState()
    val isCaptureAvailable by ScreenCaptureManager.isAvailable.collectAsState()
    val isBatteryExempt = remember { BatteryOptimization.isIgnoringBatteryOptimizations(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // API Key
        OutlinedTextField(
            value = editingApiKey,
            onValueChange = { editingApiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        if (editingApiKey != apiKey) {
            OutlinedButton(
                onClick = { scope.launch { app.settingsStore.setApiKey(editingApiKey) } }
            ) {
                Text("Save API Key")
            }
        }

        // Server URL
        OutlinedTextField(
            value = editingServerUrl,
            onValueChange = { editingServerUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (editingServerUrl != serverUrl) {
            OutlinedButton(
                onClick = { scope.launch { app.settingsStore.setServerUrl(editingServerUrl) } }
            ) {
                Text("Save Server URL")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Setup Checklist
        Text("Setup Checklist", style = MaterialTheme.typography.titleMedium)

        ChecklistItem(
            label = "API key configured",
            isOk = apiKey.isNotBlank(),
            actionLabel = null,
            onAction = {}
        )

        ChecklistItem(
            label = "Accessibility service",
            isOk = isAccessibilityEnabled,
            actionLabel = "Enable",
            onAction = { BatteryOptimization.openAccessibilitySettings(context) }
        )

        ChecklistItem(
            label = "Screen capture permission",
            isOk = isCaptureAvailable,
            actionLabel = null,
            onAction = {}
        )

        ChecklistItem(
            label = "Battery optimization disabled",
            isOk = isBatteryExempt,
            actionLabel = "Disable",
            onAction = { BatteryOptimization.requestExemption(context) }
        )
    }
}

@Composable
private fun ChecklistItem(
    label: String,
    isOk: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = if (isOk) "OK" else "Missing",
                    tint = if (isOk) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
                Text(label)
            }
            if (!isOk && actionLabel != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
```

**Step 3: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/ui/screens/SettingsScreen.kt android/app/src/main/java/com/thisux/droidclaw/util/BatteryOptimization.kt
git commit -m "feat(android): add SettingsScreen with checklist and BatteryOptimization util"
```

---

### Task 12: UI — LogsScreen

**Files:**
- Create: `android/app/src/main/java/com/thisux/droidclaw/ui/screens/LogsScreen.kt`

**Step 1: Create LogsScreen.kt**

```kotlin
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
```

**Step 2: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/thisux/droidclaw/ui/screens/LogsScreen.kt
git commit -m "feat(android): add LogsScreen with expandable step cards"
```

---

### Task 13: Final Integration & Build Verification

**Files:**
- All previously created files

**Step 1: Full clean build**

Run: `cd android && ./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL with 0 errors

**Step 2: Verify APK exists**

Run: `ls -la android/app/build/outputs/apk/debug/app-debug.apk`
Expected: File exists

**Step 3: Commit all remaining changes**

```bash
cd android && git add -A && git status
git commit -m "feat(android): complete DroidClaw v1 companion app

- Accessibility service with ScreenTreeBuilder and GestureExecutor
- Ktor WebSocket with reliable reconnection and auth handshake
- Foreground ConnectionService with notification
- MediaProjection screen capture with vision fallback
- DataStore settings for API key, server URL
- Compose UI with bottom nav (Home, Settings, Logs)
- Home: connection status, goal input, live step log
- Settings: API key, server URL, setup checklist
- Logs: expandable step cards with reasoning"
```

---

## Execution Notes

**Build requirement:** The Android project requires Android Studio or at minimum the Android SDK with API 36 installed. Gradle commands run via `./gradlew` wrapper in the `android/` directory.

**Testing on device:** After building, install via `adb install android/app/build/outputs/apk/debug/app-debug.apk`. Then:
1. Open Settings > Accessibility > DroidClaw and enable the service
2. Open the app > Settings tab > enter API key and server URL
3. Go to Home tab > tap Connect
4. Enter a goal and tap Run

**Not covered in v1 (future work):**
- Persistent session history (LogsScreen clears on restart)
- MediaProjection consent flow wired through UI (currently needs manual setup)
- Auto-connect on boot
- OEM-specific battery guidance (dontkillmyapp.com links)
