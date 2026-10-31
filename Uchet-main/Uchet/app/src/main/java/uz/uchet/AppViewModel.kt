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

class VM(app: Application, private val db: AppDb, private val dao: AppDao) : AndroidViewModel(app) {
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
    val confirmations = dao.observeConfirmations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<RecurringConfirmationEntity>())
    val planItems = dao.observePlanItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<PlanItemEntity>())
    val forecasts = dao.observeForecasts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<ForecastPlanEntity>())
    val debts = dao.observeDebts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DebtEntity>())
    
    // НОВЫЙ ПОТОК
    val customRules = dao.observeCustomRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CustomKeywordRule>())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingRecurring: StateFlow<List<RecurringEntity>> = combine(recurring, confirmations) { recs, confs ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        val monthStr = YearMonth.from(today).toString()
        val daysInMonth = today.lengthOfMonth()

        recs.filter { r ->
            val effectiveDay = minOf(r.dayOfMonth, daysInMonth)
            if (effectiveDay > today.dayOfMonth) return@filter false
            val conf = confs.find { it.recurringId == r.id && it.month == monthStr }
            when {
                conf?.status == "CONFIRMED" -> false
                conf?.status == "POSTPONED" && conf.lastPostponedDate == todayStr -> false
                else -> true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            seedDefaultCategories()
            ensureCashAccount()
            updateRates()
            migrateLegacyAutoTxs()
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

    private suspend fun migrateLegacyAutoTxs() {
        val recs = dao.getRecurringList()
        val monthStr = YearMonth.now().toString()
        recs.forEach { r ->
            if (r.lastExecutedMonth == monthStr && dao.getConfirmation(r.id, monthStr) == null) {
                dao.upsertConfirmation(RecurringConfirmationEntity(recurringId = r.id, month = monthStr, status = "CONFIRMED", actualAmountMinor = r.amountMinor))
            }
        }
    }

    fun save(t: Tx) { viewModelScope.launch { dao.upsert(t) } }
    fun bulkSave(txs: List<Tx>) { viewModelScope.launch { dao.insertAll(txs) } }
    fun delete(t: Tx) { viewModelScope.launch { dao.delete(t) } }
    fun shift(n: Long) { month.value = month.value.plusMonths(n) }
    fun saveCard(card: CardEntity) { viewModelScope.launch { dao.upsertCard(card) } }
    fun deleteCard(id: String) { if (id != "CASH") viewModelScope.launch { dao.unlinkCardTransactions(id); dao.deleteCard(id) } }
    fun saveBudget(budget: BudgetEntity) { viewModelScope.launch { dao.upsertBudget(budget) } }
    fun deleteBudget(category: String) { viewModelScope.launch { dao.deleteBudget(category) } }
    fun saveRecurring(r: RecurringEntity) { viewModelScope.launch { dao.upsertRecurring(r) } }
    fun deleteRecurring(r: RecurringEntity) { viewModelScope.launch { dao.deleteRecurring(r); dao.deleteConfirmationsForRecurring(r.id) } }
    fun saveCategory(c: CategoryEntity) { viewModelScope.launch { dao.upsertCategory(c) } }
    fun deleteCategory(name: String) { viewModelScope.launch { dao.replaceCategory(name, "📦 Другое"); dao.deleteCategory(name) } }
    fun saveGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.upsertGoal(goal) } }
    fun deleteGoal(goal: SavingsGoalEntity) { viewModelScope.launch { dao.deleteGoal(goal) } }
    fun savePlanItem(item: PlanItemEntity) { viewModelScope.launch { dao.upsertPlanItem(item) } }
    fun deletePlanItem(id: Long) { viewModelScope.launch { dao.deletePlanItem(id) } }
    fun saveForecast(f: ForecastPlanEntity, onSaved: (Long) -> Unit = {}) { viewModelScope.launch { val insertedId = dao.upsertForecast(f); val finalId = if (f.id == 0L) insertedId else f.id; withContext(Dispatchers.Main) { onSaved(finalId) } } }
    fun deleteForecast(id: Long) { viewModelScope.launch { dao.deleteForecast(id) } }
    fun observeForecastItems(forecastId: Long): Flow<List<ForecastItemEntity>> = dao.observeForecastItems(forecastId)
    fun observeForecastDeposits(forecastId: Long): Flow<List<ForecastDepositEntity>> = dao.observeForecastDeposits(forecastId)
    fun observeDepositOps(depositId: Long): Flow<List<ForecastDepositOpEntity>> = dao.observeDepositOps(depositId)
    fun observeAllOpsForForecast(forecastId: Long): Flow<List<ForecastDepositOpEntity>> = dao.observeAllOpsForForecast(forecastId)
    fun saveForecastItem(item: ForecastItemEntity) { viewModelScope.launch { dao.upsertForecastItem(item) } }
    fun deleteForecastItem(id: Long) { viewModelScope.launch { dao.deleteForecastItem(id) } }
    fun saveForecastDeposit(d: ForecastDepositEntity) { viewModelScope.launch { dao.upsertForecastDeposit(d) } }
    fun deleteForecastDeposit(id: Long) { viewModelScope.launch { dao.deleteForecastDeposit(id) } }
    fun saveDepositOp(op: ForecastDepositOpEntity) { viewModelScope.launch { dao.upsertDepositOp(op) } }
    fun deleteDepositOp(id: Long) { viewModelScope.launch { dao.deleteDepositOp(id) } }
    fun calculateForecast(forecast: ForecastPlanEntity): List<ForecastCalculator.MonthForecast> {
        return kotlinx.coroutines.runBlocking {
            val items = dao.observeForecastItems(forecast.id).first()
            val deposits = dao.observeForecastDeposits(forecast.id).first()
            val allOps = mutableListOf<ForecastDepositOpEntity>()
            deposits.forEach { d -> allOps.addAll(dao.observeDepositOps(d.id).first()) }
            ForecastCalculator.calculate(forecast, items, deposits, allOps)
        }
    }

    fun adjustBalance(cardId: String, diffMinor: Long) {
        if (diffMinor == 0L) return
        val tx = Tx(amountMinor = kotlin.math.abs(diffMinor), type = if (diffMinor > 0) TxType.INCOME else TxType.EXPENSE, cardId = cardId, description = "Корректировка баланса", category = "⚙️ Система", timestamp = System.currentTimeMillis(), source = "ADJUSTMENT")
        viewModelScope.launch { dao.upsert(tx) }
    }

    fun depositToGoal(goal: SavingsGoalEntity, cardId: String, amountMinor: Long) {
        viewModelScope.launch {
            val tx = Tx(amountMinor = amountMinor, type = TxType.TRANSFER, cardId = cardId, toCardId = "GOAL_${goal.id}", description = goal.name, category = "🏦 Копилка", timestamp = System.currentTimeMillis(), source = "SAVINGS")
            dao.upsert(tx)
            dao.upsertGoal(goal.copy(currentAmountMinor = goal.currentAmountMinor + amountMinor))
        }
    }

    fun confirmRecurring(r: RecurringEntity, actualAmountMinor: Long) {
        if (actualAmountMinor <= 0) return
        viewModelScope.launch {
            val today = LocalDate.now()
            val monthStr = YearMonth.from(today).toString()
            val existing = dao.getConfirmation(r.id, monthStr)
            if (existing?.status == "CONFIRMED") return@launch
            val effectiveDay = minOf(r.dayOfMonth, today.lengthOfMonth())
            val txDate = today.withDayOfMonth(effectiveDay)
            val txTimestamp = txDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            dao.upsert(Tx(amountMinor = actualAmountMinor, type = r.type, cardId = r.cardId, description = r.description, category = r.category, timestamp = txTimestamp, source = "RECURRING"))
            dao.upsertConfirmation(RecurringConfirmationEntity(recurringId = r.id, month = monthStr, status = "CONFIRMED", actualAmountMinor = actualAmountMinor))
        }
    }

    fun postponeRecurring(r: RecurringEntity) {
        viewModelScope.launch {
            dao.upsertConfirmation(RecurringConfirmationEntity(recurringId = r.id, month = YearMonth.now().toString(), status = "POSTPONED", lastPostponedDate = LocalDate.now().toString()))
        }
    }

    fun saveDebt(debt: DebtEntity) {
        viewModelScope.launch {
            dao.upsertDebt(debt)
            if (debt.id == 0L) {
                val txType = if (debt.type == DebtType.BORROWED) TxType.INCOME else TxType.EXPENSE
                val txDesc = if (debt.type == DebtType.BORROWED) "Взял в долг: ${debt.personName}" else "Дал в долг: ${debt.personName}"
                val tx = Tx(amountMinor = debt.amountMinor, type = txType, description = txDesc, category = "🤝 Долги", timestamp = debt.dateMillis, source = "DEBT")
                dao.upsert(tx)
            }
        }
    }

    fun markDebtPaid(debt: DebtEntity) {
        viewModelScope.launch {
            dao.upsertDebt(debt.copy(isPaid = true))
            val txType = if (debt.type == DebtType.BORROWED) TxType.EXPENSE else TxType.INCOME
            val txDesc = if (debt.type == DebtType.BORROWED) "Вернул долг: ${debt.personName}" else "Мне вернули долг: ${debt.personName}"
            val tx = Tx(amountMinor = debt.amountMinor, type = txType, description = txDesc, category = "🤝 Долги", timestamp = System.currentTimeMillis(), source = "DEBT")
            dao.upsert(tx)
        }
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch {
            dao.deleteDebt(debt)
            if (!debt.isPaid) {
                val all = dao.getAllTxs()
                val relatedTx = all.find { it.source == "DEBT" && it.amountMinor == debt.amountMinor && it.timestamp == debt.dateMillis }
                if (relatedTx != null) dao.delete(relatedTx)
            }
        }
    }

    // НОВЫЕ МЕТОДЫ ДЛЯ ПРАВИЛ
    fun saveCustomRule(keyword: String, category: String, type: TxType) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            dao.upsertCustomRule(CustomKeywordRule(keyword = keyword.trim().lowercase(), category = category, type = type))
        }
    }
    fun deleteCustomRule(rule: CustomKeywordRule) { viewModelScope.launch { dao.deleteCustomRule(rule) } }

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
        val allConfirmations = dao.getAllConfirmations()
        val allDebts = dao.getAllDebts()
        val allRules = dao.getAllCustomRules()

        val root = JSONObject()

        val txsArray = JSONArray()
        allTx.forEach { t -> txsArray.put(JSONObject().apply { put("id", t.id); put("amountMinor", t.amountMinor); put("type", t.type.name); put("cardId", t.cardId ?: JSONObject.NULL); put("toCardId", t.toCardId ?: JSONObject.NULL); put("toAmountMinor", t.toAmountMinor ?: JSONObject.NULL); put("description", t.description); put("category", t.category); put("timestamp", t.timestamp); put("source", t.source); put("currency", t.currency); put("photoPath", t.photoPath ?: JSONObject.NULL) }) }
        root.put("txs", txsArray)

        val debtsArray = JSONArray()
        allDebts.forEach { d -> debtsArray.put(JSONObject().apply { put("id", d.id); put("personName", d.personName); put("amountMinor", d.amountMinor); put("type", d.type.name); put("dateMillis", d.dateMillis); put("isPaid", d.isPaid); put("description", d.description); put("currency", d.currency) }) }
        root.put("debts", debtsArray)

        val rulesArray = JSONArray()
        allRules.forEach { r -> rulesArray.put(JSONObject().apply { put("id", r.id); put("keyword", r.keyword); put("category", r.category); put("type", r.type.name) }) }
        root.put("custom_rules", rulesArray)

        val cardsArray = JSONArray()
        allCards.forEach { c -> cardsArray.put(JSONObject().apply { put("id", c.id); put("last4", c.last4); put("name", c.name); put("currency", c.currency); put("initialBalanceMinor", c.initialBalanceMinor) }) }
        root.put("cards", cardsArray)

        val budgetsArray = JSONArray()
        allBudgets.forEach { b -> budgetsArray.put(JSONObject().apply { put("category", b.category); put("limitMinor", b.limitMinor) }) }
        root.put("budgets", budgetsArray)

        val recurringArray = JSONArray()
        allRecurring.forEach { r -> recurringArray.put(JSONObject().apply { put("id", r.id); put("amountMinor", r.amountMinor); put("type", r.type.name); put("cardId", r.cardId ?: JSONObject.NULL); put("description", r.description); put("category", r.category); put("dayOfMonth", r.dayOfMonth); put("lastExecutedMonth", r.lastExecutedMonth) }) }
        root.put("recurring", recurringArray)

        val confirmationsArray = JSONArray()
        allConfirmations.forEach { c -> confirmationsArray.put(JSONObject().apply { put("recurringId", c.recurringId); put("month", c.month); put("status", c.status); put("actualAmountMinor", c.actualAmountMinor ?: JSONObject.NULL); put("lastPostponedDate", c.lastPostponedDate ?: JSONObject.NULL) }) }
        root.put("recurring_confirmations", confirmationsArray)

        val catsArray = JSONArray()
        allCats.forEach { c -> catsArray.put(JSONObject().apply { put("name", c.name); put("type", c.type.name) }) }
        root.put("categories", catsArray)

        val goalsArray = JSONArray()
        allGoals.forEach { g -> goalsArray.put(JSONObject().apply { put("id", g.id); put("name", g.name); put("targetAmountMinor", g.targetAmountMinor); put("currentAmountMinor", g.currentAmountMinor) }) }
        root.put("goals", goalsArray)

        val plansArray = JSONArray()
        allPlans.forEach { p -> plansArray.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("type", p.type.name); put("amountMinor", p.amountMinor); put("startMonth", p.startMonth); put("endMonth", p.endMonth); put("isFixed", p.isFixed) }) }
        root.put("plan_items", plansArray)

        val forecastArray = JSONArray()
        allForecasts.forEach { f -> forecastArray.put(JSONObject().apply { put("id", f.id); put("name", f.name); put("months", f.months); put("startMonth", f.startMonth); put("createdAt", f.createdAt) }) }
        root.put("forecasts", forecastArray)

        val fiArray = JSONArray()
        allForecastItems.forEach { i -> fiArray.put(JSONObject().apply { put("id", i.id); put("forecastId", i.forecastId); put("name", i.name); put("type", i.type.name); put("amountMinor", i.amountMinor); put("currency", i.currency); put("originalAmountMinor", i.originalAmountMinor ?: JSONObject.NULL); put("startMonth", i.startMonth); put("endMonth", i.endMonth); put("isFixed", i.isFixed) }) }
        root.put("forecast_items", fiArray)

        val fdArray = JSONArray()
        allForecastDeposits.forEach { d -> fdArray.put(JSONObject().apply { put("id", d.id); put("forecastId", d.forecastId); put("name", d.name); put("initialAmountMinor", d.initialAmountMinor); put("currency", d.currency); put("originalInitialAmountMinor", d.originalInitialAmountMinor ?: JSONObject.NULL); put("annualRatePercent", d.annualRatePercent); put("startMonth", d.startMonth); put("endMonth", d.endMonth); put("monthlyContributionMinor", d.monthlyContributionMinor); put("monthlyWithdrawalMinor", d.monthlyWithdrawalMinor); put("capitalization", d.capitalization.name) }) }
        root.put("forecast_deposits", fdArray)

        val opsArray = JSONArray()
        allDepositOps.forEach { o -> opsArray.put(JSONObject().apply { put("id", o.id); put("depositId", o.depositId); put("month", o.month); put("amountMinor", o.amountMinor); put("description", o.description) }) }
        root.put("forecast_deposit_ops", opsArray)

        return root.toString(2)
    }

    fun createBackup(password: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = buildBackupJson()
                val encrypted = withContext(Dispatchers.Default) { BackupEncryption.encrypt(jsonString, password) }
                onResult(encrypted)
            } catch (e: Exception) { e.printStackTrace(); onResult(null) }
        }
    }

    private suspend fun applyBackupJson(root: JSONObject) {
        val txsList = mutableListOf<Tx>()
        root.optJSONArray("txs")?.let { txsArray ->
            for (i in 0 until txsArray.length()) {
                val o = txsArray.getJSONObject(i)
                txsList.add(Tx(o.optLong("id"), o.getLong("amountMinor"), TxType.valueOf(o.getString("type")),
                    if (o.isNull("cardId")) null else o.getString("cardId"), if (o.isNull("toCardId")) null else o.optString("toCardId"),
                    if (o.isNull("toAmountMinor")) null else o.optLong("toAmountMinor"), o.optString("description"), o.optString("category"),
                    o.getLong("timestamp"), o.optString("source"), o.optString("currency", "UZS"), if (o.isNull("photoPath")) null else o.getString("photoPath")))
            }
        }

        val debtsList = mutableListOf<DebtEntity>()
        root.optJSONArray("debts")?.let { debtsArray ->
            for (i in 0 until debtsArray.length()) {
                val o = debtsArray.getJSONObject(i)
                debtsList.add(DebtEntity(o.optLong("id"), o.getString("personName"), o.getLong("amountMinor"), DebtType.valueOf(o.getString("type")), o.getLong("dateMillis"), o.optBoolean("isPaid"), o.optString("description"), o.optString("currency", "UZS")))
            }
        }

        val rulesList = mutableListOf<CustomKeywordRule>()
        root.optJSONArray("custom_rules")?.let { rulesArray ->
            for (i in 0 until rulesArray.length()) {
                val o = rulesArray.getJSONObject(i)
                rulesList.add(CustomKeywordRule(o.optLong("id"), o.getString("keyword"), o.getString("category"), TxType.valueOf(o.getString("type"))))
            }
        }

        val cardsList = mutableListOf<CardEntity>()
        root.optJSONArray("cards")?.let { cardsArray ->
            for (i in 0 until cardsArray.length()) {
                val o = cardsArray.getJSONObject(i)
                cardsList.add(CardEntity(o.getString("id"), o.getString("last4"), o.getString("name"), o.getString("currency"), o.getLong("initialBalanceMinor")))
            }
        }

        val budgetsList = mutableListOf<BudgetEntity>()
        root.optJSONArray("budgets")?.let { budgetsArray ->
            for (i in 0 until budgetsArray.length()) {
                val o = budgetsArray.getJSONObject(i)
                budgetsList.add(BudgetEntity(o.getString("category"), o.getLong("limitMinor")))
            }
        }

        val recurringList = mutableListOf<RecurringEntity>()
        root.optJSONArray("recurring")?.let { recurringArray ->
            for (i in 0 until recurringArray.length()) {
                val o = recurringArray.getJSONObject(i)
                recurringList.add(RecurringEntity(o.optLong("id"), o.getLong("amountMinor"), TxType.valueOf(o.getString("type")), if (o.isNull("cardId")) null else o.getString("cardId"), o.optString("description"), o.optString("category"), o.getInt("dayOfMonth"), o.optString("lastExecutedMonth")))
            }
        }

        val confirmationsList = mutableListOf<RecurringConfirmationEntity>()
        root.optJSONArray("recurring_confirmations")?.let { confirmationsArray ->
            for (i in 0 until confirmationsArray.length()) {
                val o = confirmationsArray.getJSONObject(i)
                confirmationsList.add(RecurringConfirmationEntity(o.getLong("recurringId"), o.getString("month"), o.getString("status"), if (o.isNull("actualAmountMinor")) null else o.optLong("actualAmountMinor"), if (o.isNull("lastPostponedDate")) null else o.optString("lastPostponedDate")))
            }
        }

        val catsList = mutableListOf<CategoryEntity>()
        root.optJSONArray("categories")?.let { catsArray ->
            for (i in 0 until catsArray.length()) {
                val o = catsArray.getJSONObject(i)
                catsList.add(CategoryEntity(o.getString("name"), TxType.valueOf(o.getString("type"))))
            }
        }

        val goalsList = mutableListOf<SavingsGoalEntity>()
        root.optJSONArray("goals")?.let { goalsArray ->
            for (i in 0 until goalsArray.length()) {
                val o = goalsArray.getJSONObject(i)
                goalsList.add(SavingsGoalEntity(o.optLong("id"), o.getString("name"), o.getLong("targetAmountMinor"), o.getLong("currentAmountMinor")))
            }
        }

        val plansList = mutableListOf<PlanItemEntity>()
        root.optJSONArray("plan_items")?.let { plansArray ->
            for (i in 0 until plansArray.length()) {
                val o = plansArray.getJSONObject(i)
                plansList.add(PlanItemEntity(o.optLong("id"), o.getString("name"), TxType.valueOf(o.getString("type")), o.getLong("amountMinor"), o.getString("startMonth"), o.getString("endMonth"), o.optBoolean("isFixed", true)))
            }
        }

        val forecastList = mutableListOf<ForecastPlanEntity>()
        root.optJSONArray("forecasts")?.let { forecastArray ->
            for (i in 0 until forecastArray.length()) {
                val o = forecastArray.getJSONObject(i)
                forecastList.add(ForecastPlanEntity(o.optLong("id"), o.getString("name"), o.getInt("months"), o.getString("startMonth"), o.optLong("createdAt", System.currentTimeMillis())))
            }
        }

        val fiList = mutableListOf<ForecastItemEntity>()
        root.optJSONArray("forecast_items")?.let { fiArray ->
            for (i in 0 until fiArray.length()) {
                val o = fiArray.getJSONObject(i)
                fiList.add(ForecastItemEntity(o.optLong("id"), o.getLong("forecastId"), o.getString("name"), TxType.valueOf(o.getString("type")), o.getLong("amountMinor"), o.optString("currency", "UZS"), if (o.isNull("originalAmountMinor")) null else o.optLong("originalAmountMinor"), o.getString("startMonth"), o.getString("endMonth"), o.optBoolean("isFixed", true)))
            }
        }

        val fdList = mutableListOf<ForecastDepositEntity>()
        root.optJSONArray("forecast_deposits")?.let { fdArray ->
            for (i in 0 until fdArray.length()) {
                val o = fdArray.getJSONObject(i)
                val cap = try { CapitalizationType.valueOf(o.optString("capitalization", "MONTHLY")) } catch (e: Exception) { CapitalizationType.MONTHLY }
                fdList.add(ForecastDepositEntity(o.optLong("id"), o.getLong("forecastId"), o.getString("name"), o.getLong("initialAmountMinor"), o.optString("currency", "UZS"), if (o.isNull("originalInitialAmountMinor")) null else o.optLong("originalInitialAmountMinor"), o.getDouble("annualRatePercent"), o.getString("startMonth"), o.getString("endMonth"), o.optLong("monthlyContributionMinor", 0L), o.optLong("monthlyWithdrawalMinor", 0L), cap))
            }
        }

        val opsList = mutableListOf<ForecastDepositOpEntity>()
        root.optJSONArray("forecast_deposit_ops")?.let { opsArray ->
            for (i in 0 until opsArray.length()) {
                val o = opsArray.getJSONObject(i)
                opsList.add(ForecastDepositOpEntity(o.optLong("id"), o.getLong("depositId"), o.getString("month"), o.getLong("amountMinor"), o.optString("description")))
            }
        }

        db.withTransaction {
            dao.clearTxs(); dao.clearDebts(); dao.clearCustomRules(); dao.clearCards(); dao.clearBudgets(); dao.clearRecurring()
            dao.clearConfirmations(); dao.clearCategories(); dao.clearGoals(); dao.clearPlanItems(); dao.clearForecasts()

            dao.insertAll(txsList)
            debtsList.forEach { dao.upsertDebt(it) }
            rulesList.forEach { dao.upsertCustomRule(it) }
            cardsList.forEach { dao.upsertCard(it) }
            budgetsList.forEach { dao.upsertBudget(it) }
            recurringList.forEach { dao.upsertRecurring(it) }
            confirmationsList.forEach { dao.upsertConfirmation(it) }
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
                val jsonString = withContext(Dispatchers.Default) { BackupEncryption.decrypt(data, password) }
                val root = JSONObject(jsonString)
                applyBackupJson(root)
                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    fun restoreBackupLegacy(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                applyBackupJson(root)
                ensureCashAccount()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }
}
