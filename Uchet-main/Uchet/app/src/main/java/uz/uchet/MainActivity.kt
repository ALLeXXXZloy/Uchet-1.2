package uz.uchet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MainActivity : FragmentActivity() {
    private val pendingCheckTrigger = mutableStateOf(0)
    private val currentTheme = mutableStateOf(AppTheme.SYSTEM)
    private val currentLang = mutableStateOf(AppLanguage.RU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)

        val oldPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val newPrefs = SecurityHelper.getEncryptedPrefs(this)
        if (oldPrefs.contains("pin") && !newPrefs.contains("pin")) {
            newPrefs.edit()
                .putString("pin", oldPrefs.getString("pin", "0000"))
                .putBoolean("use_biometric", oldPrefs.getBoolean("use_biometric", true))
                .apply()
            oldPrefs.edit().clear().apply()
        }

        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        currentTheme.value = try { AppTheme.valueOf(prefs.getString("app_theme", "SYSTEM") ?: "SYSTEM") } catch (e: Exception) { AppTheme.SYSTEM }
        currentLang.value = try { AppLanguage.valueOf(prefs.getString("app_lang", "RU") ?: "RU") } catch (e: Exception) { AppLanguage.RU }

        setContent {
            val theme by currentTheme
            val lang by currentLang
            val colorScheme = when (theme) {
                AppTheme.LIGHT -> lightColorScheme(); AppTheme.DARK -> darkColorScheme()
                AppTheme.SYSTEM -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    var isAuthenticated by remember { mutableStateOf(false) }
                    if (isAuthenticated) App(pendingCheckTrigger = pendingCheckTrigger, currentTheme = currentTheme, currentLang = currentLang)
                    else LockScreen(activity = this@MainActivity, lang = lang, onUnlocked = { isAuthenticated = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); pendingCheckTrigger.value++
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, lang: AppLanguage, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val prefs = SecurityHelper.getEncryptedPrefs(activity)
    val savedPin = prefs.getString("pin", "0000") ?: "0000"
    val useBiometric = prefs.getBoolean("use_biometric", true)

    val biometricManager = BiometricManager.from(activity)
    val canUseBiometric = useBiometric &&
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val t = { key: String -> Translations.t(key, lang) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(t("locked"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = false },
            label = { Text(t("pin")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = error,
            supportingText = { if (error) Text(t("wrong_pin"), color = RED) }
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (pin == savedPin) onUnlocked()
                else { pin = ""; error = true }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("login")) }

        if (canUseBiometric) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                biometricError = null
                val executor = ContextCompat.getMainExecutor(activity)

                val cryptoObject: BiometricPrompt.CryptoObject? = try {
                    BiometricPrompt.CryptoObject(SecurityHelper.getCryptoObject())
                } catch (e: Exception) {
                    Log.e("BiometricDebug", "cryptoObject creation failed: ${e.message}", e)
                    null
                }

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(t("login"))
                    .setSubtitle(t("fingerprint"))
                    .setNegativeButtonText(t("pin"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result); onUnlocked()
                    }
                    override fun onAuthenticationFailed() { super.onAuthenticationFailed() }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        biometricError = errString.toString()
                        Log.e("BiometricDebug", "auth error $errorCode: $errString")
                    }
                }

                val biometricPrompt = BiometricPrompt(activity, executor, callback)
                try {
                    if (cryptoObject != null) biometricPrompt.authenticate(promptInfo, cryptoObject)
                    else biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    Log.e("BiometricDebug", "authenticate() failed: ${e.message}", e)
                    biometricError = e.localizedMessage ?: "Ошибка биометрии"
                }
            }) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text(t("fingerprint"))
            }

            biometricError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = RED, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(vm: VM = viewModel(), pendingCheckTrigger: MutableState<Int>? = null, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>) {
    val context = LocalContext.current
    val month by vm.month.collectAsState(); val txs by vm.txs.collectAsState(); val allTxs by vm.allTxs.collectAsState()
    val cards by vm.cards.collectAsState(); val budgets by vm.budgets.collectAsState(); val recurring by vm.recurring.collectAsState()
    val goals by vm.goals.collectAsState(); val rates by vm.rates.collectAsState()
    val categories by vm.categories.collectAsState()
    val forecasts by vm.forecasts.collectAsState()
    val expenseCategories = categories.filter { it.type == TxType.EXPENSE }.map { it.name }
    val incomeCategories = categories.filter { it.type == TxType.INCOME }.map { it.name }
    val t = { key: String -> Translations.t(key, currentLang.value) }

    LaunchedEffect(cards) {
        if (cards.none { it.id == "CASH" }) {
            vm.saveCard(CardEntity(id = "CASH", last4 = "CASH", name = "Наличные", currency = "UZS", initialBalanceMinor = 0))
        }
    }

    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var isVoiceTxAdding by remember { mutableStateOf(false) }
    var isPendingSms by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }; var showBudgetManager by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }; var showCategoryManager by remember { mutableStateOf(false) }
    var showGoalManager by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }; var showRestoreConfirm by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var importedTxs by remember { mutableStateOf<List<Tx>?>(null) }; var showImportConfirm by remember { mutableStateOf(false) }
    var importedCards by remember { mutableStateOf<List<CardEntity>?>(null) }; var showCardImportConfirm by remember { mutableStateOf(false) }

    var ocrDebugInfo by remember { mutableStateOf<String?>(null) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showForecastList by remember { mutableStateOf(false) }
    var openedForecastId by remember { mutableStateOf<Long?>(null) }
    var showBalanceDetails by remember { mutableStateOf(false) }

    var showPasswordDialogBackup by remember { mutableStateOf(false) }
    var showPasswordDialogRestore by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }

    var searchQuery by remember { mutableStateOf("") }; var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()

    // Авто-закрытие открытого прогноза, если он был удалён
    LaunchedEffect(forecasts) {
        if (openedForecastId != null && forecasts.none { it.id == openedForecastId }) {
            openedForecastId = null
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
                timestamp = pendingObj.optLong("timestamp"), source = "SMS"
            )
            editing = pendingTx; isPendingSms = true
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

    val csvExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(generateCsv(allTxs).toByteArray()) }; scope.launch { snackbarHostState.showSnackbar(t("export_ok")) } }
            catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } }
        }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let {
            val pwd = pendingBackupPassword
            if (pwd != null) {
                vm.createBackup(pwd) { blob ->
                    if (blob != null) {
                        try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(blob) }; scope.launch { snackbarHostState.showSnackbar(t("backup_saved_ok")) } }
                        catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } }
                    } else scope.launch { snackbarHostState.showSnackbar(t("error")) }
                    pendingBackupPassword = null
                }
            }
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } ?: return@let
                if (BackupEncryption.isEncrypted(bytes)) {
                    pendingRestoreBytes = bytes; showPasswordDialogRestore = true
                } else {
                    scope.launch { snackbarHostState.showSnackbar(t("restore_legacy_warning")) }
                    vm.restoreBackupLegacy(String(bytes, Charsets.UTF_8)) { ok ->
                        if (ok) scope.launch { snackbarHostState.showSnackbar(t("export_ok")) }
                        else scope.launch { snackbarHostState.showSnackbar(t("error")) }
                    }
                }
            } catch (e: Exception) { scope.launch { snackbarHostState.showSnackbar(t("error")) } }
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch {
            try {
                val mimeType = context.contentResolver.getType(it) ?: ""
                val text = if (mimeType == "application/pdf") extractTextFromPdf(context, it) else context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                val parsedTxs = SmsParser.parseImportedFile(text, allTxs)
                if (parsedTxs.isNotEmpty()) { importedTxs = parsedTxs; showImportConfirm = true }
                else snackbarHostState.showSnackbar(t("no_tx_found"))
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
                else {
                    ocrDebugInfo = buildString {
                        appendLine("--- ${t("ocr_debug")} ---"); appendLine(imageInfo)
                        appendLine("\n${t("debug_not_found")}"); appendLine("\n--- ${t("debug_what_ocr")} ---")
                        if (rawText.isBlank()) appendLine(t("debug_empty")) else appendLine(rawText)
                    }
                }
            }.addOnFailureListener { e -> ocrDebugInfo = "${t("error")} ML KIT:\n${e.message}" }
        } }
    }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

    val filteredTxs by remember(txs, searchQuery, filterType, currentLang.value) {
        derivedStateOf {
            val queryNumbers = searchQuery.filter { it.isDigit() }
            val hasNumbers = queryNumbers.isNotEmpty()
            txs.filter { tx ->
                val matches = searchQuery.isEmpty() ||
                    tx.description.contains(searchQuery, true) ||
                    Translations.translateCategory(tx.category, currentLang.value).contains(searchQuery, true) ||
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
    val totalForWidget = remember(totalLiquidBalanceUzs, goals) { totalLiquidBalanceUzs + goals.sumOf { it.currentAmountMinor } }

    LaunchedEffect(totalForWidget) {
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().putLong("total_balance", totalForWidget).apply()
        BalanceWidget.updateAll(context)
    }

    when {
        showAnalytics -> {
            BackHandler { showAnalytics = false }
            AnalyticsScreen(txs = txs, allTxs = allTxs, currentLang = currentLang.value, onBack = { showAnalytics = false })
        }
        openedForecastId != null -> {
            val forecast = forecasts.find { it.id == openedForecastId }
            if (forecast != null) {
                BackHandler { openedForecastId = null }
                ForecastScreen(
                    forecast = forecast,
                    currentLang = currentLang.value,
                    vm = vm,
                    onBack = { openedForecastId = null }
                )
            }
        }
        showForecastList -> {
            BackHandler { showForecastList = false }
            ForecastListScreen(
                forecasts = forecasts,
                currentLang = currentLang.value,
                onSave = { vm.saveForecast(it) },
                onDelete = { vm.deleteForecast(it) },
                onOpen = { openedForecastId = it.id },
                onBack = { showForecastList = false }
            )
        }
        else -> {
            if (showRestoreConfirm) {
                AlertDialog(onDismissRequest = { showRestoreConfirm = false }, title = { Text(t("restore")) }, text = { Text(t("restore") + "?") },
                    confirmButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(t("confirm"), color = RED) } },
                    dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(t("cancel")) } })
            }

            if (showImportConfirm && importedTxs != null) {
                val txsToImport = importedTxs!!
                AlertDialog(
                    onDismissRequest = { showImportConfirm = false; importedTxs = null },
                    title = { Text(t("import_data")) },
                    text = { Text("${t("import_confirm")} ${txsToImport.size}") },
                    confirmButton = {
                        TextButton(onClick = {
                            val existing = cards.map { it.id }.toSet()
                            txsToImport.mapNotNull { it.cardId }.distinct().forEach { cardId ->
                                if (!existing.contains(cardId)) {
                                    val last4 = cardId.split("_").firstOrNull() ?: "0000"
                                    val currency = cardId.split("_").getOrNull(1) ?: "UZS"
                                    vm.saveCard(CardEntity(id = cardId, last4 = last4, name = "${t("import_prefix")} *$last4", currency = currency, initialBalanceMinor = 0))
                                }
                            }
                            vm.bulkSave(txsToImport)
                            scope.launch { snackbarHostState.showSnackbar("${t("import_confirm")} ${txsToImport.size}") }
                            showImportConfirm = false; importedTxs = null
                        }) { Text(t("confirm"), color = GREEN) }
                    },
                    dismissButton = { TextButton(onClick = { showImportConfirm = false; importedTxs = null }) { Text(t("cancel")) } }
                )
            }

            if (showCardImportConfirm && importedCards != null) {
                val cardsToImport = importedCards!!
                AlertDialog(onDismissRequest = { showCardImportConfirm = false; importedCards = null }, title = { Text(t("import_cards")) },
                    text = { Text("${t("found_cards")} ${cardsToImport.size}") },
                    confirmButton = { TextButton(onClick = { cardsToImport.forEach { c -> vm.saveCard(c) }; scope.launch { snackbarHostState.showSnackbar(t("cards_added")) }; showCardImportConfirm = false; importedCards = null }) { Text(t("confirm"), color = GREEN) } },
                    dismissButton = { TextButton(onClick = { showCardImportConfirm = false; importedCards = null }) { Text(t("cancel")) } })
            }

            if (ocrDebugInfo != null) {
                AlertDialog(onDismissRequest = { ocrDebugInfo = null }, title = { Text(t("ocr_debug")) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectionContainer { Text(ocrDebugInfo!!, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } } },
                    confirmButton = { TextButton(onClick = { ocrDebugInfo = null }) { Text(t("close")) } })
            }

            if (showSettings) SettingsDialog(context, onDismiss = { showSettings = false }, snackbarHostState, scope, currentTheme, currentLang, vm)
            if (showCategoryManager) CategoryManagerDialog(categories, currentLang.value, { vm.saveCategory(it) }, { vm.deleteCategory(it) }, { showCategoryManager = false })
            if (showGoalManager) GoalManagerDialog(goals, cards, currentLang.value, { vm.saveGoal(it) }, { vm.deleteGoal(it) }, { g, c, a -> vm.depositToGoal(g, c, a) }, { showGoalManager = false })

            if (showBalanceDetails) {
                AlertDialog(onDismissRequest = { showBalanceDetails = false }, title = { Text(t("total_balance")) },
                    text = { Column { cards.forEach { c -> val bal = cardBalancesInUzs[c.id] ?: 0L; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${c.name} (${c.currency})", fontWeight = FontWeight.Medium); Text("${bal.money()} UZS", fontWeight = FontWeight.Bold, color = if (bal >= 0) GREEN else RED) } } } },
                    confirmButton = { TextButton(onClick = { showBalanceDetails = false }) { Text(t("close")) } })
            }

            if (showPasswordDialogBackup) {
                PasswordDialog(t("backup_new_password"), true, currentLang.value,
                    onDismiss = { showPasswordDialogBackup = false },
                    onConfirm = { pwd ->
                        pendingBackupPassword = pwd; showPasswordDialogBackup = false
                        jsonExportLauncher.launch("uchet_backup_${System.currentTimeMillis()}.uchet")
                    })
            }

            if (showPasswordDialogRestore && pendingRestoreBytes != null) {
                PasswordDialog(t("backup_enter_password"), false, currentLang.value,
                    onDismiss = { showPasswordDialogRestore = false; pendingRestoreBytes = null },
                    onConfirm = { pwd ->
                        val bytes = pendingRestoreBytes!!
                        showPasswordDialogRestore = false
                        scope.launch { snackbarHostState.showSnackbar(t("backup_decrypting")) }
                        vm.restoreBackup(bytes, pwd) { ok ->
                            if (ok) scope.launch { snackbarHostState.showSnackbar(t("export_ok")) }
                            else scope.launch { snackbarHostState.showSnackbar(t("backup_wrong_password")) }
                            pendingRestoreBytes = null
                        }
                    })
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, t("prev")) }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    val title = "${getMonthName(month.monthValue, currentLang.value)} ${month.year}"
                                    Text(title, modifier = Modifier.clickable { showMonthPicker = true }.padding(8.dp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    DropdownMenu(expanded = showMonthPicker, onDismissRequest = { showMonthPicker = false }) {
                                        (1..12).forEach { m ->
                                            DropdownMenuItem(text = { Text("${getMonthName(m, currentLang.value)} ${month.year}") }, onClick = { vm.month.value = YearMonth.of(month.year, m); showMonthPicker = false })
                                        }
                                    }
                                }
                                IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Default.ChevronRight, t("next")) }
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text(t("scan_sms")) }, leadingIcon = { Icon(Icons.Default.Message, null) }, onClick = {
                                        showMenu = false
                                        scanInboxSms(context, allTxs) { found ->
                                            if (found.isNotEmpty()) { importedTxs = found; showImportConfirm = true }
                                            else scope.launch { snackbarHostState.showSnackbar(t("new_sms_not_found")) }
                                        }
                                    })
                                    DropdownMenuItem(text = { Text(t("forecast_list")) }, leadingIcon = { Icon(Icons.Default.TrendingUp, null) }, onClick = { showMenu = false; showForecastList = true })
                                    DropdownMenuItem(text = { Text(t("import_csv")) }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) })
                                    DropdownMenuItem(text = { Text(t("import_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("application/pdf")) })
                                    DropdownMenuItem(text = { Text(t("import_cards")) }, leadingIcon = { Icon(Icons.Default.AddCard, null) }, onClick = { showMenu = false; cardImagePicker.launch("image/*") })
                                    DropdownMenuItem(text = { Text(t("manage_cards")) }, leadingIcon = { Icon(Icons.Default.CreditCard, null) }, onClick = { showMenu = false; showCardManager = true })
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text(t("export")) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; csvExportLauncher.launch("uchet_export.csv") })
                                    DropdownMenuItem(text = { Text(t("backup")) }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { showMenu = false; showPasswordDialogBackup = true })
                                    DropdownMenuItem(text = { Text(t("restore")) }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { showMenu = false; jsonImportLauncher.launch(arrayOf("*/*")) })
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text(t("settings")) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showSettings = true })
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    Box(
                        modifier = Modifier.size(56.dp).shadow(6.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .combinedClickable(
                                onClick = { adding = true },
                                onLongClick = { isVoiceTxAdding = true; adding = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, t("add_tx"), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            ) { pad ->
                LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAnalytics = true }.padding(8.dp)) { Icon(Icons.Default.BarChart, t("analytics"), tint = MaterialTheme.colorScheme.primary); Text(t("analytics"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showGoalManager = true }.padding(8.dp)) { Icon(Icons.Default.Savings, t("goals"), tint = MaterialTheme.colorScheme.primary); Text(t("goals"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showCategoryManager = true }.padding(8.dp)) { Icon(Icons.Default.Category, t("categories"), tint = MaterialTheme.colorScheme.primary); Text(t("categories"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showRecurringManager = true }.padding(8.dp)) { Icon(Icons.Default.Autorenew, t("recurring"), tint = MaterialTheme.colorScheme.primary); Text(t("recurring"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showBudgetManager = true }.padding(8.dp)) { Icon(Icons.Default.AccountBalanceWallet, t("budgets"), tint = MaterialTheme.colorScheme.primary); Text(t("budgets"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Card(Modifier.fillMaxWidth().clickable { showBalanceDetails = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t("total_balance"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text("${totalLiquidBalanceUzs.money()} UZS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (goals.isNotEmpty()) item {
                        Card(Modifier.fillMaxWidth().clickable { showGoalManager = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
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

                    if (budgets.isNotEmpty()) item {
                        Card(Modifier.fillMaxWidth().clickable { showBudgetManager = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(t("budgets_month"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
                                budgets.forEach { b ->
                                    val spent = txs.filter { it.type == TxType.EXPENSE && it.category == b.category }.sumOf { it.amountMinor }
                                    val progress = if (b.limitMinor > 0) spent.toFloat() / b.limitMinor.toFloat() else 0f
                                    val pColor = when { progress >= 1f -> RED; progress >= 0.8f -> YELLOW; else -> GREEN }
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Translations.translateCategory(b.category, currentLang.value), fontWeight = FontWeight.Medium); Text("${spent.money()} / ${b.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified) }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = pColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
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

                    if (filteredTxs.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text(if (searchQuery.isEmpty()) t("empty_tx") else t("no_found"), style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }

                    byDay.forEach { (day, list) ->
                        item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                        items(list, key = { it.id }) { tx -> TxRow(tx, currentLang.value) { editing = tx } }
                    }
                }
            }

            if (adding || editing != null) {
                TxDialog(
                    initial = editing, cards = cards,
                    expenseCategories = expenseCategories, incomeCategories = incomeCategories,
                    currentLang = currentLang.value, isPendingSms = isPendingSms,
                    autoStartVoice = isVoiceTxAdding, rates = rates, vm = vm,
                    onDismiss = {
                        if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                        adding = false; editing = null; isVoiceTxAdding = false
                    },
                    onSave = {
                        vm.save(it)
                        if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                        adding = false; editing = null; isVoiceTxAdding = false
                    },
                    onDelete = if (isPendingSms) null else editing?.let { e ->
                        { vm.delete(e); editing = null; scope.launch {
                            val timerJob = launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }
                            val result = snackbarHostState.showSnackbar(message = t("deleted"), actionLabel = t("undo"), duration = SnackbarDuration.Indefinite)
                            timerJob.cancel()
                            if (result == SnackbarResult.ActionPerformed) vm.save(e)
                        } }
                    }
                )
            }

            if (showCardManager) CardManagerDialog(cards, cardBalancesInUzs, allTxs, { vm.saveCard(it) }, { vm.deleteCard(it) }, { id, diff -> vm.adjustBalance(id, diff) }, currentLang.value, rates, { showCardManager = false })
            if (showBudgetManager) BudgetManagerDialog(budgets, expenseCategories, { vm.saveBudget(it) }, { vm.deleteBudget(it) }, currentLang.value, { showBudgetManager = false })
            if (showRecurringManager) RecurringManagerDialog(recurring, cards, expenseCategories, incomeCategories, currentLang.value, { vm.saveRecurring(it) }, { vm.deleteRecurring(it) }, { showRecurringManager = false })
        }
    }
}
