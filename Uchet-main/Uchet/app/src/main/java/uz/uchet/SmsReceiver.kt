package uz.uchet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullText = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: ""

        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val isDebugMode = prefs.getBoolean("sms_debug_mode", false)
        val isBank = sender.contains("bank", true) || sender.contains("kapital", true) ||
                     sender.contains("uzum", true) || sender.contains("tez", true) ||
                     sender.contains("anorbank", true) || sender.contains("tenge", true) ||
                     sender.contains("agro", true) || sender.contains("tbc", true) ||
                     sender.length <= 6

        if ((!isBank && !isDebugMode) || fullText.isEmpty()) return

        val (category, tx) = SmsParser.classify(sender, fullText, System.currentTimeMillis())
        if (category != SmsCategory.TRANSACTION || tx == null) return

        savePending(context, tx)
        showNotification(context, tx)
    }

    private fun savePending(context: Context, tx: BankTx) {
        val pendingJson = JSONObject().apply {
            put("amountMinor", tx.amountMinor)
            put("type", tx.type.name)
            put("card", tx.cardLast4?.let { "${it}_UZS" } ?: JSONObject.NULL)
            put("description", tx.merchant)
            put("category", tx.category)
            put("timestamp", tx.timestamp)
            put("currency", tx.currency)
        }
        context.getSharedPreferences("pending_sms", Context.MODE_PRIVATE)
            .edit().putString("pending", pendingJson.toString()).apply()
    }

    private fun showNotification(context: Context, tx: BankTx) {
        val channelId = "sms_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "SMS операции", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val amountStr = "${tx.amountMinor / 100}.${String.format("%02d", tx.amountMinor % 100)}"
        val typeStr = if (tx.type == TxType.INCOME) "Доход" else "Расход"
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новая операция")
            .setContentText("$typeStr: $amountStr ${tx.currency}. ${tx.merchant.take(40)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}
