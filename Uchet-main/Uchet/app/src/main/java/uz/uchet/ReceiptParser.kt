package uz.uchet

import java.math.BigDecimal
import java.math.RoundingMode
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

    // Паттерн для поиска даты и времени
    private val dateRegex = Regex("""\b(\d{2}[-.]\d{2}[-.]\d{2,4})\s+(\d{2}:\d{2}(?::\d{2})?)\b""")
    
    // Паттерны для номера чека
    private val receiptNoRegex = Regex("""(?i)(?:chek|operatsiya|savdo|fm chek)\s*raqami\s*[:№]?\s*(?:N|№)?\s*(\d+)""")
    private val nPrefixRegex = Regex("""(?i)\bN\s*(\d{3,})\b""")
    
    // Паттерн для фискального признака
    private val fiscalRegex = Regex("""(?i)(?:fiskal|fm|fb)\s*(?:imzo|belgi|id|raqami)?\s*[:]?\s*([a-z0-9]{8,})""")

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        
        val amount = extractTotalAmount(lines)
        val dateMillis = extractDate(text)
        val seller = extractSeller(lines)
        val receiptNo = extractReceiptNo(text)
        val fiscal = extractFiscalSign(text)

        return ParsedReceipt(amount, dateMillis, seller, receiptNo, fiscal)
    }

    private fun extractTotalAmount(lines: List<String>): Long? {
        // Ищем строки с "Jami", но исключаем "QQS" (НДС)
        val jamiIndices = lines.indices.filter { i ->
            val line = lines[i].lowercase()
            (line.contains("jami") || line.contains("to'lov uchun")) &&
            !line.contains("qqs") && 
            !line.contains("jami pozitsiya")
        }

        // Проходим по найденным индексам с конца (обычно итог внизу)
        for (idx in jamiIndices.reversed()) {
            val line = lines[idx]
            val nextLine = lines.getOrNull(idx + 1) ?: ""

            // 1. Ищем число в той же строке (например: JAMI =53190 или Jami 74 000.00)
            val sameLineNumber = extractMaxNumber(line)
            if (sameLineNumber != null && sameLineNumber > 0) {
                return sameLineNumber
            }

            // 2. Если в этой строке нет числа, смотрим следующую (To'lov uchun jami \n 74 000.00)
            val nextLineNumber = extractMaxNumber(nextLine)
            if (nextLineNumber != null && nextLineNumber > 0) {
                return nextLineNumber
            }
        }
        return null
    }

    private fun extractMaxNumber(text: String): Long? {
        // Ищем все числа, включая те, что с точками или запятыми (например 74 000.00)
        val matches = Regex("""(\d[\d\s]*[.,]\d{2}|\d{3,})""").findAll(text)
        
        var maxVal = -1L
        for (match in matches) {
            val raw = match.value
            // Игнорируем длинные числа (фискальные, U-ID) - они обычно > 10 цифр
            val cleanDigits = raw.replace(Regex("[^0-9]"), "")
            if (cleanDigits.length > 9) continue 

            val parsed = try {
                val normalized = raw.replace(" ", "").replace(",", ".")
                BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            } catch (e: Exception) {
                // Если не получилось с точкой, пробуем как целое
                cleanDigits.toLongOrNull()?.times(100)
            }
            
            if (parsed != null && parsed > maxVal) {
                maxVal = parsed
            }
        }
        return if (maxVal > 0) maxVal else null
    }

    private fun extractDate(text: String): Long? {
        val match = dateRegex.find(text) ?: return null
        val dateStr = match.groupValues[1]
        val timeStr = match.groupValues[2]

        // Нормализуем разделители
        val normalizedDate = dateStr.replace('-', '.').replace('/', '.')
        val parts = normalizedDate.split(".")
        if (parts.size != 3) return null

        var year = parts[2].toIntOrNull() ?: return null
        if (year < 100) year += 2000 // Исправляем 26 на 2026

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val timeFormatter = if (timeStr.count { it == ':' } == 2) 
            DateTimeFormatter.ofPattern("HH:mm:ss") 
            else DateTimeFormatter.ofPattern("HH:mm")

        return try {
            val date = LocalDate.parse("${parts[0]}.${parts[1]}.$year", formatter)
            val time = LocalTime.parse(timeStr, timeFormatter)
            LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun extractSeller(lines: List<String>): String? {
        // Ищем первое упоминание кавычек или "X/K", "MCHJ", "Smart Business"
        for (line in lines.take(5)) {
            if (line.contains("X/K", true) || line.contains("MCHJ", true) || line.contains("OOO", true)) {
                return line
            }
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
