package uz.uchet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

enum class AnalyticsMode { EXPENSE, INCOME, OVERALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    txs: List<Tx>, 
    allTxs: List<Tx>, 
    currentLang: AppLanguage, 
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    
    // ★ T-004: Единый переключатель на 3 режима
    var selectedMode by remember { mutableStateOf(AnalyticsMode.EXPENSE) }

    val incomeTotal = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expenseTotal = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val netTotal = incomeTotal - expenseTotal

    // Для графиков по категориям (только для Расходов и Доходов)
    val categoryData = when (selectedMode) {
        AnalyticsMode.EXPENSE -> txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        AnalyticsMode.INCOME -> txs.filter { it.type == TxType.INCOME }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        AnalyticsMode.OVERALL -> emptyMap() // В общем режиме пирог не нужен, чтобы не путать
    }

    // Состояния для диалогов
    var selectedCategoryDetails by remember { mutableStateOf<String?>(null) }
    var showFullscreenPie by remember { mutableStateOf(false) }
    var showDailyDynamics by remember { mutableStateOf(false) } // ★ T-005

    Column(Modifier.padding(paddingValues).fillMaxSize().verticalScroll(rememberScrollState())) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) }
            Text(t("analytics"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // ★ T-004: 3 режима (Расходы, Доходы, Общая)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                SegmentedButton(
                    selected = selectedMode == AnalyticsMode.EXPENSE,
                    onClick = { selectedMode = AnalyticsMode.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) { Text(t("expense")) }
                SegmentedButton(
                    selected = selectedMode == AnalyticsMode.INCOME,
                    onClick = { selectedMode = AnalyticsMode.INCOME },
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) { Text(t("income")) }
                SegmentedButton(
                    selected = selectedMode == AnalyticsMode.OVERALL,
                    onClick = { selectedMode = AnalyticsMode.OVERALL },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) { Text(t("overall")) }
            }

            // Сводка
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (selectedMode == AnalyticsMode.EXPENSE || selectedMode == AnalyticsMode.OVERALL) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("expense"), fontWeight = FontWeight.Medium)
                            Text("-${expenseTotal.money()}", color = RED, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (selectedMode == AnalyticsMode.INCOME || selectedMode == AnalyticsMode.OVERALL) {
                        if (selectedMode == AnalyticsMode.OVERALL) Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("income"), fontWeight = FontWeight.Medium)
                            Text("+${incomeTotal.money()}", color = GREEN, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (selectedMode == AnalyticsMode.OVERALL) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("total"), fontWeight = FontWeight.Bold)
                            Text(
                                "${if (netTotal >= 0) "+" else ""}${netTotal.money()}",
                                color = if (netTotal >= 0) GREEN else RED,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Проверка на пустоту
            val hasData = when (selectedMode) {
                AnalyticsMode.EXPENSE -> expenseTotal > 0
                AnalyticsMode.INCOME -> incomeTotal > 0
                AnalyticsMode.OVERALL -> incomeTotal > 0 || expenseTotal > 0
            }

            if (!hasData) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BarChart, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(t("no_found"), color = Color.Gray)
                }
            } else {
                // ★ T-005: График по дням с кнопкой "Подробнее"
                Text(
                    when (selectedMode) {
                        AnalyticsMode.EXPENSE -> t("daily_chart")
                        AnalyticsMode.INCOME -> t("daily_chart_inc")
                        AnalyticsMode.OVERALL -> t("daily_dynamics")
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                DailyChart(
                    txs = txs, 
                    yearMonth = YearMonth.now(), 
                    mode = selectedMode, 
                    currentLang = currentLang,
                    onShowDetails = { showDailyDynamics = true } // ★ Вызов окна T-005
                )
                Spacer(Modifier.height(32.dp))

                // График по месяцам
                if (selectedMode != AnalyticsMode.OVERALL) {
                    Text(
                        if (selectedMode == AnalyticsMode.INCOME) t("monthly_chart_inc") else t("monthly_chart"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    MonthlyChart(allTxs = allTxs, type = if (selectedMode == AnalyticsMode.INCOME) TxType.INCOME else TxType.EXPENSE, currentLang = currentLang)
                    Spacer(Modifier.height(32.dp))
                }

                // Пирог по категориям (только для Расходов и Доходов)
                if (categoryData.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedMode == AnalyticsMode.INCOME) t("inc_by_cat") else t("exp_by_cat"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showFullscreenPie = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Развернуть")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    CategoryPieChart(
                        data = categoryData, 
                        lang = currentLang,
                        onCategoryClick = { cat -> selectedCategoryDetails = cat }
                    )
                }
            }
            Spacer(Modifier.height(80.dp)) 
        }
    }

    // ★ T-005: Диалог "Динамика по дням"
    if (showDailyDynamics) {
        DailyDynamicsDialog(
            txs = txs,
            yearMonth = YearMonth.now(),
            mode = selectedMode,
            currentLang = currentLang,
            onDismiss = { showDailyDynamics = false }
        )
    }

    // Диалог детализации (Drill-down)
    if (selectedCategoryDetails != null) {
        val cat = selectedCategoryDetails!!
        val catTxs = txs.filter { it.category == cat }.sortedByDescending { it.timestamp }
        
        AlertDialog(
            onDismissRequest = { selectedCategoryDetails = null },
            title = { Text("${Translations.translateCategory(cat, currentLang)} (${catTxs.size})") },
            text = {
                if (catTxs.isEmpty()) {
                    Text(t("no_found"))
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(catTxs) { tx ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (tx.description.isNotBlank()) tx.description else t("desc"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val date = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                    Text(date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Text(
                                    "${if (tx.type == TxType.INCOME) "+" else "-"}${tx.amountMinor.money()}",
                                    color = if (tx.type == TxType.INCOME) GREEN else RED,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedCategoryDetails = null }) { Text(t("close")) } }
        )
    }

    // Полноэкранный диалог пирога
    if (showFullscreenPie) {
        Dialog(
            onDismissRequest = { showFullscreenPie = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedMode == AnalyticsMode.INCOME) t("inc_by_cat") else t("exp_by_cat"),
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showFullscreenPie = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = t("close"))
                        }
                    }
                    
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                        LargePieChart(categoryData, currentLang)
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    val total = categoryData.values.sum().toFloat()
                    val colors = listOf(
                        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D),
                        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
                    )
                    categoryData.entries.sortedByDescending { it.value }.forEachIndexed { index, entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(16.dp).background(colors[index % colors.size], shape = CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                Translations.translateCategory(entry.key, currentLang),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${entry.value.money()} (${(entry.value / total * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ★ T-005: Новый диалог "Динамика по дням"
@Composable
fun DailyDynamicsDialog(
    txs: List<Tx>,
    yearMonth: YearMonth,
    mode: AnalyticsMode,
    currentLang: AppLanguage,
    onDismiss: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    val daysInMonth = yearMonth.lengthOfMonth()
    val zone = ZoneId.systemDefault()
    
    // Группируем по дням
    val dailyData = (1..daysInMonth).associateWith { day ->
        val dayTxs = txs.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().dayOfMonth == day }
        val inc = dayTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val exp = dayTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        inc to exp
    }.filter { (_, data) ->
        when (mode) {
            AnalyticsMode.EXPENSE -> data.second > 0
            AnalyticsMode.INCOME -> data.first > 0
            AnalyticsMode.OVERALL -> data.first > 0 || data.second > 0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("daily_dynamics"), style = MaterialTheme.typography.titleMedium) },
        text = {
            if (dailyData.isEmpty()) {
                Text(t("no_found"), color = Color.Gray)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(dailyData.entries.toList()) { entry ->
                        val day = entry.key
                        val (inc, exp) = entry.value
                        
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$day ${getMonthName(yearMonth.monthValue, currentLang).take(3)}", fontWeight = FontWeight.Medium)
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (mode == AnalyticsMode.EXPENSE || mode == AnalyticsMode.OVERALL) {
                                        if (exp > 0) Text("-${exp.money()}", color = RED, fontWeight = FontWeight.Bold)
                                        else if (mode == AnalyticsMode.OVERALL) Text("—", color = Color.Gray)
                                    }
                                    if (mode == AnalyticsMode.INCOME || mode == AnalyticsMode.OVERALL) {
                                        if (inc > 0) Text("+${inc.money()}", color = GREEN, fontWeight = FontWeight.Bold)
                                        else if (mode == AnalyticsMode.OVERALL) Text("—", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("close")) } }
    )
}

@Composable
fun DailyChart(
    txs: List<Tx>, 
    yearMonth: YearMonth, 
    mode: AnalyticsMode, 
    currentLang: AppLanguage,
    onShowDetails: () -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val zone = ZoneId.systemDefault()
    
    // Если режим "Общая", показываем суммарный график (доход + расход) или только расход, чтобы не путать
    val dailyAmounts = (1..daysInMonth).associateWith { day ->
        val dayTxs = txs.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().dayOfMonth == day }
        when (mode) {
            AnalyticsMode.EXPENSE -> dayTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
            AnalyticsMode.INCOME -> dayTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            AnalyticsMode.OVERALL -> dayTxs.sumOf { if (it.type == TxType.EXPENSE) -it.amountMinor else it.amountMinor } // Для общего графика можно показывать сальдо, но это сложно. Сделаем просто сумму всех транзакций по модулю.
        }
    }
    val maxAmount = (dailyAmounts.values.maxOrNull() ?: 1L).toFloat()

    val barColor = when (mode) {
        AnalyticsMode.EXPENSE -> RED
        AnalyticsMode.INCOME -> GREEN
        AnalyticsMode.OVERALL -> BLUE
    }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(mode) {
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
        
        // Вывод выбранного дня + Кнопка "Подробнее"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (selectedDay != null) {
                val amount = dailyAmounts[selectedDay] ?: 0L
                Text(
                    "${Translations.t("day", currentLang)}: $selectedDay — ${amount.money()} UZS",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            TextButton(onClick = onShowDetails) {
                Text(Translations.t("view_text", currentLang), fontSize = 12.sp)
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
            d.year == currentYear.value && d.monthValue == m && it.type == type
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
fun CategoryPieChart(
    data: Map<String, Long>, 
    lang: AppLanguage,
    onCategoryClick: (String) -> Unit
) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
    )
    val sortedData = data.entries.sortedByDescending { it.value }
    
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = Modifier.size(110.dp).padding(8.dp).pointerInput(data) {
                detectTapGestures { offset ->
                    // Клики по легенде надежнее
                }
            }
        ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(entry.key) }
                        .padding(vertical = 2.dp)
                ) {
                    Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun LargePieChart(data: Map<String, Long>, lang: AppLanguage) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return
    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
    )
    val sortedData = data.entries.sortedByDescending { it.value }

    Canvas(modifier = Modifier.fillMaxSize()) {
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
        drawCircle(color = Color.White, radius = size.minDimension / 3f)
    }
}
