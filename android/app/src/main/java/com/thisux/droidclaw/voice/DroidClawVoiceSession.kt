package com.thisux.droidclaw.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thisux.droidclaw.connection.ConnectionService
import com.thisux.droidclaw.ui.theme.DroidClawTheme

class DroidClawVoiceSession(context: Context) : VoiceInteractionSession(context) {

    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val savedStateOwner = object : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performRestore(null) }
    }

    override fun onCreateContentView(): View {
        lifecycleOwner.registry.currentState = Lifecycle.State.CREATED

        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                DroidClawTheme {
                    GoalInputSheet(
                        onSubmit = { goal -> submitGoal(goal) },
                        onDismiss = { hide() }
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onHide() {
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
        lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun submitGoal(goal: String) {
        val intent = Intent(context, ConnectionService::class.java).apply {
            action = ConnectionService.ACTION_SEND_GOAL
            putExtra(ConnectionService.EXTRA_GOAL, goal)
        }
        context.startService(intent)
        hide()
    }
}
