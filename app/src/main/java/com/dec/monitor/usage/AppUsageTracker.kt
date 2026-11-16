package com.dec.monitor.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object AppUsageTracker {

    suspend fun getDailyReport(context: Context) = withContext(Dispatchers.IO) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.HOURS.toMillis(24)

            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime, endTime
            )

            if (stats.isNullOrEmpty()) {
                TelegramBot.sendMessage("📊 No usage data — enable Usage Access in Settings → Dec Monitor")
                return@withContext
            }

            val sorted = stats
                .filter { it.totalTimeInForeground > 60_000 } // more than 1 min
                .sortedByDescending { it.totalTimeInForeground }
                .take(15)

            val sb = StringBuilder("📊 <b>App Usage — Last 24h</b>\n\n")
            for (stat in sorted) {
                val mins = TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground)
                val hours = mins / 60
                val remainMins = mins % 60
                val timeStr = if (hours > 0) "${hours}h ${remainMins}m" else "${remainMins}m"
                val appName = try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: Exception) { stat.packageName }
                sb.appendLine("$appName — $timeStr")
            }

            TelegramBot.sendMessage(sb.toString())
        } catch (e: Exception) {
            TelegramBot.sendMessage("📊 Usage report error: ${e.message}")
        }
    }
}
