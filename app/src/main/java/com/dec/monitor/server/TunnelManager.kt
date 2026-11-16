package com.dec.monitor.server

import android.content.Context
import android.net.wifi.WifiManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object TunnelManager {

    private val client = OkHttpClient()
    var publicUrl: String? = null

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        val localIp = getLocalIp(context)
        val localUrl = "http://$localIp:7474"

        // try to get public URL via serveo.net SSH tunnel concept
        // since we can't run binaries, try a free HTTP tunnel API
        var tunnelUrl: String? = null

        try {
            val response = client.newCall(
                Request.Builder()
                    .url("https://api.tunnelto.dev/expose")
                    .post(
                        okhttp3.RequestBody.create(
                            okhttp3.MediaType.parse("application/json"),
                            """{"local_port":7474}"""
                        )
                    )
                    .build()
            ).execute()
            val body = response.body()?.string()
            if (body != null) {
                tunnelUrl = JSONObject(body).optString("tunnel_url")
            }
        } catch (e: Exception) { }

        publicUrl = tunnelUrl ?: localUrl

        val streamUrl = "${publicUrl}/stream"
        val ssUrl = "${publicUrl}/screenshot"

        TelegramBot.sendMessage(
            """
🔗 <b>Dec Monitor Server Online</b>
Device: ${android.os.Build.MODEL}

📡 <b>Local Network:</b>
📸 Screenshot: <code>$localUrl/screenshot</code>
🔴 Stream: <code>$localUrl/stream</code>
🔋 Battery: <code>$localUrl/battery</code>
🤖 MCP: <code>$localUrl/mcp/status</code>

${if (tunnelUrl != null) "🌐 <b>Public URL:</b>\n<code>$tunnelUrl</code>\n\n📸 $tunnelUrl/screenshot\n🔴 $tunnelUrl/stream" else "⚠️ Public tunnel unavailable — use local IP on same WiFi"}

<b>Live View (same WiFi):</b>
Open in browser → <code>$localUrl/stream</code>
            """.trimIndent()
        )
    }

    private fun getLocalIp(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            "localhost"
        }
    }

    fun stop() {
        publicUrl = null
    }
}
