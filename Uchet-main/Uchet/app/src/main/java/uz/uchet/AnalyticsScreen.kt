package uz.uchet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(txs: List<Tx>, allTxs: List<Tx>, currentLang: AppLanguage, onBack: () -> Unit) {
    val t = { key: String -> Translations.t(key, currentLang) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("analytics")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (txs.any { it.type == TxType.EXPENSE }) {
                Text(t("daily_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                DailyExpenseChart(txs = txs, yearMonth = YearMonth.now())
                Spacer(Modifier.height(32.dp))
            }
            if (allTxs.any { it.type == TxType.EXPENSE }) {
                Text(t("monthly_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                MonthlyExpenseChart(allTxs = allTxs)
                Spacer(Modifier.height(32.dp))
            }
            val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
            if (expensesByCategory.isNotEmpty()) {
                Text(t("exp_by_cat"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                CategoryPieChart(expensesByCategory, currentLang)
            }
        }
    }
}

@Composable
fun DailyExpenseChart(txs: List<Tx>, yearMonth: YearMonth) {
    val daysInMonth = yearMonth.lengthOfMonth(); val zone = ZoneId.systemDefault()
    val dailyExpenses = (1..daysInMonth).associateWith { day -> txs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.dayOfMonth == day && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (dailyExpenses.values.maxOrNull() ?: 1L).toFloat()

    val barColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.secondary

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(Unit) {
            detectTapGestures { offset ->
                val step = size.width / daysInMonth
                val clickedDay = (offset.x / step).toInt() + 1
                selectedDay = if (selectedDay == clickedDay) null else clickedDay
            }
        }) {
            val width = size.width; val height = size.height; val barWidth = (width / daysInMonth) * 0.6f; val step = width / daysInMonth
            dailyExpenses.forEach { (day, amount) ->
                if (amount > 0) {
                    val isSelected = selectedDay == day
                    val currentBarColor = if (isSelected) selectedBarColor else barColor
                    val barHeight = (amount / maxExpense) * height
                    val x = (day - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = currentBarColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedDay != null) {
            Text("${Translations.t("day", AppLanguage.RU)}: $selectedDay. ${Translations.t("total", AppLanguage.RU)}: ${dailyExpenses[selectedDay]?.money()} UZS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = selectedBarColor)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MonthlyExpenseChart(allTxs: List<Tx>) {
    val currentYear = Year.now(); val zone = ZoneId.systemDefault()
    val monthlyExpenses = (1..12).associateWith { m -> allTxs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.year == currentYear.value && date.monthValue == m && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (monthlyExpenses.values.maxOrNull() ?: 1L).toFloat()

    val barColor = MaterialTheme.colorScheme.secondary
    val selectedBarColor = MaterialTheme.colorScheme.primary

    var selectedMonth by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(Unit) {
            detectTapGestures { offset ->
                val step = size.width / 12
                val clickedMonth = (offset.x / step).toInt() + 1
                selectedMonth = if (selectedMonth == clickedMonth) null else clickedMonth
            }
        }) {
            val width = size.width; val height = size.height; val step = width / 12; val barWidth = step * 0.6f
            monthlyExpenses.forEach { (month, amount) ->
                if (amount > 0) {
                    val isSelected = selectedMonth == month
                    val currentBarColor = if (isSelected) selectedBarColor else barColor
                    val barHeight = (amount / maxExpense) * height
                    val x = (month - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = currentBarColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedMonth != null) {
            Text("${Translations.t("date", AppLanguage.RU)}: $selectedMonth. ${Translations.t("total", AppLanguage.RU)}: ${monthlyExpenses[selectedMonth]?.money()} UZS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = selectedBarColor)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("4", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("7", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("10", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("12", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun CategoryPieChart(data: Map<String, Long>, lang: AppLanguage) {
    val total = data.values.sum().toFloat(); if (total == 0f) return; val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)); val sortedData = data.entries.sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) { Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) { var startAngle = -90f; sortedData.forEachIndexed { index, entry -> val sweepAngle = (entry.value / total) * 360f; drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true); startAngle += sweepAngle } }
        Spacer(Modifier.width(16.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { sortedData.take(4).forEachIndexed { index, entry -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape)); Spacer(Modifier.width(6.dp)); Text("${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) } } } }
}
