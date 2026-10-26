package uz.uchet

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)
private val YELLOW = Color(0xFFF9A825)
private val BLUE = Color(0xFF1976D2)

fun getMonthFormatter(lang: AppLanguage): DateTimeFormatter {
    val locale = when(lang) {
        AppLanguage.RU -> Locale("ru"); AppLanguage.UZ -> Locale("uz"); AppLanguage.EN -> Locale("en")
        AppLanguage.TG -> Locale("tg"); AppLanguage.KAA -> Locale("kaa"); AppLanguage.KK -> Locale("kk")
    }
    return DateTimeFormatter.ofPattern("LLLL yyyy", locale)
}

val DEFAULT_EXPENSE_CATEGORIES = listOf("🍔 Еда", "🚕 Транспорт", "🏠 Жилье", "🛒 Покупки", "💊 Здоровье", "🎉 Развлечения", "📦 Другое")
val DEFAULT_INCOME_CATEGORIES = listOf("💵 Зарплата", "🎁 Подарок", "💸 Перевод", "📈 Инвестиции", "📦 Другое")

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage { RU, UZ, EN, TG, KAA, KK }

object Translations {
    private val dict = mapOf(
        "ru" to mapOf(
            "my_accounts" to "Карты", "exp_by_cat" to "Расходы по категориям", "daily_chart" to "Динамика расходов (по дням)", "monthly_chart" to "Расходы по месяцам (за год)",
            "budgets_month" to "Бюджеты на месяц", "income" to "Доходы", "expense" to "Расходы", "total" to "Итого", "search" to "Поиск...", "all" to "Все", "add" to "Добавить",
            "new_tx" to "Новая операция", "edit" to "Изменить", "amount" to "Сумма", "category" to "Категория", "account" to "Счет оплаты", "other" to "Другая...",
            "desc" to "Описание", "date" to "Дата (дд.мм.гггг)", "save" to "Сохранить", "cancel" to "Отмена", "delete" to "Удалить", "settings" to "Настройки",
            "theme" to "Тема оформления", "language" to "Язык / Til / Language", "categories" to "Категории", "recurring" to "Регулярные", "budgets" to "Бюджеты",
            "export" to "Экспорт в Excel (CSV)", "backup" to "Сохранить копию (JSON)", "restore" to "Загрузить копию (JSON)", "biometric" to "Вход по отпечатку",
            "change_pin" to "Сменить PIN", "locked" to "Приложение заблокировано", "login" to "Войти", "fingerprint" to "Использовать отпечаток",
            "sms_title" to "Проверьте операцию", "sms_data" to "Данные из СМС/Уведомления", "confirm" to "Подтвердить", "reject" to "Отклонить", "pin" to "PIN-код",
            "wrong_pin" to "Неверный PIN-код", "no_found" to "Ничего не найдено", "limit" to "Лимит", "day" to "День месяца (1-31)", "set_limit" to "Сохранить лимит",
            "add_cat" to "Добавить категорию", "add_temp" to "Добавить шаблон", "sys" to "Системная", "light" to "Светлая", "dark" to "Темная",
            "cash" to "Наличные", "card" to "Карта", "upload_sms" to "СМС / Чек",
            "goals" to "Копилка", "add_goal" to "Добавить цель", "goal_name" to "Название цели", "target_amount" to "Целевая сумма",
            "deposit" to "Пополнить", "deposit_amount" to "Сумма пополнения", "source_card" to "Списать с карты", "completed" to "Достигнуто!",
            "import_csv" to "Импорт из CSV", "import_pdf" to "Импорт из PDF", "import_confirm" to "Найдено транзакций: ",
            "import_cards" to "Импорт карт (Скрин)", "analytics" to "Аналитика", "total_balance" to "Общий баланс",
            "take_photo" to "Сделать фото", "choose_photo" to "Из галереи", "scan_qr" to "Сканировать QR",
            "currency" to "Валюта", "sms_debug" to "Принимать все СМС (Тест)", "update_rates" to "Обновить курсы валют"
        ),
        "uz" to mapOf(
            "my_accounts" to "Kartalar", "exp_by_cat" to "Kategoriyalar bo'yicha xarajatlar", "daily_chart" to "Xarajatlar dinamikasi (kunlik)", "monthly_chart" to "Xarajatlar (oylik)",
            "budgets_month" to "Oylik byudjetlar", "income" to "Daromadlar", "expense" to "Xarajatlar", "total" to "Jami", "search" to "Qidirish...", "all" to "Barchasi", "add" to "Qo'shish",
            "new_tx" to "Yangi operatsiya", "edit" to "O'zgartirish", "amount" to "Summa", "category" to "Kategoriya", "account" to "To'lov hisobi", "other" to "Boshqa...",
            "desc" to "Tavsif", "date" to "Sana (kk.oo.yyyy)", "save" to "Saqlash", "cancel" to "Bekor qilish", "delete" to "O'chirish", "settings" to "Sozlamalar",
            "theme" to "Mavzu", "language" to "Язык / Til / Language", "categories" to "Kategoriyalar", "recurring" to "Muntazam", "budgets" to "Byudjetlar",
            "export" to "Excel (CSV) ga eksport", "backup" to "Zaxira nusxasini saqlash (JSON)", "restore" to "Zaxirani yuklash (JSON)", "biometric" to "Barmoq izi bilan kirish",
            "change_pin" to "PIN kodni o'zgartirish", "locked" to "Ilova bloklangan", "login" to "Kirish", "fingerprint" to "Barmoq izidan foydalanish",
            "sms_title" to "Operatsiyani tekshiring", "sms_data" to "SMS/Bildirishnoma ma'lumotlari", "confirm" to "Tasdiqlash", "reject" to "Rad etish", "pin" to "PIN kod",
            "wrong_pin" to "Noto'g'ri PIN kod", "no_found" to "Hech narsa topilmadi", "limit" to "Limit", "day" to "Oy kuni (1-31)", "set_limit" to "Limitni saqlash",
            "add_cat" to "Kategoriya qo'shish", "add_temp" to "Shablon qo'shish", "sys" to "Tizim", "light" to "Yorug'", "dark" to "Qorong'i",
            "cash" to "Naqd", "card" to "Karta", "upload_sms" to "SMS / Chek",
            "goals" to "Jamg'arma", "add_goal" to "Maqsad qo'shish", "goal_name" to "Maqsad nomi", "target_amount" to "Maqsad summasi",
            "deposit" to "To'ldirish", "deposit_amount" to "To'ldirish summasi", "source_card" to "Kartadan yechish", "completed" to "Erishildi!",
            "import_csv" to "CSV dan import", "import_pdf" to "PDF dan import", "import_confirm" to "Topilgan operatsiyalar: ",
            "import_cards" to "Kartalarni import qilish", "analytics" to "Analitika", "total_balance" to "Umumiy balans",
            "take_photo" to "Rasmga olish", "choose_photo" to "Galereyadan", "scan_qr" to "QR skanerlash",
            "currency" to "Valyuta", "sms_debug" to "Barcha SMSlarni qabul qilish", "update_rates" to "Valyuta kurslarini yangilash"
        ),
        "en" to mapOf(
            "my_accounts" to "Cards", "exp_by_cat" to "Expenses by Category", "daily_chart" to "Daily Expense Dynamics", "monthly_chart" to "Monthly Expenses",
            "budgets_month" to "Monthly Budgets", "income" to "Income", "expense" to "Expenses", "total" to "Total", "search" to "Search...", "all" to "All", "add" to "Add",
            "new_tx" to "New Transaction", "edit" to "Edit", "amount" to "Amount", "category" to "Category", "account" to "Account", "other" to "Other...",
            "desc" to "Description", "date" to "Date (dd.mm.yyyy)", "save" to "Save", "cancel" to "Cancel", "delete" to "Delete", "settings" to "Settings",
            "theme" to "Theme", "language" to "Language", "categories" to "Categories", "recurring" to "Recurring", "budgets" to "Budgets",
            "export" to "Export (CSV)", "backup" to "Backup (JSON)", "restore" to "Restore (JSON)", "biometric" to "Biometric Login",
            "change_pin" to "Change PIN", "locked" to "App is Locked", "login" to "Login", "fingerprint" to "Use Fingerprint",
            "sms_title" to "Check Transaction", "sms_data" to "SMS/Notification Data", "confirm" to "Confirm", "reject" to "Reject", "pin" to "PIN",
            "wrong_pin" to "Wrong PIN", "no_found" to "Nothing found", "limit" to "Limit", "day" to "Day (1-31)", "set_limit" to "Set Limit",
            "add_cat" to "Add Category", "add_temp" to "Add Template", "sys" to "System", "light" to "Light", "dark" to "Dark",
            "cash" to "Cash", "card" to "Card", "upload_sms" to "SMS / Receipt",
            "goals" to "Savings", "add_goal" to "Add Goal", "goal_name" to "Goal Name", "target_amount" to "Target",
            "deposit" to "Deposit", "deposit_amount" to "Amount", "source_card" to "Source", "completed" to "Completed!",
            "import_csv" to "Import CSV", "import_pdf" to "Import PDF", "import_confirm" to "Found: ",
            "import_cards" to "Import Cards", "analytics" to "Analytics", "total_balance" to "Total Balance",
            "take_photo" to "Take Photo", "choose_photo" to "Gallery", "scan_qr" to "Scan QR",
            "currency" to "Currency", "sms_debug" to "Accept all SMS (Test)", "update_rates" to "Update Rates"
        )
    )

