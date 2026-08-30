package com.example.spendtrack

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.regex.Pattern

class KotakNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val allowed = setOf("com.kotak811")

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowed) return
        val n = sbn.notification
        val title = n.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val full = "$title\n$body".trim()

        val m = Pattern.compile("Sent\\s+Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(full)
        if (!m.find()) return
        val amount = m.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return
        
        val merchant = Regex("to\\s+(.+?)\\s+on\\s+\\d{2}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE)
            .find(full)?.groupValues?.get(1)?.trim() ?: "Kotak UPI"
            
        val hash = sha(full)
        val dao = AppDb.get(this).dao()

        scope.launch {
            if (dao.hasHash(hash)) return@launch
            val intent = Intent(this@KotakNotificationListener, ConfirmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("amount", amount)
                putExtra("merchant", merchant)
                putExtra("hash", hash)
                putExtra("RAW_TEXT", full)
            }
            startActivity(intent)
        }
    }

    private fun sha(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
