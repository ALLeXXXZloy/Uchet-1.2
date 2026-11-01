package uz.uchet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

// ★ ДЛЯ ВАРИАНТА Б: Класс для хранения строки с координатами
data class PdfLine(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val page: Int
)

// ★ ДЛЯ ВАРИАНТА Б: Кастомный стриппер, который собирает координаты
class CoordinateTextStripper : PDFTextStripper() {
    val lines = mutableListOf<PdfLine>()
    override fun writeString(text: String, textPositions: List<TextPosition>) {
        if (text.isBlank() || textPositions.isEmpty()) return
        val first = textPositions.first()
        val last = textPositions.last()
        // PDFBox возвращает координаты от нижнего левого угла, YDirAdj переводит в координаты от верхнего левого
        val x = first.xDirAdj
        val y = first.yDirAdj
        val w = (last.xDirAdj + last.widthDirAdj) - x
        val h = first.heightDirAdj
        lines.add(PdfLine(text.trim(), x, y, w, h, currentPageNo))
    }
}

fun scanInboxSms(
    context: Context,
    existingTxs: List<Tx>,
    customRules: List<CustomKeywordRule> = emptyList(),
    onFound: (List<Tx>) -> Unit
) {
    onFound(SmsParser.scanInbox(context, existingTxs, customRules))
}

fun parseSmsText(text: String): ParsedSms? = SmsParser.parseForOcr(text)

fun uriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    e.printStackTrace()
    null
}

// Извлекает просто текст (для старых парсеров, у которых нет координат)
fun extractTextFromPdf(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()
            text
        } ?: ""
    } catch (e: Exception) { "" }
}

// ★ Рендеринг страницы PDF в Bitmap
fun renderPdfPageToBitmap(context: Context, uri: Uri, pageIndex: Int = 0): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val renderer = PDFRenderer(document)
            val bitmap = renderer.renderImageWithDPI(pageIndex, 200f) // 200 DPI для четкости
            document.close()
            bitmap
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ★ Вырезает кусок из Bitmap по координатам PdfLine (с отступами)
fun cropBitmapByLine(original: Bitmap, line: PdfLine, padding: Int = 10): Bitmap {
    val left = (line.x - padding).coerceAtLeast(0f).toInt()
    val top = (line.y - line.height - padding).coerceAtLeast(0f).toInt()
    val right = (line.x + line.width + padding).coerceAtMost(original.width.toFloat()).toInt()
    val bottom = (line.y + padding).coerceAtMost(original.height.toFloat()).toInt()
    
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)

    return try {
        Bitmap.createBitmap(original, left, top, width, height)
    } catch (e: Exception) {
        original // Возвращаем оригинал, если не удалось обрезать
    }
}

fun parseCardsFromScreenshot(text: String): List<CardEntity> {
    // ... (без изменений)
    val result = mutableListOf<CardEntity>()
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val amountRegex = Regex("""([0-9Oo\s\u00A0]+(?:[.,]\d{1,2})?)\s*(?:cyM|сум|UZS|USD|EUR|RUB|so['`]?m)""", RegexOption.IGNORE_CASE)
    val cardRegex = Regex("""\b([0-9Oo]{4})\b""")
    val pendingAmounts = mutableListOf<Long>()
    var currentBank = "Bank"
    var currentCurrency = "UZS"

    for (line in lines) {
        if (line.contains("ANORBANK", ignoreCase = true)) currentBank = "Anorbank"
        else if (line.contains("UZUM", ignoreCase = true)) currentBank = "Uzum"
        else if (line.contains("KAPITAL", ignoreCase = true)) currentBank = "Kapitalbank"
        else if (line.contains("TBC", ignoreCase = true)) currentBank = "TBC"
        else if (line.contains("HUMO", ignoreCase = true)) currentBank = "Humo"
        else if (line.contains("VISA", ignoreCase = true)) currentBank = "Visa"
        else if (line.contains("MULTI", ignoreCase = true)) currentBank = "Multi"
        if (line.contains("/")) continue
        val amtMatch = amountRegex.find(line)
        if (amtMatch != null) {
            if (line.contains("USD", true)) currentCurrency = "USD"
            else if (line.contains("EUR", true)) currentCurrency = "EUR"
            else if (line.contains("RUB", true)) currentCurrency = "RUB"
            else currentCurrency = "UZS"
            val amtStr = amtMatch.groupValues[1].replace(" ", "").replace("\u00A0", "").replace(",", ".").replace('O', '0').replace('o', '0')
            val normalizedAmt = if (amtStr.contains(".") && amtStr.substringAfter(".").length == 1) "${amtStr}0" else amtStr
            val parsedAmount = normalizedAmt.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
            if (parsedAmount != null) pendingAmounts.add(parsedAmount)
            continue
        }
        val cardMatch = cardRegex.find(line)
        if (cardMatch != null) {
            val startIndex = cardMatch.range.first
            val prefix = line.substring(0, startIndex)
            if (prefix.contains("****")) continue
            val rawLast4 = cardMatch.groupValues[1]
            val last4 = rawLast4.replace('O', '0').replace('o', '0')
            if (last4.startsWith("20")) continue
            val shortName = getShortBankName(currentBank)
            val finalName = "$shortName $last4"
            if (result.none { it.last4 == last4 }) {
                val amountToAssign = if (pendingAmounts.isNotEmpty()) pendingAmounts.removeAt(0) else 0L
                result.add(CardEntity(id = "${last4}_$currentCurrency", last4 = last4, name = finalName, currency = currentCurrency, initialBalanceMinor = amountToAssign))
            }
        }
    }
    return result
}

fun generateCsv(transactions: List<Tx>): String {
    val sb = StringBuilder("Дата,Время,Тип,Сумма (UZS),Категория,Описание,Карта,Источник,Валюта\n")
    val zone = ZoneId.systemDefault()
    transactions.sortedBy { it.timestamp }.forEach { t ->
        val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone)
        val amount = BigDecimal(t.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
        sb.append("${dt.format(DATE_FMT)},${dt.format(DateTimeFormatter.ofPattern("HH:mm"))},${t.type.label},$amount,${t.category.replace(",", " ")},${t.description.replace(",", " ").replace("\n", " ")},${if (t.cardId.isNullOrEmpty()) "Наличные" else "ID: ${t.cardId}"},${t.source},${t.currency}\n")
    }
    return sb.toString()
}
