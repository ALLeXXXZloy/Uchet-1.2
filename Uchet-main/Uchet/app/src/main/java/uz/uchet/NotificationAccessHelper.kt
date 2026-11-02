package uz.uchet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationAccessHelper {

    /**
     * Проверяет, выдан ли приложению доступ к прослушиванию уведомлений.
     * Работает на всех API (включая minSdk 26).
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Открывает системный экран "Доступ к уведомлениям".
     * Если экран недоступен (кастомные прошивки), пробует fallback-варианты.
     */
    fun requestNotificationAccess(context: Context) {
        // 1) Стандартный экран "Доступ к уведомлениям"
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            return
        } catch (_: Exception) { }

        // 2) Fallback: экран приложения в настройках
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            return
        } catch (_: Exception) { }

        // 3) Крайний fallback: общие настройки
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) { }
    }
}
