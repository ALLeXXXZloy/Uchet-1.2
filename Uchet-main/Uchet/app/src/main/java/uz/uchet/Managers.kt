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

// ... (GoalManagerSheet, CardManagerSheet, BudgetManagerSheet,
//      RecurringManagerSheet, CategoryManagerSheet, SettingsSheet,
//      DebtManagerSheet — БЕЗ ИЗМЕНЕНИЙ, копируются из вашего файла) ...

// ★★ НОВАЯ ФУНКЦИЯ — добавить в конец Managers.kt ★★
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleManagerSheet(
    rules: List<CustomKeywordRule>,
    currentLang: AppLanguage,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onSave: (String, String, TxType) -> Unit,
    onDelete: (CustomKeywordRule) -> Unit,
    onDismiss: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var ruleToDelete by remember { mutableStateOf<CustomKeywordRule?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("learned_rules"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "При встрече слова в СМС/чеке/голосе автоматически выбирается указанная категория",
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
                                Text(rule.keyword, fontWeight = FontWeight.Bold)
                                Text(
                                    "→ ${Translations.translateCategory(rule.category, currentLang)} (${rule.type.localizedLabel(currentLang)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { ruleToDelete = rule }) {
                                Icon(Icons.Default.Delete, null, tint = RED)
                            }
                        }
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
