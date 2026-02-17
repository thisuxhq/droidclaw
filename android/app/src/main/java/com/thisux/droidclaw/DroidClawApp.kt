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
