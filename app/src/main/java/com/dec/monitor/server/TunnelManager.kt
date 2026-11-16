package com.dec.monitor.server

import android.content.Context
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object TunnelManager {

    var publicUrl: String? = null
    private var tunnelProcess: Process? = null

    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        val binary = File(context.filesDir, "cloudflared")

        if (!binary.exists()) {
            val downloaded = downloadBinary(context, binary)
            if (!downloaded) {
                TelegramBot.sendMessage("❌ Cloudflared download failed — check internet")
                return@withContext
            }
        }

        binary.setExecutable(true, false)

        // verify executable
        try {
            val testProcess = ProcessBuilder(binary.absolutePath, "version")
                .redirectErrorStream(true)
                .start()
            val output = testProcess.inputStream.bufferedReader().readText()
            testProcess.waitFor()
            if (!output.contains("cloudflare")) {
                // wrong arch — try other arch
                binary.delete()
                val downloaded = downloadBinary(context, binary, fallback = true)
                if (!downloaded) {
                    TelegramBot.sendMessage("❌ Binary arch mismatch — try manually")
                    return@withContext
                }
                binary.setExecutable(true, false)
            }
        } catch (e: Exception) { }

        startTunnel(binary)
    }

    private fun startTunnel(binary: File) {
        try {
            val process = ProcessBuilder(
                binary.absolutePath,
                "tunnel",
                "--url", "http://localhost:7474",
                "--no-autoupdate",
                "--logLevel", "info"
            ).redirectErrorStream(true).start()

            tunnelProcess = process

            // read output in background thread
            Thread {
                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    // cloudflare prints URL in log
                    if (l.contains("trycloudflare.com")) {
                        val urlRegex = Regex("https://[a-zA-Z0-9\\-]+\\.trycloudflare\\.com")
                        val match = urlRegex.find(l)
                        if (match != null && publicUrl == null) {
                            publicUrl = match.value
                            kotlinx.coroutines.runBlocking {
                                TelegramBot.sendMessage(
                                    """
🔗 <b>Dec Monitor Online</b>
Device: ${android.os.Build.MODEL}

📸 Screenshot: ${match.value}/screenshot
🔴 Live Stream: ${match.value}/stream
🔋 Battery: ${match.value}/battery
🤖 MCP Status: ${match.value}/mcp/status
🖼 MCP Screenshot: ${match.value}/mcp/screenshot

<b>Claude Custom Connector URL:</b>
<code>${match.value}</code>

<b>WebSocket:</b>
<code>${match.value.replace("https://", "wss://")}/ws</code>
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                }
            }.start()

        } catch (e: Exception) {
            kotlinx.coroutines.runBlocking {
                TelegramBot.sendMessage("❌ Tunnel start failed: ${e.message}")
            }
        }
    }

    private fun downloadBinary(context: Context, dest: File, fallback: Boolean = false): Boolean {
        // detect ABI
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        val url = when {
            fallback -> when {
                abi.contains("arm64") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
                abi.contains("arm") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm"
                abi.contains("x86_64") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
                else -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
            }
            else -> when {
                abi.contains("arm64") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
                abi.contains("arm") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm"
                abi.contains("x86_64") -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
                else -> "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
            }
        }

        return try {
            TelegramBot.sendMessage("⬇️ Downloading cloudflared ($abi)...")
            val connection = URL(url).openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            connection.connect()
            FileOutputStream(dest).use { out ->
                connection.getInputStream().copyTo(out)
            }
            dest.exists() && dest.length() > 1000
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        tunnelProcess?.destroy()
        tunnelProcess = null
        publicUrl = null
    }
}
