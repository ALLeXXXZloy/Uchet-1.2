package uz.uchet

object MerchantCategorizer {

    fun getCategory(
        merchantName: String?,
        availableCategories: List<String>,
        customRules: List<CustomKeywordRule> = emptyList()   // ★ НОВОЕ
    ): String? {
        if (merchantName.isNullOrBlank()) return null
        val name = merchantName.lowercase()

        // ★ 1. Пользовательские (выученные) правила — приоритетнее стандартных
        for (rule in customRules) {
            if (name.contains(rule.keyword.lowercase())) {
                // Возвращаем точное совпадение из списка категорий,
                // иначе — то, что записано в правиле
                return availableCategories.find { it == rule.category } ?: rule.category
            }
        }

        // 2. Стандартные правила (без изменений)
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
