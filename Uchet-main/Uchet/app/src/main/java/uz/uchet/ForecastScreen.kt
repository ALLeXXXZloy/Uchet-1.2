package uz.uchet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    forecast: ForecastPlanEntity,
    currentLang: AppLanguage,
    vm: VM,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }

    val itemsFlow = remember(forecast.id) { vm.observeForecastItems(forecast.id) }
    val depositsFlow = remember(forecast.id) { vm.observeForecastDeposits(forecast.id) }
    val opsFlow = remember(forecast.id) { vm.observeAllOpsForForecast(forecast.id) }

    val items by itemsFlow.collectAsState(initial = emptyList())
    val deposits by depositsFlow.collectAsState(initial = emptyList())
    val allOps by opsFlow.collectAsState(initial = emptyList())
    
    // ДОБАВЛЕНО: Получаем курсы валют для конвертации
    val rates by vm.rates.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var showAddItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ForecastItemEntity?>(null) }
    var showAddDeposit by remember { mutableStateOf(false) }
    var editingDeposit by remember { mutableStateOf<ForecastDepositEntity?>(null) }
    var openedDeposit by remember { mutableStateOf<ForecastDepositEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<ForecastItemEntity?>(null) }
    var depositToDelete by remember { mutableStateOf<ForecastDepositEntity?>(null) }

    val openedDepositOpsFlow = remember(openedDeposit?.id) {
        if (openedDeposit != null) vm.observeDepositOps(openedDeposit!!.id)
        else flowOf(emptyList())
    }
    val openedDepositOps by openedDepositOpsFlow.collectAsState(initial = emptyList())

    // Предвычисляем весь прогноз один раз
    val calc = remember(forecast, items, deposits, allOps) {
        ForecastCalculator.calculate(forecast, items, deposits, allOps)
    }

    val forecastEnd = remember(forecast) {
        YearMonth.parse(forecast.startMonth)
            .plusMonths(forecast.months.toLong() - 1)
            .toString()
    }

    // Диалоги удаления
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForecastItem(itemToDelete!!.id)
                    itemToDelete = null
                }) { Text(t("yes"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text(t("no")) } }
        )
    }
    if (depositToDelete != null) {
        AlertDialog(
            onDismissRequest = { depositToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForecastDeposit(depositToDelete!!.id)
                    depositToDelete = null
                }) { Text(t("yes"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { depositToDelete = null }) { Text(t("no")) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(forecast.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) }
                }
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> FloatingActionButton(onClick = { showAddItem = true }) {
                    Icon(Icons.Default.Add, t("forecast_item_new"))
                }
                1 -> FloatingActionButton(onClick = { showAddDeposit = true }) {
                    Icon(Icons.Default.Add, t("deposit_new"))
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(t("forecast_tab_plan")) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(t("forecast_tab_deposits")) }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(t("forecast_tab_calc")) }
                )
            }

            when (tab) {
                0 -> PlanTab(
                    items = items,
                    currentLang = currentLang,
                    onEdit = { editingItem = it },
                    onDelete = { itemToDelete = it }
                )
                1 -> DepositsTab(
                    deposits = deposits,
                    currentLang = currentLang,
                    onOpen = { openedDeposit = it },
                    onEdit = { editingDeposit = it },
                    onDelete = { depositToDelete = it }
                )
                2 -> CalcTab(
                    calc = calc,
                    currentLang = currentLang
                )
            }
        }
    }

    // ----- ДИАЛОГ ОПЕРАЦИИ ПЛАНА (с логикой разделения) -----
    if (showAddItem || editingItem != null) {
        ForecastItemDialog(
            initial = editingItem,
            forecastId = forecast.id,
            forecastStart = forecast.startMonth,
            forecastEnd = forecastEnd,
            rates = rates, // Передаем курсы
            currentLang = currentLang,
            onDismiss = { showAddItem = false; editingItem = null },
            onSave = { item, splitMonth ->
                if (splitMonth != null && editingItem != null && splitMonth > editingItem!!.startMonth) {
                    // ЛОГИКА РАЗДЕЛЕНИЯ:
                    // 1. Обрезаем старую операцию до месяца перед разделением
                    val monthBefore = YearMonth.parse(splitMonth).minusMonths(1).toString()
                    if (monthBefore >= editingItem!!.startMonth) {
                        vm.saveForecastItem(editingItem!!.copy(endMonth = monthBefore))
                    } else {
                        // Если разделение началось с самого начала, удаляем старую
                        vm.deleteForecastItem(editingItem!!.id)
                    }
                    // 2. Создаем новую операцию с новыми параметрами
                    vm.saveForecastItem(item.copy(id = 0, startMonth = splitMonth))
                } else {
                    // Обычное сохранение (без разделения)
                    vm.saveForecastItem(item)
                }
                showAddItem = false
                editingItem = null
            }
        )
    }

    // ----- ДИАЛОГ ВКЛАДА -----
    if (showAddDeposit || editingDeposit != null) {
        DepositDialog(
            initial = editingDeposit,
            forecastId = forecast.id,
            forecastStart = forecast.startMonth,
            forecastEnd = forecastEnd,
            rates = rates, // Передаем курсы
            currentLang = currentLang,
            onDismiss = { showAddDeposit = false; editingDeposit = null },
            onSave = {
                vm.saveForecastDeposit(it)
                showAddDeposit = false
                editingDeposit = null
            }
        )
    }

    // ----- ДИАЛОГ ИСТОРИИ ВКЛАДА -----
    if (openedDeposit != null) {
        DepositTimelineDialog(
            deposit = openedDeposit!!,
            ops = openedDepositOps,
            currentLang = currentLang,
            onSaveOp = { vm.saveDepositOp(it) },
            onDeleteOp = { vm.deleteDepositOp(it) },
            onDismiss = { openedDeposit = null }
        )
    }
}

