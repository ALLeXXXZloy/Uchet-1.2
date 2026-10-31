package uz.uchet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class QuickActionsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions_layout)

            // Настраиваем клики
            setClickIntent(context, views, R.id.btn_food, "🍔 Еда", "EXPENSE")
            setClickIntent(context, views, R.id.btn_transport, "🚕 Транспорт", "EXPENSE")
            setClickIntent(context, views, R.id.btn_shopping, "🛒 Покупки", "EXPENSE")
            setClickIntent(context, views, R.id.btn_income, "💵 Зарплата", "INCOME")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setClickIntent(context: Context, views: RemoteViews, viewId: Int, category: String, type: String) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("QUICK_ACTION_CATEGORY", category)
                putExtra("QUICK_ACTION_TYPE", type)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                viewId, // Уникальный requestCode для каждой кнопки
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }
}
