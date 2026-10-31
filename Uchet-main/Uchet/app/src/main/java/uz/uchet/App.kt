package uz.uchet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.time.YearMonth
import java.util.UUID
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(
    vm: VM = koinViewModel(),
    pendingCheckTrigger: MutableState<Int>? = null,
    widgetActionTrigger: MutableState<Int>? = null,
    intent: Intent? = null,
    currentTheme: MutableState<AppTheme>,
    currentLang: MutableState<AppLanguage>
) {
    val context = LocalContext.current
    val month by vm.month.collectAsState()
    val allTxs by vm.allTxs.collectAsState()
    val txs by vm.txs.collectAsState()
    val cards by vm.cards.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val recurring by vm.recurring.collectAsState()
    val goals by vm.goals.collectAsState()
    val rates by vm.rates.collectAsState()
    val categories by vm.categories.collectAsState()
    val forecasts by vm.forecasts.collectAsState()
    val debts by vm.debts.collectAsState()
    val customRules by vm.customRules.collectAsState()
    val corrections by vm.corrections.collectAsState()

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
    val totalForWidget = remember(totalLiquidBalanceUzs, goals) { totalLiquidBalanceUzs + goals.sumOf { it.currentAmountMinor } }
    LaunchedEffect(totalForWidget) {
        context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE).edit().putLong("total_balance", totalForWidget).apply()
        BalanceWidget.updateAll(context)
    }

    var currentTab by remember { mutableIntStateOf(0) }
    var openedForecastId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    var showCardManager by remember { mutableStateOf(false) }
    var showBudgetManager by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showGoalManager by remember { mutableStateOf(false) }
    var showDebtManager by remember { mutableStateOf(false) }
    var showBalanceDetails by remember { mutableStateOf(false) }
    var showRuleManager by remember { mutableStateOf(false) }
    var showCorrectionManager by remember { mutableStateOf(false) }

    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedQuickTemplate by remember { mutableStateOf<QuickTemplate?>(null) }
    var isVoiceTxAdding by remember { mutableStateOf(false) }
    var isPendingSms by remember { mutableStateOf(false) }

    var pendingRuleAppliedId by remember { mutableStateOf<Long?>(null) }
    var pendingRuleAppliedKeyword by remember { mutableStateOf<String?>(null) }
    // ★ D: исходный текст из очереди
    var pendingRawText by remember { mutableStateOf<String?>(null) }

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showPasswordDialogBackup by remember { mutableStateOf(false) }
    var showPasswordDialogRestore by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importedTxs by remember { mutableStateOf<List<Tx>?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var importedCards by remember { mutableStateOf<List<CardEntity>?>(null) }
    var showCardImportConfirm by remember { mutableStateOf(false) }
    var ocrDebugInfo by remember { mutableStateOf<String?>(null) }

    // ★ D: bulk-импорт чеков
    var bulkTotal by remember { mutableIntStateOf(0) }
    var bulkProcessed by remember { mutableIntStateOf(0) }
    var bulkInProgress by remember { mutableStateOf(false) }
    var bulkResultCount by remember { mutableIntStateOf(0) }
    var showBulkResult by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val t = { key: String -> Translations.t(key, currentLang.value) }

    val widgetTrigger = widgetActionTrigger?.value ?: 0
    LaunchedEffect(widgetTrigger) {
        val actionCategory = intent?.getStringExtra("QUICK_ACTION_CATEGORY")
        val actionType = intent?.getStringExtra("QUICK_ACTION_TYPE")
        if (actionCategory != null && actionType != null) {
            val txType = if (actionType == "INCOME") TxType.INCOME else TxType.EXPENSE
            selectedQuickTemplate = QuickTemplate(category = actionCategory, averageAmountMinor = 0L, type = txType)
            adding = true
            intent.removeExtra("QUICK_ACTION_CATEGORY")
        }
    }

    LaunchedEffect(cards) {
        if (cards.none { it.id == "CASH" }) {
            vm.saveCard(CardEntity(id = "CASH", last4 = "CASH", name = "Наличные", currency = "UZS", initialBalanceMinor = 0))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECEIVE_SMS)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.CAMERA)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    fun checkPendingSms() {
        if (editing != null || adding) return
        val pendingObj = PendingQueue.peek(context) ?: return
        try {
            val pendingTx = Tx(
                id = 0, amountMinor = pendingObj.getLong("amountMinor"),
                type = TxType.valueOf(pendingObj.getString("type")),
                cardId = if (pendingObj.isNull("card")) null else pendingObj.getString("card"),
                description = pendingObj.optString("description"),
                category = pendingObj.optString("category"),
                timestamp = pendingObj.optLong("timestamp"),
                source = "SMS",
                rawText = pendingObj.optString("rawBody").takeIf { it.isNotBlank() }
            )
            pendingRuleAppliedId = if (pendingObj.isNull("ruleAppliedId")) null else pendingObj.optLong("ruleAppliedId")
            pendingRuleAppliedKeyword = if (pendingObj.isNull("ruleAppliedKeyword")) null else pendingObj.optString("ruleAppliedKeyword").takeIf { it.isNotBlank() }
            pendingRawText = pendingObj.optString("rawBody").takeIf { it.isNotBlank() }
            editing = pendingTx
            isPendingSms = true
        } catch (e: Exception) { PendingQueue.pop(context) }
    }

    val trigger = pendingCheckTrigger?.value ?: 0
    LaunchedEffect(trigger) { checkPendingSms() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkPendingSms() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ★ D: suspend-обёртка над ML Kit OCR
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
                .addOnFailureListener { cont.resume("") }
        } catch (e: Exception) { cont.resume("") }
    }

    // ★ D: bulk-импорт чеков
    val bulkReceiptsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        bulkTotal = uris.size
        bulkProcessed = 0
        bulkInProgress = true
        scope.launch {
            val imported = mutableListOf<Tx>()
            val expenseCats = categories.filter { it.type == TxType.EXPENSE }.map { it.name }
            for ((idx, uri) in uris.withIndex()) {
                bulkProcessed = idx
                try {
                    val bitmap = withContext(Dispatchers.IO) { uriToBitmap(context, uri) } ?: continue
                    val rawText = withContext(Dispatchers.IO) { recognizeText(bitmap) }
                    if (rawText.isBlank()) continue
                    val receipt = ReceiptParser.parse(rawText)
                    var amount = receipt.amountMinor
                    if (amount == null || amount <= 0) amount = ReceiptParser.extractBiggestAmount(rawText)
                    if (amount == null || amount <= 0) continue
                    val seller = receipt.sellerName ?: ""
                    val cat = MerchantCategorizer.getCategory(seller, expenseCats, customRules, TxType.EXPENSE)?.category ?: "📦 Другое"
                    // Сохраняем фото чека
                    val photoFile = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                    photoFile.parentFile?.mkdirs()
                    withContext(Dispatchers.IO) { photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
                    imported.add(Tx(
                        amountMinor = amount, type = TxType.EXPENSE,
                        description = seller, category = cat,
                        timestamp = receipt.dateMillis ?: System.currentTimeMillis(),
                        source = "OCR", photoPath = photoFile.absolutePath, rawText = rawText
                    ))
                } catch (e: Exception) { /* пропускаем битый файл */ }
            }
            bulkProcessed = uris.size
            if (imported.isNotEmpty()) vm.bulkSave(imported)
            bulkResultCount = imported.size
            bulkInProgress = false
            showBulkResult = true
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = withContext(Dispatchers.IO) { PdfReportGenerator.generateReport(context, it, txs, month, currentLang.value) }
                if (success) snackbarHostState.showSnackbar("PDF отчет сохранен") else snackbarHostState.showSnackbar(t("error"))
            }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(generateCsv(allTxs).toByteArray()) }; scope.launch { snackbarHostState.showSnackbar(t("export_ok")) } } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } } }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let { val pwd = pendingBackupPassword; if (pwd != null) { vm.createBackup(pwd) { blob -> if (blob != null) { try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(blob) }; scope.launch { snackbarHostState.showSnackbar(t("backup_saved_ok")) } } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } } } else scope.launch { snackbarHostState.showSnackbar(t("error")) }; pendingBackupPassword = null } } }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } ?: return@let
                if (BackupEncryption.isEncrypted(bytes)) { pendingRestoreBytes = bytes; showPasswordDialogRestore = true }
                else { scope.launch { snackbarHostState.showSnackbar(t("restore_legacy_warning")) }; vm.restoreBackupLegacy(String(bytes, Charsets.UTF_8)) { ok -> if (ok) scope.launch { snackbarHostState.showSnackbar(t("export_ok")) } else scope.launch { snackbarHostState.showSnackbar(t("error")) } } }
            } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } }
        }
    }
    val fileImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch {
            try {
                val mimeType = context.contentResolver.getType(it) ?: ""
                val text = if (mimeType == "application/pdf") extractTextFromPdf(context, it) else context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                val parsedTxs = withContext(Dispatchers.IO) { SmsParser.parseImportedFile(text, allTxs, customRules) }
                if (parsedTxs.isNotEmpty()) { importedTxs = parsedTxs; showImportConfirm = true } else snackbarHostState.showSnackbar(t("no_tx_found"))
            } catch (e: Exception) { snackbarHostState.showSnackbar(t("file_read_error")) }
        } }
    }
    val cardImagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch {
            val bitmap = uriToBitmap(context, it)
            if (bitmap == null) { ocrDebugInfo = "${t("image_error")}\n$it"; return@launch }
            val imageInfo = "${t("debug_size")}: ${bitmap.width}x${bitmap.height}"
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val parsedCards = parseCardsFromScreenshot(rawText)
                if (parsedCards.isNotEmpty()) { importedCards = parsedCards; showCardImportConfirm = true }
                else { ocrDebugInfo = buildString { appendLine("--- ${t("ocr_debug")} ---"); appendLine(imageInfo); appendLine("\n${t("debug_not_found")}"); appendLine("\n--- ${t("debug_what_ocr")} ---"); if (rawText.isBlank()) appendLine(t("debug_empty")) else appendLine(rawText) } }
            }.addOnFailureListener { e -> ocrDebugInfo = "${t("error")} ML KIT:\n${e.message}" }
        } }
    }

    val expenseCategories = categories.filter { it.type == TxType.EXPENSE }.map { it.name }
    val incomeCategories = categories.filter { it.type == TxType.INCOME }.map { it.name }

    if (openedForecastId != null) {
        val forecast = forecasts.find { it.id == openedForecastId }
        if (forecast != null) {
            BackHandler { openedForecastId = null }
            ForecastScreen(forecast = forecast, currentLang = currentLang.value, vm = vm, onBack = { openedForecastId = null })
            return
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(t("operations")) })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(t("analytics")) })
                NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { BadgedBox(badge = { if (vm.pendingRecurring.collectAsState().value.isNotEmpty()) Badge { Text("${vm.pendingRecurring.collectAsState().value.size}") } }) { Icon(Icons.Default.Assignment, null) } }, label = { Text(t("standard_forecast")) })
                NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.TrendingUp, null) }, label = { Text(t("forecast_list")) })
                NavigationBarItem(selected = false, onClick = { showSettings = true }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text(t("settings")) })
            }
        },
        topBar = {
            if (currentTab == 0) {
                TopAppBar(
                    title = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, t("prev")) }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                val title = "${getMonthName(month.monthValue, currentLang.value)} ${month.year}"
                                Text(title, modifier = Modifier.clickable { showMonthPicker = true }.padding(8.dp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                DropdownMenu(expanded = showMonthPicker, onDismissRequest = { showMonthPicker = false }) { (1..12).forEach { m -> DropdownMenuItem(text = { Text("${getMonthName(m, currentLang.value)} ${month.year}") }, onClick = { vm.month.value = YearMonth.of(month.year, m); showMonthPicker = false }) } }
                            }
                            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Default.ChevronRight, t("next")) }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(t("scan_sms")) },
                                    leadingIcon = { Icon(Icons.Default.Message, null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            val found = withContext(Dispatchers.IO) { SmsParser.scanInbox(context, allTxs, customRules) }
                                            if (found.isNotEmpty()) { importedTxs = found; showImportConfirm = true }
                                            else snackbarHostState.showSnackbar(t("new_sms_not_found"))
                                        }
                                    }
                                )
                                DropdownMenuItem(text = { Text(t("import_csv")) }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) })
                                DropdownMenuItem(text = { Text(t("import_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("application/pdf")) })
                                // ★ D: bulk-импорт чеков
                                DropdownMenuItem(
                                    text = { Text(t("import_receipts_bulk")) },
                                    leadingIcon = { Icon(Icons.Default.Collections, null) },
                                    onClick = { showMenu = false; bulkReceiptsLauncher.launch(arrayOf("image/*")) }
                                )
                                DropdownMenuItem(text = { Text(t("import_cards")) }, leadingIcon = { Icon(Icons.Default.AddCard, null) }, onClick = { showMenu = false; cardImagePicker.launch("image/*") })
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(t("learned_rules")) },
                                    leadingIcon = { Icon(Icons.Default.School, null) },
                                    onClick = { showMenu = false; showRuleManager = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(t("learned_corrections")) },
                                    leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                    onClick = { showMenu = false; showCorrectionManager = true }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("export_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; pdfExportLauncher.launch("uchet_report_${month}.pdf") })
                                DropdownMenuItem(text = { Text(t("export")) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; csvExportLauncher.launch("uchet_export.csv") })
                                DropdownMenuItem(text = { Text(t("backup")) }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { showMenu = false; showPasswordDialogBackup = true })
                                DropdownMenuItem(text = { Text(t("restore")) }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { showMenu = false; jsonImportLauncher.launch(arrayOf("*/*")) })
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                Box(modifier = Modifier.size(56.dp).shadow(6.dp, CircleShape).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).combinedClickable(onClick = { adding = true; selectedQuickTemplate = null }, onLongClick = { isVoiceTxAdding = true; adding = true }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, t("add_tx"), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    ) { pad ->
        when (currentTab) {
            0 -> OperationsScreen(
                vm = vm, paddingValues = pad, currentLang = currentLang.value,
                onEditTx = { editing = it },
                onDeleteTx = { tx ->
                    vm.delete(tx)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = t("deleted"),
                            actionLabel = t("undo"),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.save(tx)
                    }
                },
                onSwitchTab = { currentTab = it },
                onShowCardManager = { showCardManager = true }, onShowBudgetManager = { showBudgetManager = true },
                onShowRecurringManager = { showRecurringManager = true }, onShowCategoryManager = { showCategoryManager = true },
                onShowGoalManager = { showGoalManager = true }, onShowDebtManager = { showDebtManager = true },
                onShowBalanceDetails = { showBalanceDetails = true }, onQuickTemplateClick = { template -> selectedQuickTemplate = template; adding = true }
            )
            1 -> AnalyticsScreen(txs = txs, allTxs = allTxs, currentLang = currentLang.value, onBack = { currentTab = 0 })
            2 -> StandardForecastScreen(vm = vm, currentLang = currentLang.value, onManageRecurring = { showRecurringManager = true }, onBack = { currentTab = 0 })
            3 -> ForecastListScreen(forecasts = forecasts, currentLang = currentLang.value, onSave = { vm.saveForecast(it) }, onDelete = { vm.deleteForecast(it) }, onOpen = { openedForecastId = it.id }, onBack = { currentTab = 0 })
        }
    }

    if (showSettings) SettingsSheet(context, { showSettings = false }, snackbarHostState, scope, currentTheme, currentLang, vm)
    if (showCategoryManager) CategoryManagerSheet(categories, currentLang.value, { vm.saveCategory(it) }, { vm.deleteCategory(it) }, { showCategoryManager = false })
    if (showGoalManager) GoalManagerSheet(goals, cards, currentLang.value, { vm.saveGoal(it) }, { vm.deleteGoal(it) }, { g, c, a -> vm.depositToGoal(g, c, a) }, { showGoalManager = false })
    if (showCardManager) CardManagerSheet(cards, cardBalancesInUzs, allTxs, { vm.saveCard(it) }, { vm.deleteCard(it) }, { id, diff -> vm.adjustBalance(id, diff) }, currentLang.value, rates, { showCardManager = false })
    if (showBudgetManager) BudgetManagerSheet(budgets, expenseCategories, { vm.saveBudget(it) }, { vm.deleteBudget(it) }, currentLang.value, { showBudgetManager = false })
    if (showRecurringManager) RecurringManagerSheet(recurring, cards, expenseCategories, incomeCategories, currentLang.value, { vm.saveRecurring(it) }, { vm.deleteRecurring(it) }, { showRecurringManager = false })
    if (showDebtManager) DebtManagerSheet(debts, currentLang.value, { vm.saveDebt(it) }, { vm.markDebtPaid(it) }, { vm.deleteDebt(it) }, { showDebtManager = false })

    if (showRuleManager) RuleManagerSheet(
        rules = customRules,
        currentLang = currentLang.value,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories,
        onSave = { kw, cat, type -> vm.saveCustomRule(kw, cat, type) },
        onDelete = { rule -> vm.deleteCustomRule(rule) },
        onDeleteUnused = { vm.deleteUnusedRules() },
        onDismiss = { showRuleManager = false }
    )

    if (showCorrectionManager) CorrectionManagerSheet(
        corrections = corrections,
        currentLang = currentLang.value,
        onDelete = { c -> vm.deleteCorrection(c) },
        onClearAll = { vm.clearCorrections() },
        onDismiss = { showCorrectionManager = false }
    )

    if (adding || editing != null) {
        TxDialog(
            initial = editing, quickTemplate = selectedQuickTemplate, cards = cards,
            expenseCategories = expenseCategories, incomeCategories = incomeCategories,
            currentLang = currentLang.value, isPendingSms = isPendingSms,
            autoStartVoice = isVoiceTxAdding, rates = rates,
            customRules = customRules,
            ruleAppliedId = pendingRuleAppliedId,
            ruleAppliedKeyword = pendingRuleAppliedKeyword,
            rawSourceText = pendingRawText,   // ★ D
            vm = vm,
            onDismiss = {
                if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                adding = false; editing = null; isVoiceTxAdding = false; selectedQuickTemplate = null
                pendingRuleAppliedId = null; pendingRuleAppliedKeyword = null; pendingRawText = null
            },
            onSave = {
                vm.save(it)
                if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                adding = false; editing = null; isVoiceTxAdding = false; selectedQuickTemplate = null
                pendingRuleAppliedId = null; pendingRuleAppliedKeyword = null; pendingRawText = null
            },
            onSaveRule = { kw, cat, type -> vm.saveCustomRule(kw, cat, type) },
            onRuleApplied = { id -> vm.bumpRuleApplied(id) },
            onRuleRejected = { id -> vm.bumpRuleRejected(id) },
            // ★ D: теперь 5 параметров, включая rawText
            onRecordCorrection = { hint, field, parsed, corrected, rawText ->
                vm.recordCorrection(
                    source = editing?.source ?: (if (isPendingSms) "SMS" else "MANUAL"),
                    merchantHint = hint,
                    field = field,
                    parsedValue = parsed,
                    correctedValue = corrected,
                    rawText = rawText
                )
            },
            onDelete = if (isPendingSms) null else editing?.let { e -> { vm.delete(e); editing = null; scope.launch { val timerJob = launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }; val result = snackbarHostState.showSnackbar(message = t("deleted"), actionLabel = t("undo"), duration = SnackbarDuration.Indefinite); timerJob.cancel(); if (result == SnackbarResult.ActionPerformed) vm.save(e) } } }
        )
    }

    if (showImportConfirm && importedTxs != null) {
        val txsToImport = importedTxs!!
        AlertDialog(onDismissRequest = { showImportConfirm = false; importedTxs = null }, title = { Text(t("import_data")) }, text = { Text("${t("import_confirm")} ${txsToImport.size}") },
            confirmButton = { TextButton(onClick = { val existing = cards.map { it.id }.toSet(); txsToImport.mapNotNull { it.cardId }.distinct().forEach { cardId -> if (!existing.contains(cardId)) { val last4 = cardId.split("_").firstOrNull() ?: "0000"; val currency = cardId.split("_").getOrNull(1) ?: "UZS"; vm.saveCard(CardEntity(id = cardId, last4 = last4, name = "${t("import_prefix")} *$last4", currency = currency, initialBalanceMinor = 0)) } }; vm.bulkSave(txsToImport); scope.launch { snackbarHostState.showSnackbar("${t("import_confirm")} ${txsToImport.size}") }; showImportConfirm = false; importedTxs = null }) { Text(t("confirm"), color = GREEN) } },
            dismissButton = { TextButton(onClick = { showImportConfirm = false; importedTxs = null }) { Text(t("cancel")) } })
    }
    if (showCardImportConfirm && importedCards != null) {
        val cardsToImport = importedCards!!
        AlertDialog(onDismissRequest = { showCardImportConfirm = false; importedCards = null }, title = { Text(t("import_cards")) }, text = { Text("${t("found_cards")} ${cardsToImport.size}") },
            confirmButton = { TextButton(onClick = { cardsToImport.forEach { c -> vm.saveCard(c) }; scope.launch { snackbarHostState.showSnackbar(t("cards_added")) }; showCardImportConfirm = false; importedCards = null }) { Text(t("confirm"), color = GREEN) } },
            dismissButton = { TextButton(onClick = { showCardImportConfirm = false; importedCards = null }) { Text(t("cancel")) } })
    }

    // ★ D: прогресс bulk-импорта чеков
    if (bulkInProgress) {
        AlertDialog(
            onDismissRequest = { /* запрещаем закрывать до завершения */ },
            title = { Text(t("import_receipts_bulk")) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { if (bulkTotal > 0) bulkProcessed.toFloat() / bulkTotal.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$bulkProcessed / $bulkTotal")
                }
            },
            confirmButton = {}
        )
    }

    // ★ D: результат bulk-импорта
    if (showBulkResult) {
        AlertDialog(
            onDismissRequest = { showBulkResult = false },
            title = { Text(t("import_receipts_bulk")) },
            text = { Text("${t("bulk_import_done")} $bulkResultCount ${t("bulk_import_suffix")}") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkResult = false
                    currentTab = 0
                }) { Text(t("open_for_review"), color = GREEN) }
            },
            dismissButton = { TextButton(onClick = { showBulkResult = false }) { Text(t("close")) } }
        )
    }

    if (showBalanceDetails) {
        AlertDialog(onDismissRequest = { showBalanceDetails = false }, title = { Text(t("total_balance")) }, text = { Column { cards.forEach { c -> val bal = cardBalancesInUzs[c.id] ?: 0L; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${c.name} (${c.currency})", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium); Text("${bal.money()} UZS", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (bal >= 0) GREEN else RED) } } } }, confirmButton = { TextButton(onClick = { showBalanceDetails = false }) { Text(t("close")) } })
    }
    if (showPasswordDialogBackup) { PasswordDialog(t("backup_new_password"), true, currentLang.value, onDismiss = { showPasswordDialogBackup = false }, onConfirm = { pwd -> pendingBackupPassword = pwd; showPasswordDialogBackup = false; jsonExportLauncher.launch("uchet_backup_${System.currentTimeMillis()}.uchet") }) }
    if (showPasswordDialogRestore && pendingRestoreBytes != null) { PasswordDialog(t("backup_enter_password"), false, currentLang.value, onDismiss = { showPasswordDialogRestore = false; pendingRestoreBytes = null }, onConfirm = { pwd -> val bytes = pendingRestoreBytes!!; showPasswordDialogRestore = false; scope.launch { snackbarHostState.showSnackbar(t("backup_decrypting")) }; vm.restoreBackup(bytes, pwd) { ok -> if (ok) scope.launch { snackbarHostState.showSnackbar(t("export_ok")) } else scope.launch { snackbarHostState.showSnackbar(t("backup_wrong_password")) }; pendingRestoreBytes = null } }) }
    if (ocrDebugInfo != null) { AlertDialog(onDismissRequest = { ocrDebugInfo = null }, title = { Text(t("ocr_debug")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectionContainer { Text(ocrDebugInfo!!, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } } }, confirmButton = { TextButton(onClick = { ocrDebugInfo = null }) { Text(t("close")) } }) }
}
