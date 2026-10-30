package uz.uchet

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class TxType(val label: String) {
    INCOME("Доход"),
    EXPENSE("Расход"),
    TRANSFER("Перевод себе"),
    ADJUSTMENT("Корректировка")
}

enum class CapitalizationType {
    MONTHLY,
    DAILY,
    END_OF_TERM
}

@Entity(
    tableName = "txs",
    indices = [
        Index(value = ["timestamp"], name = "index_txs_timestamp"),
        Index(value = ["cardId"],    name = "index_txs_cardId"),
        Index(value = ["toCardId"],  name = "index_txs_toCardId"),
        Index(value = ["category"],  name = "index_txs_category")
    ]
)
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardId: String? = null,
    val toCardId: String? = null,
    val toAmountMinor: Long? = null,
    val description: String = "",
    val category: String = "",
    val timestamp: Long,
    val source: String = "MANUAL",
    val currency: String = "UZS",
    val photoPath: String? = null
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val last4: String,
    val name: String,
    val currency: String,
    val initialBalanceMinor: Long
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitMinor: Long
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardId: String? = null,
    val description: String = "",
    val category: String = "",
    val dayOfMonth: Int,
    val lastExecutedMonth: String? = null
)

// Статусы подтверждения регулярных платежей.
// Используется «Стандартным прогнозом»: при наступлении даты регулярной операции
// пользователю предлагается подтвердить сумму или отложить на завтра.
@Entity(tableName = "recurring_confirmations", primaryKeys = ["recurringId", "month"])
data class RecurringConfirmationEntity(
    val recurringId: Long,
    val month: String,                      // "YYYY-MM"
    val status: String,                     // "CONFIRMED", "POSTPONED"
    val actualAmountMinor: Long? = null,
    val lastPostponedDate: String? = null   // "YYYY-MM-DD"
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val type: TxType
)

@Entity(tableName = "goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmountMinor: Long,
    val currentAmountMinor: Long = 0
)

@Entity(tableName = "rates")
data class ExchangeRateEntity(
    @PrimaryKey val currency: String,
    val rateToUzs: Double
)

@Entity(tableName = "plan_items")
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxType,
    val amountMinor: Long,
    val startMonth: String,
    val endMonth: String,
    val isFixed: Boolean = true
)

