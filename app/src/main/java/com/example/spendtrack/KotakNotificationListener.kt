package com.example.spendtrack

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.regex.Pattern

class KotakNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val allowed = setOf("com.kotak811", "com.msf.kopl", "com.google.android.apps.messaging", "com.oneplus.mms", "com.android.mms", "com.samsung.android.messaging", "com.coloros.mms")

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg !in allowed) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val full = "$title $text".trim()

        // Matches "Sent Rs.11.00", "Sent Rs 11", "Debited INR 500", "Paid Rs 1,500.50", etc.
        val pattern = Pattern.compile(
            "(?:Sent|Debited|Paid|Spent)\\s*(?:Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(full)

        if (matcher.find()) {
            val amount = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return
            val hash = sha(full)
            val merchant = parseMerchant(full)
            val dao = AppDb.get(this).dao()

            scope.launch {
                // Deduplicate: avoid re-opening dialog if notification hash exists
                if (dao.hasHash(hash)) return@launch

                val intent = Intent(this@KotakNotificationListener, ConfirmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("amount", amount)
                    putExtra("merchant", merchant)
                    putExtra("hash", hash)
                    putExtra("RAW_TEXT", full)
                }
                
                startActivity(intent)
            }
        }
    }

    private fun parseMerchant(text: String): String {
        // Matches "to <Name> on DD-MM-YY"
        val specific = Regex("to\\s+(.+?)\\s+on\\s+\\d{2}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE).find(text)
        if (specific != null) return specific.groupValues[1].trim()

        // Fallback broad match
        val generic = Pattern.compile("(?:to|at|info)\\s+([A-Za-z0-9\\s]{2,25})", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (generic.find()) generic.group(1)?.trim() ?: "Kotak Debit" else "Kotak Debit"
    }

    private fun sha(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