    fun t(key: String, lang: AppLanguage): String {
        val langKey = when(lang) { AppLanguage.RU -> "ru"; AppLanguage.UZ -> "uz"; AppLanguage.EN -> "en"; AppLanguage.TG -> "ru"; AppLanguage.KAA -> "ru"; AppLanguage.KK -> "ru" }
        return dict[langKey]?.get(key) ?: dict["en"]?.get(key) ?: key
    }

    fun translateCategory(cat: String, lang: AppLanguage): String {
        if (lang == AppLanguage.RU) return cat
        val isUz = lang == AppLanguage.UZ; val isEn = lang == AppLanguage.EN
        return when(cat) {
            "🍔 Еда" -> if(isUz) "🍔 Ovqat" else if(isEn) "🍔 Food" else cat
            "🚕 Транспорт" -> if(isUz) "🚕 Transport" else if(isEn) "🚕 Transport" else cat
            "🏠 Жилье" -> if(isUz) "🏠 Uy-joy" else if(isEn) "🏠 Housing" else cat
            "🛒 Покупки" -> if(isUz) "🛒 Xaridlar" else if(isEn) "🛒 Shopping" else cat
            "💊 Здоровье" -> if(isUz) "💊 Salomatlik" else if(isEn) "💊 Health" else cat
            "🎉 Развлечения" -> if(isUz) "🎉 Ko'ngilochar" else if(isEn) "🎉 Entertainment" else cat
            "📦 Другое" -> if(isUz) "📦 Boshqa" else if(isEn) "📦 Other" else cat
            "💵 Зарплата" -> if(isUz) "💵 Maosh" else if(isEn) "💵 Salary" else cat
            "🎁 Подарок" -> if(isUz) "🎁 Sovg'a" else if(isEn) "🎁 Gift" else cat
            "💸 Перевод" -> if(isUz) "💸 O'tkazma" else if(isEn) "💸 Transfer" else cat
            "📈 Инвестиции" -> if(isUz) "📈 Investitsiya" else if(isEn) "📈 Investment" else cat
            else -> cat
        }
    }
}

