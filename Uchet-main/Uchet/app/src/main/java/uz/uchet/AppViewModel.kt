package uz.uchet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.*

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

    // Старый прогноз (для совместимости до этапа B)
    val planItems = dao.observePlanItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<PlanItemEntity>())

    // НОВАЯ СИСТЕМА
    val forecasts = dao.observeForecasts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ForecastPlanEntity>())

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
        val now = LocalDate.now()
        val currentMonthStr = YearMonth.now().toString()
        val currentDay = now.dayOfMonth
        val templates = dao.getRecurringList()
        for (t in templates) {
            val effectiveDay = minOf(t.dayOfMonth, now.lengthOfMonth())
            if (effectiveDay <= currentDay && t.lastExecutedMonth != currentMonthStr) {
                val tx = Tx(
                    amountMinor = t.amountMinor, type = t.type, cardId = t.cardId,
                    description = t.description, category = t.category,
                    timestamp = now.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    source = "AUTO"
                )
                dao.upsert(tx)
                dao.upsertRecurring(t.copy(lastExecutedMonth = currentMonthStr))
            }
        }
    }

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun bulkSave(txs: List<Tx>) { viewModelScope.launch { dao.insertAll(txs) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }

    fun deleteCard(id: String) {
        if (id != "CASH") viewModelScope.launch {
            dao.unlinkCardTransactions(id)
            dao.deleteCard(id)
        }
    }

    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
    fun saveRecurring(r: RecurringEntity) { viewModelScope.launch { dao.upsertRecurring(r) } }
    fun deleteRecurring(r: RecurringEntity) { viewModelScope.launch { dao.deleteRecurring(r) } }
    fun saveCategory(c: CategoryEntity) { viewModelScope.launch { dao.upsertCategory(c) } }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            dao.replaceCategory(name, "📦 Другое")
            dao.deleteCategory(name)
        }
    }

    fun saveGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.upsertGoal(goal) } }
    fun deleteGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.deleteGoal(goal) } }

    // ---------- СТАРЫЙ ПРОГНОЗ (для совместимости, до этапа B) ----------
    fun savePlanItem(item: PlanItemEntity) { viewModelScope.launch { dao.upsertPlanItem(item) } }
    fun deletePlanItem(id: Long) { viewModelScope.launch { dao.deletePlanItem(id) } }

    // ---------- НОВАЯ СИСТЕМА: Прогнозы ----------
    fun saveForecast(f: ForecastPlanEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = dao.upsertForecast(f)
            // Room возвращает id только для INSERT, для UPDATE возвращает -1.
            // Если id = 0 (новый) — вернём реальный id. Если f.id > 0 — UPDATE.
            val finalId = if (f.id == 0L) id else f.id
            withContext(Dispatchers.Main) { onSaved(finalId) }
        }
    }

    fun deleteForecast(id: Long) { viewModelScope.launch { dao.deleteForecast(id) } }

    fun observeForecastItems(forecastId: Long): Flow<List<ForecastItemEntity>> =
        dao.observeForecastItems(forecastId)

    fun observeForecastDeposits(forecastId: Long): Flow<List<ForecastDepositEntity>> =
        dao.observeForecastDeposits(forecastId)

    fun observeDepositOps(depositId: Long): Flow<List<ForecastDepositOpEntity>> =
        dao.observeDepositOps(depositId)

    fun saveForecastItem(item: ForecastItemEntity) { viewModelScope.launch { dao.upsertForecastItem(item) } }
    fun deleteForecastItem(id: Long) { viewModelScope.launch { dao.deleteForecastItem(id) } }

    fun saveForecastDeposit(d: ForecastDepositEntity) { viewModelScope.launch { dao.upsertForecastDeposit(d) } }
    fun deleteForecastDeposit(id: Long) { viewModelScope.launch { dao.deleteForecastDeposit(id) } }

    fun saveDepositOp(op: ForecastDepositOpEntity) { viewModelScope.launch { dao.upsertDepositOp(op) } }
    fun deleteDepositOp(id: Long) { viewModelScope.launch { dao.deleteDepositOp(id) } }

    // Вспомогательный метод: полный расчёт прогноза
    fun calculateForecast(forecast: ForecastPlanEntity): List<ForecastCalculator.MonthForecast> {
        return runBlockingSafe {
            val items = dao.observeForecastItems(forecast.id).first()
            val deposits = dao.observeForecastDeposits(forecast.id).first()
            // Для операций собираем через observeDepositOps для каждого вклада
            val allOps = mutableListOf<ForecastDepositOpEntity>()
            deposits.forEach { d -> allOps.addAll(dao.observeDepositOps(d.id).first()) }
            ForecastCalculator.calculate(forecast, items, deposits, allOps)
        }
    }

    // Небольшой хелпер, чтобы можно было вызвать из non-suspend контекста (в Compose)
    private fun <T> runBlockingSafe(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }

    // ---------- Служебное ----------
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
            val tx = Tx(
                amountMinor = amountMinor, type = TxType.TRANSFER, cardId = cardId,
                toCardId = "GOAL_${goal.id}", description = goal.name,
                category = "🏦 Копилка", timestamp = System.currentTimeMillis(), source = "SAVINGS"
            )
            dao.upsert(tx)
            dao.upsertGoal(goal.copy(currentAmountMinor = goal.currentAmountMinor + amountMinor))
        }
    }

    // ---------- Бэкап / восстановление ----------
    private suspend fun buildBackupJson(): String {
        val allTx = allTxs.first()
        val allCards = cards.first()
        val allBudgets = budgets.first()
        val allRecurring = dao.getRecurringList()
        val allCats = dao.getCategoriesList()
        val allGoals = goals.first()
        val allPlans = dao.getPlanItemsList()
        val allForecasts = dao.getForecastsList()
        val allForecastItems = dao.getAllForecastItems()
        val allForecastDeposits = dao.getAllForecastDeposits()
        val allDepositOps = dao.getAllDepositOps()

        val root = JSONObject()

        val txsArray = JSONArray()
        allTx.forEach { t -> txsArray.put(JSONObject().apply {
            put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name)
            put("cardId", t.cardId ?: JSONObject.NULL); put("toCardId", t.toCardId ?: JSONObject.NULL)
            put("toAmountMinor", t.toAmountMinor ?: JSONObject.NULL)
            put("description", t.description); put("category", t.category); put("timestamp", t.timestamp)
            put("source", t.source); put("currency", t.currency); put("photoPath", t.photoPath ?: JSONObject.NULL)
        }) }
        root.put("txs", txsArray)

        val cardsArray = JSONArray()
        allCards.forEach { c -> cardsArray.put(JSONObject().apply {
            put("id", c.id); put("last4", c.last4); put("name", c.name)
            put("currency", c.currency); put("initialBalanceMinor", c.initialBalanceMinor)
        }) }
        root.put("cards", cardsArray)

        val budgetsArray = JSONArray()
        allBudgets.forEach { b -> budgetsArray.put(JSONObject().apply {
            put("category", b.category); put("limitMinor", b.limitMinor)
        }) }
        root.put("budgets", budgetsArray)

        val recurringArray = JSONArray()
        allRecurring.forEach { r -> recurringArray.put(JSONObject().apply {
            put("id", r.id); put("amountMinor", r.amountMinor); put("type", r.type.name)
            put("cardId", r.cardId ?: JSONObject.NULL); put("description", r.description)
            put("category", r.category); put("dayOfMonth", r.dayOfMonth)
            put("lastExecutedMonth", r.lastExecutedMonth)
        }) }
        root.put("recurring", recurringArray)

        val catsArray = JSONArray()
        allCats.forEach { c -> catsArray.put(JSONObject().apply {
            put("name", c.name); put("type", c.type.name)
        }) }
        root.put("categories", catsArray)

        val goalsArray = JSONArray()
        allGoals.forEach { g -> goalsArray.put(JSONObject().apply {
            put("id", g.id); put("name", g.name); put("targetAmountMinor", g.targetAmountMinor)
            put("currentAmountMinor", g.currentAmountMinor)
        }) }
        root.put("goals", goalsArray)

        val plansArray = JSONArray()
        allPlans.forEach { p -> plansArray.put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("type", p.type.name)
            put("amountMinor", p.amountMinor); put("startMonth", p.startMonth)
            put("endMonth", p.endMonth); put("isFixed", p.isFixed)
        }) }
        root.put("plan_items", plansArray)

        val forecastArray = JSONArray()
        allForecasts.forEach { f -> forecastArray.put(JSONObject().apply {
            put("id", f.id); put("name", f.name); put("months", f.months)
            put("startMonth", f.startMonth); put("createdAt", f.createdAt)
        }) }
        root.put("forecasts", forecastArray)

        val fiArray = JSONArray()
        allForecastItems.forEach { i -> fiArray.put(JSONObject().apply {
            put("id", i.id); put("forecastId", i.forecastId); put("name", i.name)
            put("type", i.type.name); put("amountMinor", i.amountMinor)
            put("startMonth", i.startMonth); put("endMonth", i.endMonth); put("isFixed", i.isFixed)
        }) }
        root.put("forecast_items", fiArray)

        val fdArray = JSONArray()
        allForecastDeposits.forEach { d -> fdArray.put(JSONObject().apply {
            put("id", d.id); put("forecastId", d.forecastId); put("name", d.name)
            put("initialAmountMinor", d.initialAmountMinor); put("annualRatePercent", d.annualRatePercent)
            put("startMonth", d.startMonth); put("endMonth", d.endMonth)
            put("monthlyContributionMinor", d.monthlyContributionMinor)
            put("monthlyWithdrawalMinor", d.monthlyWithdrawalMinor)
        }) }
        root.put("forecast_deposits", fdArray)

        val opsArray = JSONArray()
        allDepositOps.forEach { o -> opsArray.put(JSONObject().apply {
            put("id", o.id); put("depositId", o.depositId); put("month", o.month)
            put("amountMinor", o.amountMinor); put("description", o.description)
        }) }
        root.put("forecast_deposit_ops", opsArray)

        return root.toString(2)
    }

    fun createBackup(password: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = buildBackupJson()
                val encrypted = withContext(Dispatchers.Default) {
                    BackupEncryption.encrypt(jsonString, password)
                }
                onResult(encrypted)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    private suspend fun applyBackupJson(root: JSONObject) {
        val txsList = mutableListOf<Tx>()
        val txsArray = root.optJSONArray("txs")
        if (txsArray != null) {
            for (i in 0 until txsArray.length()) {
                val o = txsArray.getJSONObject(i)
                txsList.add(Tx(
                    id = o.optLong("id"), amountMinor = o.getLong("amountMinor"),
                    type = TxType.valueOf(o.getString("type")),
                    cardId = if (o.isNull("cardId")) null else o.getString("cardId"),
                    toCardId = if (o.isNull("toCardId")) null else o.optString("toCardId"),
                    toAmountMinor = if (o.isNull("toAmountMinor")) null else o.optLong("toAmountMinor"),
                    description = o.optString("description"), category = o.optString("category"),
                    timestamp = o.getLong("timestamp"), source = o.optString("source"),
                    currency = o.optString("currency", "UZS"),
                    photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath")
                ))
            }
        }

        val cardsList = mutableListOf<CardEntity>()
        val cardsArray = root.optJSONArray("cards")
        if (cardsArray != null) {
            for (i in 0 until cardsArray.length()) {
                val o = cardsArray.getJSONObject(i)
                cardsList.add(CardEntity(
                    id = o.getString("id"), last4 = o.getString("last4"),
                    name = o.getString("name"), currency = o.getString("currency"),
                    initialBalanceMinor = o.getLong("initialBalanceMinor")
                ))
            }
        }

        val budgetsList = mutableListOf<BudgetEntity>()
        val budgetsArray = root.optJSONArray("budgets")
        if (budgetsArray != null) {
            for (i in 0 until budgetsArray.length()) {
                val o = budgetsArray.getJSONObject(i)
                budgetsList.add(BudgetEntity(category = o.getString("category"), limitMinor = o.getLong("limitMinor")))
            }
        }

        val recurringList = mutableListOf<RecurringEntity>()
        val recurringArray = root.optJSONArray("recurring")
        if (recurringArray != null) {
            for (i in 0 until recurringArray.length()) {
                val o = recurringArray.getJSONObject(i)
                recurringList.add(RecurringEntity(
                    id = o.optLong("id"), amountMinor = o.getLong("amountMinor"),
                    type = TxType.valueOf(o.getString("type")),
                    cardId = if (o.isNull("cardId")) null else o.getString("cardId"),
                    description = o.optString("description"), category = o.optString("category"),
                    dayOfMonth = o.getInt("dayOfMonth"), lastExecutedMonth = o.optString("lastExecutedMonth")
                ))
            }
        }

        val catsList = mutableListOf<CategoryEntity>()
        val catsArray = root.optJSONArray("categories")
        if (catsArray != null) {
            for (i in 0 until catsArray.length()) {
                val o = catsArray.getJSONObject(i)
                catsList.add(CategoryEntity(name = o.getString("name"), type = TxType.valueOf(o.getString("type"))))
            }
        }

        val goalsList = mutableListOf<SavingsGoalEntity>()
        val goalsArray = root.optJSONArray("goals")
        if (goalsArray != null) {
            for (i in 0 until goalsArray.length()) {
                val o = goalsArray.getJSONObject(i)
                goalsList.add(SavingsGoalEntity(
                    id = o.optLong("id"), name = o.getString("name"),
                    targetAmountMinor = o.getLong("targetAmountMinor"),
                    currentAmountMinor = o.getLong("currentAmountMinor")
                ))
            }
        }

        val plansList = mutableListOf<PlanItemEntity>()
        val plansArray = root.optJSONArray("plan_items")
        if (plansArray != null) {
            for (i in 0 until plansArray.length()) {
                val o = plansArray.getJSONObject(i)
                plansList.add(PlanItemEntity(
                    id = o.optLong("id"), name = o.getString("name"),
                    type = TxType.valueOf(o.getString("type")),
                    amountMinor = o.getLong("amountMinor"),
                    startMonth = o.getString("startMonth"),
                    endMonth = o.getString("endMonth"),
                    isFixed = o.optBoolean("isFixed", true)
                ))
            }
        }

        val forecastList = mutableListOf<ForecastPlanEntity>()
        val forecastArray = root.optJSONArray("forecasts")
        if (forecastArray != null) {
            for (i in 0 until forecastArray.length()) {
                val o = forecastArray.getJSONObject(i)
                forecastList.add(ForecastPlanEntity(
                    id = o.optLong("id"), name = o.getString("name"),
                    months = o.getInt("months"), startMonth = o.getString("startMonth"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }

        val fiList = mutableListOf<ForecastItemEntity>()
        val fiArray = root.optJSONArray("forecast_items")
        if (fiArray != null) {
            for (i in 0 until fiArray.length()) {
                val o = fiArray.getJSONObject(i)
                fiList.add(ForecastItemEntity(
                    id = o.optLong("id"), forecastId = o.getLong("forecastId"),
                    name = o.getString("name"), type = TxType.valueOf(o.getString("type")),
                    amountMinor = o.getLong("amountMinor"),
                    startMonth = o.getString("startMonth"), endMonth = o.getString("endMonth"),
                    isFixed = o.optBoolean("isFixed", true)
                ))
            }
        }

        val fdList = mutableListOf<ForecastDepositEntity>()
        val fdArray = root.optJSONArray("forecast_deposits")
        if (fdArray != null) {
            for (i in 0 until fdArray.length()) {
                val o = fdArray.getJSONObject(i)
                fdList.add(ForecastDepositEntity(
                    id = o.optLong("id"), forecastId = o.getLong("forecastId"),
                    name = o.getString("name"),
                    initialAmountMinor = o.getLong("initialAmountMinor"),
                    annualRatePercent = o.getDouble("annualRatePercent"),
                    startMonth = o.getString("startMonth"), endMonth = o.getString("endMonth"),
                    monthlyContributionMinor = o.optLong("monthlyContributionMinor", 0L),
                    monthlyWithdrawalMinor = o.optLong("monthlyWithdrawalMinor", 0L)
                ))
            }
        }

        val opsList = mutableListOf<ForecastDepositOpEntity>()
        val opsArray = root.optJSONArray("forecast_deposit_ops")
        if (opsArray != null) {
            for (i in 0 until opsArray.length()) {
                val o = opsArray.getJSONObject(i)
                opsList.add(ForecastDepositOpEntity(
                    id = o.optLong("id"), depositId = o.getLong("depositId"),
                    month = o.getString("month"), amountMinor = o.getLong("amountMinor"),
                    description = o.optString("description")
                ))
            }
        }

        db.withTransaction {
            dao.clearTxs(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()
            dao.clearCategories(); dao.clearGoals(); dao.clearPlanItems(); dao.clearForecasts()

            dao.insertAll(txsList)
            cardsList.forEach { dao.upsertCard(it) }
            budgetsList.forEach { dao.upsertBudget(it) }
            recurringList.forEach { dao.upsertRecurring(it) }
            catsList.forEach { dao.upsertCategory(it) }
            goalsList.forEach { dao.upsertGoal(it) }
            plansList.forEach { dao.upsertPlanItem(it) }
            forecastList.forEach { dao.upsertForecast(it) }
            fiList.forEach { dao.upsertForecastItem(it) }
            fdList.forEach { dao.upsertForecastDeposit(it) }
            opsList.forEach { dao.upsertDepositOp(it) }
        }
    }

    fun restoreBackup(data: ByteArray, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.Default) {
                    BackupEncryption.decrypt(data, password)
                }
                val root = JSONObject(jsonString)
                applyBackupJson(root)
                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun restoreBackupLegacy(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                applyBackupJson(root)
                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}
