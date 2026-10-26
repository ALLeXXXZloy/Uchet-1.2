package uz.uchet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject

class BankNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        val bankPackages = listOf(
            "uz.kapitalbank.mobile", "uz.uzumbank", "uz.tez", "uz.anorbank",
            "uz.tengebank", "uz.agrobank", "uz.tbc", "uz.avo"
        )
        if (!bankPackages.any { packageName.contains(it, true) } &&
            !packageName.contains("bank", true)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val big   = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        val fullText = "$title. $big"

        Log.d("BankPush", "[$packageName] $fullText")

        val (category, tx) = SmsParser.classify(packageName, fullText, System.currentTimeMillis())
        if (category != SmsCategory.TRANSACTION || tx == null) return

        val pendingJson = JSONObject().apply {
            put("amountMinor", tx.amountMinor)
            put("type", tx.type.name)
            put("card", tx.cardLast4?.let { "${it}_UZS" } ?: JSONObject.NULL)
            put("description", tx.merchant)
            put("category", tx.category)
            put("timestamp", tx.timestamp)
            put("currency", tx.currency)
        }
        getSharedPreferences("pending_sms", MODE_PRIVATE)
            .edit().putString("pending", pendingJson.toString()).apply()
    }
}