fun Long.money(): String {
    val abs = kotlin.math.abs(this)
    val whole = String.format(Locale.US, "%,d", abs / 100).replace(',', ' ')
    val frac = abs % 100
    val sign = if (this < 0) "-" else ""
    return "$sign$whole.%02d".format(frac)
}

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
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
            if (t.dayOfMonth <= currentDay && t.lastExecutedMonth != currentMonthStr) {
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

    fun depositToGoal(goal: SavingsGoalEntity, cardId: String, amountMinor: Long) {
        viewModelScope.launch {
            val tx = Tx(amountMinor = amountMinor, type = TxType.TRANSFER, cardId = cardId, description = goal.name, category = "🏦 Копилка", timestamp = System.currentTimeMillis(), source = "SAVINGS")
            dao.upsert(tx); dao.upsertGoal(goal.copy(currentAmountMinor = goal.currentAmountMinor + amountMinor))
        }
    }

    fun createBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val allTx = allTxs.first(); val allCards = cards.first(); val allBudgets = budgets.first(); val allRecurring = dao.getRecurringList(); val allCats = dao.getCategoriesList(); val allGoals = goals.first()
                val root = JSONObject()
                val txsArray = JSONArray(); allTx.forEach { t -> txsArray.put(JSONObject().apply { put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name); put("cardId", t.cardId ?: JSONObject.NULL); put("description", t.description); put("category", t.category); put("timestamp", t.timestamp); put("source", t.source); put("currency", t.currency); put("photoPath", t.photoPath ?: JSONObject.NULL) }) }; root.put("txs", txsArray)
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
                dao.clearTxs(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()
                val txsArray = root.optJSONArray("txs"); if (txsArray != null) { for (i in 0 until txsArray.length()) { val o = txsArray.getJSONObject(i); dao.upsert(Tx(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardId = if (o.isNull("cardId")) null else o.getString("cardId"), description = o.optString("description"), category = o.optString("category"), timestamp = o.getLong("timestamp"), source = o.optString("source"), currency = o.optString("currency", "UZS"), photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"))) } }
                val cardsArray = root.optJSONArray("cards"); if (cardsArray != null) { for (i in 0 until cardsArray.length()) { val o = cardsArray.getJSONObject(i); dao.upsertCard(CardEntity(id = o.getString("id"), last4 = o.getString("last4"), name = o.getString("name"), currency = o.getString("currency"), initialBalanceMinor = o.getLong("initialBalanceMinor"))) } }
                val budgetsArray = root.optJSONArray("budgets"); if (budgetsArray != null) { for (i in 0 until budgetsArray.length()) { val o = budgetsArray.getJSONObject(i); dao.upsertBudget(BudgetEntity(category = o.getString("category"), limitMinor = o.getLong("limitMinor"))) } }
                val recurringArray = root.optJSONArray("recurring"); if (recurringArray != null) { for (i in 0 until recurringArray.length()) { val o = recurringArray.getJSONObject(i); dao.upsertRecurring(RecurringEntity(id = o.optLong("id"), amountMinor = o.getLong("amountMinor"), type = TxType.valueOf(o.getString("type")), cardId = if (o.isNull("cardId")) null else o.getString("cardId"), description = o.optString("description"), category = o.optString("category"), dayOfMonth = o.getInt("dayOfMonth"), lastExecutedMonth = o.optString("lastExecutedMonth"))) } }
                val catsArray = root.optJSONArray("categories"); if (catsArray != null) { for (i in 0 until catsArray.length()) { val o = catsArray.getJSONObject(i); dao.upsertCategory(CategoryEntity(name = o.getString("name"), type = TxType.valueOf(o.getString("type")))) } }
                val goalsArray = root.optJSONArray("goals"); if (goalsArray != null) { for (i in 0 until goalsArray.length()) { val o = goalsArray.getJSONObject(i); dao.upsertGoal(SavingsGoalEntity(id = o.optLong("id"), name = o.getString("name"), targetAmountMinor = o.getLong("targetAmountMinor"), currentAmountMinor = o.getLong("currentAmountMinor"))) } }
                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
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
    val prefs = activity.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
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
    var pendingRestoreJson by remember { mutableStateOf("") }
    var importedTxs by remember { mutableStateOf<List<Tx>?>(null) }; var showImportConfirm by remember { mutableStateOf(false) }
    var importedCards by remember { mutableStateOf<List<CardEntity>?>(null) }; var showCardImportConfirm by remember { mutableStateOf(false) }
    
    var showAnalytics by remember { mutableStateOf(false) }
    var showBalanceDetails by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }; var filterType by remember { mutableStateOf<TxType?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.CAMERA)
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    fun checkPendingSms() {
        if (editing != null || adding) return
        val prefs = context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE)
        val pendingJson = prefs.getString("pending", null) ?: return
        try {
            val obj = JSONObject(pendingJson)
            val pendingTx = Tx(id = 0, amountMinor = obj.getLong("amountMinor"), type = TxType.valueOf(obj.getString("type")), cardId = if (obj.isNull("card")) null else obj.getString("card"), description = obj.optString("description"), category = obj.optString("category"), timestamp = obj.optLong("timestamp"), source = "SMS")
            editing = pendingTx; isPendingSms = true
        } catch (e: Exception) { prefs.edit().remove("pending").apply() }
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
                val parsedTxs = parseImportedFile(text)
                if (parsedTxs.isNotEmpty()) { importedTxs = parsedTxs; showImportConfirm = true } 
                else { snackbarHostState.showSnackbar("Не найдено транзакций") }
            } catch (e: Exception) { snackbarHostState.showSnackbar("Ошибка чтения файла") }
        } }
    }

    val cardImagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch {
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { visionText ->
                    val parsedCards = parseCardsFromScreenshot(visionText.text)
                    if (parsedCards.isNotEmpty()) { importedCards = parsedCards; showCardImportConfirm = true } 
                    else { scope.launch { snackbarHostState.showSnackbar("Не удалось распознать карты") } }
                }
            }
        } }
    }

    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    val filteredTxs = txs.filter { tx -> (searchQuery.isEmpty() || tx.description.contains(searchQuery, true) || Translations.translateCategory(tx.category, currentLang.value).contains(searchQuery, true)) && (filterType == null || tx.type == filterType) }
    val byDay = filteredTxs.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }

    // Подсчет общего баланса в UZS (с учетом валютных карт)
    val cardBalancesInUzs = cards.associate { card ->
        val cardTxs = allTxs.filter { it.cardId == card.id }
        val cardIncome = cardTxs.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
        val cardExpense = cardTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
        val cardTransfer = cardTxs.filter { it.type == TxType.TRANSFER }.sumOf { it.amountMinor }
        val balanceInCardCurrency = card.initialBalanceMinor + cardIncome - cardExpense - cardTransfer
        
        // Конвертируем в UZS для общего баланса
        val rate = if (card.currency == "UZS") 1.0 else rates.find { it.currency == card.currency }?.rateToUzs ?: 1.0
        val balanceInUzs = (balanceInCardCurrency.toDouble() * rate).toLong()
        
        card.id to balanceInUzs
    }
    val totalLiquidBalanceUzs = cardBalancesInUzs.values.sum()

    LaunchedEffect(cardBalancesInUzs, goals) {
        val total = totalLiquidBalanceUzs + goals.sumOf { it.currentAmountMinor }
        val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("total_balance", total).apply()
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val componentName = android.content.ComponentName(context, BalanceWidget::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { id -> BalanceWidget.updateWidget(context, appWidgetManager, id) }
    }

    if (showRestoreConfirm) {
        AlertDialog(onDismissRequest = { showRestoreConfirm = false }, title = { Text(t("restore")) }, text = { Text(t("restore") + "?") },
            confirmButton = { TextButton(onClick = { vm.restoreBackup(pendingRestoreJson) { success -> if (success) scope.launch { snackbarHostState.showSnackbar("OK!") } else scope.launch { snackbarHostState.showSnackbar("Error") } }; showRestoreConfirm = false }) { Text(t("confirm"), color = RED) } },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(t("cancel")) } })
    }

    if (showImportConfirm && importedTxs != null) {
        val txsToImport = importedTxs!!
        AlertDialog(onDismissRequest = { showImportConfirm = false; importedTxs = null }, title = { Text("Импорт данных") }, 
            text = { Text("${t("import_confirm")} ${txsToImport.size}") },
            confirmButton = { TextButton(onClick = { vm.bulkSave(txsToImport); scope.launch { snackbarHostState.showSnackbar("Импортировано: ${txsToImport.size}") }; showImportConfirm = false; importedTxs = null }) { Text(t("confirm"), color = GREEN) } },
            dismissButton = { TextButton(onClick = { showImportConfirm = false; importedTxs = null }) { Text(t("cancel")) } })
    }

    if (showCardImportConfirm && importedCards != null) {
        val cardsToImport = importedCards!!
        AlertDialog(onDismissRequest = { showCardImportConfirm = false; importedCards = null }, title = { Text("Импорт карт") }, 
            text = { Text("Найдено карт: ${cardsToImport.size}") },
            confirmButton = { TextButton(onClick = { cardsToImport.forEach { card -> vm.saveCard(card) }; scope.launch { snackbarHostState.showSnackbar("Карты добавлены!") }; showCardImportConfirm = false; importedCards = null }) { Text(t("confirm"), color = GREEN) } },
            dismissButton = { TextButton(onClick = { showCardImportConfirm = false; importedCards = null }) { Text(t("cancel")) } })
    }

    if (showSettings) SettingsDialog(context, onDismiss = { showSettings = false }, snackbarHostState, scope, currentTheme, currentLang, vm)
    if (showCategoryManager) CategoryManagerDialog(categories, currentLang.value, { vm.saveCategory(it) }, { vm.deleteCategory(it) }, { showCategoryManager = false })
    if (showGoalManager) GoalManagerDialog(goals, cards, currentLang.value, { vm.saveGoal(it) }, { vm.deleteGoal(it) }, { g, c, a -> vm.depositToGoal(g, c, a) }, { showGoalManager = false })
    if (showAnalytics) AnalyticsDialog(txs = txs, allTxs = allTxs, currentLang = currentLang.value, onDismiss = { showAnalytics = false })

    if (showBalanceDetails) {
        AlertDialog(onDismissRequest = { showBalanceDetails = false }, title = { Text(t("total_balance")) },
            text = { Column { cards.forEach { c -> val bal = cardBalancesInUzs[c.id] ?: 0L; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${c.name} (${c.currency})", fontWeight = FontWeight.Medium); Text("${bal.money()} UZS", fontWeight = FontWeight.Bold, color = if (bal >= 0) GREEN else RED) } } } },
            confirmButton = { TextButton(onClick = { showBalanceDetails = false }) { Text(t("cancel")) } })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Prev") }
                        Text(month.format(getMonthFormatter(currentLang.value)).replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.shift(1) }) { Icon(Icons.Default.ChevronRight, "Next") }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(t("import_csv")) }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) })
                            DropdownMenuItem(text = { Text(t("import_pdf")) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { showMenu = false; fileImportLauncher.launch(arrayOf("application/pdf")) })
                            DropdownMenuItem(text = { Text(t("import_cards")) }, leadingIcon = { Icon(Icons.Default.AddCard, null) }, onClick = { showMenu = false; cardImagePicker.launch("image/*") })
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
        floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, t("add")) } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp)) {
            item {
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(t("search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
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
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
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
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
        TxDialog(initial = editing, cards = cards, expenseCategories = expenseCategories, incomeCategories = incomeCategories, currentLang = currentLang.value, isPendingSms = isPendingSms, rates = rates,
            onDismiss = { if (isPendingSms) { context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE).edit().remove("pending").apply(); isPendingSms = false }; adding = false; editing = null },
            onSave = { vm.save(it); if (isPendingSms) { context.getSharedPreferences("pending_sms", android.content.Context.MODE_PRIVATE).edit().remove("pending").apply(); isPendingSms = false }; adding = false; editing = null },
            onDelete = if (isPendingSms) null else editing?.let { e -> { vm.delete(e); editing = null; scope.launch { val timerJob = launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }; val result = snackbarHostState.showSnackbar(message = "Удалено", actionLabel = "Отменить", duration = SnackbarDuration.Indefinite); timerJob.cancel(); if (result == SnackbarResult.ActionPerformed) vm.save(e) } } }
        )
    }

    if (showCardManager) CardManagerDialog(cards, cardBalancesInUzs, allTxs, { vm.saveCard(it) }, { vm.deleteCard(it) }, currentLang.value, rates, { showCardManager = false })
    if (showBudgetManager) BudgetManagerDialog(budgets, expenseCategories, { vm.saveBudget(it) }, { vm.deleteBudget(it) }, currentLang.value, { showBudgetManager = false })
    if (showRecurringManager) RecurringManagerDialog(recurring, cards, expenseCategories, incomeCategories, currentLang.value, { vm.saveRecurring(it) }, { vm.deleteRecurring(it) }, { showRecurringManager = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDialog(txs: List<Tx>, allTxs: List<Tx>, currentLang: AppLanguage, onDismiss: () -> Unit) {
    val t = { key: String -> Translations.t(key, currentLang) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("analytics")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (txs.any { it.type == TxType.EXPENSE }) {
                    Text(t("daily_chart"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    DailyExpenseChart(txs = txs, yearMonth = YearMonth.now())
                    Spacer(Modifier.height(24.dp))
                }
                if (allTxs.any { it.type == TxType.EXPENSE }) {
                    Text(t("monthly_chart"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    MonthlyExpenseChart(allTxs = allTxs)
                    Spacer(Modifier.height(24.dp))
                }
                val expensesByCategory = txs.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
                if (expensesByCategory.isNotEmpty()) {
                    Text(t("exp_by_cat"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    CategoryPieChart(expensesByCategory, currentLang)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(initial: Tx?, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, isPendingSms: Boolean = false, rates: List<ExchangeRateEntity>, onDismiss: () -> Unit, onSave: (Tx) -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)
    
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf(initial?.category ?: expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: "UZS") }
    var selectedCardId by remember { mutableStateOf(initial?.cardId ?: "CASH") }
    var isCustomCard by remember { mutableStateOf(initial?.cardId?.let { !cards.map { c -> c.id }.contains(it) } ?: false) }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var photoPath by remember { mutableStateOf(initial?.photoPath) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val t = { key: String -> Translations.t(key, currentLang) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val currentUri = tempCameraUri
            if (currentUri != null) {
                isLoading = true
                val bitmap = uriToBitmap(context, currentUri)
                bitmap?.let { bmp ->
                    val image = InputImage.fromBitmap(bmp, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(image).addOnSuccessListener { visionText ->
                        val parsed = parseSmsText(visionText.text)
                        if (parsed != null) { amount = parsed.amountStr; desc = parsed.description }
                        val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                        file.parentFile?.mkdirs()
                        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        photoPath = file.absolutePath
                        isLoading = false
                    }.addOnFailureListener { isLoading = false }
                }
            }
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isLoading = true
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image).addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val rawValue = barcodes[0].rawValue ?: ""
                        desc = "QR: ${rawValue.take(30)}"
                        val amountMatch = Regex("""total=(\d+)""").find(rawValue)
                        if (amountMatch != null) amount = amountMatch.groupValues[1]
                    }
                    isLoading = false
                }.addOnFailureListener { isLoading = false }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isLoading = true
            val bitmap = uriToBitmap(context, it)
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { visionText ->
                    val parsed = parseSmsText(visionText.text)
                    if (parsed != null) { amount = parsed.amountStr; desc = parsed.description }
                    val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    photoPath = file.absolutePath
                    isLoading = false
                }.addOnFailureListener { isLoading = false }
            }
        }
    }

    LaunchedEffect(type) {
        val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { isPendingSms -> t("sms_title"); initial == null -> t("new_tx"); else -> t("edit") }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPendingSms) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(12.dp)) { Text(t("sms_data"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(t("sms_title"), style = MaterialTheme.typography.bodySmall) } } }
                
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.values().forEachIndexed { i, tt -> SegmentedButton(type == tt, { type = tt }, SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(if(tt == TxType.INCOME) t("income") else if(tt == TxType.EXPENSE) t("expense") else "→") } }
                }

                if (!isPendingSms) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                            file.parentFile?.mkdirs()
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text(t("take_photo")) }
                        
                        Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text(t("choose_photo")) }

                        Button(onClick = { qrLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(4.dp)); Text(t("scan_qr")) }
                    }
                }

                if (photoPath != null) {
                    AsyncImage(model = File(photoPath!!), contentDescription = "Receipt", modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text(t("amount")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Box(Modifier.weight(0.5f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(currency) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("UZS", "USD", "EUR", "RUB").forEach { ccy ->
                                DropdownMenuItem(text = { Text(ccy) }, onClick = { currency = ccy; expanded = false })
                            }
                        }
                    }
                }

                Text(t("category") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories
                    currentCategories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) }
                }

                Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c -> FilterChip(selected = selectedCardId == c.id && !isCustomCard, onClick = { selectedCardId = c.id; isCustomCard = false }, label = { Text("${c.name} (${c.currency})") }) }
                    FilterChip(selected = isCustomCard, onClick = { isCustomCard = true; selectedCardId = "" }, label = { Text(t("other")) })
                }
                if (isCustomCard) {
                    var customLast4 by remember { mutableStateOf("") }
                    var customCurrency by remember { mutableStateOf("UZS") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customLast4, onValueChange = { customLast4 = it.filter(Char::isDigit).take(4); selectedCardId = "${customLast4}_${customCurrency}" }, label = { Text("4 цифры") }, singleLine = true, modifier = Modifier.weight(1f))
                        Box(Modifier.weight(0.5f)) {
                            var expanded by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(customCurrency) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("UZS", "USD", "EUR", "RUB").forEach { ccy ->
                                    DropdownMenuItem(text = { Text(ccy) }, onClick = { customCurrency = ccy; selectedCardId = "${customLast4}_${ccy}"; expanded = false })
                                }
                            }
                        }
                    }
                }
                
                OutlinedTextField(desc, { desc = it }, label = { Text(t("desc")) })
                OutlinedTextField(date, { date = it }, label = { Text(t("date")) }, singleLine = true)
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton({
                var amountMinor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                
                // Если выбрана не UZS валюта, конвертируем в UZS для хранения, если карта UZS
                if (currency != "UZS" && amountMinor != null) {
                    val rateEntity = rates.find { it.currency == currency }
                    if (rateEntity != null) {
                        amountMinor = (amountMinor.toDouble() * rateEntity.rateToUzs).toBigDecimal().setScale(0, RoundingMode.HALF_UP).toLong()
                    }
                }
                
                val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull()
                when {
                    amountMinor == null || amountMinor <= 0 -> error = "Error"
                    d == null -> error = "Error"
                    isCustomCard && !selectedCardId.contains("_") -> error = "Error"
                    else -> {
                        val finalCardId = if (selectedCardId.isEmpty() || selectedCardId == "CASH") null else selectedCardId
                        onSave(Tx(id = initial?.id ?: 0, amountMinor = amountMinor, type = type, cardId = finalCardId, description = desc.trim(), category = category, timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), source = initial?.source ?: if (isPendingSms) "SMS" else "MANUAL", currency = currency, photoPath = photoPath))
                    }
                }
            }) { Text(if (isPendingSms) t("confirm") else t("save")) }
        },
        dismissButton = { Row { onDelete?.let { TextButton(it) { Text(t("delete"), color = RED) } }; TextButton(onDismiss) { Text(if (isPendingSms) t("reject") else t("cancel")) } } }
    )
}