// ---------- Вкладка «План» ----------
@Composable
private fun PlanTab(
    items: List<ForecastItemEntity>,
    currentLang: AppLanguage,
    onEdit: (ForecastItemEntity) -> Unit,
    onDelete: (ForecastItemEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    if (items.isEmpty()) {
        EmptyState(Icons.Default.List, t("forecast_items_empty"))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${item.startMonth} — ${item.endMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    
                    // Показываем исходную сумму и валюту, если она не UZS
                    val displayAmount = if (item.currency != "UZS" && item.originalAmountMinor != null) {
                        val orig = java.math.BigDecimal(item.originalAmountMinor).movePointLeft(2).toPlainString()
                        "$orig ${item.currency}"
                    } else {
                        "${item.amountMinor.money()} UZS"
                    }
                    
                    Text(
                        "${if (item.type == TxType.INCOME) "+" else "-"}$displayAmount",
                        color = if (item.type == TxType.INCOME) GREEN else RED,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, null, tint = RED)
                    }
                }
            }
        }
    }
}

// ---------- Вкладка «Вклады» ----------
@Composable
private fun DepositsTab(
    deposits: List<ForecastDepositEntity>,
    currentLang: AppLanguage,
    onOpen: (ForecastDepositEntity) -> Unit,
    onEdit: (ForecastDepositEntity) -> Unit,
    onDelete: (ForecastDepositEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    if (deposits.isEmpty()) {
        EmptyState(Icons.Default.Savings, t("forecast_deposits_empty"))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(deposits, key = { it.id }) { d ->
            val capLabel = when (d.capitalization) {
                CapitalizationType.MONTHLY -> Translations.t("deposit_cap_monthly", currentLang)
                CapitalizationType.DAILY -> Translations.t("deposit_cap_daily", currentLang)
                CapitalizationType.END_OF_TERM -> Translations.t("deposit_cap_end", currentLang)
            }
            val displayAmount = if (d.currency != "UZS" && d.originalInitialAmountMinor != null) {
                val orig = java.math.BigDecimal(d.originalInitialAmountMinor).movePointLeft(2).toPlainString()
                "$orig ${d.currency}"
            } else {
                "${d.initialAmountMinor.money()} UZS"
            }
            
            Card(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onOpen(d) }
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium)
                        Text(
                            "$displayAmount • ${d.annualRatePercent}% • $capLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            "${d.startMonth} — ${d.endMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = { onEdit(d) }) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDelete(d) }) {
                        Icon(Icons.Default.Delete, null, tint = RED)
                    }
                }
            }
        }
    }
}

// ---------- Вкладка «Прогноз» (расчёт) ----------
@Composable
private fun CalcTab(
    calc: List<ForecastCalculator.MonthForecast>,
    currentLang: AppLanguage
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(calc) { data ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (data.runningBalance < 0)
                        RED.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            getMonthName(data.month.monthValue, currentLang) + " " + data.month.year,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${data.runningBalance.money()} UZS",
                            fontWeight = FontWeight.Bold,
                            color = if (data.runningBalance >= 0) GREEN else RED
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("+${data.income.money()}", color = GREEN, style = MaterialTheme.typography.bodySmall)
                        Text("-${data.expense.money()}", color = RED, style = MaterialTheme.typography.bodySmall)
                    }
                    if (data.depositPortfolioChange != 0L) {
                        Text(
                            "💰 ${if (data.depositPortfolioChange > 0) "+" else ""}${data.depositPortfolioChange.money()} (${t("forecast_tab_deposits")})",
                            color = if (data.depositPortfolioChange >= 0) GREEN else RED,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// ---------- Пустое состояние ----------
@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(text, color = Color.Gray, textAlign = TextAlign.Center)
    }
}
