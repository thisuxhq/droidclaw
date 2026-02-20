package com.thisux.droidclaw.voice

import android.content.Intent
import android.speech.RecognitionService

class DroidClawRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {}
    override fun onCancel(callback: Callback?) {}
    override fun onStopListening(callback: Callback?) {}
}
