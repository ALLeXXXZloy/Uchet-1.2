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
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullText = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: ""

            val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val isDebugMode = prefs.getBoolean("sms_debug_mode", false)
            val isBank = sender.contains("bank", true) || sender.contains("kapital", true) || sender.contains("uzum", true) || sender.contains("tez", true) || sender.length <= 6
                         
            if ((isBank || isDebugMode) && fullText.isNotEmpty()) {
                val parsed = parseAutoSms(fullText)
                if (parsed != null) {
                    val smsPrefs = context.getSharedPreferences("pending_sms", Context.MODE_PRIVATE)
                    val pendingJson = JSONObject().apply {
                        put("amountMinor", parsed.amountMinor); put("type", parsed.type.name)
                        put("card", parsed.card ?: JSONObject.NULL); put("description", parsed.description)
                        put("category", parsed.category); put("timestamp", System.currentTimeMillis())
                    }
                    smsPrefs.edit().putString("pending", pendingJson.toString()).apply()
                    showNotification(context, parsed)
                }
            }
        }
    }

    private fun showNotification(context: Context, parsed: AutoParsedSms) {
        val channelId = "sms_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS операции", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val amountStr = "${parsed.amountMinor / 100}.${String.format("%02d", parsed.amountMinor % 100)}"
        val typeStr = if (parsed.type == TxType.INCOME) "Доход" else "Расход"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Новая операция")
            .setContentText("$typeStr: $amountStr UZS. Нажмите для проверки")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent).setAutoCancel(true).build()
        notificationManager.notify(1, notification)
    }
}

data class AutoParsedSms(val amountMinor: Long, val type: TxType, val card: String?, val description: String, val category: String)

fun parseAutoSms(text: String): AutoParsedSms? {
    val lowerText = text.lowercase()
    val isIncome = lowerText.contains("пополнение") || lowerText.contains("зачисление") || lowerText.contains("popolneniye") || lowerText.contains("kartaga o'tkazma")
    val isExpense = lowerText.contains("списание") || lowerText.contains("оплата") || lowerText.contains("snyatiye") || lowerText.contains("xarid") || lowerText.contains("pokupka") || lowerText.contains("chiqim")
    if (!isIncome && !isExpense) return null
    val type = if (isIncome) TxType.INCOME else TxType.EXPENSE

    val lines = text.lines()
    var amountMinor: Long? = null
    for (line in lines) {
        val lowerLine = line.lowercase()
        if (lowerLine.contains("баланс") || lowerLine.contains("кешбэк") || lowerLine.contains("qoldiq")) continue
        val regex = Regex("""(-?[\d\s]+[.,]\d{2})""")
        val match = regex.find(line)
        if (match != null) { amountMinor = match.groupValues[1].replace(" ", "").replace(",", ".").toBigDecimalOrNull()?.abs()?.movePointRight(2)?.toLong(); break }
    }
    if (amountMinor == null || amountMinor == 0L) return null

    val card = Regex("""(?:karta|карта|\*+|х)\s*\(?\*+(\d{4})\)?""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.take(4)
    val category = when {
        lowerText.contains("atto") || lowerText.contains("market") -> "🍔 Еда"
        lowerText.contains("uzumbank") -> "🚕 Транспорт"
        else -> "📦 Другое"
    }
    return AutoParsedSms(amountMinor, type, card, text.take(50), category)
}
