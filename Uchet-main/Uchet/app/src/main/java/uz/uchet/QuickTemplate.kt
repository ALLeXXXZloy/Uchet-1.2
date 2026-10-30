package uz.uchet

// Модель для быстрой кнопки на главном экране
data class QuickTemplate(
    val category: String,
    val averageAmountMinor: Long,
    val type: TxType
)
