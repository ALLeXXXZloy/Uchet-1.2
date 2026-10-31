package uz.uchet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object NotificationHelper {
    private const val CHANNEL_ID = "daily_reminder_channel"
    private const val WORK_NAME = "DailyReminderWork"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Напоминания"
            val descriptionText = "Ежедневные напоминания о записи трат"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDailyReminder(context: Context) {
        val now = LocalDateTime.now()
        val targetTime = now.withHour(21).withMinute(0).withSecond(0)
        
        val delayMillis = if (now.isAfter(targetTime)) {
            // Если 21:00 уже прошло, планируем на завтра
            Duration.between(now, targetTime.plusDays(1)).toMillis()
        } else {
            Duration.between(now, targetTime).toMillis()
        }

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    const val NOTIF_CHANNEL = CHANNEL_ID
}
