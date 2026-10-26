package uz.uchet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale

class BalanceWidget : AppWidgetProvider() {
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
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val totalBalanceMinor = prefs.getLong("total_balance", 0L)

            val abs = kotlin.math.abs(totalBalanceMinor)
            val whole = String.format(Locale.US, "%,d", abs / 100).replace(',', ' ')
            val frac = abs % 100
            val sign = if (totalBalanceMinor < 0) "-" else ""
            val balanceText = "$sign$whole.%02d UZS".format(frac)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_balance_text, balanceText)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_balance_text, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
