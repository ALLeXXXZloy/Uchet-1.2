package uz.uchet

object MerchantCategorizer {

    // ★ #1 + #7: фильтр по типу + самое длинное ключевое слово
    fun getCategory(
        merchantName: String?,
        availableCategories: List<String>,
        customRules: List<CustomKeywordRule> = emptyList(),
        expectedType: TxType = TxType.EXPENSE
    ): String? {
        if (merchantName.isNullOrBlank()) return null
        val name = merchantName.lowercase()

        // 1. Пользовательские правила — приоритетнее стандартных
        val matched = customRules
            .filter { it.type == expectedType && name.contains(it.keyword.lowercase()) }
            .maxByOrNull { it.keyword.length }
        if (matched != null) {
            return availableCategories.find { it == matched.category } ?: matched.category
        }

        // 2. Стандартные правила
        fun findCat(vararg keywords: String): String? {
            val match = keywords.any { name.contains(it) }
            if (!match) return null
            val target = keywords.last()
            return availableCategories.find { it.contains(target, ignoreCase = true) }
        }

        return when {
            name.contains("korzinka") || name.contains("makro") || name.contains("magnat") ||
            name.contains("uzum") || name.contains("apelsin") || name.contains("dilzar") ||
            name.contains("xalq retail") || name.contains("torg") || name.contains("market") ->
                availableCategories.find { it.contains("Покупки", true) } ?: availableCategories.find { it.contains("Еда", true) }

            name.contains("kfc") || name.contains("lavash") || name.contains("pizza") ||
            name.contains("burger") || name.contains("coffee") || name.contains("choyxona") ||
            name.contains("osh") || name.contains("food") || name.contains("cafe") ->
                availableCategories.find { it.contains("Еда", true) }

            name.contains("yandex") || name.contains("taxi") || name.contains("go ") ||
            name.contains("milliy") || name.contains("benzin") || name.contains("metan") ||
            name.contains("petrol") || name.contains("metro") ->
                availableCategories.find { it.contains("Транспорт", true) }

            name.contains("apteka") || name.contains("dorixona") || name.contains("shifo") ||
            name.contains("clinic") || name.contains("med") ->
                availableCategories.find { it.contains("Здоровье", true) }

            name.contains("cinema") || name.contains("kino") || name.contains("playstation") ||
            name.contains("steam") || name.contains("netflix") ->
                availableCategories.find { it.contains("Развлечения", true) }

            else -> null
        }
    }
}
