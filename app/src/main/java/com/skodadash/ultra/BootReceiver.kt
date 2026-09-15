package com.skodadash.ultra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                val settings = SettingsRepository(context)
                val enabled = runBlocking { settings.getAutoTripEnabled() }
                if (enabled) {
                    val serviceIntent = Intent(context, AutoTripService::class.java).apply { action = AutoTripService.ACTION_START }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
