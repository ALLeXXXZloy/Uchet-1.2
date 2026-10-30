// ... (начало файла без изменений до ForecastItemEntity)

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
    val amountMinor: Long, // ВСЕГДА в UZS (для стабильности расчетов)
    val currency: String = "UZS", // Исходная валюта ввода
    val originalAmountMinor: Long? = null, // Оригинальная сумма (для отображения)
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
    val initialAmountMinor: Long, // ВСЕГДА в UZS
    val currency: String = "UZS",
    val originalInitialAmountMinor: Long? = null,
    val annualRatePercent: Double,
    val startMonth: String,
    val endMonth: String,
    val monthlyContributionMinor: Long = 0, // В UZS
    val monthlyWithdrawalMinor: Long = 0, // В UZS
    val capitalization: CapitalizationType = CapitalizationType.MONTHLY
)

// ... (DAO без изменений)

@Database(
    entities = [
        Tx::class, CardEntity::class, BudgetEntity::class, RecurringEntity::class,
        CategoryEntity::class, SavingsGoalEntity::class, ExchangeRateEntity::class,
        PlanItemEntity::class,
        ForecastPlanEntity::class, ForecastItemEntity::class,
        ForecastDepositEntity::class, ForecastDepositOpEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    // ... (companion object)
        // НОВАЯ МИГРАЦИЯ
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE forecast_items ADD COLUMN currency TEXT NOT NULL DEFAULT 'UZS'")
                db.execSQL("ALTER TABLE forecast_items ADD COLUMN originalAmountMinor INTEGER DEFAULT NULL")
                
                db.execSQL("ALTER TABLE forecast_deposits ADD COLUMN currency TEXT NOT NULL DEFAULT 'UZS'")
                db.execSQL("ALTER TABLE forecast_deposits ADD COLUMN originalInitialAmountMinor INTEGER DEFAULT NULL")
            }
        }

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "uchet_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
