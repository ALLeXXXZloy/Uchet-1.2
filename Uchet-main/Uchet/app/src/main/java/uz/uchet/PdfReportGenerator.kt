package uz.uchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PdfReportGenerator {

    fun generateReport(
        context: Context,
        uri: Uri,
        txs: List<Tx>,
        month: YearMonth,
        currentLang: AppLanguage
    ): Boolean {
        val t = { key: String -> Translations.t(key, currentLang) }
        val document = PdfDocument()

        // Настройки страницы (A4)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Настройки шрифтов
        val paintTitle = Paint().apply { textSize = 24f; isFakeBoldText = true; color = Color.BLACK }
        val paintSubtitle = Paint().apply { textSize = 14f; color = Color.DKGRAY }
        val paintText = Paint().apply { textSize = 12f; color = Color.BLACK }
        val paintBoldText = Paint().apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val paintLine = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var y = 50f
        val marginLeft = 40f
        val marginRight = 555f
        val pageHeight = 842f
        val marginBottom = 50f

        // --- ЗАГОЛОВОК ---
        canvas.drawText("Uchet - ${getMonthName(month.monthValue, currentLang)} ${month.year}", marginLeft, y, paintTitle)
        y += 30f

        val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        val net = income - expense

        canvas.drawText("${t("income")}: +${income.money()} UZS", marginLeft, y, paintSubtitle)
        canvas.drawText("${t("expense")}: -${expense.money()} UZS", marginLeft + 200f, y, paintSubtitle)
        canvas.drawText("${t("total")}: ${net.money()} UZS", marginLeft + 400f, y, paintSubtitle)
        y += 20f
        canvas.drawLine(marginLeft, y, marginRight, y, paintLine)
        y += 20f

        // --- СПИСОК ОПЕРАЦИЙ ---
        canvas.drawText("Date", marginLeft, y, paintBoldText)
        canvas.drawText("Category", marginLeft + 100f, y, paintBoldText)
        canvas.drawText("Description", marginLeft + 250f, y, paintBoldText)
        canvas.drawText("Amount", marginRight - 60f, y, paintBoldText)
        y += 15f
        canvas.drawLine(marginLeft, y, marginRight, y, paintLine)
        y += 20f

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val sortedTxs = txs.sortedByDescending { it.timestamp }

        for (tx in sortedTxs) {
            // Проверка на окончание страницы
            if (y > pageHeight - marginBottom) {
                document.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                page = document.startPage(newPageInfo)
                canvas = page.canvas
                y = marginBottom
            }

            val dateStr = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
            val categoryStr = Translations.translateCategory(tx.category, currentLang).take(20)
            val descStr = tx.description.take(25)
            val amountStr = "${if (tx.type == TxType.INCOME) "+" else "-"}${tx.amountMinor.money()}"

            canvas.drawText(dateStr, marginLeft, y, paintText)
            canvas.drawText(categoryStr, marginLeft + 100f, y, paintText)
            canvas.drawText(descStr, marginLeft + 250f, y, paintText)
            
            // Выравнивание суммы по правому краю
            val amountWidth = paintText.measureText(amountStr)
            canvas.drawText(amountStr, marginRight - amountWidth, y, paintText)
            
            y += 20f
        }

        document.finishPage(page)

        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            document.writeTo(outputStream)
            outputStream?.close()
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            false
        }
    }
}
