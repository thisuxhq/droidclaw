package com.thisux.droidclaw.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.thisux.droidclaw.model.DeviceInfoMsg

object DeviceInfoHelper {
    fun get(context: Context): DeviceInfoMsg {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        return DeviceInfoMsg(
            model = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            androidVersion = android.os.Build.VERSION.RELEASE,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            batteryLevel = batteryPct,
            isCharging = plugged != 0
        )
    }

    fun getBattery(context: Context): Pair<Int, Boolean> {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return Pair(batteryPct, plugged != 0)
    }
}
