package uz.uchet

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.YearMonth

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDb.get(context)
        val dao = db.dao()

        val today = LocalDate.now()
        val monthStr = YearMonth.from(today).toString()
        val todayStr = today.toString()

        val recs = dao.getRecurringList()
        val confs = dao.getAllConfirmations()

        // Проверяем, есть ли неподтвержденные регулярные операции
        val pending = recs.filter { r ->
            val effectiveDay = minOf(r.dayOfMonth, today.lengthOfMonth())
            if (effectiveDay > today.dayOfMonth) return@filter false
            
            val conf = confs.find { it.recurringId == r.id && it.month == monthStr }
            when {
                conf?.status == "CONFIRMED" -> false
                conf?.status == "POSTPONED" && conf.lastPostponedDate == todayStr -> false
                else -> true
            }
        }

        val title: String
        val text: String
        
        if (pending.isNotEmpty()) {
            title = "Не забудьте подтвердить операции"
            text = "У вас ${pending.size} регулярных операций ожидают подтверждения."
        } else {
            title = "Запись расходов"
            text = "Не забудьте записать сегодняшние траты!"
        }

        showNotification(context, title, text)
        return Result.success()
    }

    private fun showNotification(context: Context, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationHelper.NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(1001, builder.build())
        } catch (e: SecurityException) {
            // Разрешение на уведомления не выдано
        }
    }
}
