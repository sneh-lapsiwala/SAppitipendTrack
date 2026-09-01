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

        // 1. Ignore SpendTrack's own notifications
        val pkg = sbn.packageName ?: return
        if (pkg == this.packageName) return

        // 2. Filter for SMS, Banking, and UPI apps only
        val isAllowedApp = pkg.contains("sms", ignoreCase = true) ||
                           pkg.contains("mms", ignoreCase = true) ||
                           pkg.contains("messaging", ignoreCase = true) ||
                           pkg.contains("kotak", ignoreCase = true) ||
                           pkg.contains("google.android.apps.messaging", ignoreCase = true) ||
                           pkg.contains("google.android.apps.nbu.paisa.user", ignoreCase = true) ||
                           pkg.contains("phonepe", ignoreCase = true) ||
                           pkg.contains("paytm", ignoreCase = true)

        if (!isAllowedApp) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // 3. Extract text fields
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val ticker = notif.tickerText?.toString() ?: ""
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ") ?: ""

        val full = "$title $text $bigText $subText $ticker $textLines".trim()
        if (full.isBlank()) return

        // 4. Keyword gate for financial alerts
        val isTransaction = full.contains("Kotak", ignoreCase = true) ||
                            full.contains("UPI", ignoreCase = true) ||
                            full.contains("debited", ignoreCase = true) ||
                            full.contains("sent", ignoreCase = true) ||
                            full.contains("spent", ignoreCase = true) ||
                            full.contains("paid", ignoreCase = true) ||
                            full.contains("txn", ignoreCase = true) ||
                            full.contains("transferred", ignoreCase = true)

        if (!isTransaction) return

        // 5. Amount parsing regex
        val pattern = Pattern.compile(
            "(?:(?:Sent|Debited|Paid|Spent|Txn|Transfer(?:red)?)(?:\\s+by|\\s+of|\\s+for)?\\s*(?:Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:debited|spent|paid|sent))",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(full)

        if (matcher.find()) {
            val rawAmount = matcher.group(1) ?: matcher.group(2) ?: return
            val amount = rawAmount.replace(",", "").toDoubleOrNull() ?: return
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

                // Confirmation Toast at bottom
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Logged ₹${"%.2f".format(amount)} to $merchant", Toast.LENGTH_SHORT).show()
                }

                // Heads-up notification to edit category
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
                description = "SpendTrack transaction alerts"
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
            .setContentTitle("SpendTrack: ₹${"%.2f".format(amount)} Recorded")
            .setContentText("Paid to $merchant • Tap to edit category")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(expenseId.toInt(), notification)
    }

    private fun parseMerchant(text: String): String {
        val specific = Regex("to\\s+(.+?)\\s+on\\s+\\d{2}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE).find(text)
        if (specific != null) return specific.groupValues[1].trim()

        val atMatch = Regex("(?:to|at|vpa|info)\\s+([A-Za-z0-9\\s]{2,25})", RegexOption.IGNORE_CASE).find(text)
        if (atMatch != null) return atMatch.groupValues[1].trim()

        return "Kotak Debit"
    }

    private fun sha(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
