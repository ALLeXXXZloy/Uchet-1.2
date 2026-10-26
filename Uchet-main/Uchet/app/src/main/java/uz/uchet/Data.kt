package uz.uchet

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TxType(val label: String) {
    INCOME("Доход"),
    EXPENSE("Расход"),
    TRANSFER("Перевод")
}

@Entity(tableName = "txs")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val type: TxType,
    val cardId: String? = null,
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

@Dao
interface Dao {
    @Query("SELECT * FROM txs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observe(start: Long, end: Long): Flow<List<Tx>>

    @Query("SELECT * FROM txs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Tx>>

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

    @Query("SELECT * FROM categories")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesList(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategory(name: String)

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

    @Query("DELETE FROM txs")
    suspend fun clearTxs()

    @Query("DELETE FROM cards")
    suspend fun clearCards()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM recurring")
    suspend fun clearRecurring()
}

@Database(
    entities = [Tx::class, CardEntity::class, BudgetEntity::class, RecurringEntity::class, CategoryEntity::class, SavingsGoalEntity::class, ExchangeRateEntity::class],
    version = 1, // Откатили версию
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): Dao

    companion object {
        @Volatile
        private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "uchet_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
