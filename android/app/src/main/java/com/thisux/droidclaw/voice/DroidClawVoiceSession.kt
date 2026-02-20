package com.thisux.droidclaw.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.thisux.droidclaw.connection.ConnectionService

class DroidClawVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, ConnectionService::class.java).apply {
            action = ConnectionService.ACTION_SHOW_COMMAND_PANEL
        }
        context.startService(intent)
        hide()
    }
}
