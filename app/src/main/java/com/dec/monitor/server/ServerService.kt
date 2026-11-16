package com.dec.monitor.server

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ServerService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(5, buildNotification())

        CoroutineScope(Dispatchers.IO).launch {
            PhoneServer.start(applicationContext)
            TunnelManager.start(applicationContext)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        PhoneServer.stop()
        TunnelManager.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "server"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Server", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Service")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
