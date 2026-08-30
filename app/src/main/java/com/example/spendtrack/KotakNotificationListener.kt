package com.example.spendtrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class KotakNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val allowed = setOf(
        "com.kotak811",
        "com.msf.kopl",
        "com.google.android.apps.messaging",
        "com.oneplus.mms",
        "com.oplus.mms",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.coloros.mms"
    )

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

        val pattern = Pattern.compile(
            "(?:Sent|Debited|Paid|Spent)\\s*(?:Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(full)

        if (matcher.find()) {
            val amount = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return
            val hash = sha(full)
            val merchant = parseMerchant(full)
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val dao = AppDb.get(this).dao()

            scope.launch {
                if (dao.hasHash(hash)) return@launch

                val insertedId = dao.insert(
                    Expense(
                        amount = amount,
                        merchant = merchant,
                        category = "Other",
                        date = today,
                        source = "KOTAK",
                        notificationHash = hash
                    )
                )

                showLoggedNotification(insertedId, amount, merchant)
            }
        }
    }

    private fun showLoggedNotification(expenseId: Long, amount: Double, merchant: String) {
        val channelId = "spendtrack_alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Expense Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SpendTrack automatic transaction notifications"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EDIT_EXPENSE_ID", expenseId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            expenseId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SpendTrack: ₹${"%.2f".format(amount)} Logged")
            .setContentText("Paid to $merchant • Tap to change category")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(expenseId.toInt(), notif)
    }

    private fun parseMerchant(text: String): String {
        val specific = Regex("to\\s+(.+?)\\s+on\\s+\\d{2}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE).find(text)
        if (specific != null) return specific.groupValues[1].trim()

        val generic = Pattern.compile("(?:to|at|info)\\s+([A-Za-z0-9\\s]{2,25})", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (generic.find()) generic.group(1)?.trim() ?: "Kotak Debit" else "Kotak Debit"
    }

    private fun sha(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
