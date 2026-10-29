package uz.uchet

object VoiceParser {
    data class VoiceTxResult(
        val amountMinor: Long?,
        val type: TxType?,
        val cardLast4: String?,
        val isCash: Boolean,
        val categoryName: String?,
        val description: String
    )

    // НОВОЕ ПОЛЕ balanceMinor
    data class VoiceCardResult(
        val bankName: String?,
        val last4: String,
        val balanceMinor: Long? = null
    )

    private val ones = mapOf(
        "ноль" to 0L, "один" to 1L, "одна" to 1L, "два" to 2L, "две" to 2L, "три" to 3L,
        "четыре" to 4L, "пять" to 5L, "шесть" to 6L, "семь" to 7L, "восемь" to 8L, "девять" to 9L,
        "nol" to 0L, "bir" to 1L, "ikki" to 2L, "uch" to 3L, "to'rt" to 4L, "tort" to 4L,
        "besh" to 5L, "olti" to 6L, "yetti" to 7L, "sakkiz" to 8L, "to'qqiz" to 9L, "toqqiz" to 9L
    )
    private val tens = mapOf(
        "десять" to 10L, "двадцать" to 20L, "тридцать" to 30L, "сорок" to 40L,
        "пятьдесят" to 50L, "шестьдесят" to 60L, "семьдесят" to 70L, "восемьдесят" to 80L,
        "девяносто" to 90L,
        "o'n" to 10L, "on" to 10L, "yigirma" to 20L, "o'ttiz" to 30L, "ottiz" to 30L,
        "qirq" to 40L, "ellik" to 50L, "oltmish" to 60L, "yetmish" to 70L,
        "sakson" to 80L, "to'qson" to 90L, "toqson" to 90L
    )
    private val hundreds = mapOf(
        "сто" to 100L, "двести" to 200L, "триста" to 300L, "четыреста" to 400L,
        "пятьсот" to 500L, "шестьсот" to 600L, "семьсот" to 700L, "восемьсот" to 800L,
        "девятьсот" to 900L, "yuz" to 100L
    )
    private val multipliers = mapOf(
        "тысяча" to 1000L, "тысячи" to 1000L, "тысяч" to 1000L, "тыс" to 1000L,
        "минг" to 1000L, "ming" to 1000L,
        "миллион" to 1000000L, "миллиона" to 1000000L, "миллионов" to 1000000L, "млн" to 1000000L,
        "million" to 1000000L, "mln" to 1000000L
    )

    fun parseTransaction(text: String, expenseCategories: List<String>, incomeCategories: List<String>): VoiceTxResult {
        val lower = text.lowercase().replace("ё", "е")

        val expenseKw = listOf("потратил", "потратила", "купил", "купила", "оплатил", "оплатила",
            "расход", "оплата", "трата", "sarfladim", "sotib oldim", "to'ladim", "toladim", "xarajat", "harajat")
        val incomeKw = listOf("получил", "получила", "зарплата", "доход", "приход", "заработал",
            "заработала", "oldim", "maosh", "daromad", "kirim", "ishladim")
        val type: TxType? = when {
            expenseKw.any { lower.contains(it) } -> TxType.EXPENSE
            incomeKw.any { lower.contains(it) } -> TxType.INCOME
            else -> null
        }

        val isCash = listOf("наличные", "наличкой", "наличными", "кэш", "naqd", "нал ").any { lower.contains(it) }
        val cardLast4 = Regex("""(?:карт[а-я]*|karta[s]?)\s+(\d{4})""").find(lower)?.groupValues?.get(1)

        val amountMinor = parseAmount(lower)
        val categoryList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        val categoryName = findCategory(lower, categoryList)

        return VoiceTxResult(amountMinor, type, cardLast4, isCash, categoryName, text.trim())
    }

    fun parseCard(text: String): VoiceCardResult? {
        val lower = text.lowercase()
        val m = Regex("""\b(\d{4})\b""").find(lower) ?: return null
        val banks = listOf(
            "kapitalbank", "uzum", "anorbank", "tbc", "humo", "uzcard",
            "aloqa", "agrobank", "sberbank", "ipoteka", "trast", "asakabank"
        )
        val bank = banks.firstOrNull { lower.contains(it) }
        
        // ПЫТАЕМСЯ ИЗВЛЕЧЬ ОСТАТОК
        val balanceMinor = parseAmount(lower)
        
        return VoiceCardResult(bank, m.groupValues[1], balanceMinor)
    }

