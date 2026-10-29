package uz.uchet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    planItems: List<PlanItemEntity>,
    currentLang: AppLanguage,
    onSave: (PlanItemEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PlanItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<PlanItemEntity?>(null) }

    val months = remember {
        val list = mutableListOf<YearMonth>()
        var current = YearMonth.now()
        for (i in 0..11) { list.add(current); current = current.plusMonths(1) }
        list
    }

    val monthlyPlans = remember(planItems) {
        months.associateWith { month ->
            planItems.filter { item ->
                try {
                    val start = YearMonth.parse(item.startMonth)
                    val end = YearMonth.parse(item.endMonth)
                    month >= start && month <= end
                } catch (e: Exception) { false }
            }
        }
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDelete(itemToDelete!!.id); itemToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text(t("no")) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("forecast")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, t("plan_add"))
            }
        }
    ) { pad ->
        if (planItems.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text(t("plan_empty"), color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                var runningBalance = 0L
                items(months) { month ->
                    val itemsForMonth = monthlyPlans[month] ?: emptyList()
                    val income = itemsForMonth.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
                    val expense = itemsForMonth.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
                    val monthBalance = income - expense
                    runningBalance += monthBalance

                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (runningBalance < 0) RED.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    getMonthName(month.monthValue, currentLang) + " " + month.year,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${runningBalance.money()} UZS",
                                    fontWeight = FontWeight.Bold,
                                    color = if (runningBalance >= 0) GREEN else RED
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("+${income.money()}", color = GREEN, style = MaterialTheme.typography.bodySmall)
                                Text("-${expense.money()}", color = RED, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "= ${monthBalance.money()}",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (monthBalance >= 0) GREEN else RED
                                )
                            }
                            if (itemsForMonth.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                itemsForMonth.forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                if (item.type == TxType.INCOME) t("income") else t("expense"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${if (item.type == TxType.INCOME) "+" else "-"}${item.amountMinor.money()}",
                                                color = if (item.type == TxType.INCOME) GREEN else RED,
                                                fontWeight = FontWeight.Medium
                                            )
                                            IconButton(onClick = { editingItem = item }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { itemToDelete = item }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = RED, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingItem != null) {
        PlanItemDialog(
            item = editingItem,
            currentLang = currentLang,
            onDismiss = { showAddDialog = false; editingItem = null },
            onSave = { onSave(it); showAddDialog = false; editingItem = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanItemDialog(item: PlanItemEntity?, currentLang: AppLanguage, onDismiss: () -> Unit, onSave: (PlanItemEntity) -> Unit) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var amount by remember { mutableStateOf(item?.amountMinor?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "") }
    var type by remember { mutableStateOf(item?.type ?: TxType.EXPENSE) }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
    var startMonth by remember { mutableStateOf(item?.startMonth ?: YearMonth.now().format(formatter)) }
    var endMonth by remember { mutableStateOf(item?.endMonth ?: YearMonth.now().plusMonths(11).format(formatter)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) t("plan_new") else t("plan_edit")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(t("plan_name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }
                    SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text(t("plan_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startMonth, onValueChange = { startMonth = it },
                    label = { Text(t("plan_start")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endMonth, onValueChange = { endMonth = it },
                    label = { Text(t("plan_end")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching {
                    BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull()
                if (name.isNotBlank() && amt != null && amt > 0
                    && startMonth.matches(Regex("\\d{4}-\\d{2}"))
                    && endMonth.matches(Regex("\\d{4}-\\d{2}"))
                ) {
                    onSave(PlanItemEntity(
                        id = item?.id ?: 0,
                        name = name.trim(),
                        type = type,
                        amountMinor = amt,
                        startMonth = startMonth,
                        endMonth = endMonth,
                        isFixed = true
                    ))
                }
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}
