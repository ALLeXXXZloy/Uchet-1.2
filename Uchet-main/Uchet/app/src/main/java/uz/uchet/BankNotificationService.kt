package uz.uchet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class BankNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        
        val packageName = sbn.packageName ?: return
        // Список пакетов банковских приложений (можно расширить)
        val bankPackages = listOf("uz.kapitalbank.mobile", "uz.uzumbank", "uz.tez", "uz.anorbank")
        
        if (bankPackages.any { packageName.contains(it, true) } || packageName.contains("bank", true)) {
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
            
            val fullText = "$title. $bigText"
            Log.d("BankPush", "Received: $fullText")
            
            // Если это финансовая операция, парсим и сохраняем
            val parsed = parseAutoSms(fullText) // Используем тот же парсер, что и для СМС
            if (parsed != null) {
                val prefs = getSharedPreferences("pending_sms", MODE_PRIVATE)
                val pendingJson = org.json.JSONObject().apply {
                    put("amountMinor", parsed.amountMinor)
                    put("type", parsed.type.name)
                    put("card", parsed.card ?: org.json.JSONObject.NULL)
                    put("description", parsed.description)
                    put("category", parsed.category)
                    put("timestamp", System.currentTimeMillis())
                }
                prefs.edit().putString("pending", pendingJson.toString()).apply()
                
                // Показываем уведомление от нашего приложения
                val fakeContext = applicationContext
                if (fakeContext != null) {
                    // Можно вызвать метод показа уведомления, либо просто оставить, 
                    // чтобы пользователь сам открыл приложение.
                }
            }
        }
    }
}
