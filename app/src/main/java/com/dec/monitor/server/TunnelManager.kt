package com.dec.monitor.server

import android.content.Context
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

object TunnelManager {

    private var tunnelProcess: Process? = null
    var publicUrl: String? = null

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        try {
            // download cloudflared binary if not present
            val binary = File(context.filesDir, "cloudflared")
            if (!binary.exists()) {
                downloadCloudflared(context, binary)
            }
            binary.setExecutable(true)

            // start tunnel
            val process = ProcessBuilder(
                binary.absolutePath,
                "tunnel", "--url", "http://localhost:7474",
                "--no-autoupdate"
            ).redirectErrorStream(true).start()

            tunnelProcess = process

            // read output to find URL
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains("trycloudflare.com") || l.contains("https://")) {
                    val urlRegex = Regex("https://[a-zA-Z0-9\\-]+\\.trycloudflare\\.com")
                    val match = urlRegex.find(l)
                    if (match != null) {
                        publicUrl = match.value
                        TelegramBot.sendMessage(
                            """
🔗 <b>Dec Monitor Online</b>
Device: ${android.os.Build.MODEL}

<b>Links:</b>
📱 Live Stream: $publicUrl/stream
📸 Screenshot: $publicUrl/screenshot
🔋 Battery: $publicUrl/battery
🤖 MCP Status: $publicUrl/mcp/status

<b>Add to Claude:</b>
URL: <code>$publicUrl</code>

<b>WebSocket:</b>
<code>${publicUrl?.replace("https://", "wss://")}/ws</code>
                            """.trimIndent()
                        )
                        break
                    }
                }
            }
        } catch (e: Exception) {
            TelegramBot.sendMessage("❌ Tunnel failed: ${e.message}")
        }
    }

    private fun downloadCloudflared(context: Context, dest: File) {
        // ARM64 binary for Android
        val url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
        val connection = URL(url).openConnection()
        connection.connect()
        FileOutputStream(dest).use { out ->
            connection.getInputStream().copyTo(out)
        }
    }

    fun stop() {
        tunnelProcess?.destroy()
        tunnelProcess = null
        publicUrl = null
    }
}
