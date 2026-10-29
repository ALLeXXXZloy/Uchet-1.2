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
import androidx.compose.material.icons.filled.BarChart
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
    var selectedType by remember { mutableStateOf(TxType.EXPENSE) }

    val incomeMonth = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expenseMonth = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val netMonth = incomeMonth - expenseMonth

    val filteredTxs = txs.filter { it.type == selectedType }
    val filteredAllTxs = allTxs.filter { it.type == selectedType }
    val byCategory = filteredTxs.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("analytics")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Сводка
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t("income"), fontWeight = FontWeight.Medium)
                        Text("+${incomeMonth.money()}", color = GREEN, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t("expense"), fontWeight = FontWeight.Medium)
                        Text("-${expenseMonth.money()}", color = RED, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t("total"), fontWeight = FontWeight.Bold)
                        Text(
                            "${if (netMonth >= 0) "+" else ""}${netMonth.money()}",
                            color = if (netMonth >= 0) GREEN else RED,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Переключатель
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedType == TxType.EXPENSE,
                    onClick = { selectedType = TxType.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(t("expense")) }
                SegmentedButton(
                    selected = selectedType == TxType.INCOME,
                    onClick = { selectedType = TxType.INCOME },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(t("income")) }
            }

            Spacer(Modifier.height(24.dp))

            if (filteredTxs.isEmpty() && filteredAllTxs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BarChart, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(t("no_found"), color = Color.Gray)
                }
            } else {
                if (filteredTxs.isNotEmpty()) {
                    Text(
                        if (selectedType == TxType.INCOME) t("daily_chart_inc") else t("daily_chart"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    DailyChart(txs = filteredTxs, yearMonth = YearMonth.now(), type = selectedType, currentLang = currentLang)
                    Spacer(Modifier.height(32.dp))
                }
                if (filteredAllTxs.isNotEmpty()) {
                    Text(
                        if (selectedType == TxType.INCOME) t("monthly_chart_inc") else t("monthly_chart"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    MonthlyChart(allTxs = filteredAllTxs, type = selectedType, currentLang = currentLang)
                    Spacer(Modifier.height(32.dp))
                }
                if (byCategory.isNotEmpty()) {
                    Text(
                        if (selectedType == TxType.INCOME) t("inc_by_cat") else t("exp_by_cat"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    CategoryPieChart(byCategory, currentLang)
                }
            }
        }
    }
}

@Composable
fun DailyChart(txs: List<Tx>, yearMonth: YearMonth, type: TxType, currentLang: AppLanguage) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val zone = ZoneId.systemDefault()
    val dailyAmounts = (1..daysInMonth).associateWith { day ->
        txs.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().dayOfMonth == day }
            .sumOf { it.amountMinor }
    }
    val maxAmount = (dailyAmounts.values.maxOrNull() ?: 1L).toFloat()

    val barColor = if (type == TxType.INCOME) GREEN else RED
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(type) {
            detectTapGestures { offset ->
                val step = size.width / daysInMonth
                val clicked = ((offset.x / step).toInt() + 1).coerceIn(1, daysInMonth)
                selectedDay = if (selectedDay == clicked) null else clicked
            }
        }) {
            val width = size.width
            val height = size.height
            val barWidth = (width / daysInMonth) * 0.6f
            val step = width / daysInMonth
            dailyAmounts.forEach { (day, amount) ->
                if (amount > 0) {
                    val alpha = if (selectedDay == null || selectedDay == day) 1f else 0.4f
                    val barHeight = (amount / maxAmount) * height
                    val x = (day - 1) * step + (step - barWidth) / 2
                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x, height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedDay != null) {
            Text(
                "${Translations.t("day", currentLang)}: $selectedDay — ${dailyAmounts[selectedDay]?.money()} UZS",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MonthlyChart(allTxs: List<Tx>, type: TxType, currentLang: AppLanguage) {
    val currentYear = Year.now()
    val zone = ZoneId.systemDefault()
    val monthlyAmounts = (1..12).associateWith { m ->
        allTxs.filter {
            val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            d.year == currentYear.value && d.monthValue == m
        }.sumOf { it.amountMinor }
    }
    val maxAmount = (monthlyAmounts.values.maxOrNull() ?: 1L).toFloat()

    val barColor = if (type == TxType.INCOME) GREEN else RED
    var selectedMonth by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(type) {
            detectTapGestures { offset ->
                val step = size.width / 12
                val clicked = ((offset.x / step).toInt() + 1).coerceIn(1, 12)
                selectedMonth = if (selectedMonth == clicked) null else clicked
            }
        }) {
            val width = size.width
            val height = size.height
            val step = width / 12
            val barWidth = step * 0.6f
            monthlyAmounts.forEach { (month, amount) ->
                if (amount > 0) {
                    val alpha = if (selectedMonth == null || selectedMonth == month) 1f else 0.4f
                    val barHeight = (amount / maxAmount) * height
                    val x = (month - 1) * step + (step - barWidth) / 2
                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x, height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedMonth != null) {
            Text(
                "${getMonthName(selectedMonth!!, currentLang)}: ${monthlyAmounts[selectedMonth]?.money()} UZS",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("4", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("7", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("10", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("12", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CategoryPieChart(data: Map<String, Long>, lang: AppLanguage) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
    )
    val sortedData = data.entries.sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) {
            var startAngle = -90f
            sortedData.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / total) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true
                )
                startAngle += sweepAngle
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sortedData.take(4).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
