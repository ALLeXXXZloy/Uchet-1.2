package uz.uchet

import android.content.Context
import android.net.Uri
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SmsCategory { TRANSACTION, OTP, DEBT_REMINDER, ADVERTISING, PERSONAL }

data class BankTx(
    val amountMinor: Long, val type: TxType, val cardLast4: String?, val balanceMinor: Long?,
    val merchant: String, val category: String, val timestamp: Long, val sender: String,
    val rawBody: String, val currency: String = "UZS",
    val ruleAppliedId: Long? = null,
    val ruleAppliedKeyword: String? = null
)

object SmsParser {
    private val AMOUNT_RE = Regex("""([+-]?)(\d{1,3}(?:[ \u00A0,]\d{3})*|\d+)(?:[.,](\d{1,2}))?(?:\s*,\s*|\s+)(?:UZS\b|so[''`]?m\b|сум\b|sum\b|сўм)""", RegexOption.IGNORE_CASE)
    private val DATE_FORMATS = listOf(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    private val BALANCE_KEYWORDS = listOf("dostupno", "ostatok", "qoldiq", "qoldig'i", "balans", "balance", "баланс", "остаток", "доступно")
    private val INCOME_VERBS = listOf("popolnenie", "пополнение", "postuplenie", "поступление", "zachislenie", "зачисление", "kirim", "credit account", "credit part", "refund", "vozvrat", "возврат", "vnesenie nalichnykh", "vnosenie nalichnykh", "получение перевода", "perevod na kartu", "перевод на карту", "tushdi", "kelib tushdi", "keldi", "kirib keldi", "yetib keldi")
    private val EXPENSE_VERBS = listOf("oplata", "оплата", "spisanie", "списание", "pokupka", "покупка", "xarid", "snjatie nalichnih", "snyatiye", "снятие", "naqd yechish", "получение наличных", "poluchenie nalichnyh", "vidacha nalichnykh", "выдача наличных", "operaciya", "операция", "prinyata summa", "принята сумма", "otpravleno", "отправлено", "perevod s", "перевод с", "debit acc presentment", "yechildi", "yechib olindi", "списаны", "spisany")
    private val STRONG_DEBT = listOf("задолженность", "задолженност", "qarzdorlik", "qarzdor", "qarzingiz", "qarz mavjud", "просим оплатить", "оплатите", "to'lang", "to’lang", "тўланг", "погасите", "недостаточно средств", "не хватает средств", "номер заблокирован", "vaш номер заблокирован", "bloklandi", "расторжение договора", "shartnoma bekor", "kechik", "muddatli to'lov kuni", "muddatli to'lovni amalga oshiring", "eslatamiz", "просрочен", "просрочк", "мвд", "mib", "исполнительный", "ijro ishi", "prokuratura", "прокуратура", "sudga", "суд", "принудительн", "ispolnitelniy")
    private val COMPLETION_VERBS = listOf("spisano", "списано", "spisany", "списаны", "spisana", "списана", "yechildi", "yechib olindi", "вычтено", "снято", "zachisleno", "зачислено", "postupil", "поступило", "qabul qilindi", "amalga oshirildi", "выполнено")
    private val OTP_WARNINGS = listOf("никому не передавайте", "никому не сообщайте", "никому не говорите", "nikomu ne peredavayte", "nikomu ne soobshchayte", "nikomu ne govorite", "hech kimga bermang", "hech kimga aytmang", "kodni hech kimga", "не сообщайте код", "не передавайте код", "don't share", "не давайте код", "faqat firibgarlar", "только мошенники", "osteregaytes moshennikov", "остерегайтесь мошенников")
    private val OTP_MARKERS = listOf("ваш код", "код подтверждения", "код авторизации", "код для входа", "kod podtverzhdeniya", "kod dlya vhoda", "kod dlya vxoda", "tasdiqlash kodi", "kirish kodi", "verification code", "your code", "is your code", "one-time code", "одноразовый код", "пароль", "parol", "password", "pin code", "pin-kod")
    private val DEBT_MARKERS = STRONG_DEBT + listOf("закончились минуты", "закончились мб", "закончился трафик", "пакет заканчивается", "баланс счета", "баланс счёта", "минуты закончились", "предстоящий платеж", "долг", "должник", "yaqinlashib kelayotgan", "маблағ етарли эмас", "yetarli emas")
    private val AD_MARKERS = listOf("скидка", "chegirma", "акция", "aksiya", "тариф", "tarif", "подарок", "sovg'a", "sovga", "супер vip", "super vip", "подключите опцию", "подключите пакет", "переходите на", "выберите пакет", "новый тариф", "распродажа", "промокод", "promokod", "бонус", "bonus", "купите", "shop", "магазин", "market", "красивых номеров", "красивый номер", "не упустите", "shoshiling", "торопитесь", "успейте", "только сегодня", "только сейчас", "faqat bugun", "последний шанс", "предложение", "taklif", "instagram", "facebook", "telegram", "подписка", "регистрация на", "курс", "марафон", "вебинар", "опрос", "so'rovnoma", "недвижимость", "turar-joy", "квартира", "kvartira", "ипотека", "ipoteka", "автокредит", "mikrozaym", "микрозайм", "микрокредит", "займ", "kredit oling", "ставка", "гарантия", "garantiya", "визит", "tashrif", "бесплатно", "bepul", "розыгрыш", "выиграйте", "yutib oling", "приз", "sovrin")
    private val SERVICE_BLACKLIST = listOf("sms info", "sms narxi", "стоимость sms", "sms o'chirildi", "sms o’chirildi", "sms-info")

    fun classify(sender: String, body: String, timestamp: Long, customRules: List<CustomKeywordRule> = emptyList()): Pair<SmsCategory, BankTx?> {
        val lower = body.lowercase()

        val ignoredByRule = customRules.any { it.isIgnored && lower.contains(it.keyword) }
        if (ignoredByRule) return SmsCategory.ADVERTISING to null

        val tx = tryParseTransaction(sender, body, timestamp, lower, customRules)
        if (tx != null) return SmsCategory.TRANSACTION to tx

        if (OTP_WARNINGS.any { lower.contains(it) }) return SmsCategory.OTP to null
        val hasCompletion = COMPLETION_VERBS.any { lower.contains(it) }
        if (!hasCompletion && STRONG_DEBT.any { lower.contains(it) }) return SmsCategory.DEBT_REMINDER to null
        if (isWeakOtp(lower)) return SmsCategory.OTP to null
        if (DEBT_MARKERS.any { lower.contains(it) }) return SmsCategory.DEBT_REMINDER to null
        if (AD_MARKERS.count { lower.contains(it) } >= 2) return SmsCategory.ADVERTISING to null
        return SmsCategory.PERSONAL to null
    }

    // ★ T-006: сканирование по периоду
    fun scanInbox(context: Context, existing: List<Tx>, customRules: List<CustomKeywordRule>, daysBack: Int = 7): List<Tx> {
        val result = mutableListOf<Tx>()
        val fromMillis = System.currentTimeMillis() - daysBack.toLong() * 24L * 60L * 60L * 1000L
        val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), null, "date >= ?", arrayOf(fromMillis.toString()), "date DESC") ?: return result
        cursor.use { c ->
            val bodyIdx = c.getColumnIndex("body"); val dateIdx = c.getColumnIndex("date"); val addrIdx = c.getColumnIndex("address")
            var n = 0
            while (c.moveToNext() && n < 2000) {
                val body = c.getString(bodyIdx); if (body == null) { n++; continue }
                val date = c.getLong(dateIdx); val sender = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                val (cat, tx) = classify(sender, body, date, customRules)
                if (cat == SmsCategory.TRANSACTION && tx != null) {
                    val dup = existing.any { it.amountMinor == tx.amountMinor && kotlin.math.abs(it.timestamp - date) < 60_000 }
                    if (!dup) result.add(tx.toTx("SMS"))
                }
                n++
            }
        }
        return result
    }

    // ★ T-001: Парсинг PDF с координатами
    fun parsePdfLines(lines: List<PdfLine>, existing: List<Tx> = emptyList(), customRules: List<CustomKeywordRule> = emptyList()): List<Pair<Tx, PdfLine>> {
        val fullText = lines.joinToString("\n") { it.text }
        
        // Автоопределение банка по заголовку
        val isTez = fullText.contains("ВЫПИСКА ПО КАРТЕ") && fullText.contains("Номер карты клиента:")
        val isAnorbank = fullText.contains("ANOR BANK") || fullText.contains("ANORBANK")
        val isKapital = fullText.contains("KAPITALBANK")
        val isUzum = fullText.contains("UZUM BANK") || fullText.contains("Uzum Bank")

        val parsedWithCoords = when {
            isTez -> parseTezLines(lines, customRules)
            isAnorbank -> parseAnorbankLines(lines, customRules)
            isKapital -> parseKapitalLines(lines, customRules)
            isUzum -> parseUzumLines(lines, customRules)
            else -> parseGenericPdfLines(lines, customRules) // fallback
        }

        if (existing.isEmpty()) return parsedWithCoords
        return parsedWithCoords.filterNot { (newTx, _) -> 
            existing.any { old -> old.amountMinor == newTx.amountMinor && old.type == newTx.type && old.cardId == newTx.cardId && kotlin.math.abs(old.timestamp - newTx.timestamp) < 60_000L } 
        }
    }

    // ★ ПАРСЕР ANORBANK (суммы без копеек, + и -)
    private fun parseAnorbankLines(lines: List<PdfLine>, customRules: List<CustomKeywordRule>): List<Pair<Tx, PdfLine>> {
        val result = mutableListOf<Pair<Tx, PdfLine>>()
        val cardRegex = Regex("""Номер карты:\s*\d+\*+(\d{4})""")
        var cardLast4 = "0000"
        for (line in lines.take(30)) {
            cardRegex.find(line.text)?.let { cardLast4 = it.groupValues[1] }
        }
        val cardId = "${cardLast4}_UZS"
        
        // Регулярка для Anorbank: дата (dd.MM.yyyy HH:mm), номер док (может быть слитно с суммой), сумма (+/- 100 000), описание
        val txRegex = Regex("""(?<date>\d{2}\.\d{2}\.\d{4})\s+(?<time>\d{2}:\d{2})\s*\d*\s*(?<sign>[+-])\s*(?<amount>\d{1,3}(?:\s\d{3})*)\s*(?<desc>.+?)$""")

        for (line in lines) {
            val match = txRegex.find(line.text) ?: continue
            val dateStr = match.groups["date"]?.value ?: continue
            val timeStr = match.groups["time"]?.value ?: continue
            val sign = match.groups["sign"]?.value ?: ""
            val amountStr = match.groups["amount"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""

            val cleanAmount = amountStr.replace(" ", "")
            val amountMinor = (cleanAmount.toDoubleOrNull() ?: continue).times(100).toLong()
            val type = if (sign == "+") TxType.INCOME else TxType.EXPENSE
            val ts = try {
                LocalDateTime.parse("$dateStr $timeStr", DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }
            val category = guessCategory(desc.lowercase(), desc, customRules, type).category

            result.add(Tx(
                amountMinor = amountMinor, type = type, cardId = cardId, description = desc,
                category = category, timestamp = ts, source = "IMPORT", rawText = line.text
            ) to line)
        }
        return result
    }

    // ★ ПАРСЕР KAPITALBANK (суммы с копейками, иногда без +)
    private fun parseKapitalLines(lines: List<PdfLine>, customRules: List<CustomKeywordRule>): List<Pair<Tx, PdfLine>> {
        val result = mutableListOf<Pair<Tx, PdfLine>>()
        val cardRegex = Regex("""Номер карты:\s*\d+\*+(\d{4})""")
        var cardLast4 = "0000"
        for (line in lines.take(30)) {
            cardRegex.find(line.text)?.let { cardLast4 = it.groupValues[1] }
        }
        val cardId = "${cardLast4}_UZS"

        // Регулярка: дата (dd.MM.yyyy), сумма (может быть без +, но с -), описание
        val txRegex = Regex("""(?<date>\d{2}\.\d{2}\.\d{4})\s+(?<amount>[+-]?\d{1,3}(?:\s\d{3})*[,.]\d{2})\s*(?<desc>.+?)$""")

        for (line in lines) {
            val match = txRegex.find(line.text) ?: continue
            val dateStr = match.groups["date"]?.value ?: continue
            val amountStr = match.groups["amount"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""

            val cleanAmount = amountStr.replace(" ", "").replace(",", ".")
            val amountMinor = cleanAmount.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong() ?: continue
            
            // Определяем тип по знаку или по контексту описания
            val isExpense = amountStr.startsWith("-") || desc.lowercase().contains("списание") || desc.lowercase().contains("оплата")
            val type = if (isExpense) TxType.EXPENSE else TxType.INCOME
            
            val ts = try {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }
            val category = guessCategory(desc.lowercase(), desc, customRules, type).category

            result.add(Tx(
                amountMinor = amountMinor, type = type, cardId = cardId, description = desc,
                category = category, timestamp = ts, source = "IMPORT", rawText = line.text
            ) to line)
        }
        return result
    }

    // ★ ПАРСЕР UZUM BANK (суммы с точкой, без знака)
    private fun parseUzumLines(lines: List<PdfLine>, customRules: List<CustomKeywordRule>): List<Pair<Tx, PdfLine>> {
        val result = mutableListOf<Pair<Tx, PdfLine>>()
        val cardRegex = Regex("""Номер карты:\s*\d+\*+(\d{4})""")
        var cardLast4 = "0000"
        for (line in lines.take(30)) {
            cardRegex.find(line.text)?.let { cardLast4 = it.groupValues[1] }
        }
        val cardId = "${cardLast4}_UZS"

        // Регулярка: дата (yyyy-MM-dd HH:mm:ss), номер, сумма (1700.00), описание
        val txRegex = Regex("""(?<date>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\s+\d{2}\.\d{2}\.\d{4}\s+\d+\s+(?<amount>\d+\.\d{2})\s+(?<desc>.+?)$""")

        for (line in lines) {
            val match = txRegex.find(line.text) ?: continue
            val dateStr = match.groups["date"]?.value ?: continue
            val amountStr = match.groups["amount"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""

            val amountMinor = amountStr.replace(".", "").toLongOrNull() ?: continue
            
            // В Uzum нет знака, тип по описанию
            val lowerDesc = desc.lowercase()
            val isExpense = lowerDesc.contains("оплата") || lowerDesc.contains("списание") || lowerDesc.contains("покупка")
            val type = if (isExpense) TxType.EXPENSE else TxType.INCOME
            
            val ts = try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }
            val category = guessCategory(desc.lowercase(), desc, customRules, type).category

            result.add(Tx(
                amountMinor = amountMinor, type = type, cardId = cardId, description = desc,
                category = category, timestamp = ts, source = "IMPORT", rawText = line.text
            ) to line)
        }
        return result
    }

    // ★ ПАРСЕР TEZ (с координатами)
    private fun parseTezLines(lines: List<PdfLine>, customRules: List<CustomKeywordRule>): List<Pair<Tx, PdfLine>> {
        val result = mutableListOf<Pair<Tx, PdfLine>>()
        val cardRegex = Regex("""Номер карты клиента:\s*\d{6}\*+(\d{4})""")
        var cardLast4 = "0000"
        for (line in lines.take(20)) { cardRegex.find(line.text)?.let { cardLast4 = it.groupValues[1] } }
        val cardId = "${cardLast4}_UZS"

        // Формат: 01-06-2026 08:19:54 +15 000,00 -15 000,00 ...
        val txRegex = Regex("""(?<date>\d{2}-\d{2}-\d{4})\s+(?<time>\d{2}:\d{2}:\d{2})\s*(?<desc>.*?)(?<sign>[+-])(?<amount>\d{1,3}(?:\s\d{3})*[,.]\d{2})""")

        for (line in lines) {
            val match = txRegex.find(line.text) ?: continue
            val dateStr = match.groups["date"]?.value ?: continue
            val timeStr = match.groups["time"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""
            val sign = match.groups["sign"]?.value ?: ""
            val amountStr = match.groups["amount"]?.value ?: continue

            val lowerDesc = desc.lowercase()
            if (lowerDesc.contains("копилк") || lowerDesc.contains("popolnenie kopilka") || lowerDesc.contains("rasxod popolnenie kopilka") || lowerDesc.contains("округление баланса") || lowerDesc.contains("процент на остаток") || lowerDesc.contains("разбитие копилки")) continue

            val cleanAmount = amountStr.replace(" ", "").replace(",", ".")
            val amountMinor = (cleanAmount.toDoubleOrNull() ?: continue).times(100).toLong()
            val type = if (sign == "+") TxType.INCOME else TxType.EXPENSE
            val ts = try {
                LocalDateTime.parse("$dateStr $timeStr", DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }
            val category = guessCategory(lowerDesc, desc, customRules, type).category

            result.add(Tx(
                amountMinor = amountMinor, type = type, cardId = cardId, description = desc,
                category = category, timestamp = ts, source = "IMPORT", rawText = line.text
            ) to line)
        }
        return result
    }

    // ★ УНИВЕРСАЛЬНЫЙ ПАРСЕР (fallback)
    private fun parseGenericPdfLines(lines: List<PdfLine>, customRules: List<CustomKeywordRule>): List<Pair<Tx, PdfLine>> {
        val result = mutableListOf<Pair<Tx, PdfLine>>()
        val txRegex = Regex("""(?<date>\d{2}\.\d{2}\.\d{4}|\d{4}-\d{2}-\d{2})(?:\s+\d{2}:\d{2}(?::\d{2})?)?\s*(?<sign>[+-])?\s*(?<amount>[\d\s\u00A0]+[.,]\d{2})\s*(?<desc>.+?)""", RegexOption.DOT_MATCHES_ALL)

        for (i in lines.indices) {
            val line = lines[i]
            val match = txRegex.find(line.text) ?: continue
            val dateStr = match.groups["date"]?.value ?: continue
            val amountStr = match.groups["amount"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""
            val sign = match.groups["sign"]?.value ?: ""

            val formatter = if (dateStr.contains("-")) DateTimeFormatter.ofPattern("yyyy-MM-dd") else DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val ts = try { LocalDate.parse(dateStr, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }

            val cleanAmount = amountStr.replace(" ", "").replace("\u00A0", "").replace(",", ".")
            val amountMinor = cleanAmount.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong() ?: continue
            if (amountMinor == 0L) continue

            val cardLast4 = extractCardLast4(desc)
            val cardId = cardLast4?.let { "${it}_UZS" }
            val lowerDesc = desc.lowercase()
            val isExpense = sign == "-" || lowerDesc.contains("списание") || lowerDesc.contains("оплата") || lowerDesc.contains("покупка") || lowerDesc.contains("снятие") || lowerDesc.contains("atm") || lowerDesc.contains("выд нал")
            val isIncome = sign == "+" || lowerDesc.contains("зачисление") || lowerDesc.contains("поступление") || lowerDesc.contains("пополнение") || lowerDesc.contains("возврат")
            val type = when { isExpense -> TxType.EXPENSE; isIncome -> TxType.INCOME; lowerDesc.contains("перевод") -> TxType.TRANSFER; else -> TxType.EXPENSE }
            val category = guessCategory(lowerDesc, "", customRules, type).category

            result.add(Tx(
                amountMinor = amountMinor, type = type, cardId = cardId, description = desc.take(80),
                category = category, timestamp = ts, source = "IMPORT", rawText = line.text
            ) to line)
        }
        return result
    }

    private fun tryParseTransaction(sender: String, body: String, timestamp: Long, lower: String, customRules: List<CustomKeywordRule>): BankTx? {
        if (SERVICE_BLACKLIST.any { lower.contains(it) }) return null
        if (!listOf("karta", "карта", "*", "uzs", "sum", "сум", "so'm", "so‘m", "so`m", "сўм", "usd", "eur", "balans", "dostupno", "ostatok", "qoldiq", "oplata", "оплата", "spisanie", "списание").any { lower.contains(it) }) return null
        val amounts = extractAmounts(body); if (amounts.isEmpty()) return null
        val balance = amounts.filter { a -> BALANCE_KEYWORDS.any { kw -> a.precedingLower.contains(kw) } }.firstOrNull()?.value
        val txAmount = amounts.firstOrNull { a -> a.precedingLower.contains("summa") || a.precedingLower.contains("сумма") } ?: amounts.firstOrNull { a -> (INCOME_VERBS + EXPENSE_VERBS).any { v -> a.precedingLower.contains(v) } && BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) } } ?: amounts.firstOrNull { a -> a.sign != null && BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) } } ?: amounts.firstOrNull { a -> BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) } } ?: return null
        val hasExpense = EXPENSE_VERBS.any { lower.contains(it) }
        val hasIncome = INCOME_VERBS.any { lower.contains(it) }
        val type = when { hasExpense && !hasIncome -> TxType.EXPENSE; hasIncome && !hasExpense -> TxType.INCOME; txAmount.sign == '-' -> TxType.EXPENSE; txAmount.sign == '+' -> TxType.INCOME; hasExpense -> TxType.EXPENSE; hasIncome -> TxType.INCOME; else -> TxType.EXPENSE }
        val card = extractCardLast4(body)
        val merchant = extractMerchant(body)
        val match = guessCategory(lower, merchant, customRules, type)
        return BankTx(
            amountMinor = txAmount.value, type = type, cardLast4 = card, balanceMinor = balance,
            merchant = merchant, category = match.category, timestamp = timestamp, sender = sender, rawBody = body,
            ruleAppliedId = match.ruleId, ruleAppliedKeyword = match.keyword
        )
    }

    private fun extractAmounts(text: String): List<AmountMatch> {
        val out = mutableListOf<AmountMatch>()
        for (m in AMOUNT_RE.findAll(text)) {
            val intPart = m.groupValues[2].replace(Regex("[ \u00A0,]"), "")
            val frac = m.groupValues[3].ifEmpty { "00" }.padEnd(2, '0').take(2)
            val value = try { intPart.toLong() * 100L + frac.toLong() } catch (_: Exception) { continue }
            val preceding = text.substring(maxOf(0, m.range.first - 50), m.range.first).lowercase()
            out.add(AmountMatch(value, m.groupValues[1].firstOrNull(), m.range.first, preceding))
        }
        return out
    }

    private fun extractCardLast4(body: String): String? {
        val fullCardRegex = Regex("""\b(?:\d[ -]*?){13,16}\b"""); val fullMatch = fullCardRegex.find(body)?.value?.replace(Regex("[^0-9]"), "")
        if (fullMatch != null && fullMatch.length >= 16) return fullMatch.takeLast(4)
        val maskedCardRegex = Regex("""\*+\s*(\d{4})\b"""); maskedCardRegex.find(body)?.let { return it.groupValues[1] }
        val parenRegex = Regex("""\(\*(\d{4})\)"""); parenRegex.find(body)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractMerchant(body: String): String {
        val patterns = listOf(Regex("""(?:oplata|оплата|pokupka|покупка|xarid)\s+[\d\s.,]+\s*(?:UZS|so[''`]?m|сум)?\s*[;:,]\s*([^;,\n]{3,80})""", RegexOption.IGNORE_CASE), Regex("""(?:v terminale|в терминале)\s+([^;,\n]{3,80})""", RegexOption.IGNORE_CASE), Regex("""["«]([^"»\n]{3,80})["»]"""))
        for (p in patterns) { val s = p.find(body)?.groupValues?.get(1)?.trim() ?: continue; if (s.length >= 3 && !s.all { it.isDigit() || it.isWhitespace() }) return s.take(60) }
        return ""
    }

    private fun guessCategory(lower: String, merchant: String, customRules: List<CustomKeywordRule>, expectedType: TxType): RuleMatch {
        val t = "$lower ${merchant.lowercase()}"
        val matched = customRules
            .filter { !it.isIgnored && it.type == expectedType && t.contains(it.keyword) }
            .maxByOrNull { it.keyword.length }
        if (matched != null) return RuleMatch(matched.category, matched.id, matched.keyword)

        fun hit(vararg words: String) = words.any { t.contains(it) }
        val stdCategory = when {
            hit("yandex.go", "yandex go", "taxi", "taksi", "transport", "avtobus", "metan", "cng", "azs", "benzin", "auto") -> "🚕 Транспорт"
            hit("market", "korzinka", "xalq retail", "uzum market", "magnit", "apelsin", "makro", "dilzar", "torg", "idea", "texnomart", "mediapark", "electronics", "galaxy", "iphone") -> "🛒 Покупки"
            hit("food", "restoran", "restaurant", "burger", "pizza", "kfc", "mcdonald", "lavash", "shaurma", "anglesey", "osh", "havas", "somsa", "cafe", "coffee") -> "🍔 Еда"
            hit("pharm", "dorixona", "med", "clinic", "klinika", "hospital", "shifo") -> "💊 Здоровье"
            hit("atm", "nalichnyh", "nalichnykh", "vidacha", "выдача", "снятие", "naqd yechish") -> "📦 Другое"
            hit("perevod", "перевод", "transfer", "p2p", "card2card") -> "💸 Перевод"
            hit("salary", "zarplata", "maosh", "зарплата", "aliment") -> "💵 Зарплата"
            else -> "📦 Другое"
        }
        return RuleMatch(stdCategory, null, null)
    }

    private fun isWeakOtp(lower: String): Boolean {
        if (!OTP_MARKERS.any { lower.contains(it) }) return false
        return Regex("""\b\d{4,8}\b""").containsMatchIn(lower) || Regex("""\b[A-Z]*\d[A-Z0-9]{3,6}\b""").containsMatchIn(lower.uppercase())
    }

    private fun BankTx.toTx(source: String) = Tx(
        amountMinor = amountMinor, type = type,
        cardId = cardLast4?.let { "${it}_$currency" },
        description = merchant, category = category, timestamp = timestamp,
        source = source, currency = currency,
        rawText = rawBody
    )

    private data class AmountMatch(val value: Long, val sign: Char?, val start: Int, val precedingLower: String)
}
