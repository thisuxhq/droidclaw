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

            captureManager = ScreenCaptureManager(this@ConnectionService).also { mgr ->
                if (ScreenCaptureManager.hasConsent()) {
                    try {
                        mgr.initialize(
                            ScreenCaptureManager.consentResultCode!!,
                            ScreenCaptureManager.consentData!!
                        )
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Screen capture unavailable (needs mediaProjection service type): ${e.message}")
                    }
                }
            }

            val ws = ReliableWebSocket(lifecycleScope) { msg ->
                commandRouter?.handleMessage(msg)
            }
            webSocket = ws

            val router = CommandRouter(ws, captureManager)
            commandRouter = router

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
            launch { ws.errorMessage.collect { errorMessage.value = it } }
            launch { router.currentSteps.collect { currentSteps.value = it } }
            launch { router.currentGoalStatus.collect { currentGoalStatus.value = it } }
            launch { router.currentGoal.collect { currentGoal.value = it } }

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

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DroidClaw::ConnectionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
