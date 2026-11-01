package uz.uchet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    initialTxs: List<Tx>,
    cards: List<CardEntity>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    currentLang: AppLanguage,
    rates: List<ExchangeRateEntity>,
    customRules: List<CustomKeywordRule>,
    vm: VM,
    onSave: (List<Tx>) -> Unit,
    onCancel: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    
    // Локальное состояние списка
    var currentTxs by remember { mutableStateOf(initialTxs) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }
    
    // Для свайпа
    val dismissState = rememberSwipeToDismissBoxState() // Просто для инициализации

    BackHandler { onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проверка импорта (${currentTxs.size})") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Отмена") }
                },
                actions = {
                    TextButton(onClick = { onSave(currentTxs) }) {
                        Text("Сохранить всё", fontWeight = FontWeight.Bold, color = GREEN)
                    }
                }
            )
        }
    ) { pad ->
        if (currentTxs.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Text("Все операции удалены", color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCancel) { Text("Вернуться") }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(currentTxs, key = { it.hashCode() }) { tx ->
                // Свайп для удаления
                val dismissBoxState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            currentTxs = currentTxs.filter { item -> item.hashCode() != tx.hashCode() }
                            true
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissBoxState,
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(RED.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                        }
                    },
                    content = {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        Translations.translateCategory(tx.category, currentLang), 
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (tx.description.isNotBlank()) {
                                        Text(tx.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                                    }
                                    val date = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                    Text(date.format(DATE_FMT), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Text(
                                    "${if (tx.type == TxType.INCOME) "+" else "-"}${tx.amountMinor.money()}",
                                    color = if (tx.type == TxType.INCOME) GREEN else RED,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { editingTx = tx }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Диалог редактирования
    if (editingTx != null) {
        TxDialog(
            initial = editingTx,
            quickTemplate = null,
            cards = cards,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            currentLang = currentLang,
            isPendingSms = false,
            autoStartVoice = false,
            rates = rates,
            customRules = customRules,
            ruleAppliedId = null,
            ruleAppliedKeyword = null,
            rawSourceText = editingTx?.rawText,
            vm = vm,
            onDismiss = { editingTx = null },
            onSave = { updated ->
                currentTxs = currentTxs.map { if (it.hashCode() == editingTx.hashCode()) updated else it }
                editingTx = null
            },
            onSaveRule = { kw, cat, type -> vm.saveCustomRule(kw, cat, type) },
            onRuleApplied = { },
            onRuleRejected = { },
            onRecordCorrection = { hint, field, parsed, corrected, rawText ->
                vm.recordCorrection("IMPORT", hint, field, parsed, corrected, rawText)
            },
            onDelete = {
                currentTxs = currentTxs.filter { it.hashCode() != editingTx.hashCode() }
                editingTx = null
            }
        )
    }
}
