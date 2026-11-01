package com.example.uchet // Замените на ваш package name

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Импортируйте ваши сущности и ViewModel
// import com.example.uchet.data.Tx
// import com.example.uchet.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(
    tx: Tx?, // null = создание новой, не null = редактирование
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Состояния полей
    var amount by remember { mutableStateOf(tx?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(tx?.type ?: "EXPENSE") } // EXPENSE / INCOME
    var cardId by remember { mutableStateOf(tx?.cardId ?: 0) }
    var category by remember { mutableStateOf(tx?.category ?: "Разное") }
    var merchant by remember { mutableStateOf(tx?.merchant ?: "") }
    var rawText by remember { mutableStateOf(tx?.rawText) }

    // Состояние для показа диалога с оригиналом (FB-16)
    var showRawTextDialog by remember { mutableStateOf(false) }

    // Состояния для выпадающих списков
    var showCardDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    // Получаем данные из ViewModel (предполагается, что они там есть)
    val cards = viewModel.cards.collectAsState().value
    val categories = viewModel.categories.collectAsState().value // Список категорий

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Заголовок
                Text(
                    text = if (tx == null) "Новая операция" else "Редактирование",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Блок с оригинальным текстом (FB-16)
                if (!rawText.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Источник: Импорт (СМС/Чек/Выписка)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showRawTextDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Оригинал")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Посмотреть оригинал")
                            }
                        }
                    }
                }

                // Бейдж «Применено правило» (если есть)
                if (tx?.appliedRuleId != null && tx.appliedRuleId != 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Применено правило: ${tx.appliedRuleName ?: "Автоматически"}",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { 
                                // Логика отмены правила
                                // viewModel.rejectRule(tx)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Отменить правило", tint = Color.Red)
                        }
                    }
                }

                // Выбор типа (Расход / Доход)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        label = { Text("Расход") }
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        label = { Text("Доход") }
                    )
                }

                // Поле ввода суммы
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сумма") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                // Выбор карты (Упрощенный вариант)
                ExposedDropdownMenuBox(
                    expanded = showCardDropdown,
                    onExpandedChange = { showCardDropdown = !showCardDropdown },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = cards.find { it.id == cardId }?.name ?: "Выберите карту",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Карта / Счет") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCardDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showCardDropdown,
                        onDismissRequest = { showCardDropdown = false }
                    ) {
                        cards.forEach { card ->
                            DropdownMenuItem(
                                text = { Text(card.name) },
                                onClick = {
                                    cardId = card.id
                                    showCardDropdown = false
                                }
                            )
                        }
                    }
                }

                // Поле ввода категории
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Категория") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                // Поле ввода получателя
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Получатель / Описание") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    singleLine = true
                )

                // Кнопки сохранения/отмены
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
                            if (parsedAmount == null) {
                                Toast.makeText(context, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            // Вызов сохранения в ViewModel
                            // Если tx == null, создаем новый объект Tx с rawText
                            // иначе обновляем существующий
                            val newTx = tx?.copy(
                                amount = parsedAmount,
                                type = type,
                                cardId = cardId,
                                category = category,
                                merchant = merchant,
                                rawText = rawText // сохраняем оригинал
                            ) ?: Tx(
                                amount = parsedAmount,
                                type = type,
                                cardId = cardId,
                                category = category,
                                merchant = merchant,
                                rawText = rawText,
                                date = System.currentTimeMillis()
                            )
                            
                            // viewModel.saveTx(newTx) // Вызов вашего метода сохранения
                            onDismiss()
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }

    // =====================================================================
    // FB-16: Диалог просмотра оригинального текста
    // =====================================================================
    if (showRawTextDialog) {
        AlertDialog(
            onDismissRequest = { showRawTextDialog = false },
            title = { 
                Text(
                    text = "Оригинальный текст", 
                    style = MaterialTheme.typography.titleMedium
                ) 
            },
            text = {
                // Прокручиваемый и выделяемый контейнер
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = rawText ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawTextDialog = false }) {
                    Text("Закрыть")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(rawText ?: ""))
                    Toast.makeText(context, "Текст скопирован в буфер", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Копировать")
                }
            }
        )
    }
}