@Composable
fun GoalManagerDialog(goals: List<SavingsGoalEntity>, cards: List<CardEntity>, lang: AppLanguage, onSaveGoal: (SavingsGoalEntity) -> Unit, onDeleteGoal: (SavingsGoalEntity) -> Unit, onDeposit: (SavingsGoalEntity, String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var targetAmount by remember { mutableStateOf("") }
    var depositGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }; var depositAmount by remember { mutableStateOf("") }
    var depositCardId by remember { mutableStateOf(cards.firstOrNull()?.id ?: "CASH") }
    val t = { key: String -> Translations.t(key, lang) }
    val currentDepositGoal = depositGoal

    if (currentDepositGoal != null) {
        AlertDialog(onDismissRequest = { depositGoal = null }, title = { Text("${t("deposit")}: ${currentDepositGoal.name}") },
            text = { Column { OutlinedTextField(value = depositAmount, onValueChange = { depositAmount = it }, label = { Text(t("deposit_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text(t("source_card") + ":", style = MaterialTheme.typography.labelMedium); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = depositCardId == c.id, onClick = { depositCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) }; FilterChip(selected = depositCardId == "CASH", onClick = { depositCardId = "CASH" }, label = { Text(t("cash")) }) } } },
            confirmButton = { TextButton(onClick = { val amountMinor = runCatching { BigDecimal(depositAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (amountMinor != null && amountMinor > 0) { onDeposit(currentDepositGoal, depositCardId, amountMinor); depositGoal = null; depositAmount = "" } }) { Text(t("deposit")) } },
            dismissButton = { TextButton(onClick = { depositGoal = null }) { Text(t("cancel")) } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(t("goals")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("add_goal") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("goal_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = targetAmount, onValueChange = { targetAmount = it }, label = { Text(t("target_amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                Button(onClick = { val targetMinor = runCatching { BigDecimal(targetAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (name.isNotBlank() && targetMinor != null && targetMinor > 0) { onSaveGoal(SavingsGoalEntity(name = name.trim(), targetAmountMinor = targetMinor)); name = ""; targetAmount = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add_goal")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("goals") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (goals.isEmpty()) Text(t("no_found"), color = Color.Gray)
                goals.forEach { goal -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(goal.name, fontWeight = FontWeight.Bold); Text("${goal.currentAmountMinor.money()} / ${goal.targetAmountMinor.money()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton(onClick = { depositGoal = goal }) { Icon(Icons.Default.Add, t("deposit"), tint = GREEN) }; IconButton(onClick = { onDeleteGoal(goal) }) { Icon(Icons.Default.Delete, t("delete"), tint = RED) } } }
            } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
    }
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    e.printStackTrace()
    null
}

fun extractTextFromPdf(context: Context, uri: Uri): String {
    return try { context.contentResolver.openInputStream(uri)?.use { inputStream -> val doc = PDDocument.load(inputStream); val stripper = PDFTextStripper(); val text = stripper.getText(doc); doc.close(); text } ?: "" } catch (e: Exception) { "" }
}

fun parseImportedFile(text: String): List<Tx> {
    val result = mutableListOf<Tx>()
    val lines = text.lines()
    val kapitalPdfRegex = Regex("""^(\d{2}\.\d{2}\.\d{4})(-?)\s*([\d\s]+,\d{2})(.*)$""")
    val generalDateRegex = Regex("""(\d{2}[./]\d{2}[./]\d{4}|\d{4}-\d{2}-\d{2})""")
    val generalAmountRegex = Regex("""(-?\d[\d\s]*[.,]?\d{0,2})\s*(?:uzs|сум|rub|руб|\$|€)?$""", RegexOption.IGNORE_CASE)

    for (line in lines) {
        if (line.isBlank()) continue
        val kapitalMatch = kapitalPdfRegex.find(line.trim())
        if (kapitalMatch != null) {
            val dateStr = kapitalMatch.groupValues[1]; val sign = kapitalMatch.groupValues[2]; val amountStr = kapitalMatch.groupValues[3].replace(" ", "").replace(",", "."); val description = kapitalMatch.groupValues[4].trim()
            val amountDouble = amountStr.toDoubleOrNull() ?: continue
            val isIncome = sign.isEmpty() && amountDouble > 0 && (description.contains("пополнение", true) || description.contains("зачисление", true))
            val amountMinor = kotlin.math.abs(amountDouble).toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            if (amountMinor > 0) {
                val timestamp = try { val parts = dateStr.split("."); LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                result.add(Tx(amountMinor = amountMinor, type = if (isIncome) TxType.INCOME else TxType.EXPENSE, description = description.take(40), category = if (isIncome) "💵 Зарплата" else "📦 Другое", timestamp = timestamp, source = "IMPORT"))
                continue
            }
        }
        val dateMatch = generalDateRegex.find(line); val amountMatch = generalAmountRegex.find(line.trim())
        if (dateMatch != null && amountMatch != null) {
            val dateStr = dateMatch.value.replace(".", "-").replace("/", "-"); val cleanAmountStr = amountMatch.groupValues[1].replace(" ", "").replace(",", ".")
            val amountDouble = cleanAmountStr.toDoubleOrNull() ?: continue
            val isIncome = !line.contains("-") && amountDouble > 0 && (line.contains("пополнение", true) || line.contains("зачисление", true) || line.contains("income", true))
            val amountMinor = kotlin.math.abs(amountDouble).toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            if (amountMinor <= 0) continue
            val timestamp = try { val parts = dateStr.split("-"); val (year, month, day) = if (parts[0].length == 4) Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt()); LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
            val description = line.replace(dateMatch.value, "").replace(amountMatch.value, "").trim().replace("\\s+".toRegex(), " ").take(40)
            result.add(Tx(amountMinor = amountMinor, type = if (isIncome) TxType.INCOME else TxType.EXPENSE, description = description.ifBlank { "Импорт" }, category = if (isIncome) "💵 Зарплата" else "📦 Другое", timestamp = timestamp, source = "IMPORT"))
        }
    }
    return result
}

fun parseCardsFromScreenshot(text: String): List<CardEntity> {
    val result = mutableListOf<CardEntity>()
    val cardRegex = Regex("""([\d\s]+[.,]\d{2})\s*(?:сум|uzs)[\s\S]*?(\d{4})""", RegexOption.IGNORE_CASE)
    val matches = cardRegex.findAll(text)
    for (match in matches) {
        val balanceStr = match.groupValues[1].replace(" ", "").replace(",", ".")
        val last4 = match.groupValues[2]
        val balanceMinor = balanceStr.toDoubleOrNull()?.toBigDecimal()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong() ?: 0L
        if (result.none { it.last4 == last4 }) {
            result.add(CardEntity(id = "${last4}_UZS", last4 = last4, name = "Импорт *$last4", currency = "UZS", initialBalanceMinor = balanceMinor))
        }
    }
    return result
}

// ЭТА ФУНКЦИЯ И КЛАСС ДОБАВЛЕНЫ СЮДА
data class ParsedSms(val amountStr: String, val card: String?, val description: String, val category: String)

fun parseSmsText(text: String): ParsedSms? {
    val lowerText = text.lowercase()
    val lines = text.lines()
    var amountStr: String? = null
    for (line in lines) {
        val lowerLine = line.lowercase()
        if (lowerLine.contains("баланс") || lowerLine.contains("кешбэк") || lowerLine.contains("qoldiq")) continue
        val regex = Regex("""(-?[\d\s]+[.,]\d{2})""")
        val match = regex.find(line)
        if (match != null) { amountStr = match.groupValues[1].replace(" ", "").replace(",", "."); break }
    }
    if (amountStr == null) return null
    val card = Regex("""(?:karta|карта|\*+|х)\s*\(?\*+(\d{4})\)?""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.take(4)
    val description = text.take(50).replace("\n", " ").trim()
    val category = when {
        lowerText.contains("atto") || lowerText.contains("market") || lowerText.contains("jami") -> "🍔 Еда"
        lowerText.contains("uzumbank") -> "🚕 Транспорт"
        lowerText.contains("самозанятому") -> "💵 Зарплата"
        else -> "📦 Другое"
    }
    return ParsedSms(amountStr, card, description, category)
}

@Composable
fun DailyExpenseChart(txs: List<Tx>, yearMonth: YearMonth) {
    val daysInMonth = yearMonth.lengthOfMonth(); val zone = ZoneId.systemDefault()
    val dailyExpenses = (1..daysInMonth).associateWith { day -> txs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.dayOfMonth == day && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (dailyExpenses.values.maxOrNull() ?: 1L).toFloat(); val barColor = MaterialTheme.colorScheme.primary
    Column { Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) { val width = size.width; val height = size.height; val barWidth = (width / daysInMonth) * 0.6f; val step = width / daysInMonth; dailyExpenses.forEach { (day, amount) -> if (amount > 0) { val barHeight = (amount / maxExpense) * height; val x = (day - 1) * step + (step - barWidth) / 2; drawRoundRect(color = barColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())) } } }
        Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("15", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("$daysInMonth", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }
}

@Composable
fun MonthlyExpenseChart(allTxs: List<Tx>) {
    val currentYear = Year.now(); val zone = ZoneId.systemDefault()
    val monthlyExpenses = (1..12).associateWith { m -> allTxs.filter { val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date.year == currentYear.value && date.monthValue == m && it.type == TxType.EXPENSE }.sumOf { it.amountMinor } }
    val maxExpense = (monthlyExpenses.values.maxOrNull() ?: 1L).toFloat(); val barColor = MaterialTheme.colorScheme.secondary
    Column { Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) { val width = size.width; val height = size.height; val step = width / 12; val barWidth = step * 0.6f; monthlyExpenses.forEach { (month, amount) -> if (amount > 0) { val barHeight = (amount / maxExpense) * height; val x = (month - 1) * step + (step - barWidth) / 2; drawRoundRect(color = barColor, topLeft = Offset(x, height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())) } } }
        Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("4", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("7", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("10", style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text("12", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerDialog(categories: List<CategoryEntity>, lang: AppLanguage, onSave: (CategoryEntity) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var isIncome by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; val t = { key: String -> Translations.t(key, lang) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("categories")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("add_cat") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { SegmentedButton(!isIncome, { isIncome = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(t("expense")) }; SegmentedButton(isIncome, { isIncome = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(t("income")) } }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("category")) }, placeholder = { Text("🐶") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
            Button(onClick = { if (name.isNotBlank() && !categories.any { it.name == name }) { onSave(CategoryEntity(name = name.trim(), type = if (isIncome) TxType.INCOME else TxType.EXPENSE)); name = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("expense") + ":", style = MaterialTheme.typography.titleSmall, color = RED)
            categories.filter { it.type == TxType.EXPENSE }.forEach { c -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge); IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
            Spacer(Modifier.height(16.dp)); Text(t("income") + ":", style = MaterialTheme.typography.titleSmall, color = GREEN)
            categories.filter { it.type == TxType.INCOME }.forEach { c -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(Translations.translateCategory(c.name, lang), style = MaterialTheme.typography.bodyLarge); IconButton({ onDelete(c.name) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>, vm: VM) {
    val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE); val settingsPrefs = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
    var oldPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    var smsDebug by remember { mutableStateOf(settingsPrefs.getBoolean("sms_debug_mode", false)) }
    val savedPin = prefs.getString("pin", "0000") ?: "0000"; val t = { key: String -> Translations.t(key, currentLang.value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("settings")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("language"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentLang.value == AppLanguage.RU, onClick = { currentLang.value = AppLanguage.RU; settingsPrefs.edit().putString("app_lang", "RU").apply() }, label = { Text("Русский") })
                FilterChip(selected = currentLang.value == AppLanguage.UZ, onClick = { currentLang.value = AppLanguage.UZ; settingsPrefs.edit().putString("app_lang", "UZ").apply() }, label = { Text("O'zbekcha") })
                FilterChip(selected = currentLang.value == AppLanguage.EN, onClick = { currentLang.value = AppLanguage.EN; settingsPrefs.edit().putString("app_lang", "EN").apply() }, label = { Text("English") })
                FilterChip(selected = currentLang.value == AppLanguage.TG, onClick = { currentLang.value = AppLanguage.TG; settingsPrefs.edit().putString("app_lang", "TG").apply() }, label = { Text("Тоҷикӣ") })
                FilterChip(selected = currentLang.value == AppLanguage.KAA, onClick = { currentLang.value = AppLanguage.KAA; settingsPrefs.edit().putString("app_lang", "KAA").apply() }, label = { Text("Qaraqalpaqsha") })
                FilterChip(selected = currentLang.value == AppLanguage.KK, onClick = { currentLang.value = AppLanguage.KK; settingsPrefs.edit().putString("app_lang", "KK").apply() }, label = { Text("Қазақша") })
            }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("theme"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentTheme.value == AppTheme.SYSTEM, onClick = { currentTheme.value = AppTheme.SYSTEM; settingsPrefs.edit().putString("app_theme", "SYSTEM").apply() }, label = { Text(t("sys")) })
                FilterChip(selected = currentTheme.value == AppTheme.LIGHT, onClick = { currentTheme.value = AppTheme.LIGHT; settingsPrefs.edit().putString("app_theme", "LIGHT").apply() }, label = { Text(t("light")) })
                FilterChip(selected = currentTheme.value == AppTheme.DARK, onClick = { currentTheme.value = AppTheme.DARK; settingsPrefs.edit().putString("app_theme", "DARK").apply() }, label = { Text(t("dark")) })
            }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            
            Button(onClick = { vm.updateRates(); scope.launch { snackbarHostState.showSnackbar("Курсы обновлены") } }, modifier = Modifier.fillMaxWidth()) { Text(t("update_rates")) }
            
            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("change_pin"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin") + " (new)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin") + " (repeat)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            Button(onClick = { when { oldPin != savedPin -> scope.launch { snackbarHostState.showSnackbar(t("wrong_pin")) }; newPin.length != 4 -> scope.launch { snackbarHostState.showSnackbar("PIN: 4") }; newPin != confirmPin -> scope.launch { snackbarHostState.showSnackbar("PIN Error") }; else -> { prefs.edit().putString("pin", newPin).apply(); oldPin = ""; newPin = ""; confirmPin = ""; scope.launch { snackbarHostState.showSnackbar("PIN OK!") } } } }, modifier = Modifier.fillMaxWidth()) { Text(t("change_pin")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t("biometric"), style = MaterialTheme.typography.titleSmall) }; Switch(checked = useBiometric, onCheckedChange = { useBiometric = it; prefs.edit().putBoolean("use_biometric", it).apply() }) }
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t("sms_debug"), style = MaterialTheme.typography.titleSmall) }; Switch(checked = smsDebug, onCheckedChange = { smsDebug = it; settingsPrefs.edit().putBoolean("sms_debug_mode", it).apply() }) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

fun generateCsv(transactions: List<Tx>): String {
    val sb = StringBuilder("Дата,Время,Тип,Сумма (UZS),Категория,Описание,Карта,Источник,Валюта\n"); val zone = ZoneId.systemDefault()
    transactions.sortedBy { it.timestamp }.forEach { t -> val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone); val amount = BigDecimal(t.amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString(); sb.append("${dt.format(DATE_FMT)},${dt.format(DateTimeFormatter.ofPattern("HH:mm"))},${t.type.label},$amount,${t.category.replace(",", " ")},${t.description.replace(",", " ").replace("\n", " ")},${if (t.cardId.isNullOrEmpty()) "Наличные" else "ID: ${t.cardId}"},${t.source},${t.currency}\n") }
    return sb.toString()
}

@Composable
fun CategoryPieChart(data: Map<String, Long>, lang: AppLanguage) {
    val total = data.values.sum().toFloat(); if (total == 0f) return; val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)); val sortedData = data.entries.sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) { Canvas(modifier = Modifier.size(110.dp).padding(8.dp)) { var startAngle = -90f; sortedData.forEachIndexed { index, entry -> val sweepAngle = (entry.value / total) * 360f; drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true); startAngle += sweepAngle } }
        Spacer(Modifier.width(16.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { sortedData.take(4).forEachIndexed { index, entry -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(colors[index % colors.size], shape = CircleShape)); Spacer(Modifier.width(6.dp)); Text("${Translations.translateCategory(entry.key, lang)}: ${(entry.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) } } } }
}

@Composable
fun SumRow(label: String, v: Long, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = if (bold) FontWeight.Bold else null); Text("${v.money()} UZS", color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium) }
}

@Composable
fun TxRow(t: Tx, lang: AppLanguage, onClick: () -> Unit) {
    val (sign, color) = when (t.type) { TxType.INCOME -> "+" to GREEN; TxType.EXPENSE -> "−" to RED; TxType.TRANSFER -> "→" to BLUE }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { val icon = when (t.source) { "AUTO" -> "🔄 "; "SMS" -> "✉️ "; "SAVINGS" -> "🏦 "; "IMPORT" -> "📥 "; else -> "" }; Text("$icon${Translations.translateCategory(t.category, lang)}  ${t.description}".trim(), maxLines = 2); val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")); val cardText = if (t.cardId.isNullOrEmpty()) Translations.t("cash", lang) else "*${t.cardId.split("_")[0]} (${t.cardId.split("_")[1]})"; Text(listOfNotNull(time, cardText).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerDialog(recurring: List<RecurringEntity>, cards: List<CardEntity>, expenseCategories: List<String>, incomeCategories: List<String>, currentLang: AppLanguage, onSave: (RecurringEntity) -> Unit, onDelete: (RecurringEntity) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }; var category by remember { mutableStateOf(expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf("") }; var selectedCardId by remember { mutableStateOf("CASH") }; var desc by remember { mutableStateOf("") }; var dayOfMonth by remember { mutableStateOf("1") }
    val t = { key: String -> Translations.t(key, currentLang) }
    LaunchedEffect(type) { val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories; if (!currentList.contains(category)) category = currentList.lastOrNull() ?: "📦 Другое" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("recurring")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("add_temp") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { TxType.values().forEachIndexed { i, tt -> SegmentedButton(type == tt, { type = tt }, SegmentedButtonDefaults.itemShape(i, TxType.values().size)) { Text(if(tt == TxType.INCOME) t("income") else if(tt == TxType.EXPENSE) t("expense") else "→") } } }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) }, label = { Text(t("day")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(8.dp))
            Text(t("category") + ":", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories; currentCategories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) } }
            Spacer(Modifier.height(8.dp)); Text(t("account") + ":", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { c -> FilterChip(selected = selectedCardId == c.id, onClick = { selectedCardId = c.id }, label = { Text("${c.name} (${c.currency})") }) } }
            Spacer(Modifier.height(8.dp)); Button(onClick = { val minor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); val day = dayOfMonth.toIntOrNull(); if (minor != null && minor > 0 && day != null && day in 1..31) { onSave(RecurringEntity(amountMinor = minor, type = type, cardId = if(selectedCardId == "CASH") null else selectedCardId, description = desc.trim(), category = category, dayOfMonth = day)); amount = ""; desc = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add_temp")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("recurring") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            if (recurring.isEmpty()) Text(t("no_found"), color = Color.Gray)
            recurring.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.description.ifBlank { Translations.translateCategory(r.category, currentLang) }, fontWeight = FontWeight.Bold); Text("${r.amountMinor.money()} UZS · ${r.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(r) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@Composable
fun BudgetManagerDialog(budgets: List<BudgetEntity>, expenseCategories: List<String>, onSave: (BudgetEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, onDismiss: () -> Unit) {
    var selectedCategory by remember { mutableStateOf(expenseCategories.firstOrNull() ?: "📦 Другое") }; var limitInput by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, currentLang) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("budgets")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(t("set_limit") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { expenseCategories.forEach { c -> FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(Translations.translateCategory(c, currentLang)) }) } }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text(t("limit")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
            Button(onClick = { val limitMinor = runCatching { BigDecimal(limitInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (limitMinor != null && limitMinor > 0) { onSave(BudgetEntity(category = selectedCategory, limitMinor = limitMinor)); limitInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("set_limit")) }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("budgets") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            if (budgets.isEmpty()) Text(t("no_found"), color = Color.Gray)
            budgets.forEach { b -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(Translations.translateCategory(b.category, currentLang), fontWeight = FontWeight.Bold); Text("${t("limit")}: ${b.limitMinor.money()} UZS", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; IconButton({ onDelete(b.category) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
}

@Composable
fun CardManagerDialog(cards: List<CardEntity>, cardBalances: Map<String, Long>, allTxs: List<Tx>, onSave: (CardEntity) -> Unit, onDelete: (String) -> Unit, currentLang: AppLanguage, rates: List<ExchangeRateEntity>, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var initialBalance by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("UZS") }
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }; var actualBalanceInput by remember { mutableStateOf("") }
    val t = { key: String -> Translations.t(key, currentLang) }

    if (editingCard != null) {
        val card = editingCard!!; val currentMathBalance = cardBalances[card.id] ?: card.initialBalanceMinor
        AlertDialog(onDismissRequest = { editingCard = null }, title = { Text("${card.name} (${card.currency})") },
            text = { Column { Text("${currentMathBalance.money()} ${card.currency}", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)); Text(t("amount") + ":", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = actualBalanceInput, onValueChange = { actualBalanceInput = it }, label = { Text(t("amount")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { TextButton(onClick = { val actualMinor = runCatching { BigDecimal(actualBalanceInput.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull(); if (actualMinor != null) { onSave(card.copy(initialBalanceMinor = card.initialBalanceMinor + (actualMinor - currentMathBalance))); editingCard = null; actualBalanceInput = "" } }) { Text(t("save")) } },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text(t("cancel")) } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(t("my_accounts")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t("add") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t("desc")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp)); OutlinedTextField(value = initialBalance, onValueChange = { initialBalance = it }, label = { Text(t("amount")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Spacer(Modifier.height(8.dp))
                Button(onClick = { val balanceMinor = runCatching { BigDecimal(initialBalance.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull() ?: 0L; if (name.isNotBlank() && last4.isNotBlank()) { onSave(CardEntity(id = "${last4}_$currency", last4 = last4, name = name, currency = currency, initialBalanceMinor = balanceMinor)); name = ""; last4 = ""; initialBalance = "" } }, modifier = Modifier.fillMaxWidth()) { Text(t("add")) }
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(t("my_accounts") + ":", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                if (cards.isEmpty()) Text(t("no_found"), color = Color.Gray)
                cards.forEach { c -> val currentBal = cardBalances[c.id] ?: c.initialBalanceMinor
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${c.name} (${c.currency})", fontWeight = FontWeight.Bold); Text("${currentBal.money()} ${c.currency}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; if (c.id != "CASH") { IconButton(onClick = { editingCard = c; actualBalanceInput = BigDecimal(currentBal).movePointLeft(2).stripTrailingZeros().toPlainString() }) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }; IconButton({ onDelete(c.id) }) { Icon(Icons.Default.Delete, "Delete", tint = RED) } } }
                }
            } }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } })
    }
}
