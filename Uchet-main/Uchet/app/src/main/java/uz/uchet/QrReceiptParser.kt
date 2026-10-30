package uz.uchet

import android.net.Uri
import org.json.JSONObject
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object QrReceiptParser {

    data class QrReceiptData(
        val amountMinor: Long?,
        val dateMillis: Long?,
        val merchant: String?,
        val receiptNo: String?
    )

    fun parse(qrText: String): QrReceiptData? {
        val text = qrText.trim()
        if (text.isEmpty()) return null

        // 1. Если это ссылка (ofd.soliq.uz, receipt.uz и т.д.)
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return parseUrl(text)
        }

        // 2. Если это JSON (некоторые POS-терминалы выдают JSON в QR)
        if (text.startsWith("{") && text.endsWith("}")) {
            return parseJson(text)
        }

        // 3. Если это просто строка параметров (t=...&s=...)
        if (text.contains("=") && text.contains("&")) {
            return parseUrl("https://dummy.com/?$text")
        }

        return null
    }

    private fun parseUrl(url: String): QrReceiptData? {
        try {
            val uri = Uri.parse(url)
            
            // Извлекаем параметры
            var amountStr: String? = null
            var dateStr: String? = null
            var receiptNo: String? = null
            var merchant: String? = null

            // Стандартные ключи для суммы в OFD Soliq: 'e' (обычно), 's', 'sum', 'total'
            val amountKeys = listOf("e", "s", "sum", "total", "amount", "summa")
            val dateKeys = listOf("t", "date", "time", "datetime")
            val receiptKeys = listOf("r", "receipt", "chek", "fn")
            val merchantKeys = listOf("m", "merchant", "seller", "company")

            for (key in amountKeys) {
                val value = uri.getQueryParameter(key)
                if (!value.isNullOrBlank()) { amountStr = value; break }
            }
            for (key in dateKeys) {
                val value = uri.getQueryParameter(key)
                if (!value.isNullOrBlank()) { dateStr = value; break }
            }
            for (key in receiptKeys) {
                val value = uri.getQueryParameter(key)
                if (!value.isNullOrBlank()) { receiptNo = value; break }
            }
            for (key in merchantKeys) {
                val value = uri.getQueryParameter(key)
                if (!value.isNullOrBlank()) { merchant = URLDecoder.decode(value, "UTF-8"); break }
            }

            // Если параметры в URI не нашлись через стандартный парсер, пробуем регуляркой
            if (amountStr == null) {
                val match = Regex("""[?&](e|s|sum|total|amount)=([^&]+)""").find(url)
                if (match != null) amountStr = match.groupValues[2]
            }

            val amountMinor = parseAmount(amountStr)
            val dateMillis = parseDate(dateStr)

            // Если нашли хотя бы сумму — возвращаем результат
            if (amountMinor != null) {
                return QrReceiptData(amountMinor, dateMillis, merchant, receiptNo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseJson(json: String): QrReceiptData? {
        return try {
            val obj = JSONObject(json)
            val amountKeys = listOf("total", "sum", "amount", "summa")
            val dateKeys = listOf("date", "time", "datetime", "timestamp")
            val receiptKeys = listOf("receipt", "chek", "id", "fn")
            val merchantKeys = listOf("merchant", "seller", "company", "name")

            var amountStr: String? = null
            var dateStr: String? = null
            var receiptNo: String? = null
            var merchant: String? = null

            for (key in amountKeys) if (obj.has(key)) { amountStr = obj.get(key).toString(); break }
            for (key in dateKeys) if (obj.has(key)) { dateStr = obj.get(key).toString(); break }
            for (key in receiptKeys) if (obj.has(key)) { receiptNo = obj.get(key).toString(); break }
            for (key in merchantKeys) if (obj.has(key)) { merchant = obj.get(key).toString(); break }

            val amountMinor = parseAmount(amountStr)
            val dateMillis = parseDate(dateStr)

            if (amountMinor != null) {
                return QrReceiptData(amountMinor, dateMillis, merchant, receiptNo)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAmount(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val clean = str.replace(Regex("[^0-9.]"), "")
        if (clean.isEmpty()) return null
        return try {
            // Умножаем на 100, так как в БД мы храним в тийинах (копейках)
            val doubleVal = clean.toDoubleOrNull() ?: return null
            (doubleVal * 100).toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDate(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val clean = str.trim()
        return try {
            // Сценарий 1: Timestamp (например, 1698312000000)
            if (clean.length >= 12 && clean.all { it.isDigit() }) {
                val longVal = clean.toLongOrNull()
                // Если это Unix-время в секундах (10 цифр) или миллисекундах (13 цифр)
                if (clean.length == 10) return longVal?.times(1000)
                if (clean.length == 13) return longVal
            }

            // Сценарий 2: OFD Soliq формат (YYYYMMDDHHMMSS), например 20231026140612
            if (clean.length == 14 && clean.all { it.isDigit() }) {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                val ldt = LocalDateTime.parse(clean, formatter)
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            // Сценарий 3: Строка с датой "2023-10-26 14:06:12"
            val formats = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "dd.MM.yyyy HH:mm:ss"
            )
            for (fmt in formats) {
                try {
                    val ldt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern(fmt))
                    return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
