package uz.uchet

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter

class VM(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val txs = month.flatMapLatest { m ->
        val z = ZoneId.systemDefault()
        dao.observe(m.atDay(1).atStartOfDay(z).toInstant().toEpochMilli(), m.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTxs = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cards = dao.observeCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurring = dao.observeRecurring().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = dao.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CategoryEntity>())
    val goals = dao.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<SavingsGoalEntity>())
    val rates = dao.observeRates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ExchangeRateEntity>())

    init {
        viewModelScope.launch {
            checkAndExecuteRecurring(); seedDefaultCategories(); ensureCashAccount(); updateRates()
        }
    }

    private suspend fun seedDefaultCategories() {
        if (dao.getCategoriesList().isEmpty()) {
            DEFAULT_EXPENSE_CATEGORIES.forEach { dao.upsertCategory(CategoryEntity(it, TxType.EXPENSE)) }
            DEFAULT_INCOME_CATEGORIES.forEach { dao.upsertCategory(CategoryEntity(it, TxType.INCOME)) }
        }
    }

    private suspend fun ensureCashAccount() {
        if (cards.first().none { it.id == "CASH" }) {
            dao.upsertCard(CardEntity(id = "CASH", last4 = "CASH", name = "Наличные", currency = "UZS", initialBalanceMinor = 0))
        }
    }

    fun updateRates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://cbu.uz/ru/arkhiv-kursov-valyut/json/")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ccy = obj.getString("Ccy")
                    if (ccy in listOf("USD", "EUR", "RUB")) {
                        val rate = obj.getString("Rate").toDouble()
                        dao.upsertRate(ExchangeRateEntity(ccy, rate))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun checkAndExecuteRecurring() {
        val now = LocalDate.now(); val currentMonthStr = YearMonth.now().toString(); val currentDay = now.dayOfMonth
        val templates = dao.getRecurringList()
        for (t in templates) {
            val effectiveDay = minOf(t.dayOfMonth, now.lengthOfMonth())
            if (effectiveDay <= currentDay && t.lastExecutedMonth != currentMonthStr) {
                val tx = Tx(amountMinor = t.amountMinor, type = t.type, cardId = t.cardId, description = t.description, category = t.category, timestamp = now.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), source = "AUTO")
                dao.upsert(tx); dao.upsertRecurring(t.copy(lastExecutedMonth = currentMonthStr))
            }
        }
    }

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun bulkSave(txs: List<Tx>) { viewModelScope.launch { dao.insertAll(txs) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }
    fun deleteCard(id: String) { if (id != "CASH") viewModelScope.launch { dao.deleteCard(id) } }
    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
    fun saveRecurring(r: RecurringEntity) { viewModelScope.launch { dao.upsertRecurring(r) } }
    fun deleteRecurring(r: RecurringEntity) { viewModelScope.launch { dao.deleteRecurring(r) } }
    fun saveCategory(c: CategoryEntity) { viewModelScope.launch { dao.upsertCategory(c) } }
    fun deleteCategory(name: String) { viewModelScope.launch { dao.deleteCategory(name) } }
    fun saveGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.upsertGoal(goal) } }
    fun deleteGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.deleteGoal(goal) } }

    fun adjustBalance(cardId: String, diffMinor: Long) {
        if (diffMinor == 0L) return
        val tx = Tx(
            amountMinor = kotlin.math.abs(diffMinor),
            type = if (diffMinor > 0) TxType.INCOME else TxType.EXPENSE,
            cardId = cardId,
            description = "Корректировка баланса",
            category = "⚙️ Система",
            timestamp = System.currentTimeMillis(),
            source = "ADJUSTMENT"
        )
        viewModelScope.launch { dao.upsert(tx) }
    }

    fun depositToGoal(goal: SavingsGoalEntity, cardId: String, amountMinor: Long) {
        viewModelScope.launch {
            val tx = Tx(amountMinor = amountMinor, type = TxType.TRANSFER, cardId = cardId, toCardId = "GOAL_${goal.id}", description = goal.name, category = "🏦 Копилка", timestamp = System.currentTimeMillis(), source = "SAVINGS")
            dao.upsert(tx); dao.upsertGoal(goal.copy(currentAmountMinor = goal.currentAmountMinor + amountMinor))
        }
    }

    fun createBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val allTx = allTxs.first(); val allCards = cards.first(); val allBudgets = budgets.first(); val allRecurring = dao.getRecurringList(); val allCats = dao.getCategoriesList(); val allGoals = goals.first()
                val root = JSONObject()
                val txsArray = JSONArray(); allTx.forEach { t -> txsArray.put(JSONObject().apply { put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name); put("cardId", t.cardId ?: JSONObject.NULL); put("toCardId", t.toCardId ?: JSONObject.NULL); put("description", t.description); put("category", t.category); put("timestamp", t.timestamp); put("source", t.source); put("currency", t.currency); put("photoPath", t.photoPath ?: JSONObject.NULL) }) }; root.put("txs", txsArray)
                val cardsArray = JSONArray(); allCards.forEach { c -> cardsArray.put(JSONObject().apply { put("id", c.id); put("last4", c.last4); put("name", c.name); put("currency", c.currency); put("initialBalanceMinor", c.initialBalanceMinor) }) }; root.put("cards", cardsArray)
                val budgetsArray = JSONArray(); allBudgets.forEach { b -> budgetsArray.put(JSONObject().apply { put("category", b.category); put("limitMinor", b.limitMinor) }) }; root.put("budgets", budgetsArray)
                val recurringArray = JSONArray(); allRecurring.forEach { r -> recurringArray.put(JSONObject().apply { put("id", r.id); put("amountMinor", r.amountMinor); put("type", r.type.name); put("cardId", r.cardId ?: JSONObject.NULL); put("description", r.description); put("category", r.category); put("dayOfMonth", r.dayOfMonth); put("lastExecutedMonth", r.lastExecutedMonth) }) }; root.put("recurring", recurringArray)
                val catsArray = JSONArray(); allCats.forEach { c -> catsArray.put(JSONObject().apply { put("name", c.name); put("type", c.type.name) }) }; root.put("categories", catsArray)
                val goalsArray = JSONArray(); allGoals.forEach { g -> goalsArray.put(JSONObject().apply { put("id", g.id); put("name", g.name); put("targetAmountMinor", g.targetAmountMinor); put("currentAmountMinor", g.currentAmountMinor) }) }; root.put("goals", goalsArray)
                onResult(root.toString(2))
            } catch (e: Exception) { onResult("") }
        }
    }

    fun restoreBackup(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)

                val txsList = mutableListOf<Tx>()
                val txsArray = root.optJSONArray("txs")
                if (txsArray != null) { for (i in 0 until txsArray.length()) { val o = txsArray.getJSONObject(i); txsList.add(Tx(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardId = if (o.isNull("cardId")) null else o.getString("cardId"), toCardId = if (o.isNull("toCardId")) null else o.optString("toCardId"), description = o.optString("description"), category = o.optString("category"), timestamp = o.getLong("timestamp"), source = o.optString("source"), currency = o.optString("currency", "UZS"), photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"))) } }

                val cardsList = mutableListOf<CardEntity>()
                val cardsArray = root.optJSONArray("cards")
                if (cardsArray != null) { for (i in 0 until cardsArray.length()) { val o = cardsArray.getJSONObject(i); cardsList.add(CardEntity(id = o.getString("id"), last4 = o.getString("last4"), name = o.getString("name"), currency = o.getString("currency"), initialBalanceMinor = o.getLong("initialBalanceMinor"))) } }

                val budgetsList = mutableListOf<BudgetEntity>()
                val budgetsArray = root.optJSONArray("budgets")
                if (budgetsArray != null) { for (i in 0 until budgetsArray.length()) { val o = budgetsArray.getJSONObject(i); budgetsList.add(BudgetEntity(category = o.getString("category"), limitMinor = o.getLong("limitMinor"))) } }

                val recurringList = mutableListOf<RecurringEntity>()
                val recurringArray = root.optJSONArray("recurring")
                if (recurringArray != null) { for (i in 0 until recurringArray.length()) { val o = recurringArray.getJSONObject(i); recurringList.add(RecurringEntity(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardId = if (o.isNull("cardId")) null else o.getString("cardId"), description = o.optString("description"), category = o.optString("category"), dayOfMonth = o.getInt("dayOfMonth"), lastExecutedMonth = o.optString("lastExecutedMonth"))) } }

                val catsList = mutableListOf<CategoryEntity>()
                val catsArray = root.optJSONArray("categories")
                if (catsArray != null) { for (i in 0 until catsArray.length()) { val o = catsArray.getJSONObject(i); catsList.add(CategoryEntity(name = o.getString("name"), type = TxType.valueOf(o.getString("type")))) } }

                val goalsList = mutableListOf<SavingsGoalEntity>()
                val goalsArray = root.optJSONArray("goals")
                if (goalsArray != null) { for (i in 0 until goalsArray.length()) { val o = goalsArray.getJSONObject(i); goalsList.add(SavingsGoalEntity(id = o.optLong("id"), name = o.getString("name"), targetAmountMinor = o.getLong("targetAmountMinor"), currentAmountMinor = o.getLong("currentAmountMinor"))) } }

                db.withTransaction {
                    dao.clearTxs(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()
                    dao.clearCategories(); dao.clearGoals()

                    dao.insertAll(txsList)
                    cardsList.forEach { dao.upsertCard(it) }
                    budgetsList.forEach { dao.upsertBudget(it) }
                    recurringList.forEach { dao.upsertRecurring(it) }
                    catsList.forEach { dao.upsertCategory(it) }
                    goalsList.forEach { dao.upsertGoal(it) }
                }

                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}

class MainActivity : FragmentActivity() {
    private val pendingCheckTrigger = mutableStateOf(0)
    private val currentTheme = mutableStateOf(AppTheme.SYSTEM)
    private val currentLang = mutableStateOf(AppLanguage.RU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
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
    var pin by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    val prefs = activity.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    val savedPin = prefs.getString("pin", "0000") ?: "0000"; val useBiometric = prefs.getBoolean("use_biometric", true)
    val biometricManager = BiometricManager.from(activity)
    val canUseBiometric = useBiometric && biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    val t = { key: String -> Translations.t(key, lang) }

    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text(t("locked"), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = false }, label = { Text(t("pin")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = error, supportingText = { if (error) Text(t("wrong_pin"), color = RED) })
        Spacer(Modifier.height(16.dp)); Button(onClick = { if (pin == savedPin) onUnlocked() else { pin = ""; error = true } }, modifier = Modifier.fillMaxWidth()) { Text(t("login")) }
        if (canUseBiometric) {
            Spacer(Modifier.height(8.dp)); TextButton(onClick = {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); onUnlocked() }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(t("login")).setSubtitle(t("fingerprint")).setNegativeButtonText(t("pin")).build()
                biometricPrompt.authenticate(promptInfo)
            }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text(t("fingerprint")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel(), pendingCheckTrigger: MutableState<Int>? = null, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>) {
    val context = LocalContext.current
    val month by vm.month.collectAsState(); val txs by vm.txs.collectAsState(); val allTxs by vm.allTxs.collectAsState()
    val cards by vm.cards.collectAsState(); val budgets by vm.budgets.collectAsState(); val recurring by vm.recurring.collectAsState()
    val goals by vm.goals.collectAsState(); val rates by vm.rates.collectAsState()
    val categories by vm.categories.collectAsState()
    val expenseCategories = categories.filter { it.type == TxType.EXPENSE }.map { it.name }
    val incomeCategories = categories.filter { it.type == TxType.INCOME }.map { it.name }
    val t = { key: String -> Translations.t(key, currentLang.value) }

    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var isPendingSms by remember { mutableStateOf(false) }
    var showCardManager by remember { mutableStateOf(false) }; var showBudgetManager by remember { mutableStateOf(false) }
    var showRecurringManager by remember { mutableStateOf(false) }; var showCategoryManager by remember { mutableStateOf(false) }
    var showGoalManager by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }; var showRestoreConfirm by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf("") }
    var importedTxs by remember { mutableStateOf<List<Tx>?>(null) }; var showImportConfirm by remember { mutableStateOf(false) }
    var importedCards by remember { mutableStateOf<List<CardEntity>?>(null) }; var showCardImportConfirm by remember { mutableStateOf(false) }

    var ocrDebugInfo by remember { mutableStateOf<String?>(null) }

    var showAnalytics by remember { mutableStateOf(false) }
    var showBalanceDetails by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }; var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()

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
                id = 0,
                amountMinor = pendingObj.getLong("amountMinor"),
                type = TxType.valueOf(pendingObj.getString("type")),
                cardId = if (pendingObj.isNull("card")) null else pendingObj.getString("card"),
                description = pendingObj.optString("description"),
                category = pendingObj.optString("category"),
                timestamp = pendingObj.optLong("timestamp"),
                source = "SMS"
            )
            editing = pendingTx
            isPendingSms = true
        } catch (e: Exception) {
            PendingQueue.pop(context)
        }
    }

    val trigger = pendingCheckTrigger?.value ?: 0
    LaunchedEffect(trigger) { checkPendingSms() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkPendingSms() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? -> uri?.let { try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(generateCsv(allTxs).toByteArray()) }; scope.launch { snackbarHostState.showSnackbar("CSV OK!") } } catch (e: Exception) { } } }
    val jsonExportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> uri?.let { vm.createBackup { j -> if (j.isNotEmpty()) { try { context.contentResolver.openOutputStream(it)?.use { s -> s.write(j.toByteArray()) }; scope.launch { snackbarHostState.showSnackbar("JSON OK!") } } catch (e: Exception) { } } } } }
    val jsonImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { try { context.contentResolver.openInputStream(it)?.use { s -> pendingRestoreJson = s.bufferedReader().readText(); showRestoreConfirm = true } } catch (e: Exception) { } } }

    val fileImportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch {
            try {
                val mimeType = context.contentResolver.getType(it) ?: ""
                val text = if (mimeType == "application/pdf") extractTextFromPdf(context, it) else context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                val parsedTxs = SmsParser.parseImportedFile(text, allTxs)
                if (parsedTxs.isNotEmpty()) { importedTxs = parsedTxs; showImportConfirm = true }
                else { snackbarHostState.showSnackbar(t("no_tx_found")) }
            } catch (e: Exception) { snackbarHostState.showSnackbar(t("file_read_error")) }
        } }
    }

    val cardImagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch {
            val bitmap = uriToBitmap(context, it)
            if (bitmap == null) {
                ocrDebugInfo = "${t("image_error")}\n$it"
                return@launch
            }

            val imageInfo = "${t("debug_size")}: ${bitmap.width}x${bitmap.height}"
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image).addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val parsedCards = parseCardsFromScreenshot(rawText)

                if (parsedCards.isNotEmpty()) {
                    importedCards = parsedCards
                    showCardImportConfirm = true
                } else {
                    ocrDebugInfo = buildString {
                        appendLine("--- ${t("ocr_debug")} ---")
                        appendLine(imageInfo)
                        appendLine("\n${t("debug_not_found")}")
                        appendLine("\n--- ${t("debug_what_ocr")} ---")
                        if (rawText.isBlank()) {
                            appendLine(t("debug_empty"))
                        } else {
                            appendLine(rawText)
                        }
                    }
                }
            }.addOnFailureListener { e ->
                ocrDebugInfo = "${t("error")} ML KIT:\n${e.message}"
            }
        } }
    }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

    val filteredTxs by remember(txs, searchQuery, filterType, currentLang.value) {
        derivedStateOf {
            txs.filter { tx ->
                (searchQuery.isEmpty() || tx.description.contains(searchQuery, true) || Translations.translateCategory(tx.category, currentLang.value).contains(searchQuery, true)) &&
                (filterType == null || tx.type == filterType)
            }
        }
    }

    val byDay by remember(filteredTxs) {
        derivedStateOf {
            filteredTxs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
    }

    val cardBalancesInUzs = remember(cards, allTxs, rates) {
        cards.associate { card ->
            val cardTxs = allTxs.filter { it.cardId == card.id }
            val cardIncome = cardTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            val cardExpense = cardTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
            val cardTransferOut = cardTxs.filter { it.type == TxType.TRANSFER }.sumOf { it.amountMinor }
            val cardTransferIn = allTxs.filter { it.toCardId == card.id && it.type == TxType.TRANSFER }.sumOf { it.amountMinor }

            val balanceInCardCurrency = card.initialBalanceMinor + cardIncome + cardTransferIn - cardExpense - cardTransferOut
            val rate = if (card.currency == "UZS") 1.0 else rates.find { it.currency == card.currency }?.rateToUzs ?: 1.0
            val balanceInUzs = (balanceInCardCurrency.toDouble() * rate).toLong()

            card.id to balanceInUzs
        }
    }
    val totalLiquidBalanceUzs = cardBalancesInUzs.values.sum()

    val totalForWidget = remember(totalLiquidBalanceUzs, goals) {
        totalLiquidBalanceUzs + goals.sumOf { it.currentAmountMinor }
    }

    LaunchedEffect(totalForWidget) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("total_balance", totalForWidget).apply()
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val componentName = android.content.ComponentName(context, BalanceWidget::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { id -> BalanceWidget.updateWidget(context, appWidgetManager, id) }
    }

    if (showAnalytics) {
        BackHandler { showAnalytics = false }
        AnalyticsScreen(txs = txs, allTxs = allTxs, currentLang = currentLang.value, onBack = { showAnalytics = false })
    } else {
        if (showRestoreConfirm) {
            AlertDialog(onDismissRequest = { showRestoreConfirm = false }, title = { Text(t("restore")) }, text = { Text(t("restore") + "?") },
                confirmButton = { TextButton(onClick = { vm.restoreBackup(pendingRestoreJson) { success -> if (success) scope.launch { snackbarHostState.showSnackbar("OK!") } else scope.launch { snackbarHostState.showSnackbar(t("error")) } }; showRestoreConfirm = false }) { Text(t("confirm"), color = RED) } },
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
                        val existingCardIds = cards.map { it.id }.toSet()
                        txsToImport.mapNotNull { it.cardId }.distinct().forEach { cardId ->
                            if (!existingCardIds.contains(cardId)) {
                                val last4 = cardId.split("_").firstOrNull() ?: "0000"
                                val currency = cardId.split("_").getOrNull(1) ?: "UZS"
                                vm.saveCard(CardEntity(id = cardId, last4 = last4, name = "Импорт *$last4", currency = currency, initialBalanceMinor = 0))
                            }
                        }
                        vm.bulkSave(txsToImport)
                        scope.launch { snackbarHostState.showSnackbar("${t("import_confirm")} ${txsToImport.size}") }
                        showImportConfirm = false
                        importedTxs = null
                    }) { Text(t("confirm"), color = GREEN) }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirm = false; importedTxs = null }) { Text(t("cancel")) }
                }
            )
        }

        if (showCardImportConfirm && importedCards != null) {
            val cardsToImport = importedCards!!
            AlertDialog(onDismissRequest = { showCardImportConfirm = false; importedCards = null }, title = { Text(t("import_cards")) },
                text = { Text("${t("found_cards")} ${cardsToImport.size}") },
                confirmButton = { TextButton(onClick = { cardsToImport.forEach { card -> vm.saveCard(card) }; scope.launch { snackbarHostState.showSnackbar(t("cards_added")) }; showCardImportConfirm = false; importedCards = null }) { Text(t("confirm"), color = GREEN) } },
                dismissButton = { TextButton(onClick = { showCardImportConfirm = false; importedCards = null }) { Text(t("cancel")) } })
        }

        if (ocrDebugInfo != null) {
            AlertDialog(
                onDismissRequest = { ocrDebugInfo = null },
                title = { Text(t("ocr_debug")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                text = ocrDebugInfo!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { ocrDebugInfo = null }) {
                        Text(t("close"))
                    }
                }
            )
        }

        if (showSettings) SettingsDialog(context, onDismiss = { showSettings = false }, snackbarHostState, scope, currentTheme, currentLang, vm)
        if (showCategoryManager) CategoryManagerDialog(categories, currentLang.value, { vm.saveCategory(it) }, { vm.deleteCategory(it) }, { showCategoryManager = false })
        if (showGoalManager) GoalManagerDialog(goals, cards, currentLang.value, { vm.saveGoal(it) }, { vm.deleteGoal(it) }, { g, c, a -> vm.depositToGoal(g, c, a) }, { showGoalManager = false })

        if (showBalanceDetails) {
            AlertDialog(onDismissRequest = { showBalanceDetails = false }, title = { Text(t("total_balance")) },
                text = { Column { cards.forEach { c -> val bal = cardBalancesInUzs[c.id] ?: 0L; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${c.name} (${c.currency})", fontWeight = FontWeight.Medium); Text("${bal.money()} UZS", fontWeight = FontWeight.Bold, color = if (bal >= 0) GREEN else RED) } } } },
                confirmButton = { TextButton(onClick = { showBalanceDetails = false }) { Text(t("close")) } })
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Prev") }

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                val monthTitle = "${getMonthName(month.monthValue, currentLang.value)} ${month.year}"

                                Text(
                                    monthTitle,
                                    modifier = Modifier.clickable { showMonthPicker = true }.padding(8.dp),
                                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium
                                )
                                DropdownMenu(expanded = showMonthPicker, onDismissRequest = { showMonthPicker = false }) {
                                    (1..12).forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text("${getMonthName(m, currentLang.value)} ${month.year}") },
                                            onClick = { vm.month.value = YearMonth.of(month.year, m); showMonthPicker = false }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Default.ChevronRight, "Next") }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text(t("scan_sms")) }, leadingIcon = { Icon(Icons.Default.Message, null) }, onClick = {
                                    showMenu = false
                                    scanInboxSms(context, allTxs) { found ->
                                        if (found.isNotEmpty()) {
                                            importedTxs = found; showImportConfirm = true
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar(t("new_sms_not_found")) }
                                        }
                                    }
                                })
                                DropdownMenuItem(text = { Text(t("import_csv")) }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) })
                                DropdownMenuItem(text = { Text(t("import_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("application/pdf")) })
                                DropdownMenuItem(text = { Text(t("import_cards")) }, leadingIcon = { Icon(Icons.Default.AddCard, null) }, onClick = { showMenu = false; cardImagePicker.launch("image/*") })
                                DropdownMenuItem(text = { Text(t("manage_cards")) }, leadingIcon = { Icon(Icons.Default.CreditCard, null) }, onClick = { showMenu = false; showCardManager = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("export")) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; csvExportLauncher.launch("uchet_export.csv") })
                                DropdownMenuItem(text = { Text(t("backup")) }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { showMenu = false; jsonExportLauncher.launch("uchet_backup.json") })
                                DropdownMenuItem(text = { Text(t("restore")) }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { showMenu = false; jsonImportLauncher.launch(arrayOf("application/json")) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("settings")) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMenu = false; showSettings = true })
                            }
                        }
                    }
                )
            },
            floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, t("add_tx")) } }
        ) { pad ->
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(t("search")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAnalytics = true }.padding(8.dp)) { Icon(Icons.Default.BarChart, contentDescription = t("analytics"), tint = MaterialTheme.colorScheme.primary); Text(t("analytics"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showGoalManager = true }.padding(8.dp)) { Icon(Icons.Default.Savings, contentDescription = t("goals"), tint = MaterialTheme.colorScheme.primary); Text(t("goals"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showCategoryManager = true }.padding(8.dp)) { Icon(Icons.Default.Category, contentDescription = t("categories"), tint = MaterialTheme.colorScheme.primary); Text(t("categories"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showRecurringManager = true }.padding(8.dp)) { Icon(Icons.Default.Autorenew, contentDescription = t("recurring"), tint = MaterialTheme.colorScheme.primary); Text(t("recurring"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showBudgetManager = true }.padding(8.dp)) { Icon(Icons.Default.AccountBalanceWallet, contentDescription = t("budgets"), tint = MaterialTheme.colorScheme.primary); Text(t("budgets"), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1) }
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(modifier = Modifier.fillMaxWidth().clickable { showBalanceDetails = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
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
                        Card(Modifier.fillMaxWidth().clickable { showGoalManager = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Savings, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(8.dp)); Text(t("goals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.height(12.dp))
                                goals.forEach { goal ->
                                    val progress = if (goal.targetAmountMinor > 0) goal.currentAmountMinor.toFloat() / goal.targetAmountMinor.toFloat() else 0f
                                    val isDone = progress >= 1f
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(goal.name, fontWeight = FontWeight.Medium); Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (isDone) GREEN else Color.Unspecified) }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = if (isDone) GREEN else BLUE, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                if (budgets.isNotEmpty()) item {
                    Card(Modifier.fillMaxWidth().clickable { showBudgetManager = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t("budgets_month"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
                            budgets.forEach { budget ->
                                val spent = txs.filter { it.type == TxType.EXPENSE && it.category == budget.category }.sumOf { it.amountMinor }
                                val progress = if (budget.limitMinor > 0) spent.toFloat() / budget.limitMinor.toFloat() else 0f
                                val progressColor = when { progress >= 1f -> RED; progress >= 0.8f -> YELLOW; else -> GREEN }
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Translations.translateCategory(budget.category, currentLang.value), fontWeight = FontWeight.Medium); Text("${spent.money()} / ${budget.limitMinor.money()}", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) RED else Color.Unspecified) }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = progressColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            SumRow(t("income"), income, GREEN); SumRow(t("expense"), expense, RED); HorizontalDivider(Modifier.padding(vertical = 8.dp)); SumRow(t("total"), income - expense, MaterialTheme.colorScheme.onSurface, bold = true)
                        }
                    }
                }
                if (filteredTxs.isEmpty()) item { Text(t("no_found"), Modifier.padding(top = 32.dp), color = Color.Gray) }
                byDay.forEach { (day, list) ->
                    item { Text(day.format(DATE_FMT), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray) }
                    items(list, key = { it.id }) { tx -> TxRow(tx, currentLang.value) { editing = tx } }
                }
            }
        }

        if (adding || editing != null) {
            TxDialog(
                initial = editing,
                cards = cards,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                currentLang = currentLang.value,
                isPendingSms = isPendingSms,
                rates = rates,
                vm = vm,
                onDismiss = {
                    if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                    adding = false; editing = null
                },
                onSave = {
                    vm.save(it)
                    if (isPendingSms) { PendingQueue.pop(context); isPendingSms = false }
                    adding = false; editing = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(txs: List<Tx>, allTxs: List<Tx>, currentLang: AppLanguage, onBack: () -> Unit) {
    val t = { key: String -> Translations.t(key, currentLang) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("analytics")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (txs.any { it.type == TxType.EXPENSE }) {
                Text(t("daily_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                DailyExpenseChart(txs = txs, yearMonth = YearMonth.now())
                Spacer(Modifier.height(32.dp))
            }
            if (allTxs.any { it.type == TxType.EXPENSE }) {
                Text(t("monthly_chart"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                MonthlyExpenseChart(allTxs = allTxs)
                Spacer(Modifier.height(32.dp))
            }
            val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
            if (expensesByCategory.isNotEmpty()) {
                Text(t("exp_by_cat"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                CategoryPieChart(expensesByCategory, currentLang)
            }
        }
    }
}

@Composable
fun DailyExpenseChart(txs: List<Tx>, yearMonth: YearMonth) {
    val daysInMonth = yearMonth.lengthOfMonth(); val zone = ZoneId.systemDefault()
    val dailyExpenses = (1..daysInMonth).associateWith { day -> txs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.dayOfMonth == day && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (dailyExpenses.values.maxOrNull() ?: 1L).toFloat()

    val barColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.secondary

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(Unit) {
            detectTapGestures { offset ->
                val step = size.width / daysInMonth
                val clickedDay = (offset.x / step).toInt() + 1
                selectedDay = if (selectedDay == clickedDay) null else clickedDay
            }
        }) {
            val width = size.width; val height = size.height; val barWidth = (width / daysInMonth) * 0.6f; val step = width / daysInMonth
            dailyExpenses.forEach { (day, amount) ->
                if (amount > 0) {
                    val isSelected = selectedDay == day
                    val currentBarColor = if (isSelected) selectedBarColor else barColor
                    val barHeight = (amount / maxExpense) * height
                    val x = (day - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = currentBarColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedDay != null) {
            Text("${Translations.t("day", AppLanguage.RU)}: $selectedDay. ${Translations.t("total", AppLanguage.RU)}: ${dailyExpenses[selectedDay]?.money()} UZS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = selectedBarColor)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MonthlyExpenseChart(allTxs: List<Tx>) {
    val currentYear = Year.now(); val zone = ZoneId.systemDefault()
    val monthlyExpenses = (1..12).associateWith { m -> allTxs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.year == currentYear.value && date.monthValue == m && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (monthlyExpenses.values.maxOrNull() ?: 1L).toFloat()

    val barColor = MaterialTheme.colorScheme.secondary
    val selectedBarColor = MaterialTheme.colorScheme.primary

    var selectedMonth by remember { mutableStateOf<Int?>(null) }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).pointerInput(Unit) {
            detectTapGestures { offset ->
                val step = size.width / 12
                val clickedMonth = (offset.x / step).toInt() + 1
                selectedMonth = if (selectedMonth == clickedMonth) null else clickedMonth
            }
        }) {
            val width = size.width; val height = size.height; val step = width / 12; val barWidth = step * 0.6f
            monthlyExpenses.forEach { (month, amount) ->
                if (amount > 0) {
                    val isSelected = selectedMonth == month
                    val currentBarColor = if (isSelected) selectedBarColor else barColor
                    val barHeight = (amount / maxExpense) * height
                    val x = (month - 1) * step + (step - barWidth) / 2
                    drawRoundRect(color = currentBarColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (selectedMonth != null) {
            Text("${Translations.t("date", AppLanguage.RU)}: $selectedMonth. ${Translations.t("total", AppLanguage.RU)}: ${monthlyExpenses[selectedMonth]?.money()} UZS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = selectedBarColor)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("4", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("7", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("10", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("12", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun CategoryPieChart(data: Map<String, Long>, lang: AppLanguage) {
    val total = data.values.sum().toFloat(); if (total == 0f) return; val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)); val sortedData = data.entries.sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) { Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) { var startAngle = -90f; sortedData.forEachIndexed { index, entry -> val sweepAngle = (entry.value / total) * 360f; drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true); startAngle += sweepAngle } }
        Spacer(Modifier.width(16.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { sortedData.take(4).forEachIndexed { index, entry -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape)); Spacer(Modifier.width(6.dp)); Text("${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) } } } }
}
