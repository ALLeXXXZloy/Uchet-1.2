package uz.uchet

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(
    initial: Tx?,
    quickTemplate: QuickTemplate?,
    cards: List<CardEntity>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    currentLang: AppLanguage,
    isPendingSms: Boolean,
    autoStartVoice: Boolean,
    rates: List<ExchangeRateEntity>,
    customRules: List<CustomKeywordRule>,
    ruleAppliedId: Long?,
    ruleAppliedKeyword: String?,
    rawSourceText: String?,
    vm: VM,
    onDismiss: () -> Unit,
    onSave: (Tx) -> Unit,
    onSaveRule: (String, String, TxType) -> Unit,
    onRuleApplied: (Long) -> Unit,
    onRuleRejected: (Long) -> Unit,
    onRecordCorrection: (String, String, String, String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val t = { key: String -> Translations.t(key, currentLang) }

    // Инициализация полей
    var amountStr by remember { mutableStateOf(if (initial != null) BigDecimal(initial.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() else "") }
    var type by remember { mutableStateOf(initial?.type ?: quickTemplate?.type ?: TxType.EXPENSE) }
    var cardId by remember { mutableStateOf(initial?.cardId ?: "CASH") }
    var toCardId by remember { mutableStateOf(initial?.toCardId ?: "CASH") } // ★ ДОБАВЛЕНО: Счет получателя
    var category by remember { mutableStateOf(initial?.category ?: quickTemplate?.category ?: (if (type == TxType.EXPENSE) expenseCategories.firstOrNull() ?: "" else incomeCategories.firstOrNull() ?: "")) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    val dateMillis = initial?.timestamp ?: System.currentTimeMillis()
    val currency = initial?.currency ?: "UZS"

    // Состояния для правил
    var appliedRuleId by remember { mutableStateOf(ruleAppliedId) }
    var appliedRuleKeyword by remember { mutableStateOf(ruleAppliedKeyword) }
    
    // Состояния для самообучения
    var saveRule by remember { mutableStateOf(false) }
    var ruleKeyword by remember { mutableStateOf(description.take(15)) }

    // FB-16: Состояние для просмотра оригинала
    var showRawTextDialog by remember { mutableStateOf(false) }
    val finalRawText = initial?.rawText ?: rawSourceText

    // ★ FB-17: Состояния для режима "Это спам"
    var showSpamDialog by remember { mutableStateOf(false) }
    var spamKeyword by remember { mutableStateOf(description.take(15).trim().ifBlank { category }) }

    // Голосовой ввод
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val parsed = VoiceParser.parseTransaction(spoken, expenseCategories, incomeCategories, customRules)
                parsed.amountMinor?.let { amountStr = BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() }
                parsed.type?.let { type = it }
                parsed.cardLast4?.let { last4 -> cards.find { it.last4 == last4 }?.let { cardId = it.id } }
                if (parsed.isCash) cardId = "CASH"
                parsed.categoryName?.let { category = it }
                parsed.description.let { if (it.isNotBlank()) description = it }
                if (parsed.ruleAppliedId != null) {
                    appliedRuleId = parsed.ruleAppliedId
                    appliedRuleKeyword = parsed.ruleAppliedKeyword
                }
            }
        }
    }

    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLang == AppLanguage.UZ) "uz-UZ" else "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PROMPT, t("voice_prompt"))
            }
            try { voiceLauncher.launch(intent) } catch (e: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (initial == null) t("new_tx") else t("edit"), style = MaterialTheme.typography.titleLarge)
                    if (autoStartVoice) IconButton(onClick = { 
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLang == AppLanguage.UZ) "uz-UZ" else "ru-RU")
                        }
                        try { voiceLauncher.launch(intent) } catch (e: Exception) {}
                    }) { Icon(Icons.Default.Mic, contentDescription = "Voice", tint = MaterialTheme.colorScheme.primary) }
                }
                
                if (isPendingSms) {
                    Text("Данные из СМС/Уведомления", color = YELLOW, style = MaterialTheme.typography.bodySmall)
                }

                // ★ FB-16: Блок оригинального текста
                if (!finalRawText.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Источник: Импорт", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { showRawTextDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Info, contentDescription = "Оригинал")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Посмотреть оригинал")
                            }
                        }
                    }
                }

                // Бейдж примененного правила
                if (appliedRuleId != null && appliedRuleId != 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(8.dp).padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Применено правило: $appliedRuleKeyword", color = Color(0xFF2E7D32), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { 
                            appliedRuleId?.let { onRuleRejected(it) }
                            appliedRuleId = null
                            appliedRuleKeyword = null
                            category = if (type == TxType.EXPENSE) expenseCategories.firstOrNull() ?: "" else incomeCategories.firstOrNull() ?: ""
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = "Отменить правило", tint = Color.Red)
                        }
                    }
                }

                // ★ Тип (Расход / Доход / Перевод) - теперь 3 кнопки
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE; category = expenseCategories.firstOrNull() ?: "" }, SegmentedButtonDefaults.itemShape(0, 3)) { Text(t("expense")) }
                    SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME; category = incomeCategories.firstOrNull() ?: "" }, SegmentedButtonDefaults.itemShape(1, 3)) { Text(t("income")) }
                    SegmentedButton(type == TxType.TRANSFER, { type = TxType.TRANSFER }, SegmentedButtonDefaults.itemShape(2, 3)) { Text(t("transfer")) }
                }

                // Сумма
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(t("amount")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true
                )

                // Карта (Откуда)
                var expandedCard by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expandedCard, onExpandedChange = { expandedCard = !expandedCard }, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = cards.find { it.id == cardId }?.name ?: t("cash"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (type == TxType.TRANSFER) t("source_card") else t("account")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCard) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedCard, onDismissRequest = { expandedCard = false }) {
                        cards.forEach { card ->
                            DropdownMenuItem(text = { Text(card.name) }, onClick = { cardId = card.id; expandedCard = false })
                        }
                    }
                }

                // ★ ДОБАВЛЕНО: Карта (Куда) для переводов
                if (type == TxType.TRANSFER) {
                    var expandedToCard by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expandedToCard, onExpandedChange = { expandedToCard = !expandedToCard }, modifier = Modifier.padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = cards.find { it.id == toCardId }?.name ?: t("cash"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(t("to_account")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedToCard) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedToCard, onDismissRequest = { expandedToCard = false }) {
                            cards.filter { it.id != cardId }.forEach { card ->
                                DropdownMenuItem(text = { Text(card.name) }, onClick = { toCardId = card.id; expandedToCard = false })
                            }
                        }
                    }
                }

                // Категория (только для доходов/расходов)
                if (type != TxType.TRANSFER) {
                    var expandedCat by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expandedCat, onExpandedChange = { expandedCat = !expandedCat }, modifier = Modifier.padding(vertical = 4.dp)) {
                        val catList = if (type == TxType.EXPENSE) expenseCategories else incomeCategories
                        OutlinedTextField(
                            value = Translations.translateCategory(category, currentLang),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(t("category")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                            catList.forEach { c ->
                                DropdownMenuItem(text = { Text(Translations.translateCategory(c, currentLang)) }, onClick = { category = c; expandedCat = false })
                            }
                        }
                    }
                }

                // Описание
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(t("desc")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true
                )
                
                // Чекбокс "Запомнить для будущего"
                if (type != TxType.TRANSFER) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saveRule = !saveRule }.padding(vertical = 4.dp)) {
                        Checkbox(checked = saveRule, onCheckedChange = { saveRule = it })
                        Text("Запомнить для будущего", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (saveRule) {
                        OutlinedTextField(
                            value = ruleKeyword,
                            onValueChange = { ruleKeyword = it },
                            label = { Text("Ключевое слово для правила") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Кнопки
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        if (onDelete != null) {
                            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = RED)) {
                                Text(t("delete"))
                            }
                        }
                        TextButton(onClick = { showSpamDialog = true }, colors = ButtonDefaults.textButtonColors(contentColor = RED)) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Это спам", fontSize = 12.sp)
                        }
                    }
                    
                    Row {
                        TextButton(onClick = onDismiss) { Text(t("cancel")) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val parsedAmount = amountStr.replace(",", ".").toBigDecimalOrNull()
                            if (parsedAmount == null) {
                                Toast.makeText(context, t("error"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val finalAmountMinor = parsedAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                            
                            // Для переводов категория не нужна
                            val finalCategory = if (type == TxType.TRANSFER) "💸 Перевод" else category
                            
                            val newTx = Tx(
                                id = initial?.id ?: 0,
                                amountMinor = finalAmountMinor,
                                type = type,
                                cardId = if (cardId == "CASH") null else cardId,
                                toCardId = if (type == TxType.TRANSFER) (if (toCardId == "CASH") null else toCardId) else null,
                                description = description,
                                category = finalCategory,
                                timestamp = dateMillis,
                                source = initial?.source ?: if (isPendingSms) "SMS" else "MANUAL",
                                currency = currency,
                                rawText = finalRawText
                            )

                            // ★ Запись коррекций для самообучения
                            if (initial != null) {
                                val merchantHint = initial.description.take(30)
                                if (initial.amountMinor != newTx.amountMinor) onRecordCorrection(merchantHint, "amount", initial.amountMinor.toString(), newTx.amountMinor.toString(), finalRawText ?: "")
                                if (initial.type != newTx.type) onRecordCorrection(merchantHint, "type", initial.type.name, newTx.type.name, finalRawText ?: "")
                                if (initial.cardId != newTx.cardId) onRecordCorrection(merchantHint, "card", initial.cardId ?: "null", newTx.cardId ?: "null", finalRawText ?: "")
                                if (initial.toCardId != newTx.toCardId) onRecordCorrection(merchantHint, "toCardId", initial.toCardId ?: "null", newTx.toCardId ?: "null", finalRawText ?: "") // ★ ДОБАВЛЕНО
                                if (initial.category != newTx.category) onRecordCorrection(merchantHint, "category", initial.category, newTx.category, finalRawText ?: "")
                                if (initial.description != newTx.description) onRecordCorrection(merchantHint, "merchant", initial.description, newTx.description, finalRawText ?: "")
                            }

                            if (saveRule && ruleKeyword.isNotBlank()) {
                                onSaveRule(ruleKeyword.trim(), category, type)
                            }

                            if (appliedRuleId != null && appliedRuleId != 0L) {
                                onRuleApplied(appliedRuleId!!)
                            }

                            onSave(newTx)
                        }) {
                            Text(t("save"))
                        }
                    }
                }
            }
        }
    }

    // FB-16: Диалог оригинала
    if (showRawTextDialog && !finalRawText.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showRawTextDialog = false },
            title = { Text("Оригинальный текст", style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(finalRawText, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRawTextDialog = false }) { Text("Закрыть") } },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(finalRawText))
                    Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                }) { Text("Копировать") }
            }
        )
    }

    // FB-17: Диалог спама
    if (showSpamDialog) {
        AlertDialog(
            onDismissRequest = { showSpamDialog = false },
            title = { Text("Игнорировать как спам") },
            text = {
                Column {
                    Text("Приложение больше не будет создавать операции, если встретит это слово в СМС или уведомлении.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = spamKeyword,
                        onValueChange = { spamKeyword = it },
                        label = { Text("Ключевое слово") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (spamKeyword.isNotBlank()) {
                        vm.saveCustomRule(spamKeyword.trim(), "IGNORE", type, isIgnored = true)
                        onRecordCorrection(initial?.description ?: "СПАМ", "is_spam", "TRANSACTION", "IGNORED", finalRawText ?: "")
                        if (onDelete != null) onDelete() else onDismiss()
                    }
                }) { Text("Игнорировать и удалить", color = RED) }
            },
            dismissButton = { TextButton(onClick = { showSpamDialog = false }) { Text("Отмена") } }
        )
    }
}
