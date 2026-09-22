package com.skodadash.ultra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
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
