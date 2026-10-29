package uz.uchet

import androidx.compose.ui.graphics.Color
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Форматы ----------
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

// ---------- Цвета ----------
val GREEN = Color(0xFF2E7D32)
val RED = Color(0xFFC62828)
val YELLOW = Color(0xFFF9A825)
val BLUE = Color(0xFF1976D2)

// ---------- Категории по умолчанию ----------
val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "🍔 Еда", "🚕 Транспорт", "🏠 Жилье", "🛒 Покупки",
    "💊 Здоровье", "🎉 Развлечения", "📦 Другое"
)
val DEFAULT_INCOME_CATEGORIES = listOf(
    "💵 Зарплата", "🎁 Подарок", "💸 Перевод",
    "📈 Инвестиции", "📦 Другое"
)

// ---------- Enums приложения ----------
enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage { RU, UZ, EN, TG, KAA, KK }

// ---------- Локализация типов операций ----------
fun TxType.localizedLabel(lang: AppLanguage): String {
    return when (this) {
        TxType.INCOME -> Translations.t("income", lang)
        TxType.EXPENSE -> Translations.t("expense", lang)
        TxType.TRANSFER -> Translations.t("transfer", lang)
        TxType.ADJUSTMENT -> Translations.t("adjustment", lang)
    }
}

// ---------- Названия месяцев ----------
fun getMonthName(month: Int, lang: AppLanguage): String {
    val ru = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    val uz = listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun", "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr")
    val en = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val tg = listOf("Январ", "Феврал", "Март", "Апрел", "Май", "Июн", "Июл", "Август", "Сентябр", "Октябр", "Ноябр", "Декабр")
    val kaa = listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun", "Iyul", "Avgust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr")
    val kk = listOf("Қаңтар", "Ақпан", "Наурыз", "Сәуір", "Мамыр", "Маусым", "Шілде", "Тамыз", "Қыркүйек", "Қазан", "Қараша", "Желтоқсан")

    return when (lang) {
        AppLanguage.RU -> ru[month - 1]
        AppLanguage.UZ -> uz[month - 1]
        AppLanguage.EN -> en[month - 1]
        AppLanguage.TG -> tg[month - 1]
        AppLanguage.KAA -> kaa[month - 1]
        AppLanguage.KK -> kk[month - 1]
    }
}

// ---------- Сокращение названий банков ----------
fun getShortBankName(name: String): String {
    val vowels = "aeiouyаеёиоуыэюяAEIOUYАЕЁИОУЫЭЮЯ"
    val consonants = name.filter { it.isLetter() && it !in vowels }
    return if (consonants.length >= 4) {
        consonants.take(4).lowercase().replaceFirstChar { it.uppercase() }
    } else if (consonants.isNotEmpty()) {
        consonants.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        "Bank"
    }
}

// ---------- Красивое форматирование денег ----------
fun Long.money(): String {
    val abs = kotlin.math.abs(this)
    val whole = String.format(Locale.US, "%,d", abs / 100).replace(',', ' ')
    val frac = abs % 100
    val sign = if (this < 0) "-" else ""
    return "$sign$whole.%02d".format(frac)
}
