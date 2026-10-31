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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return@launch
                val fullText = messages.joinToString(" ") { it.messageBody ?: "" }
                val sender = messages.firstOrNull()?.originatingAddress ?: ""

                val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                val isDebugMode = prefs.getBoolean("sms_debug_mode", false)
                
                val BANK_SENDERS = listOf("kapital", "uzum", "anorbank", "tbc", "agro", "tenge", "bank", "aloqa")
                val isBank = BANK_SENDERS.any { sender.contains(it, ignoreCase = true) } || isDebugMode

                if (!isBank || fullText.isEmpty()) return@launch

                // ПОЛУЧАЕМ ПОЛЬЗОВАТЕЛЬСКИЕ ПРАВИЛА ИЗ БАЗЫ
                val db = AppDb.get(context)
                val rules = db.dao().getAllCustomRules()

                val (category, tx) = SmsParser.classify(sender, fullText, System.currentTimeMillis(), rules)
                if (category != SmsCategory.TRANSACTION || tx == null) return@launch

                PendingQueue.push(context, tx)
                showNotification(context, tx)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, tx: BankTx) {
        val channelId = "sms_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "SMS операции", NotificationManager.IMPORTANCE_HIGH))
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val typeStr = if (tx.type == TxType.INCOME) Translations.t("income", AppLanguage.RU) else Translations.t("expense", AppLanguage.RU)
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Новая операция")
            .setContentText("$typeStr: ${tx.amountMinor.money()} ${tx.currency}. ${tx.merchant.take(40)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(tx.timestamp.toInt(), n)
    }
}
