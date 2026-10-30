package uz.uchet

object MerchantCategorizer {

    fun getCategory(merchantName: String?, availableCategories: List<String>): String? {
        if (merchantName.isNullOrBlank()) return null
        val name = merchantName.lowercase()

        // Ищем первую подходящую категорию из доступных пользователю
        fun findCat(vararg keywords: String): String? {
            val match = keywords.any { name.contains(it) }
            if (!match) return null
            // Ищем категорию, которая содержит нужное слово (например, "Покупки")
            val target = keywords.last() // Используем последнее слово как маркер для поиска категории
            return availableCategories.find { it.contains(target, ignoreCase = true) }
        }

        return when {
            // 🛒 Покупки (Продукты, супермаркеты)
            name.contains("korzinka") || name.contains("makro") || name.contains("magnat") ||
            name.contains("uzum") || name.contains("apelsin") || name.contains("dilzar") ||
            name.contains("xalq retail") || name.contains("torg") || name.contains("market") -> 
                availableCategories.find { it.contains("Покупки", true) } ?: availableCategories.find { it.contains("Еда", true) }

            // 🍔 Еда (Кафе, рестораны, фастфуд)
            name.contains("kfc") || name.contains("lavash") || name.contains("pizza") ||
            name.contains("burger") || name.contains("coffee") || name.contains("choyxona") ||
            name.contains("osh") || name.contains("food") || name.contains("cafe") ->
                availableCategories.find { it.contains("Еда", true) }

            // 🚕 Транспорт (Такси, бензин, метро)
            name.contains("yandex") || name.contains("taxi") || name.contains("go ") ||
            name.contains("milliy") || name.contains("benzin") || name.contains("metan") ||
            name.contains("petrol") || name.contains("metro") -> 
                availableCategories.find { it.contains("Транспорт", true) }

            // 💊 Здоровье (Аптеки, клиники)
            name.contains("apteka") || name.contains("dorixona") || name.contains("shifo") ||
            name.contains("clinic") || name.contains("med") -> 
                availableCategories.find { it.contains("Здоровье", true) }

            // 🎉 Развлечения (Кино, игры, подписки)
            name.contains("cinema") || name.contains("kino") || name.contains("playstation") ||
            name.contains("steam") || name.contains("netflix") ->
                availableCategories.find { it.contains("Развлечения", true) }

            else -> null // Если не узнали бренд, оставляем выбор за пользователем
        }
    }
}
