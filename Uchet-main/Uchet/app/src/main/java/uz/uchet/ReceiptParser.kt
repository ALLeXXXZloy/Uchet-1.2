package uz.uchet

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReceiptParser {

    data class ParsedReceipt(
        val amountMinor: Long?,
        val dateMillis: Long?,
        val sellerName: String?,
        val receiptNo: String?,
        val fiscalSign: String?
    )

    // Дата + время: "01.02.2026 14:30", "2026-02-01 14:30:45", "09.10.2026/14:27"
    private val dateRegex = Regex(
        """\b(\d{2}[-.]\d{2}[-.]\d{2,4}|\d{4}[-.]\d{2}[-.]\d{2})[\s,/]+(\d{2}:\d{2}(?::\d{2})?)\b"""
    )

    // Номера чеков: "Savdo raqami 16531610074034", "CHEK RAQAMI: №28357"
    private val receiptNoRegex = Regex("""(?i)(?:chek|operatsiya|savdo|fm chek)\s*(?:raqami|raqam|№|N)\s*[:№]?\s*(\d+)""")
    private val nPrefixRegex = Regex("""(?i)\bN\s*(\d{3,})\b""")
    
    // Фискальные признаки
    private val fiscalRegex = Regex("""(?i)(?:fiskal|fm|fb)\s*(?:imzo|belgi|id|raqami)?\s*[:]?\s*([a-zA-Z0-9]{8,})""")

    // Единый паттерн для чисел: покрывает "74,000.00", "74 000.00", "=53190", "=15770"
    private val NUMBER_REGEX = Regex(
        """\b\d{1,3}(?:[.,\s\u00A0]\d{3})+(?:[.,]\d{2})?\b|\b\d+[.,]\d{2}\b|\b\d{3,}\b"""
    )

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val amount = extractTotalAmount(lines)
        val dateMillis = extractDate(text)
        val seller = extractSeller(lines)
        val receiptNo = extractReceiptNo(text)
        val fiscal = extractFiscalSign(text)

        return ParsedReceipt(amount, dateMillis, seller, receiptNo, fiscal)
    }

    /**
     * Финальная попытка: если специфичные парсеры не сработали,
     * взять самое крупное число в последних 50% строк, отсекая НДС и метаданные.
     */
    fun extractBiggestAmount(text: String): Long? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val startIdx = (lines.size * 0.5).toInt()
        val tail = if (lines.size - startIdx >= 3) lines.drop(startIdx) else lines

        var best: Long? = null
        for (line in tail) {
            val l = line.lowercase()
            // Отсекаем НДС, скидки и служебную информацию
            if (l.contains("qqs") || l.contains("ндс") || l.contains("chegirma") || l.contains("скидка") ||
                l.contains("karta") || l.contains("terminal") || l.contains("sana") || l.contains("vaqt") ||
                l.contains("rrn") || l.contains("stir") || l.contains("fm ") || l.contains("fb") ||
                l.contains("uid") || l.contains("mxik") || l.contains("shtrix") || l.contains("kassir") ||
                l.contains("smena") || l.contains("смена") || l.contains("kassa")) continue

            for (m in NUMBER_REGEX.findAll(line)) {
                val cleanDigits = m.value.replace(Regex("[^0-9]"), "")
                if (cleanDigits.length > 9) continue           // Отсекаем длинные ID
                val parsed = parseAmount(m.value) ?: continue
                if (parsed < 1_000) continue                   // Меньше 10 сум
                if (parsed > 100_000_000_000L) continue        // Больше 1 млрд сум
                if (best == null || parsed > best) best = parsed
            }
        }
        return best
    }

    private fun extractTotalAmount(lines: List<String>): Long? {
        val totalIndices = lines.indices.filter { isTotalLine(lines[it]) }

        for (idx in totalIndices.reversed()) {
            val line = lines[idx]
            val nextLine = lines.getOrNull(idx + 1) ?: ""
            val prevLine = lines.getOrNull(idx - 1) ?: ""

            // Ищем в этой же строке
            extractMaxNumber(line)?.let { if (it > 0) return it }
            // Ищем на следующей строке (часто бывает "To'lov uchun jami \n 74 000.00")
            extractMaxNumber(nextLine)?.let { if (it > 0) return it }
            // На всякий случай проверяем предыдущую
            extractMaxNumber(prevLine)?.let { if (it > 0) return it }
        }

        // Fallback: если маркер не найден, ищем самое крупное число в нижней части
        return extractBiggestAmount(lines.joinToString("\n"))
    }

    private fun isTotalLine(line: String): Boolean {
        val l = line.lowercase()
        // Исключения (возвращаем false)
        if (l.contains("qqs") || l.contains("ндс")) return false
        if (l.contains("chegirma") || l.contains("скидка")) return false
        if (l.contains("pozitsiya") || l.contains("позици")) return false
        if (l.contains("terminal") || l.contains("rrn") || l.contains("stir") || l.contains("mxik") || l.contains("shtrix")) return false
        if (l.contains("karta turi") || l.contains("смена") || l.contains("smena") || l.contains("kassir") || l.contains("kassa")) return false
        if (l.contains("sana") || l.contains("vaqt") || l.contains("date")) return false

        // Маркеры итога
        return l.contains("to'lov uchun jami") ||
               l.contains("to‘lov uchun jami") ||
               l.contains("to`lov uchun jami") ||
               l.contains("jami to'lov") ||
               l.contains("jami to‘lov") ||
               l.contains("jami to`lov") ||
               l.contains("to'landi") ||
               l.contains("to‘landi") ||
               l.contains("hammasi") ||
               l.contains("итого") ||
               l.contains("к оплате") ||
               l.contains("сумма к оплате") ||
               l.contains("total") ||
               l.contains("jami:") ||
               l.contains("jami =") ||
               (l.contains("jami ") && !l.contains("qqs")) ||
               l.contains("безналичный") ||
               l.contains("безнал") ||
               l.contains("bank kartasi")
    }

    private fun extractMaxNumber(text: String): Long? {
        var maxVal = -1L
        for (match in NUMBER_REGEX.findAll(text)) {
            val raw = match.value
            val cleanDigits = raw.replace(Regex("[^0-9]"), "")
            if (cleanDigits.isEmpty()) continue
            if (cleanDigits.length > 9) continue   // Отсекаем фискальные номера
            val parsed = parseAmount(raw) ?: continue
            if (parsed > maxVal) maxVal = parsed
        }
        return if (maxVal > 0) maxVal else null
    }

    /**
     * Парсит строку-число в копейки (тийины). Понимает:
     *  - "74,000.00", "74.000,00", "74 000,00", "74000.00", "74000"
     *  - "=53190" (знак равно отбрасывается)
     */
    private fun parseAmount(raw: String): Long? {
        val trimmed = raw.replace("=", "").trim()
        if (trimmed.isEmpty()) return null

        // Ищем последний [.,] с ровно двумя цифрами после — это decimal
        val decimalMatch = Regex("""[.,](\d{2})$""").find(trimmed)

        return try {
            if (decimalMatch != null) {
                val intPart = trimmed.substring(0, decimalMatch.range.first)
                    .replace(Regex("[^0-9]"), "")
                val fracPart = decimalMatch.groupValues[1]
                val intVal = intPart.toLongOrNull() ?: return null
                val fracVal = fracPart.toLongOrNull() ?: 0L
                intVal * 100 + fracVal
            } else {
                val clean = trimmed.replace(Regex("[^0-9]"), "")
                clean.toLongOrNull()?.times(100)
            }
        } catch (_: Exception) { null }
    }

    private fun extractDate(text: String): Long? {
        val match = dateRegex.find(text) ?: return null
        val dateStr = match.groupValues[1]
        val timeStr = match.groupValues[2]

        val normalizedDate = dateStr.replace('/', '.')
        val parts = normalizedDate.split("-", ".")

        val year: Int
        val day: Int
        val month: Int
        if (dateStr.contains("-") && parts[0].length == 4) {
            // yyyy-MM-dd
            year = parts.getOrNull(0)?.toIntOrNull() ?: return null
            month = parts.getOrNull(1)?.toIntOrNull() ?: return null
            day = parts.getOrNull(2)?.toIntOrNull() ?: return null
        } else {
            // dd.MM.yyyy
            day = parts.getOrNull(0)?.toIntOrNull() ?: return null
            month = parts.getOrNull(1)?.toIntOrNull() ?: return null
            var y = parts.getOrNull(2)?.toIntOrNull() ?: return null
            if (y < 100) y += 2000
            year = y
        }

        val timeFormatter = if (timeStr.count { it == ':' } == 2)
            DateTimeFormatter.ofPattern("HH:mm:ss")
        else DateTimeFormatter.ofPattern("HH:mm")

        return try {
            val date = LocalDate.of(year, month, day)
            val time = LocalTime.parse(timeStr, timeFormatter)
            LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { System.currentTimeMillis() }
    }

    private fun extractSeller(lines: List<String>): String? {
        for (line in lines.take(8)) {
            if (line.contains("X/K", true) || line.contains("MCHJ", true) || line.contains("OOO", true)) return line
            if (line.contains("\"")) {
                val parts = line.split("\"")
                if (parts.size >= 3 && parts[1].isNotBlank()) return parts[1]
            }
            if (line.contains("SMART BUSINESS", true)) return "Smart Business"
            if (line.contains("SAVDO", true) && line.length < 30) return "Savdo"
        }
        return lines.firstOrNull { it.length in 3..30 && !it.any { c -> c.isDigit() } }
    }

    private fun extractReceiptNo(text: String): String? {
        receiptNoRegex.find(text)?.let { return it.groupValues[1] }
        nPrefixRegex.find(text)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractFiscalSign(text: String): String? {
        fiscalRegex.find(text)?.let { return it.groupValues[1] }
        return null
    }
}
