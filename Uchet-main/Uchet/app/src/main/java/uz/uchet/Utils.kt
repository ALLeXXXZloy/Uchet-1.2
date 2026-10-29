package uz.uchet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

fun scanInboxSms(context: Context, existingTxs: List<Tx>, onFound: (List<Tx>) -> Unit) {
    onFound(SmsParser.scanInbox(context, existingTxs))
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

fun parseCardsFromScreenshot(text: String): List<CardEntity> {
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

            val amtStr = amtMatch.groupValues[1]
                .replace(" ", "").replace("\u00A0", "")
                .replace(",", ".")
                .replace('O', '0').replace('o', '0')

            val normalizedAmt = if (amtStr.contains(".") && amtStr.substringAfter(".").length == 1) "${amtStr}0" else amtStr
            val parsedAmount = normalizedAmt.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
            if (parsedAmount != null) {
                pendingAmounts.add(parsedAmount)
            }
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
                result.add(CardEntity(
                    id = "${last4}_$currentCurrency",
                    last4 = last4,
                    name = finalName,
                    currency = currentCurrency,
                    initialBalanceMinor = amountToAssign
                ))
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
