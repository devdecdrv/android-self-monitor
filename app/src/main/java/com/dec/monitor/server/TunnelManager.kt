package com.dec.monitor.server

import android.content.Context
import android.net.wifi.WifiManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TunnelManager {

    private val client = OkHttpClient()
    var publicUrl: String? = null

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        val localIp = getLocalIp(context)
        val localUrl = "http://$localIp:7474"

        var tunnelUrl: String? = null

        try {
            val body = """{"local_port":7474}""".toRequestBody("application/json".toMediaType())
            val response = client.newCall(
                Request.Builder()
                    .url("https://api.tunnelto.dev/expose")
                    .post(body)
                    .build()
            ).execute()
            val responseBody = response.body?.string()
            if (!responseBody.isNullOrEmpty()) {
                tunnelUrl = JSONObject(responseBody).optString("tunnel_url").ifEmpty { null }
            }
        } catch (e: Exception) { }

        publicUrl = tunnelUrl ?: localUrl

        TelegramBot.sendMessage(
            """
🔗 <b>Dec Monitor Server Online</b>
Device: ${android.os.Build.MODEL}

📡 <b>Local Network (same WiFi):</b>
📸 <code>$localUrl/screenshot</code>
🔴 <code>$localUrl/stream</code>
🔋 <code>$localUrl/battery</code>
🤖 <code>$localUrl/mcp/status</code>

${if (tunnelUrl != null) "🌐 <b>Public:</b>\n<code>$tunnelUrl/stream</code>" else "⚠️ Public tunnel unavailable — use local IP on same WiFi"}
            """.trimIndent()
        )
    }

    private fun getLocalIp(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) { "localhost" }
    }

    fun stop() {
        publicUrl = null
    }
}
