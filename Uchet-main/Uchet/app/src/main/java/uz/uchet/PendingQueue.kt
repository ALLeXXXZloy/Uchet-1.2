package uz.uchet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PendingQueue {
    private const val PREFS_NAME = "pending_sms"
    private const val KEY_QUEUE = "queue"

    fun push(context: Context, tx: BankTx) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
        
        val obj = JSONObject().apply {
            put("amountMinor", tx.amountMinor)
            put("type", tx.type.name)
            // Пункт 1.3: Исправляем валюту (было хардкодом _UZS)
            put("card", tx.cardLast4?.let { "${it}_${tx.currency}" } ?: JSONObject.NULL)
            put("description", tx.merchant)
            put("category", tx.category)
            put("timestamp", tx.timestamp)
            put("currency", tx.currency)
        }
        
        queue.put(obj)
        // Используем commit() вместо apply(), чтобы гарантировать запись на диск
        prefs.edit().putString(KEY_QUEUE, queue.toString()).commit()
    }

    fun peek(context: Context): JSONObject? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
        return if (queue.length() > 0) queue.getJSONObject(0) else null
    }

    fun pop(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
        if (queue.length() > 0) {
            queue.remove(0)
            prefs.edit().putString(KEY_QUEUE, queue.toString()).commit()
        }
    }
}
