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

private val FORECAST_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM")

// ---------- 1. Диалог создания/редактирования прогноза ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastDialog(
    initial: ForecastPlanEntity?,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (ForecastPlanEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var months by remember { mutableStateOf(initial?.months ?: 12) }
    var startMonth by remember { mutableStateOf(initial?.startMonth ?: YearMonth.now().format(FORECAST_MONTH_FMT)) }
    var showMonthsMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("forecast_new") else t("forecast_edit")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text(t("forecast_name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { showMonthsMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${t("forecast_months")}: $months")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showMonthsMenu, onDismissRequest = { showMonthsMenu = false }) {
                        listOf(3, 6, 9, 12, 24, 36).forEach { m ->
                            DropdownMenuItem(
                                text = { Text("$m") },
                                onClick = { months = m; showMonthsMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = startMonth, onValueChange = { startMonth = it; error = null },
                    label = { Text(t("forecast_start")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { error = t("error"); return@TextButton }
                if (!startMonth.matches(Regex("\\d{4}-\\d{2}"))) { error = t("error"); return@TextButton }
                onSave(ForecastPlanEntity(
                    id = initial?.id ?: 0,
                    name = name.trim(),
                    months = months,
                    startMonth = startMonth,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                ))
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

// ---------- 2. Диалог операции плана (доход/расход) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastItemDialog(
    initial: ForecastItemEntity?,
    forecastId: Long,
    forecastStart: String,
    forecastEnd: String,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (ForecastItemEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember {
        mutableStateOf(initial?.amountMinor?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "")
    }
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var startMonth by remember { mutableStateOf(initial?.startMonth ?: forecastStart) }
    var endMonth by remember { mutableStateOf(initial?.endMonth ?: forecastEnd) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("forecast_item_new") else t("forecast_item_edit")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text(t("forecast_item_name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(t("expense"), color = if (type == TxType.EXPENSE) RED else Color.Unspecified)
                    }
                    SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(t("income"), color = if (type == TxType.INCOME) GREEN else Color.Unspecified)
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; error = null },
                    label = { Text(t("forecast_item_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startMonth, onValueChange = { startMonth = it; error = null },
                    label = { Text(t("forecast_item_start")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endMonth, onValueChange = { endMonth = it; error = null },
                    label = { Text(t("forecast_item_end")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching {
                    BigDecimal(amount.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull()
                if (name.isBlank()) { error = t("error"); return@TextButton }
                if (amt == null || amt <= 0) { error = t("error"); return@TextButton }
                if (!startMonth.matches(Regex("\\d{4}-\\d{2}")) || !endMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                    error = t("error"); return@TextButton
                }
                onSave(ForecastItemEntity(
                    id = initial?.id ?: 0,
                    forecastId = forecastId,
                    name = name.trim(),
                    type = type,
                    amountMinor = amt,
                    startMonth = startMonth,
                    endMonth = endMonth,
                    isFixed = initial?.isFixed ?: true
                ))
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

// ---------- 3. Диалог вклада ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositDialog(
    initial: ForecastDepositEntity?,
    forecastId: Long,
    forecastStart: String,
    forecastEnd: String,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (ForecastDepositEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var initialAmount by remember {
        mutableStateOf(initial?.initialAmountMinor?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "")
    }
    var rate by remember { mutableStateOf(initial?.annualRatePercent?.toString() ?: "18.0") }
    var startMonth by remember { mutableStateOf(initial?.startMonth ?: forecastStart) }
    var endMonth by remember { mutableStateOf(initial?.endMonth ?: forecastEnd) }
    var monthlyContrib by remember {
        mutableStateOf(initial?.monthlyContributionMinor?.takeIf { it > 0 }?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "")
    }
    var monthlyWd by remember {
        mutableStateOf(initial?.monthlyWithdrawalMinor?.takeIf { it > 0 }?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "")
    }
    var cap by remember { mutableStateOf(initial?.capitalization ?: CapitalizationType.MONTHLY) }
    var showCapMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun capLabel(c: CapitalizationType): String = when (c) {
        CapitalizationType.MONTHLY -> t("deposit_cap_monthly")
        CapitalizationType.DAILY -> t("deposit_cap_daily")
        CapitalizationType.END_OF_TERM -> t("deposit_cap_end")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("deposit_new") else t("deposit_edit")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text(t("deposit_name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = initialAmount, onValueChange = { initialAmount = it; error = null },
                    label = { Text(t("deposit_initial")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it; error = null },
                    label = { Text(t("deposit_rate")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startMonth, onValueChange = { startMonth = it; error = null },
                    label = { Text(t("deposit_start")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endMonth, onValueChange = { endMonth = it; error = null },
                    label = { Text(t("deposit_end")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monthlyContrib, onValueChange = { monthlyContrib = it; error = null },
                    label = { Text(t("deposit_monthly_contrib")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monthlyWd, onValueChange = { monthlyWd = it; error = null },
                    label = { Text(t("deposit_monthly_wd")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { showCapMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${t("deposit_capitalization")}: ${capLabel(cap)}")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showCapMenu, onDismissRequest = { showCapMenu = false }) {
                        CapitalizationType.values().forEach { c ->
                            DropdownMenuItem(
                                text = { Text(capLabel(c)) },
                                onClick = { cap = c; showCapMenu = false }
                            )
                        }
                    }
                }
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val init = runCatching {
                    BigDecimal(initialAmount.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull()
                val r = rate.toDoubleOrNull()
                val contrib = runCatching {
                    BigDecimal(monthlyContrib.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull() ?: 0L
                val wd = runCatching {
                    BigDecimal(monthlyWd.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull() ?: 0L
                if (name.isBlank()) { error = t("error"); return@TextButton }
                if (init == null || init <= 0) { error = t("error"); return@TextButton }
                if (r == null || r <= 0) { error = t("error"); return@TextButton }
                if (!startMonth.matches(Regex("\\d{4}-\\d{2}")) || !endMonth.matches(Regex("\\d{4}-\\d{2}"))) {
                    error = t("error"); return@TextButton
                }
                onSave(ForecastDepositEntity(
                    id = initial?.id ?: 0,
                    forecastId = forecastId,
                    name = name.trim(),
                    initialAmountMinor = init,
                    annualRatePercent = r,
                    startMonth = startMonth,
                    endMonth = endMonth,
                    monthlyContributionMinor = contrib,
                    monthlyWithdrawalMinor = wd,
                    capitalization = cap
                ))
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

// ---------- 4. Диалог разовой операции по вкладу ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositOpDialog(
    deposit: ForecastDepositEntity,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (ForecastDepositOpEntity) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var month by remember { mutableStateOf(deposit.startMonth) }
    var amount by remember { mutableStateOf("") }
    var isWithdrawal by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("deposit_op_new")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!isWithdrawal, { isWithdrawal = false }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(t("deposit_op_add"), color = if (!isWithdrawal) GREEN else Color.Unspecified)
                    }
                    SegmentedButton(isWithdrawal, { isWithdrawal = true }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(t("deposit_op_withdraw"), color = if (isWithdrawal) RED else Color.Unspecified)
                    }
                }
                OutlinedTextField(
                    value = month, onValueChange = { month = it; error = null },
                    label = { Text(t("deposit_op_month")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; error = null },
                    label = { Text(t("deposit_op_amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text(t("deposit_op_desc")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = runCatching {
                    BigDecimal(amount.replace(" ", "").replace(',', '.'))
                        .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                }.getOrNull()
                if (amt == null || amt <= 0) { error = t("error"); return@TextButton }
                if (!month.matches(Regex("\\d{4}-\\d{2}"))) { error = t("error"); return@TextButton }
                onSave(ForecastDepositOpEntity(
                    id = 0,
                    depositId = deposit.id,
                    month = month,
                    amountMinor = if (isWithdrawal) -amt else amt,
                    description = desc.trim()
                ))
            }) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

// ---------- 5. Диалог истории вклада ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositTimelineDialog(
    deposit: ForecastDepositEntity,
    ops: List<ForecastDepositOpEntity>,
    currentLang: AppLanguage,
    onSaveOp: (ForecastDepositOpEntity) -> Unit,
    onDeleteOp: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    val timeline = remember(deposit, ops) {
        ForecastCalculator.calculateDepositTimeline(deposit, ops)
    }
    var showOpDialog by remember { mutableStateOf(false) }
    var opToDelete by remember { mutableStateOf<ForecastDepositOpEntity?>(null) }

    if (opToDelete != null) {
        AlertDialog(
            onDismissRequest = { opToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteOp(opToDelete!!.id)
                    opToDelete = null
                }) { Text(t("yes"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { opToDelete = null }) { Text(t("no")) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t("deposit_timeline")}: ${deposit.name}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "${deposit.initialAmountMinor.money()} • ${deposit.annualRatePercent}% • " +
                        when (deposit.capitalization) {
                            CapitalizationType.MONTHLY -> t("deposit_cap_monthly")
                            CapitalizationType.DAILY -> t("deposit_cap_daily")
                            CapitalizationType.END_OF_TERM -> t("deposit_cap_end")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(timeline) { s ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        getMonthName(s.month.monthValue, currentLang) + " " + s.month.year,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "${s.endBalance.money()}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GREEN
                                    )
                                }
                                Text(
                                    "${t("deposit_interest")}: +${s.interest.money()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GREEN
                                )
                                if (s.contribution > 0) Text(
                                    "+${s.contribution.money()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GREEN
                                )
                                if (s.withdrawal > 0) Text(
                                    "-${s.withdrawal.money()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RED
                                )
                                if (s.oneTimeOps != 0L) Text(
                                    "${if (s.oneTimeOps > 0) "+" else ""}${s.oneTimeOps.money()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (s.oneTimeOps > 0) GREEN else RED
                                )
                                if (s.accruedInterestNotYetPaid > 0) {
                                    Text(
                                        "${t("deposit_interest")} (${t("deposit_cap_end")}): ${s.accruedInterestNotYetPaid.money()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = YELLOW
                                    )
                                }
                            }
                        }
                    }

                    if (ops.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t("deposit_ops") + ":",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        items(ops) { op ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(op.month, style = MaterialTheme.typography.bodySmall)
                                    if (op.description.isNotBlank()) Text(
                                        op.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    "${if (op.amountMinor > 0) "+" else ""}${op.amountMinor.money()}",
                                    color = if (op.amountMinor > 0) GREEN else RED,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(
                                    onClick = { opToDelete = op },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete, null,
                                        tint = RED, modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = { showOpDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("deposit_op_new"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("close")) } }
    )

    if (showOpDialog) {
        DepositOpDialog(
            deposit = deposit,
            currentLang = currentLang,
            onDismiss = { showOpDialog = false },
            onSave = { onSaveOp(it); showOpDialog = false }
        )
    }
}
