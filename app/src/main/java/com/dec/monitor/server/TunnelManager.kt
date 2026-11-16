package com.dec.monitor.server

import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TunnelManager {

    private val client = OkHttpClient()
    var publicUrl: String? = null

    // Uses localhost.run — no binary needed, pure SSH tunnel via HTTP API
    // Alternative: use serveo.net
    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            // Try localhost.run API
            val response = client.newCall(
                Request.Builder()
                    .url("https://localhost.run/api/tunnels")
                    .post(
                        JSONObject().apply {
                            put("port", 7474)
                        }.toString().toRequestBody("application/json".toMediaType())
                    )
                    .build()
            ).execute()

            val body = response.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val url = json.optString("url") ?: json.optJSONArray("tunnels")
                    ?.getJSONObject(0)?.optString("url")

                if (!url.isNullOrEmpty()) {
                    publicUrl = url
                    sendTunnelMessage(url)
                    return@withContext
                }
            }
        } catch (e: Exception) { }

        // fallback — try pinggy.io (no binary, websocket-based)
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("https://free.pinggy.io/api/tunnel?port=7474&type=http")
                    .get()
                    .build()
            ).execute()

            val body = response.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                val url = json.optString("url")
                if (!url.isNullOrEmpty()) {
                    publicUrl = url
                    sendTunnelMessage(url)
                    return@withContext
                }
            }
        } catch (e: Exception) { }

        // final fallback — tell user to use ngrok manually
        TelegramBot.sendMessage(
            """
⚠️ <b>Tunnel auto-start failed</b>
Server is running locally on port 7474.

To get public URL, install <b>ngrok</b> app on phone:
1. Install ngrok from Play Store
2. Run: ngrok http 7474
3. Copy the https link

Or use <b>Tailscale</b> for private access.
            """.trimIndent()
        )
    }

    private suspend fun sendTunnelMessage(url: String) {
        TelegramBot.sendMessage(
            """
🔗 <b>Dec Monitor Online</b>
Device: ${android.os.Build.MODEL}

📱 Live Stream: $url/stream
📸 Screenshot: $url/screenshot  
🔋 Battery: $url/battery
🤖 MCP: $url/mcp/status

🌐 WebSocket: ${url.replace("https://", "wss://")}/ws

<b>Add to Claude MCP:</b>
<code>$url</code>
            """.trimIndent()
        )
    }

    fun stop() {
        publicUrl = null
    }
}
