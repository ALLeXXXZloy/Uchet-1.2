package uz.uchet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardForecastScreen(
    vm: VM,
    currentLang: AppLanguage,
    paddingValues: PaddingValues,
    onManageRecurring: () -> Unit,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    val recurring by vm.recurring.collectAsState()
    val confirmations by vm.confirmations.collectAsState()

    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    // ★ ИСПРАВЛЕНО: Состояния вынесены наверх, чтобы быть доступными внутри LazyColumn
    var editingRecurring by remember { mutableStateOf<RecurringEntity?>(null) }
    var confirmingRecurring by remember { mutableStateOf<RecurringEntity?>(null) }

    // Формируем список месяцев (например, от -6 до +6)
    val monthsList = remember {
        (-6..6).map { currentMonth.plusMonths(it.toLong()) }
    }

    // Данные для выбранного месяца
    val monthStr = selectedMonth.toString()
    val isPastMonth = selectedMonth < currentMonth
    
    val confirmationsForMonth = remember(confirmations, monthStr) {
        confirmations.filter { it.month == monthStr }
    }

    val plannedIncome = recurring.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val plannedExpense = recurring.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    
    val confirmedIncome = recurring.filter { it.type == TxType.INCOME }.sumOf { r ->
        confirmationsForMonth.find { it.recurringId == r.id && it.status == "CONFIRMED" }?.actualAmountMinor ?: 0L
    }
    val confirmedExpense = recurring.filter { it.type == TxType.EXPENSE }.sumOf { r ->
        confirmationsForMonth.find { it.recurringId == r.id && it.status == "CONFIRMED" }?.actualAmountMinor ?: 0L
    }

    Column(Modifier.fillMaxSize().padding(paddingValues)) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) }
            Text(t("standard_forecast"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onManageRecurring) {
                Icon(Icons.Default.Settings, t("manage_recurring"))
            }
        }

        if (recurring.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Autorenew, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text(t("standard_forecast_empty"), color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onManageRecurring) { Text(t("manage_recurring")) }
            }
            return@Column
        }

        // ★ Скользящее окно (выбор месяца)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            items(monthsList) { month ->
                val isSelected = month == selectedMonth
                val isCurrent = month == currentMonth
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedMonth = month },
                    label = { 
                        Text(
                            text = "${getMonthName(month.monthValue, currentLang).take(3)} ${month.year}",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Сводка за выбранный месяц
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${getMonthName(selectedMonth.monthValue, currentLang)} ${selectedMonth.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        SummaryRow(t("planned_income"), plannedIncome, GREEN)
                        SummaryRow(t("planned_expense"), plannedExpense, RED)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SummaryRow(t("confirmed_income"), confirmedIncome, GREEN, bold = true)
                        SummaryRow(t("confirmed_expense"), confirmedExpense, RED, bold = true)
                    }
                }
            }

            // Список операций
            items(recurring, key = { it.id }) { r ->
                val conf = confirmationsForMonth.find { it.recurringId == r.id }
                val status = conf?.status
                val actualAmount = conf?.actualAmountMinor ?: r.amountMinor

                // Логика доступных действий (только для текущего месяца или будущего)
                val canAct = !isPastMonth
                val isDayPassed = if (selectedMonth == currentMonth) {
                    r.dayOfMonth <= today.dayOfMonth
                } else {
                    true // В будущем месяце все дни "пройдут"
                }

                val isConfirmed = status == "CONFIRMED"
                val isSkipped = status == "SKIPPED"
                val isPostponed = status == "POSTPONED" && conf?.lastPostponedDate == today.toString()

                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isConfirmed -> GREEN.copy(alpha = 0.05f)
                            isSkipped -> Color.Gray.copy(alpha = 0.05f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (r.type == TxType.INCOME) "📈 " else "📉 ") + r.description.ifBlank { Translations.translateCategory(r.category, currentLang) },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Кнопка редактирования
                            IconButton(onClick = { editingRecurring = r }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            Translations.translateCategory(r.category, currentLang) + " · " + r.dayOfMonth + " число",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${r.amountMinor.money()} UZS",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (r.type == TxType.INCOME) GREEN else RED
                        )

                        // Блок действий / статуса
                        Spacer(Modifier.height(8.dp))
                        when {
                            // Прошлый месяц
                            isPastMonth -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (isConfirmed) GREEN else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isConfirmed) "Подтверждено (${actualAmount.money()})" else if (isSkipped) "Пропущено" else "Нет данных",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isConfirmed) GREEN else Color.Gray
                                    )
                                }
                            }
                            // Текущий или будущий месяц
                            else -> {
                                when {
                                    isConfirmed -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = GREEN, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Подтверждено: ${actualAmount.money()}", style = MaterialTheme.typography.bodySmall, color = GREEN)
                                        }
                                    }
                                    isPostponed -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, null, tint = YELLOW, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(t("postponed_until_tomorrow"), style = MaterialTheme.typography.bodySmall, color = YELLOW)
                                        }
                                    }
                                    isDayPassed && canAct -> {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { 
                                                    // Открываем диалог подтверждения
                                                    confirmingRecurring = r
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(t("confirm")) }
                                            OutlinedButton(
                                                onClick = { vm.skipRecurring(r) },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(t("not_spent")) }
                                        }
                                    }
                                    else -> {
                                        // День еще не наступил
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Ожидается ${r.dayOfMonth} числа", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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

    // Диалог редактирования
    if (editingRecurring != null) {
        EditRecurringDialog(
            recurring = editingRecurring!!,
            currentLang = currentLang,
            onDismiss = { editingRecurring = null },
            onSave = { updated ->
                vm.saveRecurring(updated)
                editingRecurring = null
            }
        )
    }

    // Диалог подтверждения
    if (confirmingRecurring != null) {
        ConfirmRecurringDialog(
            recurring = confirmingRecurring!!,
            currentLang = currentLang,
            onDismiss = { confirmingRecurring = null },
            onConfirm = { amount ->
                vm.confirmRecurring(confirmingRecurring!!, amount)
                confirmingRecurring = null
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: Long, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else null)
        Text(value.money(), color = color, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ConfirmRecurringDialog(
    recurring: RecurringEntity,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var amount by remember { mutableStateOf(BigDecimal(recurring.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recurring.type == TxType.INCOME) t("confirm_income") else t("confirm_expense")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recurring.description.ifBlank { Translations.translateCategory(recurring.category, currentLang) }, fontWeight = FontWeight.Medium)
                Text(t("planned") + ": " + recurring.amountMinor.money(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(t("actual_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                if (amt == null || amt <= 0) { error = t("error"); return@TextButton }
                onConfirm(amt)
            }) { Text(t("confirm")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@Composable
private fun EditRecurringDialog(
    recurring: RecurringEntity,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (RecurringEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var amount by remember { mutableStateOf(BigDecimal(recurring.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()) }
    var description by remember { mutableStateOf(recurring.description) }
    var dayOfMonth by remember { mutableStateOf(recurring.dayOfMonth.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать операцию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(t("amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(t("desc")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(t("day")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                val day = dayOfMonth.toIntOrNull()
                if (amt == null || amt <= 0 || day == null || day !in 1..31) { error = t("error"); return@TextButton }
                onSave(recurring.copy(amountMinor = amt, description = description.trim(), dayOfMonth = day))
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}
