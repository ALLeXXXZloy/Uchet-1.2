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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

// ★ FIX-A: CATEGORY_DEBT и CATEGORY_REPAY теперь живут в Constants.kt как публичные.
// Приватные дубликаты удалены, чтобы избежать "Conflicting declarations".

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

    var amountStr by remember { mutableStateOf(if (initial != null) BigDecimal(initial.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() else "") }
    var type by remember { mutableStateOf(initial?.type ?: quickTemplate?.type ?: TxType.EXPENSE) }
    var cardId by remember { mutableStateOf(initial?.cardId ?: "CASH") }
    var toCardId by remember { mutableStateOf(initial?.toCardId ?: "CASH") }
    var category by remember { mutableStateOf(initial?.category ?: quickTemplate?.category ?: (if (type == TxType.EXPENSE) expenseCategories.firstOrNull() ?: "" else incomeCategories.firstOrNull() ?: "")) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    val dateMillis = initial?.timestamp ?: System.currentTimeMillis()
    val currency = initial?.currency ?: "UZS"

    // ★ FB-20: Имя человека для долга
    var personName by remember {
        mutableStateOf(
            when {
                initial != null && initial.category == CATEGORY_DEBT -> extractPersonNameFromDescription(initial.description)
                else -> ""
            }
        )
    }

    // ★ FB-20: state диалога информации о связанном долге
    var showLinkedDebtInfo by remember { mutableStateOf(false) }

    var appliedRuleId by remember { mutableStateOf(ruleAppliedId) }
    var appliedRuleKeyword by remember { mutableStateOf(ruleAppliedKeyword) }

    var saveRule by remember { mutableStateOf(false) }
    var ruleKeyword by remember { mutableStateOf(description.take(15)) }

    var showRawTextDialog by remember { mutableStateOf(false) }
    val finalRawText = initial?.rawText ?: rawSourceText

    var showPhotoDialog by remember { mutableStateOf(false) }
    val photoPath = initial?.photoPath

    var showSpamDialog by remember { mutableStateOf(false) }
    var spamKeyword by remember { mutableStateOf(description.take(15).trim().ifBlank { category }) }

    // ★ FB-20: подтверждение при попытке сохранить без имени
    var nameError by remember { mutableStateOf(false) }

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
                    Text(t("sms_data"), color = YELLOW, style = MaterialTheme.typography.bodySmall)
                }

                // ★ Бейдж "Связано с долгом"
                if (initial?.linkedDebtId != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("debt_linked_badge"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("ID: #${initial.linkedDebtId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            TextButton(onClick = { showLinkedDebtInfo = true }) {
                                Text(t("info"))
                            }
                        }
                    }
                }

                // ★ Предупреждение для "Возврат долга"
                if (category == CATEGORY_REPAY) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = YELLOW.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = YELLOW)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                t("debt_repay_warning"),
                                style = MaterialTheme.typography.bodySmall,
                                color = YELLOW
                            )
                        }
                    }
                }

                // Блок источника (текст + фото)
                if (!finalRawText.isNullOrBlank() || photoPath != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t("source_data"), style = MaterialTheme.typography.labelMedium)
                            if (photoPath != null && File(photoPath).exists()) {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(
                                    model = File(photoPath),
                                    contentDescription = t("source_data"),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).clickable { showPhotoDialog = true },
                                    contentScale = ContentScale.Crop
                                )
                                Text(t("click_photo_to_zoom"), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(Modifier.height(8.dp))
                            }
                            if (!finalRawText.isNullOrBlank()) {
                                OutlinedButton(onClick = { showRawTextDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Info, contentDescription = "Оригинал")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(t("view_text"))
                                }
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
                        Text(t("rule_applied").format(appliedRuleKeyword ?: ""), color = Color(0xFF2E7D32), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            appliedRuleId?.let { onRuleRejected(it) }
                            appliedRuleId = null
                            appliedRuleKeyword = null
                            category = if (type == TxType.EXPENSE) expenseCategories.firstOrNull() ?: "" else incomeCategories.firstOrNull() ?: ""
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = t("cancel_rule"), tint = Color.Red)
                        }
                    }
                }

                // ★ Переключатель тип (если категория долга, нельзя переключать тип — иначе сломается связь)
                if (initial?.linkedDebtId == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        SegmentedButton(type == TxType.EXPENSE, {
                            type = TxType.EXPENSE
                            if (category != CATEGORY_DEBT && category != CATEGORY_REPAY) category = expenseCategories.firstOrNull() ?: ""
                        }, SegmentedButtonDefaults.itemShape(0, 3)) { Text(t("expense")) }
                        SegmentedButton(type == TxType.INCOME, {
                            type = TxType.INCOME
                            if (category != CATEGORY_DEBT && category != CATEGORY_REPAY) category = incomeCategories.firstOrNull() ?: ""
                        }, SegmentedButtonDefaults.itemShape(1, 3)) { Text(t("income")) }
                        SegmentedButton(type == TxType.TRANSFER, {
                            type = TxType.TRANSFER
                        }, SegmentedButtonDefaults.itemShape(2, 3)) { Text(t("transfer")) }
                    }
                } else {
                    // Для связанного долга показываем только тип (без возможности переключения)
                    Text(
                        t("type_locked").format(type.localizedLabel(currentLang)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
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

                // Карта (Куда) для переводов
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

                // Категория
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
                                DropdownMenuItem(
                                    text = { Text(Translations.translateCategory(c, currentLang)) },
                                    onClick = {
                                        category = c
                                        expandedCat = false
                                        if (c != CATEGORY_DEBT) personName = ""
                                    }
                                )
                            }
                        }
                    }
                }

                // ★ FB-20: Поле "Имя человека" для долга
                if (category == CATEGORY_DEBT) {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it; nameError = false },
                        label = { Text(t("debt_person_name_label")) },
                        isError = nameError,
                        supportingText = {
                            if (nameError) Text(t("debt_name_required"), color = RED)
                            else Text(
                                if (type == TxType.EXPENSE) t("debt_you_lent") else t("debt_you_borrowed"),
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                }

                // Описание (скрываем для "Долги" — оно формируется автоматически)
                if (category != CATEGORY_DEBT) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(t("desc")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                }

                // Чекбокс "Запомнить для будущего" (скрываем для долгов)
                if (type != TxType.TRANSFER && category != CATEGORY_DEBT && category != CATEGORY_REPAY) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saveRule = !saveRule }.padding(vertical = 4.dp)) {
                        Checkbox(checked = saveRule, onCheckedChange = { saveRule = it })
                        Text(t("remember_for_future"), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (saveRule) {
                        OutlinedTextField(
                            value = ruleKeyword,
                            onValueChange = { ruleKeyword = it },
                            label = { Text(t("rule_keyword")) },
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
                        if (category != CATEGORY_DEBT && category != CATEGORY_REPAY) {
                            TextButton(onClick = { showSpamDialog = true }, colors = ButtonDefaults.textButtonColors(contentColor = RED)) {
                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(t("this_is_spam"), fontSize = 12.sp)
                            }
                        }
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text(t("cancel")) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val parsedAmount = amountStr.replace(",", ".").toBigDecimalOrNull()
                            if (parsedAmount == null) {
                                Toast.makeText(context, t("error"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // ★ FB-20: валидация имени для долга
                            if (category == CATEGORY_DEBT && personName.isBlank()) {
                                nameError = true
                                return@Button
                            }

                            val finalAmountMinor = parsedAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

                            // ★ FB-20: формируем финальное описание и категорию
                            val finalCategory = when {
                                type == TxType.TRANSFER -> "💸 Перевод"
                                category == CATEGORY_DEBT -> CATEGORY_DEBT
                                else -> category
                            }
                            val finalDescription = when {
                                category == CATEGORY_DEBT -> {
                                    if (type == TxType.EXPENSE) t("debt_created_lent").format(personName.trim())
                                    else t("debt_created_borrowed").format(personName.trim())
                                }
                                else -> description
                            }

                            val newTx = Tx(
                                id = initial?.id ?: 0,
                                amountMinor = finalAmountMinor,
                                type = type,
                                cardId = if (cardId == "CASH") null else cardId,
                                toCardId = if (type == TxType.TRANSFER) (if (toCardId == "CASH") null else toCardId) else null,
                                description = finalDescription,
                                category = finalCategory,
                                timestamp = dateMillis,
                                source = initial?.source ?: if (isPendingSms) "SMS" else "MANUAL",
                                currency = currency,
                                rawText = finalRawText,
                                photoPath = photoPath,
                                linkedDebtId = initial?.linkedDebtId // сохраняем связь
                            )

                            if (initial != null) {
                                val merchantHint = initial.description.take(30)
                                if (initial.amountMinor != newTx.amountMinor) onRecordCorrection(merchantHint, "amount", initial.amountMinor.toString(), newTx.amountMinor.toString(), finalRawText ?: "")
                                if (initial.type != newTx.type) onRecordCorrection(merchantHint, "type", initial.type.name, newTx.type.name, finalRawText ?: "")
                                if (initial.cardId != newTx.cardId) onRecordCorrection(merchantHint, "card", initial.cardId ?: "null", newTx.cardId ?: "null", finalRawText ?: "")
                                if (initial.toCardId != newTx.toCardId) onRecordCorrection(merchantHint, "toCardId", initial.toCardId ?: "null", newTx.toCardId ?: "null", finalRawText ?: "")
                                if (initial.category != newTx.category) onRecordCorrection(merchantHint, "category", initial.category, newTx.category, finalRawText ?: "")
                                if (initial.description != newTx.description) onRecordCorrection(merchantHint, "merchant", initial.description, newTx.description, finalRawText ?: "")
                            }

                            if (saveRule && ruleKeyword.isNotBlank() && category != CATEGORY_DEBT) {
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

    // Инфо о связанном долге
    if (showLinkedDebtInfo && initial?.linkedDebtId != null) {
        AlertDialog(
            onDismissRequest = { showLinkedDebtInfo = false },
            title = { Text(t("debt_linked_info_title")) },
            text = {
                Column {
                    Text("ID: #${initial.linkedDebtId}")
                    Spacer(Modifier.height(4.dp))
                    Text(t("debt_linked_info_desc"))
                }
            },
            confirmButton = { TextButton(onClick = { showLinkedDebtInfo = false }) { Text(t("close")) } }
        )
    }

    // Просмотр скриншота
    if (showPhotoDialog && photoPath != null) {
        Dialog(onDismissRequest = { showPhotoDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.9f)) {
                Box(Modifier.fillMaxSize().clickable { showPhotoDialog = false }, contentAlignment = Alignment.Center) {
                    AsyncImage(model = File(photoPath), contentDescription = t("source_data"), modifier = Modifier.fillMaxWidth().padding(16.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }

    // Просмотр оригинального текста
    if (showRawTextDialog && !finalRawText.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showRawTextDialog = false },
            title = { Text(t("original_text"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(finalRawText, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRawTextDialog = false }) { Text(t("close")) } },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(finalRawText))
                    Toast.makeText(context, t("text_copied"), Toast.LENGTH_SHORT).show()
                }) { Text(t("copy")) }
            }
        )
    }

    // Диалог "Это спам"
    if (showSpamDialog) {
        AlertDialog(
            onDismissRequest = { showSpamDialog = false },
            title = { Text(t("ignore_as_spam")) },
            text = {
                Column {
                    Text(t("ignore_spam_desc"))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = spamKeyword,
                        onValueChange = { spamKeyword = it },
                        label = { Text(t("keyword")) },
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
                }) { Text(t("ignore_and_delete"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { showSpamDialog = false }) { Text(t("cancel")) } }
        )
    }
}

// ★ FB-20: извлечение имени из описания долга
private fun extractPersonNameFromDescription(description: String): String {
    val patterns = listOf("Дал в долг:", "Взял в долг:", "Дал в долг :", "Взял в долг :")
    for (p in patterns) {
        val idx = description.indexOf(p, ignoreCase = true)
        if (idx >= 0) return description.substring(idx + p.length).trim()
    }
    val colonIdx = description.indexOf(':')
    return if (colonIdx >= 0) description.substring(colonIdx + 1).trim() else ""
}
