package uz.uchet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    onManageRecurring: () -> Unit,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    val recurring by vm.recurring.collectAsState()
    val confirmations by vm.confirmations.collectAsState()
    val pending by vm.pendingRecurring.collectAsState()

    val today = LocalDate.now()
    val todayStr = today.toString()
    val monthStr = YearMonth.from(today).toString()

    val confirmed = remember(recurring, confirmations, monthStr) {
        recurring.filter { r ->
            confirmations.any { it.recurringId == r.id && it.month == monthStr && it.status == "CONFIRMED" }
        }
    }
    val postponedToday = remember(recurring, confirmations, todayStr, monthStr) {
        recurring.filter { r ->
            val c = confirmations.find { it.recurringId == r.id && it.month == monthStr }
            c?.status == "POSTPONED" && c.lastPostponedDate == todayStr
        }
    }

    fun actualOf(r: RecurringEntity): Long =
        confirmations.find {
            it.recurringId == r.id && it.month == monthStr && it.status == "CONFIRMED"
        }?.actualAmountMinor ?: r.amountMinor

    val plannedIncome = recurring.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val plannedExpense = recurring.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val confirmedIncome = confirmed.filter { it.type == TxType.INCOME }.sumOf { actualOf(it) }
    val confirmedExpense = confirmed.filter { it.type == TxType.EXPENSE }.sumOf { actualOf(it) }

    var confirming by remember { mutableStateOf<RecurringEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("standard_forecast")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) }
                },
                actions = {
                    IconButton(onClick = onManageRecurring) {
                        Icon(Icons.Default.Settings, t("manage_recurring"))
                    }
                }
            )
        }
    ) { pad ->
        if (recurring.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Autorenew, null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    t("standard_forecast_empty"),
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onManageRecurring) { Text(t("manage_recurring")) }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${getMonthName(today.monthValue, currentLang)} ${today.year}",
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
                Spacer(Modifier.height(16.dp))
            }

            if (pending.isNotEmpty()) {
                item {
                    Text(
                        "${t("pending_confirmations")} (${pending.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YELLOW
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(pending, key = { "p_${it.id}" }) { r ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = YELLOW.copy(alpha = 0.10f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    (if (r.type == TxType.INCOME) "📈 " else "📉 ") +
                                        r.description.ifBlank {
                                            Translations.translateCategory(r.category, currentLang)
                                        },
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    r.amountMinor.money(),
                                    color = if (r.type == TxType.INCOME) GREEN else RED,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                Translations.translateCategory(r.category, currentLang),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { confirming = r },
                                    modifier = Modifier.weight(1f)
                                ) { Text(t("confirm")) }
                                OutlinedButton(
                                    onClick = { vm.postponeRecurring(r) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        if (r.type == TxType.INCOME) t("not_arrived")
                                        else t("not_spent")
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (confirmed.isNotEmpty()) {
                item {
                    Text(
                        "${t("confirmed")} (${confirmed.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GREEN
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(confirmed, key = { "c_${it.id}" }) { r ->
                    val actual = actualOf(r)
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = GREEN)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        r.description.ifBlank {
                                            Translations.translateCategory(r.category, currentLang)
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        Translations.translateCategory(r.category, currentLang),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Text(
                                "${if (r.type == TxType.INCOME) "+" else "-"}${actual.money()}",
                                color = if (r.type == TxType.INCOME) GREEN else RED,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (postponedToday.isNotEmpty()) {
                item {
                    Text(
                        "${t("postponed")} (${postponedToday.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(postponedToday, key = { "pp_${it.id}" }) { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        r.description.ifBlank {
                                            Translations.translateCategory(r.category, currentLang)
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        t("postponed_until_tomorrow"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Text(
                                r.amountMinor.money(),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    confirming?.let { r ->
        ConfirmRecurringDialog(
            recurring = r,
            currentLang = currentLang,
            onDismiss = { confirming = null },
            onConfirm = { amount ->
                vm.confirmRecurring(r, amount)
                confirming = null
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: Long, color: Color, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else null
        )
        Text(
            value.money(),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
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
    var amount by remember {
        mutableStateOf(
            BigDecimal(recurring.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (recurring.type == TxType.INCOME) t("confirm_income")
                else t("confirm_expense")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    recurring.description.ifBlank {
                        Translations.translateCategory(recurring.category, currentLang)
                    },
                    fontWeight = FontWeight.Medium
                )
                Text(
                    Translations.translateCategory(recurring.category, currentLang),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    "${t("planned")}: ${recurring.amountMinor.money()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(t("actual_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = RED, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching {
                    BigDecimal(amount.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull()
                if (amt == null || amt <= 0) {
                    error = t("error")
                    return@TextButton
                }
                onConfirm(amt)
            }) { Text(t("confirm")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("cancel")) }
        }
    )
}