    private fun parseAmount(text: String): Long? {
        val clean = text
            .replace(Regex("""(?:карт[а-я]*|karta[s]?)\s+\d{4}"""), "")
            .replace(Regex("""\b\d{4}\b"""), "")

        // 1. Число + единица (500 тысяч, 1 миллион, 500 000 сум)
        val withUnit = Regex(
            """(\d[\d\s\u00A0]*)\s*(тысяч[а-я]*|тыс\b|минг|ming|миллион[а-я]*|млн|million|mln|сум|сўм|so['`]?m|som)""",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (withUnit != null) {
            val num = withUnit.groupValues[1].replace(Regex("[\\s\u00A0]"), "").toLongOrNull()
            if (num != null) {
                val u = withUnit.groupValues[2].lowercase()
                val mult = when {
                    u.startsWith("тыс") || u.startsWith("минг") || u.contains("ming") -> 1000L
                    u.startsWith("миллион") || u == "млн" || u.contains("million") || u.contains("mln") -> 1000000L
                    else -> 1L
                }
                return num * mult * 100
            }
        }

        // 2. Числа словами (пятьсот тысяч)
        val words = clean.split(Regex("[\\s,]+"))
        var total = 0L; var current = 0L; var found = false
        for (w in words) {
            val c = w.replace(Regex("[^\\p{L}']"), "")
            when {
                ones.containsKey(c) -> { current += ones[c]!!; found = true }
                tens.containsKey(c) -> { current += tens[c]!!; found = true }
                hundreds.containsKey(c) -> { current += hundreds[c]!!; found = true }
                multipliers.containsKey(c) -> {
                    total += (if (current == 0L) 1L else current) * multipliers[c]!!
                    current = 0L; found = true
                }
            }
        }
        total += current
        if (found && total > 0) return total * 100

        // 3. Просто число (с поддержкой пробела, точки, запятой как разделителя тысяч)
        val plainMatch = Regex("""(\d+(?:[.,\s\u00A0]\d{3})*)""").find(clean)
        if (plainMatch != null) {
            val cleaned = plainMatch.groupValues[1].replace(Regex("[.,\\s\u00A0]"), "")
            val parsed = cleaned.toLongOrNull()
            if (parsed != null && parsed.toString().length != 4) {
                return parsed * 100
            }
        }
        return null
    }

    private fun findCategory(text: String, categories: List<String>): String? {
        val map = mapOf(
            "🍔 Еда" to listOf("еда", "еду", "обед", "ужин", "завтрак", "кафе", "ресторан", "кофе", "перекус", "ovqat", "tushlik", "nonushta", "kafe", "restoran", "kofe"),
            "🚕 Транспорт" to listOf("такси", "транспорт", "автобус", "метро", "бензин", "бензина", "проезд", "taksi", "transport", "avtobus", "metro", "benzin"),
            "🏠 Жилье" to listOf("жилье", "жильё", "квартира", "аренда", "коммунал", "свет", "газ", "вода", "uy", "kvartira", "arenda", "kommunal"),
            "🛒 Покупки" to listOf("покупк", "магазин", "продукт", "xarid", "do'kon", "dokon", "mahsulot"),
            "💊 Здоровье" to listOf("здоровье", "аптека", "лекарств", "врач", "больниц", "salomatlik", "apteka", "dori", "shifokor"),
            "🎉 Развлечения" to listOf("развлечен", "кино", "игр", "отдых", "ko'ngilochar", "kongilochar", "kino", "o'yin", "oyin"),
            "💵 Зарплата" to listOf("зарплат", "аванс", "maosh", "oylik"),
            "🎁 Подарок" to listOf("подарок", "подар", "sovg'a", "sovga"),
            "💸 Перевод" to listOf("перевод", "перевел", "перевела", "o'tkazma", "otkazma", "perevod"),
            "📈 Инвестиции" to listOf("инвестиц", "дивиденд", "investitsiya", "dividend")
        )
        for (cat in categories) {
            val kws = map[cat] ?: continue
            if (kws.any { text.contains(it) }) return cat
        }
        return null
    }
}
