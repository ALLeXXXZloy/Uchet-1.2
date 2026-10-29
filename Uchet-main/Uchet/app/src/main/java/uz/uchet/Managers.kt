package uz.uchet

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun GoalManagerDialog(goals: List<SavingsGoalEntity>, cards: List<CardEntity>, lang: AppLanguage, onSaveGoal: (SavingsGoalEntity) -> Unit, onDeleteGoal: (SavingsGoalEntity) -> Unit, onDeposit: (SavingsGoalEntity, String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var targetAmount by remember { mutableStateOf("") }
    var depositGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }; var depositAmount by remember { mutableStateOf("") }
    var depositCardId by remember { mutableStateOf(cards.firstOrNull()?.id ?: "CASH") }
    val t = { key: String -> Translations.t(key, lang) }
    val currentDepositGoal = depositGoal

    if (currentDepositGoal != null) {
        AlertDialog(onDismissRequest = { depositGoal = null }, title = { Text("${t("deposit")}: ${currentDepositGoal.name}") },
            text = { Column { OutlinedTextField(value = depositAmount, onValueChange = { depositAmount = it }, label = { Text(t("deposit_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text(t("source_card") + ":", style = MaterialTheme.typography.labelMedium); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = depositCardId == c.id, onClick = { depositCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) }; FilterChip(selected = depositCardId == "CASH", onClick = { depositCardId = "CASH" }, label = { Text(t("cash")) }) } } },
            confirmButton = { TextButton(onClick = { val amountMinor = runCatching { BigDecimal(depositAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (amountMinor != null && amountMinor > 0) { onDeposit(currentDepositGoal, depositCardId, amountMinor); depositGoal = null; depositAmount = "" } }) { Text(t("deposit")) } },
            dismissButton = { TextButton(onClick = { depositGoal = null }) { Text(t("cancel")) } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(t("goals")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("add_goal") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("goal_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text(t("target_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                Button(onClick = { val targetMinor = runCatching { BigDecimal(targetAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (name.isNotBlank() && targetMinor != null && targetMinor > 0) { onSaveGoal(SavingsGoalEntity(name = name.trim(), targetAmountMinor = targetMinor)); name = ""; targetAmount = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add_goal")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("goals") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (goals.isEmpty()) Text(t("no_found"), color = Color.Gray)
                goals.forEach { goal -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(goal.name, fontWeight = FontWeight.Bold); Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton(onClick = { depositGoal = goal }) { Icon(Icons.Default.Add, t("deposit"), tint = GREEN) }; IconButton(onClick = { onDeleteGoal(goal) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
            } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
    }
}

@Composable
fun CardManagerDialog(cards: List<CardEntity>, cardBalances: Map<String, Long>, allTxs: List<Tx>, onSave: (CardEntity) -> Unit, onDelete: (String) -> Unit, onAdjustBalance: (String, Long) -> Unit, currentLang: AppLanguage, rates: List<ExchangeRateEntity>, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var initialBalance by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("UZS") }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }; var actualBalanceInput by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, currentLang) }

    val voiceCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                VoiceParser.parseCard(spoken)?.let { parsed ->
                    last4 = parsed.last4
                    name = parsed.bankName?.let { bn -> "${getShortBankName(bn)} ${parsed.last4}" }
                        ?: "Bank ${parsed.last4}"
                }
            }
        }
    }

    fun startVoiceCard() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLang == AppLanguage.UZ) "uz-UZ" else "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, t("voice_card_hint"))
        }
        try { voiceCardLauncher.launch(intent) } catch (e: Exception) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            try { voiceCardLauncher.launch(intent) } catch (e2: Exception) { }
        }
    }

    if (editingCard != null) {
        val card = editingCard!!; val currentMathBalance = cardBalances[card.id] ?: card.initialBalanceMinor
        AlertDialog(onDismissRequest = { editingCard = null }, title = { Text("${card.name} (${card.currency})") },
            text = { Column {
                Text("${t("current_bal")} ${currentMathBalance.money()} ${card.currency}", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp));
                Text(t("enter_fact"), style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp));
                OutlinedTextField(value = actualBalanceInput, onValueChange = { actualBalanceInput = it }, label = { Text(t("amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = {
                val actualMinor = runCatching { BigDecimal(actualBalanceInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull();
                if (actualMinor != null) {
                    val diff = actualMinor - currentMathBalance
                    onAdjustBalance(card.id, diff)
                    editingCard = null; actualBalanceInput = ""
                }
            }) { Text(t("save")) } },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text(t("cancel")) } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(t("my_accounts")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("add") + ":", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { startVoiceCard() }) {
                        Icon(Icons.Default.Mic, contentDescription = t("voice_input"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = last4, onValueChange = { last4 = it.uppercase() }, label = { Text(t("account")) }, modifier = Modifier.weight(1f), singleLine = true)
                    Box(Modifier.weight(0.5f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(currency) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("UZS", "USD", "EUR", "RUB").forEach { ccy -> DropdownMenuItem(text = { Text(ccy) }, onClick = { currency = ccy; expanded = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp)); OutlinedTextField(value = initialBalance, onValueChange = { initialBalance = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
                Button(onClick = { val balanceMinor = runCatching { BigDecimal(initialBalance.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull() ?: 0L; if (name.isNotBlank() && last4.isNotBlank()) { onSave(CardEntity(id = "${last4}_$currency", last4 = last4, name = name, currency = currency, initialBalanceMinor = balanceMinor)); name = ""; last4 = ""; initialBalance = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("my_accounts") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (cards.isEmpty()) Text(t("no_found"), color = Color.Gray)
                cards.forEach { c -> val currentBal = cardBalances[c.id] ?: c.initialBalanceMinor
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${c.name} (${c.currency})", fontWeight = FontWeight.Bold); Text("${currentBal.money()} ${c.currency}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) };
                        IconButton(onClick = { editingCard = c; actualBalanceInput = BigDecimal(currentBal).movePointLeft(2).stripTrailingZeros().toPlainString() }) { Icon(Icons.Default.Edit, t("edit"), tint = MaterialTheme.colorScheme.primary) };
                        if (c.id != "CASH") {
                            IconButton({ onDelete(c.id) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) }
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
    }
}

@Composable
fun BudgetManagerDialog(budgets: List<BudgetEntity>, expenseCategories: List<String>, onSave: (BudgetEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, onDismiss: () -> Unit) {
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }; var limitInput by remember { mutableStateOf("") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    val t = { key: String -> Translations.t(key, currentLang) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("budgets")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("set_limit") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(Translations.translateCategory(selectedCategory, currentLang))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                    expenseCategories.forEach { c ->
                        DropdownMenuItem(text = { Text(Translations.translateCategory(c, currentLang)) }, onClick = { selectedCategory = c; showCategoryMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text(t("limit")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
            Button(onClick = { val limitMinor = runCatching { BigDecimal(limitInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (limitMinor != null && limitMinor > 0) { onSave(BudgetEntity(category = selectedCategory, limitMinor = limitMinor)); limitInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("set_limit")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("budgets") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            if (budgets.isEmpty()) Text(t("no_found"), color = Color.Gray)
            budgets.forEach { b -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(Translations.translateCategory(b.category, currentLang), fontWeight = FontWeight.Bold); Text("${t("limit")}: ${b.limitMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(b.category) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerDialog(recurring: List<RecurringEntity>, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, onSave: (RecurringEntity) -> Unit, onDelete: (RecurringEntity) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }; var category by remember { mutableStateOf(expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf("") }; var selectedCardId by remember { mutableStateOf("CASH") }; var desc by remember { mutableStateOf("") }; var dayOfMonth by remember { mutableStateOf("1") }
    val t = { key: String -> Translations.t(key, currentLang) }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(type) { val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories; if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("recurring")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("add_temp") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showTypeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(type.localizedLabel(currentLang))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    listOf(TxType.INCOME, TxType.EXPENSE).forEach { tt ->
                        DropdownMenuItem(text = { Text(tt.localizedLabel(currentLang)) }, onClick = { type = tt; showTypeMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) }, label = { Text(t("day")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(8.dp))

            Text(t("category") + ":", style = MaterialTheme.typography.labelMedium)

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(Translations.translateCategory(category, currentLang))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                    val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories
                    currentCategories.forEach { c ->
                        DropdownMenuItem(text = { Text(Translations.translateCategory(c, currentLang)) }, onClick = { category = c; showCategoryMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(8.dp)); Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = selectedCardId == c.id, onClick = { selectedCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) } }
            Spacer(Modifier.height(8.dp)); Button(onClick = { val minor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); val day = dayOfMonth.toIntOrNull(); if (minor != null && minor > 0 && day != null && day in 1..31) { onSave(RecurringEntity(amountMinor = minor, type = type, cardId = if(selectedCardId == "CASH") null else selectedCardId, description = desc.trim(), category = category, dayOfMonth = day)); amount = ""; desc = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add_temp")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("recurring") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            if (recurring.isEmpty()) Text(t("no_found"), color = Color.Gray)
            recurring.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.description.ifBlank { Translations.translateCategory(r.category, currentLang) }, fontWeight = FontWeight.Bold); Text("${r.amountMinor.money()} UZS · ${r.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(r) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerDialog(categories: List<CategoryEntity>, lang: AppLanguage, onSave: (CategoryEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var isIncome by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; val t = { key: String -> Translations.t(key, lang) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("categories")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("add_cat") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { SegmentedButton(!isIncome, { isIncome = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }; SegmentedButton(isIncome, { isIncome = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) } }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("category")) }, placeholder = { Text("🐶") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
            Button(onClick = { if (name.isNotBlank() && !categories.any { it.name == name }) { onSave(CategoryEntity(name = name.trim(), type = if (isIncome) TxType.INCOME else TxType.EXPENSE)); name = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("expense") + ":", style = MaterialTheme.typography.titleSmall, color = RED)
            categories.filter { it.type == TxType.EXPENSE }.forEach { c -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge); IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
            Spacer(Modifier.height(16.dp)); Text(t("income") + ":", style = MaterialTheme.typography.titleSmall, color = GREEN)
            categories.filter { it.type == TxType.INCOME }.forEach { c -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge); IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>, vm: VM) {
    val prefs = SecurityHelper.getEncryptedPrefs(context)
    val settingsPrefs = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
    
    var oldPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    var smsDebug by remember { mutableStateOf(settingsPrefs.getBoolean("sms_debug_mode", false)) }
    val savedPin = prefs.getString("pin", "0000") ?: "0000"; val t = { key: String -> Translations.t(key, currentLang.value) }
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("settings")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("language"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentLang.value == AppLanguage.RU, onClick = { currentLang.value = AppLanguage.RU; settingsPrefs.edit().putString("app_lang", "RU").apply() }, label = { Text("Русский") })
                FilterChip(selected = currentLang.value == AppLanguage.UZ, onClick = { currentLang.value = AppLanguage.UZ; settingsPrefs.edit().putString("app_lang", "UZ").apply() }, label = { Text("O'zbekcha") })
                FilterChip(selected = currentLang.value == AppLanguage.EN, onClick = { currentLang.value = AppLanguage.EN; settingsPrefs.edit().putString("app_lang", "EN").apply() }, label = { Text("English") })
                FilterChip(selected = currentLang.value == AppLanguage.TG, onClick = { currentLang.value = AppLanguage.TG; settingsPrefs.edit().putString("app_lang", "TG").apply() }, label = { Text("Тоҷикӣ") })
                FilterChip(selected = currentLang.value == AppLanguage.KAA, onClick = { currentLang.value = AppLanguage.KAA; settingsPrefs.edit().putString("app_lang", "KAA").apply() }, label = { Text("Qaraqalpaqsha") })
                FilterChip(selected = currentLang.value == AppLanguage.KK, onClick = { currentLang.value = AppLanguage.KK; settingsPrefs.edit().putString("app_lang", "KK").apply() }, label = { Text("Қазақша") })
            }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("theme"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentTheme.value == AppTheme.SYSTEM, onClick = { currentTheme.value = AppTheme.SYSTEM; settingsPrefs.edit().putString("app_theme", "SYSTEM").apply() }, label = { Text(t("sys")) })
                FilterChip(selected = currentTheme.value == AppTheme.LIGHT, onClick = { currentTheme.value = AppTheme.LIGHT; settingsPrefs.edit().putString("app_theme", "LIGHT").apply() }, label = { Text(t("light")) })
                FilterChip(selected = currentTheme.value == AppTheme.DARK, onClick = { currentTheme.value = AppTheme.DARK; settingsPrefs.edit().putString("app_theme", "DARK").apply() }, label = { Text(t("dark")) })
            }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))

            Button(onClick = { vm.updateRates(); scope.launch { snackbarHostState.showSnackbar(t("rates_updated")) } }, modifier = Modifier.fillMaxWidth()) { Text(t("update_rates")) }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("change_pin"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_new")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_repeat")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            
            Button(onClick = { when { 
                oldPin != savedPin -> scope.launch { snackbarHostState.showSnackbar(t("wrong_pin")) }
                newPin.length != 4 -> scope.launch { snackbarHostState.showSnackbar(t("pin_error")) }
                newPin != confirmPin -> scope.launch { snackbarHostState.showSnackbar(t("pin_error")) }
                else -> { 
                    prefs.edit().putString("pin", newPin).apply()
                    oldPin = ""; newPin = ""; confirmPin = ""
                    scope.launch { snackbarHostState.showSnackbar(t("pin_ok")) } 
                } 
            } }, modifier = Modifier.fillMaxWidth()) { Text(t("change_pin")) }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Column(Modifier.weight(1f)) { Text(t("biometric"), style = MaterialTheme.typography.titleSmall) }
                Switch(checked = useBiometric, onCheckedChange = { useBiometric = it; prefs.edit().putBoolean("use_biometric", it).apply() }) 
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Column(Modifier.weight(1f)) { Text(t("sms_debug"), style = MaterialTheme.typography.titleSmall) }
                Switch(checked = smsDebug, onCheckedChange = { smsDebug = it; settingsPrefs.edit().putBoolean("sms_debug_mode", it).apply() }) 
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}