@Entity(tableName = "forecasts")
data class ForecastPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val months: Int,
    val startMonth: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "forecast_items",
    foreignKeys = [
        ForeignKey(
            entity = ForecastPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["forecastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["forecastId"], name = "index_forecast_items_forecastId")]
)
data class ForecastItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastId: Long,
    val name: String,
    val type: TxType,
    val amountMinor: Long,
    val currency: String = "UZS",
    val originalAmountMinor: Long? = null,
    val startMonth: String,
    val endMonth: String,
    val isFixed: Boolean = true
)

@Entity(
    tableName = "forecast_deposits",
    foreignKeys = [
        ForeignKey(
            entity = ForecastPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["forecastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["forecastId"], name = "index_forecast_deposits_forecastId")]
)
data class ForecastDepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forecastId: Long,
    val name: String,
    val initialAmountMinor: Long,
    val currency: String = "UZS",
    val originalInitialAmountMinor: Long? = null,
    val annualRatePercent: Double,
    val startMonth: String,
    val endMonth: String,
    val monthlyContributionMinor: Long = 0,
    val monthlyWithdrawalMinor: Long = 0,
    val capitalization: CapitalizationType = CapitalizationType.MONTHLY
)

@Entity(
    tableName = "forecast_deposit_ops",
    foreignKeys = [
        ForeignKey(
            entity = ForecastDepositEntity::class,
            parentColumns = ["id"],
            childColumns = ["depositId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["depositId"], name = "index_forecast_deposit_ops_depositId")]
)
data class ForecastDepositOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val depositId: Long,
    val month: String,
    val amountMinor: Long,
    val description: String = ""
)

@Dao
interface AppDao {
    @Query("SELECT * FROM txs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observe(start: Long, end: Long): Flow<List<Tx>>

    @Query("SELECT * FROM txs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Tx>>

    @Query("SELECT * FROM txs ORDER BY timestamp DESC")
    suspend fun getAllTxs(): List<Tx>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: Tx)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<Tx>)

    @Delete
    suspend fun delete(tx: Tx)

    @Query("SELECT * FROM cards")
    fun observeCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("UPDATE txs SET cardId = NULL WHERE cardId = :cardId")
    suspend fun unlinkCardTransactions(cardId: String)

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("SELECT * FROM recurring")
    fun observeRecurring(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun getRecurringList(): List<RecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurring(r: RecurringEntity)

    @Delete
    suspend fun deleteRecurring(r: RecurringEntity)

    // --- Recurring Confirmations ---
    @Query("SELECT * FROM recurring_confirmations WHERE recurringId = :id AND month = :month")
    suspend fun getConfirmation(id: Long, month: String): RecurringConfirmationEntity?

    @Query("SELECT * FROM recurring_confirmations")
    fun observeConfirmations(): Flow<List<RecurringConfirmationEntity>>

    @Query("SELECT * FROM recurring_confirmations")
    suspend fun getAllConfirmations(): List<RecurringConfirmationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfirmation(conf: RecurringConfirmationEntity)

    @Query("DELETE FROM recurring_confirmations WHERE recurringId = :recurringId")
    suspend fun deleteConfirmationsForRecurring(recurringId: Long)

    @Query("DELETE FROM recurring_confirmations")
    suspend fun clearConfirmations()

    // --- Categories ---
    @Query("SELECT * FROM categories")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesList(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategory(name: String)

    @Query("UPDATE txs SET category = :newCategory WHERE category = :oldCategory")
    suspend fun replaceCategory(oldCategory: String, newCategory: String)

    @Query("SELECT * FROM goals")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    @Query("SELECT * FROM rates")
    fun observeRates(): Flow<List<ExchangeRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: ExchangeRateEntity)

    @Query("SELECT * FROM plan_items")
    fun observePlanItems(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items")
    suspend fun getPlanItemsList(): List<PlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanItem(item: PlanItemEntity)

    @Query("DELETE FROM plan_items WHERE id = :id")
    suspend fun deletePlanItem(id: Long)

    @Query("DELETE FROM plan_items")
    suspend fun clearPlanItems()

    @Query("SELECT * FROM forecasts ORDER BY createdAt DESC")
    fun observeForecasts(): Flow<List<ForecastPlanEntity>>

    @Query("SELECT * FROM forecasts")
    suspend fun getForecastsList(): List<ForecastPlanEntity>

    @Query("SELECT * FROM forecasts WHERE id = :id")
    suspend fun getForecastById(id: Long): ForecastPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecast(f: ForecastPlanEntity): Long

    @Query("DELETE FROM forecasts WHERE id = :id")
    suspend fun deleteForecast(id: Long)

    @Query("DELETE FROM forecasts")
    suspend fun clearForecasts()

    @Query("SELECT * FROM forecast_items WHERE forecastId = :forecastId")
    fun observeForecastItems(forecastId: Long): Flow<List<ForecastItemEntity>>

    @Query("SELECT * FROM forecast_items")
    suspend fun getAllForecastItems(): List<ForecastItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecastItem(item: ForecastItemEntity)

    @Query("DELETE FROM forecast_items WHERE id = :id")
    suspend fun deleteForecastItem(id: Long)

    @Query("SELECT * FROM forecast_deposits WHERE forecastId = :forecastId")
    fun observeForecastDeposits(forecastId: Long): Flow<List<ForecastDepositEntity>>

    @Query("SELECT * FROM forecast_deposits")
    suspend fun getAllForecastDeposits(): List<ForecastDepositEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecastDeposit(d: ForecastDepositEntity)

    @Query("DELETE FROM forecast_deposits WHERE id = :id")
    suspend fun deleteForecastDeposit(id: Long)

    @Query("SELECT * FROM forecast_deposit_ops WHERE depositId = :depositId ORDER BY month ASC")
    fun observeDepositOps(depositId: Long): Flow<List<ForecastDepositOpEntity>>

    @Query("SELECT * FROM forecast_deposit_ops WHERE depositId IN (SELECT id FROM forecast_deposits WHERE forecastId = :forecastId)")
    fun observeAllOpsForForecast(forecastId: Long): Flow<List<ForecastDepositOpEntity>>

    @Query("SELECT * FROM forecast_deposit_ops")
    suspend fun getAllDepositOps(): List<ForecastDepositOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDepositOp(op: ForecastDepositOpEntity)

    @Query("DELETE FROM forecast_deposit_ops WHERE id = :id")
    suspend fun deleteDepositOp(id: Long)

    @Query("DELETE FROM txs")
    suspend fun clearTxs()

    @Query("DELETE FROM cards")
    suspend fun clearCards()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM recurring")
    suspend fun clearRecurring()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM goals")
    suspend fun clearGoals()
}

@Database(
    entities = [
        Tx::class, CardEntity::class, BudgetEntity::class, RecurringEntity::class,
        RecurringConfirmationEntity::class,
        CategoryEntity::class, SavingsGoalEntity::class, ExchangeRateEntity::class,
        PlanItemEntity::class,
        ForecastPlanEntity::class, ForecastItemEntity::class,
        ForecastDepositEntity::class, ForecastDepositOpEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDb? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_txs_timestamp` ON `txs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_txs_cardId`    ON `txs` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_txs_toCardId`  ON `txs` (`toCardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_txs_category`  ON `txs` (`category`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txs ADD COLUMN toAmountMinor INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`startMonth` TEXT NOT NULL, " +
                        "`endMonth` TEXT NOT NULL, " +
                        "`isFixed` INTEGER NOT NULL" +
                    ")"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forecasts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `months` INTEGER NOT NULL,
                        `startMonth` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forecast_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `forecastId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `startMonth` TEXT NOT NULL,
                        `endMonth` TEXT NOT NULL,
                        `isFixed` INTEGER NOT NULL,
                        FOREIGN KEY(`forecastId`) REFERENCES `forecasts`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forecast_deposits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `forecastId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `initialAmountMinor` INTEGER NOT NULL,
                        `annualRatePercent` REAL NOT NULL,
                        `startMonth` TEXT NOT NULL,
                        `endMonth` TEXT NOT NULL,
                        `monthlyContributionMinor` INTEGER NOT NULL,
                        `monthlyWithdrawalMinor` INTEGER NOT NULL,
                        FOREIGN KEY(`forecastId`) REFERENCES `forecasts`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forecast_deposit_ops` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `depositId` INTEGER NOT NULL,
                        `month` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        FOREIGN KEY(`depositId`) REFERENCES `forecast_deposits`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_items_forecastId` ON `forecast_items` (`forecastId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_deposits_forecastId` ON `forecast_deposits` (`forecastId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_deposit_ops_depositId` ON `forecast_deposit_ops` (`depositId`)")

                var hasOldData = false
                db.query("SELECT * FROM plan_items").use { c -> if (c.moveToFirst()) hasOldData = true }
                if (hasOldData) {
                    val now = System.currentTimeMillis()
                    db.execSQL(
                        "INSERT INTO forecasts (name, months, startMonth, createdAt) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>("Мой прогноз", 12, "2026-01", now)
                    )
                    val forecastId = db.query("SELECT last_insert_rowid()").use { c -> c.moveToFirst(); c.getLong(0) }
                    db.query("SELECT * FROM plan_items").use { cursor ->
                        val n = cursor.getColumnIndexOrThrow("name")
                        val t = cursor.getColumnIndexOrThrow("type")
                        val a = cursor.getColumnIndexOrThrow("amountMinor")
                        val s = cursor.getColumnIndexOrThrow("startMonth")
                        val e = cursor.getColumnIndexOrThrow("endMonth")
                        val f = cursor.getColumnIndexOrThrow("isFixed")
                        while (cursor.moveToNext()) {
                            db.execSQL(
                                "INSERT INTO forecast_items (forecastId, name, type, amountMinor, startMonth, endMonth, isFixed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                arrayOf<Any>(forecastId, cursor.getString(n), cursor.getString(t),
                                    cursor.getLong(a), cursor.getString(s), cursor.getString(e), cursor.getInt(f))
                            )
                        }
                    }
                }
                db.execSQL("DROP TABLE IF EXISTS `plan_items`")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE forecast_deposits ADD COLUMN capitalization TEXT NOT NULL DEFAULT 'MONTHLY'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE forecast_items ADD COLUMN currency TEXT NOT NULL DEFAULT 'UZS'")
                db.execSQL("ALTER TABLE forecast_items ADD COLUMN originalAmountMinor INTEGER DEFAULT NULL")

                db.execSQL("ALTER TABLE forecast_deposits ADD COLUMN currency TEXT NOT NULL DEFAULT 'UZS'")
                db.execSQL("ALTER TABLE forecast_deposits ADD COLUMN originalInitialAmountMinor INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recurring_confirmations` (
                        `recurringId` INTEGER NOT NULL,
                        `month` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `actualAmountMinor` INTEGER DEFAULT NULL,
                        `lastPostponedDate` TEXT DEFAULT NULL,
                        PRIMARY KEY(`recurringId`, `month`)
                    )
                """)
            }
        }

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "uchet_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
