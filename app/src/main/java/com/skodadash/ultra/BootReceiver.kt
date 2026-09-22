package com.skodadash.ultra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val receivedAction = intent.action ?: return
        if (receivedAction == Intent.ACTION_BOOT_COMPLETED ||
            receivedAction == "android.intent.action.QUICKBOOT_POWERON" ||
            receivedAction == Intent.ACTION_MY_PACKAGE_REPLACED ||
            receivedAction == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            try {
                val settings = SettingsRepository(context)
                val enabled = runBlocking { settings.getAutoTripEnabled() }
                if (enabled) {
                    val serviceIntent = Intent(context, AutoTripService::class.java)
                    serviceIntent.action = AutoTripService.ACTION_START
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
