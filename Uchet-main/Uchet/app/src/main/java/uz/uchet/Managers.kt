package uz.uchet

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalManagerSheet(goals: List<SavingsGoalEntity>, cards: List<CardEntity>, lang: AppLanguage, onSaveGoal: (SavingsGoalEntity) -> Unit, onDeleteGoal: (SavingsGoalEntity) -> Unit, onDeposit: (SavingsGoalEntity, String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var targetAmount by remember { mutableStateOf("") }
    var depositGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }; var depositAmount by remember { mutableStateOf("") }
    var depositCardId by remember { mutableStateOf(cards.firstOrNull()?.id ?: "CASH") }
    var goalToDelete by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    val t = { key: String -> Translations.t(key, lang) }
    val currentDepositGoal = depositGoal

    if (goalToDelete != null) {
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDeleteGoal(goalToDelete!!); goalToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text(t("no")) } }
        )
    }

    if (currentDepositGoal != null) {
        AlertDialog(onDismissRequest = { depositGoal = null }, title = { Text("${t("deposit")}: ${currentDepositGoal.name}") },
            text = { Column { OutlinedTextField(value = depositAmount, onValueChange = { depositAmount = it }, label = { Text(t("deposit_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text(t("source_card") + ":", style = MaterialTheme.typography.labelMedium); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = depositCardId == c.id, onClick = { depositCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) }; FilterChip(selected = depositCardId == "CASH", onClick = { depositCardId = "CASH" }, label = { Text(t("cash")) }) } } },
            confirmButton = { TextButton(onClick = { val amountMinor = runCatching { BigDecimal(depositAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (amountMinor != null && amountMinor > 0) { onDeposit(currentDepositGoal, depositCardId, amountMinor); depositGoal = null; depositAmount = "" } }) { Text(t("deposit")) } },
            dismissButton = { TextButton(onClick = { depositGoal = null }) { Text(t("cancel")) } })
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(t("goals"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                Text(t("add_goal") + ":", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("goal_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text(t("target_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { 
                    val targetMinor = runCatching { BigDecimal(targetAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                    if (name.isNotBlank() && targetMinor != null && targetMinor > 0) { 
                        onSaveGoal(SavingsGoalEntity(name = name.trim(), targetAmountMinor = targetMinor))
                        name = ""; targetAmount = ""
                    } 
                }, modifier = Modifier.fillMaxWidth()) { Text(t("add_goal")) }
                
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("goals") + ":", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                if (goals.isEmpty()) Text(t("no_found"), color = Color.Gray)
                goals.forEach { goal -> 
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                        Column(Modifier.weight(1f)) { 
                            Text(goal.name, fontWeight = FontWeight.Bold)
                            Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                        }
                        IconButton(onClick = { depositGoal = goal }) { Icon(Icons.Default.Add, t("deposit"), tint = GREEN) }
                        IconButton(onClick = { goalToDelete = goal }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } 
                    } 
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardManagerSheet(cards: List<CardEntity>, cardBalances: Map<String, Long>, allTxs: List<Tx>, onSave: (CardEntity) -> Unit, onDelete: (String) -> Unit, onAdjustBalance: (String, Long) -> Unit, currentLang: AppLanguage, rates: List<ExchangeRateEntity>, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var initialBalance by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("UZS") }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }; var actualBalanceInput by remember { mutableStateOf("") }
    var cardToDelete by remember { mutableStateOf<String?>(null) }
    val t = { key: String -> Translations.t(key, currentLang) }

    val voiceCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                VoiceParser.parseCard(spoken)?.let { parsed ->
                    last4 = parsed.last4
                    name = parsed.bankName?.let { bn -> "${getShortBankName(bn)} ${parsed.last4}" } ?: "Bank ${parsed.last4}"
                    parsed.balanceMinor?.let { bal -> initialBalance = BigDecimal(bal).movePointLeft(2).stripTrailingZeros().toPlainString() }
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

    if (cardToDelete != null) {
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDelete(cardToDelete!!); cardToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text(t("no")) } }
        )
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
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("my_accounts"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { startVoiceCard() }) {
                        Icon(Icons.Default.Mic, contentDescription = t("voice_input"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = initialBalance, onValueChange = { initialBalance = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { 
                    val balanceMinor = runCatching { BigDecimal(initialBalance.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull() ?: 0L
                    if (name.isNotBlank() && last4.isNotBlank()) { 
                        onSave(CardEntity(id = "${last4}_$currency", last4 = last4, name = name, currency = currency, initialBalanceMinor = balanceMinor))
                        name = ""; last4 = ""; initialBalance = "" 
                    } 
                }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
                
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("my_accounts") + ":", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                if (cards.isEmpty()) Text(t("no_found"), color = Color.Gray)
                cards.forEach { c -> 
                    val currentBal = cardBalances[c.id] ?: c.initialBalanceMinor
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                        Column(Modifier.weight(1f)) { 
                            Text("${c.name} (${c.currency})", fontWeight = FontWeight.Bold)
                            Text("${currentBal.money()} ${c.currency}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                        }
                        IconButton(onClick = { editingCard = c; actualBalanceInput = BigDecimal(currentBal).movePointLeft(2).stripTrailingZeros().toPlainString() }) { Icon(Icons.Default.Edit, t("edit"), tint = MaterialTheme.colorScheme.primary) }
                        if (c.id != "CASH" && c.last4 != "CASH") {
                            IconButton({ cardToDelete = c.id }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetManagerSheet(budgets: List<BudgetEntity>, expenseCategories: List<String>, onSave: (BudgetEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, onDismiss: () -> Unit) {
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }; var limitInput by remember { mutableStateOf("") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var budgetToDelete by remember { mutableStateOf<String?>(null) }
    val t = { key: String -> Translations.t(key, currentLang) }

    if (budgetToDelete != null) {
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDelete(budgetToDelete!!); budgetToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { budgetToDelete = null }) { Text(t("no")) } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("budgets"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            Text(t("set_limit") + ":", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text(t("limit")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { 
                val limitMinor = runCatching { BigDecimal(limitInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                if (limitMinor != null && limitMinor > 0) { onSave(BudgetEntity(category = selectedCategory, limitMinor = limitMinor)); limitInput = "" } 
            }, modifier = Modifier.fillMaxWidth()) { Text(t("set_limit")) }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("budgets") + ":", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            
            if (budgets.isEmpty()) Text(t("no_found"), color = Color.Gray)
            budgets.forEach { b -> 
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                    Column(Modifier.weight(1f)) { 
                        Text(Translations.translateCategory(b.category, currentLang), fontWeight = FontWeight.Bold)
                        Text("${t("limit")}: ${b.limitMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                    }
                    IconButton({ budgetToDelete = b.category }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } 
                } 
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerSheet(recurring: List<RecurringEntity>, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, onSave: (RecurringEntity) -> Unit, onDelete: (RecurringEntity) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }; var category by remember { mutableStateOf(expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf("") }; var selectedCardId by remember { mutableStateOf("CASH") }; var desc by remember { mutableStateOf("") }; var dayOfMonth by remember { mutableStateOf("1") }
    var recToDelete by remember { mutableStateOf<RecurringEntity?>(null) }
    val t = { key: String -> Translations.t(key, currentLang) }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    if (recToDelete != null) {
        AlertDialog(
            onDismissRequest = { recToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDelete(recToDelete!!); recToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { recToDelete = null }) { Text(t("no")) } }
        )
    }

    LaunchedEffect(type) { 
        val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое" 
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("recurring"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text(t("add_temp") + ":", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) }, label = { Text(t("day")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))
            Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                cards.forEach { c -> FilterChip(selected = selectedCardId == c.id, onClick = { selectedCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) } 
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { 
                val minor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                val day = dayOfMonth.toIntOrNull()
                if (minor != null && minor > 0 && day != null && day in 1..31) { 
                    onSave(RecurringEntity(amountMinor = minor, type = type, cardId = if(selectedCardId == "CASH") null else selectedCardId, description = desc.trim(), category = category, dayOfMonth = day))
                    amount = ""; desc = "" 
                } 
            }, modifier = Modifier.fillMaxWidth()) { Text(t("add_temp")) }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("recurring") + ":", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            
            if (recurring.isEmpty()) Text(t("no_found"), color = Color.Gray)
            recurring.forEach { r -> 
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                    Column(Modifier.weight(1f)) { 
                        Text(r.description.ifBlank { Translations.translateCategory(r.category, currentLang) }, fontWeight = FontWeight.Bold)
                        Text("${r.amountMinor.money()} UZS · ${r.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
                    }
                    IconButton({ recToDelete = r }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } 
                } 
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerSheet(categories: List<CategoryEntity>, lang: AppLanguage, onSave: (CategoryEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var isIncome by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; val t = { key: String -> Translations.t(key, lang) }
    var catToDelete by remember { mutableStateOf<String?>(null) }

    if (catToDelete != null) {
        AlertDialog(
            onDismissRequest = { catToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Column { Text(t("delete_confirm_text")); Spacer(Modifier.height(8.dp)); Text(t("category_in_use"), style = MaterialTheme.typography.bodySmall, color = Color.Gray) } },
            confirmButton = { TextButton(onClick = { onDelete(catToDelete!!); catToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { catToDelete = null }) { Text(t("no")) } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("categories"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text(t("add_cat") + ":", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { 
                SegmentedButton(!isIncome, { isIncome = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }
                SegmentedButton(isIncome, { isIncome = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) } 
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("category")) }, placeholder = { Text("🐶") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { 
                if (name.isNotBlank() && !categories.any { it.name == name }) { 
                    onSave(CategoryEntity(name = name.trim(), type = if (isIncome) TxType.INCOME else TxType.EXPENSE))
                    name = "" 
                } 
            }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("expense") + ":", style = MaterialTheme.typography.titleSmall, color = RED)
            categories.filter { it.type == TxType.EXPENSE }.forEach { c -> 
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                    Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge)
                    IconButton({ catToDelete = c.name }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } 
                } 
            }
            Spacer(Modifier.height(16.dp))
            Text(t("income") + ":", style = MaterialTheme.typography.titleSmall, color = GREEN)
            categories.filter { it.type == TxType.INCOME }.forEach { c -> 
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                    Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge)
                    IconButton({ catToDelete = c.name }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } 
                } 
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>, vm: VM) {
    val prefs = SecurityHelper.getEncryptedPrefs(context)
    val settingsPrefs = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
    
    var oldPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    var smsDebug by remember { mutableStateOf(settingsPrefs.getBoolean("sms_debug_mode", false)) }
    
    var dailyReminder by remember { mutableStateOf(settingsPrefs.getBoolean("daily_reminder", false)) }
    var reminderHour by remember { mutableIntStateOf(settingsPrefs.getInt("reminder_hour", 21)) }
    var reminderMinute by remember { mutableIntStateOf(settingsPrefs.getInt("reminder_minute", 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val savedPin = prefs.getString("pin", "0000") ?: "0000"; val t = { key: String -> Translations.t(key, currentLang.value) }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время напоминания") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeInput(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reminderHour = timePickerState.hour
                    reminderMinute = timePickerState.minute
                    settingsPrefs.edit()
                        .putInt("reminder_hour", reminderHour)
                        .putInt("reminder_minute", reminderMinute)
                        .apply()
                    
                    if (dailyReminder) {
                        NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            }
        )
    }
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text(t("language"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentLang.value == AppLanguage.RU, onClick = { currentLang.value = AppLanguage.RU; settingsPrefs.edit().putString("app_lang", "RU").apply() }, label = { Text("Русский") })
                FilterChip(selected = currentLang.value == AppLanguage.UZ, onClick = { currentLang.value = AppLanguage.UZ; settingsPrefs.edit().putString("app_lang", "UZ").apply() }, label = { Text("O'zbekcha") })
                FilterChip(selected = currentLang.value == AppLanguage.EN, onClick = { currentLang.value = AppLanguage.EN; settingsPrefs.edit().putString("app_lang", "EN").apply() }, label = { Text("English") })
                FilterChip(selected = currentLang.value == AppLanguage.TG, onClick = { currentLang.value = AppLanguage.TG; settingsPrefs.edit().putString("app_lang", "TG").apply() }, label = { Text("Тоҷикӣ") })
                FilterChip(selected = currentLang.value == AppLanguage.KAA, onClick = { currentLang.value = AppLanguage.KAA; settingsPrefs.edit().putString("app_lang", "KAA").apply() }, label = { Text("Qaraqalpaqsha") })
                FilterChip(selected = currentLang.value == AppLanguage.KK, onClick = { currentLang.value = AppLanguage.KK; settingsPrefs.edit().putString("app_lang", "KK").apply() }, label = { Text("Қазақша") })
            }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("theme"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentTheme.value == AppTheme.SYSTEM, onClick = { currentTheme.value = AppTheme.SYSTEM; settingsPrefs.edit().putString("app_theme", "SYSTEM").apply() }, label = { Text(t("sys")) })
                FilterChip(selected = currentTheme.value == AppTheme.LIGHT, onClick = { currentTheme.value = AppTheme.LIGHT; settingsPrefs.edit().putString("app_theme", "LIGHT").apply() }, label = { Text(t("light")) })
                FilterChip(selected = currentTheme.value == AppTheme.DARK, onClick = { currentTheme.value = AppTheme.DARK; settingsPrefs.edit().putString("app_theme", "DARK").apply() }, label = { Text(t("dark")) })
            }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.updateRates(); scope.launch { snackbarHostState.showSnackbar(t("rates_updated")) } }, modifier = Modifier.fillMaxWidth()) { Text(t("update_rates")) }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("change_pin"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_new")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_repeat")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            
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
                Text(t("biometric"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = useBiometric, onCheckedChange = { useBiometric = it; prefs.edit().putBoolean("use_biometric", it).apply() }) 
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Column(Modifier.weight(1f)) {
                    Text("Ежедневные напоминания", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Каждый день в %02d:%02d".format(reminderHour, reminderMinute),
                        style = MaterialTheme.typography.bodySmall, 
                        color = if (dailyReminder) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.clickable { showTimePicker = true }
                    )
                }
                Switch(
                    checked = dailyReminder, 
                    onCheckedChange = { isChecked ->
                        dailyReminder = isChecked
                        settingsPrefs.edit().putBoolean("daily_reminder", isChecked).apply()
                        if (isChecked) {
                            NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
                        } else {
                            NotificationHelper.cancelDailyReminder(context)
                        }
                    }
                ) 
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Text(t("sms_debug"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = smsDebug, onCheckedChange = { smsDebug = it; settingsPrefs.edit().putBoolean("sms_debug_mode", it).apply() }) 
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtManagerSheet(
    debts: List<DebtEntity>,
    currentLang: AppLanguage,
    onSave: (DebtEntity) -> Unit,
    onMarkPaid: (DebtEntity) -> Unit,
    onDelete: (DebtEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var isOwedToMe by remember { mutableStateOf(true) }
    var personName by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var debtToDelete by remember { mutableStateOf<DebtEntity?>(null) }
    val t = { key: String -> Translations.t(key, currentLang) }

    if (debtToDelete != null) {
        AlertDialog(
            onDismissRequest = { debtToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = { TextButton(onClick = { onDelete(debtToDelete!!); debtToDelete = null }) { Text(t("yes"), color = RED) } },
            dismissButton = { TextButton(onClick = { debtToDelete = null }) { Text(t("no")) } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("debts"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            val totalOwedToMe = debts.filter { !it.isPaid && it.type == DebtType.LENT }.sumOf { it.amountMinor }
            val totalIOwe = debts.filter { !it.isPaid && it.type == DebtType.BORROWED }.sumOf { it.amountMinor }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(t("owed_to_me"), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text("+${totalOwedToMe.money()} UZS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GREEN)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(t("i_owe"), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text("-${totalIOwe.money()} UZS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RED)
                }
            }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))

            Text("Добавить долг:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(isOwedToMe, { isOwedToMe = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("owed_to_me")) }
                SegmentedButton(!isOwedToMe, { isOwedToMe = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("i_owe")) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = personName, onValueChange = { personName = it }, label = { Text(t("person_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text(t("amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val amountMinor = runCatching { BigDecimal(amountInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                if (personName.isNotBlank() && amountMinor != null && amountMinor > 0) {
                    onSave(DebtEntity(
                        personName = personName.trim(),
                        amountMinor = amountMinor,
                        type = if (isOwedToMe) DebtType.LENT else DebtType.BORROWED,
                        dateMillis = System.currentTimeMillis(),
                        description = desc.trim()
                    ))
                    personName = ""; amountInput = ""; desc = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text("Активные долги:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            val activeDebts = debts.filter { !it.isPaid }
            if (activeDebts.isEmpty()) Text(t("no_found"), color = Color.Gray)
            activeDebts.forEach { d ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (d.type == DebtType.LENT) Icons.Default.CallReceived else Icons.Default.CallMade,
                            contentDescription = null,
                            tint = if (d.type == DebtType.LENT) GREEN else RED
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.personName, fontWeight = FontWeight.Bold)
                            if (d.description.isNotBlank()) Text(d.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${if (d.type == DebtType.LENT) "+" else "-"}${d.amountMinor.money()}", color = if (d.type == DebtType.LENT) GREEN else RED, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { onMarkPaid(d) }) { Text(t("mark_paid")) }
                        }
                        IconButton(onClick = { debtToDelete = d }) { Icon(Icons.Default.Delete, null, tint = RED) }
                    }
                }
            }

            if (debts.any { it.isPaid }) {
                Spacer(Modifier.height(16.dp))
                Text(t("debt_paid"), style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                debts.filter { it.isPaid }.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.personName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${d.amountMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        IconButton(onClick = { debtToDelete = d }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
// ★★★ RuleManagerSheet — управление правилами самообучения ★★★
// ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleManagerSheet(
    rules: List<CustomKeywordRule>,
    currentLang: AppLanguage,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onSave: (String, String, TxType) -> Unit,
    onDelete: (CustomKeywordRule) -> Unit,
    onDeleteUnused: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var ruleToDelete by remember { mutableStateOf<CustomKeywordRule?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showConfirmDeleteUnused by remember { mutableStateOf(false) }

    val unusedCount = rules.count { it.appliedCount == 0 }

    LaunchedEffect(type) {
        val list = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!list.contains(category)) category = list.firstOrNull() ?: "📦 Другое"
    }

    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = {
                TextButton(onClick = { onDelete(ruleToDelete!!); ruleToDelete = null }) {
                    Text(t("yes"), color = RED)
                }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text(t("no")) } }
        )
    }

    if (showConfirmDeleteUnused) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteUnused = false },
            title = { Text(t("rule_delete_unused")) },
            text = { Text("Будет удалено $unusedCount правил, которые ещё ни разу не сработали.") },
            confirmButton = {
                TextButton(onClick = { onDeleteUnused(); showConfirmDeleteUnused = false }) {
                    Text(t("yes"), color = RED)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmDeleteUnused = false }) { Text(t("no")) } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("learned_rules"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "При встрече слова в СМС, чеке или голосовой команде категория выбирается автоматически",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))

            if (rules.isEmpty()) {
                Text(
                    "Пока нет выученных правил.\nОтредактируйте операцию и отметьте «Запомнить для будущего».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                rules.forEach { rule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(rule.keyword, fontWeight = FontWeight.Bold)
                                    if (rule.appliedCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "×${rule.appliedCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GREEN,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (rule.rejectedCount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "✕${rule.rejectedCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RED
                                        )
                                    }
                                }
                                Text(
                                    "→ ${Translations.translateCategory(rule.category, currentLang)} (${rule.type.localizedLabel(currentLang)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                rule.lastAppliedAt?.let { ts ->
                                    Text(
                                        formatLastApplied(ts),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            IconButton(onClick = { ruleToDelete = rule }) {
                                Icon(Icons.Default.Delete, null, tint = RED)
                            }
                        }
                    }
                }

                if (unusedCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showConfirmDeleteUnused = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RED)
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${t("rule_delete_unused")} ($unusedCount)")
                    }
                }
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))

            Text("Добавить правило вручную:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }
                SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Ключевое слово") },
                placeholder = { Text("Например: Uzum, Шаурма") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(Translations.translateCategory(category, currentLang))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                    val list = if (type == TxType.INCOME) incomeCategories else expenseCategories
                    list.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(Translations.translateCategory(c, currentLang)) },
                            onClick = { category = c; showCategoryMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (keyword.isNotBlank() && category.isNotBlank()) {
                        onSave(keyword.trim(), category.trim(), type)
                        keyword = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("add")) }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatLastApplied(ts: Long): String {
    val diffMin = (System.currentTimeMillis() - ts) / 60_000L
    return when {
        diffMin < 1 -> "сработало только что"
        diffMin < 60 -> "сработало ${diffMin} мин назад"
        diffMin < 60 * 24 -> "сработало ${diffMin / 60} ч назад"
        diffMin < 60 * 24 * 30 -> "сработало ${diffMin / (60 * 24)} дн назад"
        else -> "сработало ${diffMin / (60 * 24 * 30)} мес назад"
    }
}

// ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
// ★★★ CorrectionManagerSheet — просмотр выученных коррекций (шаг C) ★★★
// ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionManagerSheet(
    corrections: List<CorrectionEntity>,
    currentLang: AppLanguage,
    onDelete: (CorrectionEntity) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(t("correction_clear_all")) },
            text = { Text(t("delete_confirm_text")) },
            confirmButton = {
                TextButton(onClick = { onClearAll(); showClearConfirm = false }) { Text(t("yes"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(t("no")) } }
        )
    }

    // Группируем: merchantHint -> field -> список коррекций
    val grouped = corrections.groupBy { it.merchantHint }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("learned_corrections"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                t("correction_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))

            if (corrections.isEmpty()) {
                Text(
                    t("correction_empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                Text(
                    "${t("correction_total")}: ${corrections.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                grouped.forEach { (hint, list) ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (hint.isBlank()) "—" else hint,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            list.sortedByDescending { it.lastSeenAt }.forEach { c ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                fieldLabel(c.field, currentLang),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.Gray
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            if (c.appliedCount >= 3) {
                                                Text(
                                                    "● ${t("correction_ready")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GREEN,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Text(
                                                    "×${c.appliedCount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            "${formatParsedValue(c.field, c.parsedValue)} → ${formatParsedValue(c.field, c.correctedValue)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            relativeTime(c.lastSeenAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray.copy(alpha = 0.7f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(c) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = RED.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RED)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("correction_clear_all"))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun fieldLabel(field: String, lang: AppLanguage): String = when (field) {
    "amount" -> Translations.t("field_amount", lang)
    "type" -> Translations.t("field_type", lang)
    "card" -> Translations.t("field_card", lang)
    "category" -> Translations.t("field_category", lang)
    "merchant" -> Translations.t("field_merchant", lang)
    else -> field
}

private fun formatParsedValue(field: String, value: String): String {
    return when (field) {
        "amount" -> {
            val l = value.toLongOrNull()
            if (l != null) l.money() else value
        }
        "type" -> when (value) {
            "INCOME" -> "Доход"
            "EXPENSE" -> "Расход"
            "TRANSFER" -> "Перевод"
            "ADJUSTMENT" -> "Корректировка"
            else -> value
        }
        "card" -> if (value == "null") "Без карты" else "*${value.split("_").firstOrNull() ?: value}"
        else -> value
    }
}

private fun relativeTime(ts: Long): String {
    val diffMin = (System.currentTimeMillis() - ts) / 60_000L
    return when {
        diffMin < 1 -> "только что"
        diffMin < 60 -> "$diffMin мин назад"
        diffMin < 60 * 24 -> "${diffMin / 60} ч назад"
        diffMin < 60 * 24 * 30 -> "${diffMin / (60 * 24)} дн назад"
        else -> "${diffMin / (60 * 24 * 30)} мес назад"
    }
}
