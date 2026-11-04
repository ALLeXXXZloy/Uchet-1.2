package uz.uchet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val packageName = sbn.packageName
        
        // Логируем каждое уведомление для отладки (смотри Logcat)
        Log.d("BankNotifService", "onNotificationPosted from: $packageName")

        // ★ ИСПРАВЛЕНО: добавлены реальные имена пакетов с твоего устройства
        val bankPackages = setOf(
            // Kapitalbank
            "uz.kapitalbank.android", "uz.kapitalbank.kbonline", "uz.kapitalbank.mobile24",
            // Uzum
            "uz.uzum.app", "uz.uzum.avto.android",
            // Anorbank
            "uz.anormobile.retail",
            // Tez (Hamkorbank)
            "uz.hamkorbank.tezbank",
            // Tenge
            "uz.tune.tenge",
            // AVO
            "uz.avo.app",
            // На случай, если установятся другие (из старого списка)
            "uz.agrobank", "uz.tbc", "uz.asakabank", "uz.kapitalbank.mobile", "uz.uzumbank", "uz.tez", "uz.anorbank", "uz.tengebank", "uz.avo"
        )
        
        if (packageName !in bankPackages) return

        // Пункт 1.10: Отсекаем групповые и постоянные уведомления
        val flags = sbn.notification?.flags ?: 0
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.isOngoing) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val big   = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Пункт 2.9: Читаем MessagingStyle (актуально для Uzum, Kapital)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it?.toString() ?: "" } ?: ""

        val fullText = listOf(title, big, text, lines).filter { it.isNotBlank() }.joinToString(". ")

        // Пункт 2.10: Используем время публикации уведомления
        val txTimestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val appContext = applicationContext

        scope.launch {
            try {
                // ★ ИСПРАВЛЕНО: правила самообучения
                val rules = AppDb.get(appContext).dao().getAllCustomRules()
                val (category, tx) = SmsParser.classify(packageName, fullText, txTimestamp, rules)

                if (category != SmsCategory.TRANSACTION || tx == null) {
                    Log.d("BankNotifService", "Not a transaction: $fullText")
                    return@launch
                }

                // Пункт 1.10: Дедупликация
                val prefs = appContext.getSharedPreferences("notif_dedup", MODE_PRIVATE)
                val key = "${tx.amountMinor}_${tx.cardLast4}_${tx.type}"
                val lastTs = prefs.getLong(key, 0L)
                if (txTimestamp - lastTs < 60_000L) return@launch
                prefs.edit().putLong(key, txTimestamp).apply()

                PendingQueue.push(appContext, tx)
                Log.d("BankNotifService", "Transaction parsed and queued: ${tx.amountMinor}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
