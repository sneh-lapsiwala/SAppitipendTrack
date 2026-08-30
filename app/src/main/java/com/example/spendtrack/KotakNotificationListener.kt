package com.example.spendtrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class KotakNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // Extract full text from all possible Android notification fields
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val full = "$title $text $bigText $subText".trim()

        // Diagnostic Filter: check if this is related to banking/SMS
        val isRelevant = full.contains("Sent", ignoreCase = true) ||
                         full.contains("Debited", ignoreCase = true) ||
                         full.contains("Paid", ignoreCase = true) ||
                         full.contains("Spent", ignoreCase = true) ||
                         full.contains("Credited", ignoreCase = true) ||
                         full.contains("Received", ignoreCase = true)
        if (!isRelevant) return

        // LIVE DIAGNOSTIC TOAST (Proves the listener is alive and reading)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Read: ${full.take(50)}...", Toast.LENGTH_LONG).show()
        }

        // Matches "Sent Rs.1.00", "Sent Rs 1.00", "Debited Rs. 500", etc.
        val pattern = Pattern.compile(
             """(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]+)?)""",
             Pattern.CASE_INSENSITIVE
        )
        
        val matcher = pattern.matcher(full)
        val isDebit = Regex(
            """\b(sent|debited|debit|paid|spent|purchase|withdrawn)\b""",
            RegexOption.IGNORE_CASE
            ).containsMatchIn(full)

        val isCredit = Regex(
            """\b(credited|credit|received|deposit)\b""",
            RegexOption.IGNORE_CASE
            ).containsMatchIn(full)
        
        if (matcher.find()) {
            val amount = (matcher.group(1) ?: matcher.group(2))?.replace(",", "")?.toDoubleOrNull() ?: return
            val hash = sha(full)
            val merchant = parseMerchant(full)
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val dao = AppDb.get(this).dao()

            scope.launch {
                // Prevent duplicates
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
        } else {
            // Regex failed to match amount, but notification was received
            showDebugNotification("Regex Failed to Match", full)
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

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SpendTrack: ₹${"%.2f".format(amount)} Logged")
            .setContentText("Paid to $merchant • Tap to change category")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(expenseId.toInt(), notification)
    }

    private fun showDebugNotification(title: String, rawText: String) {
        val channelId = "spendtrack_alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("SpendTrack Debug: $title")
            .setContentText(rawText.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(rawText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
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
