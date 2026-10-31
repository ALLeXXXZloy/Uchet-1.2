package uz.uchet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    vm: VM,
    paddingValues: PaddingValues,
    currentLang: AppLanguage,
    onEditTx: (Tx) -> Unit,
    onDeleteTx: (Tx) -> Unit, // НОВЫЙ КОЛЛБЭК ДЛЯ УДАЛЕНИЯ
    onSwitchTab: (Int) -> Unit,
    onShowCardManager: () -> Unit,
    onShowBudgetManager: () -> Unit,
    onShowRecurringManager: () -> Unit,
    onShowCategoryManager: () -> Unit,
    onShowGoalManager: () -> Unit,
    onShowDebtManager: () -> Unit,
    onShowBalanceDetails: () -> Unit,
    onQuickTemplateClick: (QuickTemplate) -> Unit
) {
    val txs by vm.txs.collectAsState()
    val cards by vm.cards.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val goals by vm.goals.collectAsState()
    val rates by vm.rates.collectAsState()
    val allTxs by vm.allTxs.collectAsState()
    val pendingRecurring by vm.pendingRecurring.collectAsState()
    val debts by vm.debts.collectAsState()

    val t = { key: String -> Translations.t(key, currentLang) }

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TxType?>(null) }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

    val quickTemplates = remember(txs) {
        val grouped = txs.filter { it.type == TxType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.size to list.sumOf { it.amountMinor } / list.size }
        grouped.entries
            .sortedByDescending { it.value.first }
            .take(5)
            .map { QuickTemplate(it.key, it.value.second, TxType.EXPENSE) }
    }

    val filteredTxs by remember(txs, searchQuery, filterType, currentLang) {
        derivedStateOf {
            val queryNumbers = searchQuery.filter { it.isDigit() }
            val hasNumbers = queryNumbers.isNotEmpty()
            txs.filter { tx ->
                val matches = searchQuery.isEmpty() ||
                        tx.description.contains(searchQuery, true) ||
                        Translations.translateCategory(tx.category, currentLang).contains(searchQuery, true) ||
                        (hasNumbers && tx.amountMinor.toString().contains(queryNumbers))
                matches && (filterType == null || tx.type == filterType)
            }
        }
    }

    val byDay by remember(filteredTxs) {
        derivedStateOf { filteredTxs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() } }
    }

    val cardBalancesInUzs = remember(cards, allTxs, rates) {
        cards.associate { card ->
            val cardTxs = allTxs.filter { it.cardId == card.id }
            val cardIncome = cardTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            val cardExpense = cardTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
            val cardTransferOut = cardTxs.filter { it.type == TxType.TRANSFER }.sumOf { it.amountMinor }
            val cardTransferIn = allTxs.filter { it.toCardId == card.id && it.type == TxType.TRANSFER }.sumOf { it.toAmountMinor ?: it.amountMinor }
            val balanceInCardCurrency = card.initialBalanceMinor + cardIncome + cardTransferIn - cardExpense - cardTransferOut
            val rate = if (card.currency == "UZS") 1.0 else rates.find { it.currency == card.currency }?.rateToUzs ?: 1.0
            card.id to (balanceInCardCurrency.toDouble() * rate).toLong()
        }
    }
    val totalLiquidBalanceUzs = cardBalancesInUzs.values.sum()

    LazyColumn(
        Modifier.padding(paddingValues).fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)
    ) {
        if (pendingRecurring.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { onSwitchTab(2) },
                    colors = CardDefaults.cardColors(containerColor = YELLOW.copy(alpha = 0.15f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = YELLOW)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${pendingRecurring.size} ${t("pending_forecast")}", fontWeight = FontWeight.Medium, color = YELLOW)
                            Text(t("standard_forecast"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = YELLOW)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("search")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, t("clear")) }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text(t("all")) })
                FilterChip(selected = filterType == TxType.INCOME, onClick = { filterType = TxType.INCOME }, label = { Text(t("income")) })
                FilterChip(selected = filterType == TxType.EXPENSE, onClick = { filterType = TxType.EXPENSE }, label = { Text(t("expense")) })
            }
            Spacer(Modifier.height(12.dp))
        }

        if (quickTemplates.isNotEmpty()) {
            item {
                Text("Быстрый ввод", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    items(quickTemplates) { template ->
                        Card(
                            modifier = Modifier.width(100.dp).clickable { onQuickTemplateClick(template) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Translations.translateCategory(template.category, currentLang).take(10), style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text(template.averageAmountMinor.money(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RED)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowGoalManager() }.padding(4.dp)) { Icon(Icons.Default.Savings, t("goals"), tint = MaterialTheme.colorScheme.primary); Text(t("goals"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowCategoryManager() }.padding(4.dp)) { Icon(Icons.Default.Category, t("categories"), tint = MaterialTheme.colorScheme.primary); Text(t("categories"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowRecurringManager() }.padding(4.dp)) { Icon(Icons.Default.Autorenew, t("recurring"), tint = MaterialTheme.colorScheme.primary); Text(t("recurring"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowBudgetManager() }.padding(4.dp)) { Icon(Icons.Default.AccountBalanceWallet, t("budgets"), tint = MaterialTheme.colorScheme.primary); Text(t("budgets"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowCardManager() }.padding(4.dp)) { Icon(Icons.Default.CreditCard, t("my_accounts"), tint = MaterialTheme.colorScheme.primary); Text(t("my_accounts"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onShowDebtManager() }.padding(4.dp)) {
                    BadgedBox(badge = { val activeDebts = debts.count { !it.isPaid }; if (activeDebts > 0) Badge { Text("$activeDebts") } }) { Icon(Icons.Default.Handshake, t("debts"), tint = MaterialTheme.colorScheme.primary) }
                    Text(t("debts"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Card(Modifier.fillMaxWidth().clickable { onShowBalanceDetails() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("total_balance"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("${totalLiquidBalanceUzs.money()} UZS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (goals.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().clickable { onShowGoalManager() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(8.dp)); Text(t("goals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(12.dp))
                        goals.forEach { goal ->
                            val progress = if (goal.targetAmountMinor > 0) goal.currentAmountMinor.toFloat() / goal.targetAmountMinor.toFloat() else 0f
                            val done = progress >= 1f
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(goal.name, fontWeight = FontWeight.Medium); Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (done) GREEN else Color.Unspecified) }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = if (done) GREEN else BLUE, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().clickable { onShowBudgetManager() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t("budgets_month"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
                        budgets.forEach { b ->
                            val spent = txs.filter { it.type == TxType.EXPENSE && it.category == b.category }.sumOf { it.amountMinor }
                            val progress = if (b.limitMinor > 0) spent.toFloat() / b.limitMinor.toFloat() else 0f
                            val pColor = when { progress >= 1f -> RED; progress >= 0.8f -> YELLOW; else -> GREEN }
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Translations.translateCategory(b.category, currentLang), fontWeight = FontWeight.Medium); Text("${spent.money()} / ${b.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified) }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = pColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SumRow(t("income"), income, GREEN); SumRow(t("expense"), expense, RED)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SumRow(t("total"), income - expense, MaterialTheme.colorScheme.onSurface, bold = true)
                }
            }
        }

        if (filteredTxs.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(if (searchQuery.isEmpty()) t("empty_tx") else t("no_found"), style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }

        // --- ОБНОВЛЕННЫЙ СПИСОК С ПОДДЕРЖКОЙ СВАЙПОВ ---
        byDay.forEach { (day, list) ->
            item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
            items(list, key = { it.id }) { tx ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        when (it) {
                            SwipeToDismissBoxValue.EndToStart -> { onDeleteTx(tx); true }
                            SwipeToDismissBoxValue.StartToEnd -> { onEditTx(tx); false } // false, чтобы не удалять элемент визуально
                            else -> false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val color = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> BLUE.copy(alpha = 0.5f)
                            SwipeToDismissBoxValue.EndToStart -> RED.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                        val alignment = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                            else -> Alignment.Center
                        }
                        val icon = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                            SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                            else -> Icons.Default.Clear
                        }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .padding(horizontal = 20.dp),
                            contentAlignment = alignment
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White)
                        }
                    },
                    content = {
                        // Оборачиваем в Box, чтобы добавить фон и скругления
                        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            TxRow(tx, currentLang) { onEditTx(tx) }
                        }
                    }
                )
            }
        }
        // ------------------------------------------------
    }
}
