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
    val amountMinor: Long,
    val type: TxType,
    val cardLast4: String?,
    val balanceMinor: Long?,
    val merchant: String,
    val category: String,
    val timestamp: Long,
    val sender: String,
    val rawBody: String,
    val currency: String = "UZS"
)

object SmsParser {

    // [±]123[ 456][.,78]( )?UZS|so'm|сум|sum
    private val AMOUNT_RE = Regex(
        """([+-]?)(\d{1,3}(?:[ \u00A0]\d{3})*|\d+)(?:[.,](\d{1,2}))?(?:\s*,\s*|\s+)(?:UZS\b|so[''`]?m\b|сум\b|sum\b)""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    )

    private val BALANCE_KEYWORDS = listOf(
        "dostupno", "ostatok", "qoldiq", "qoldig'i", "balans",
        "balance", "баланс", "остаток", "доступно"
    )

    // --- доход ---
    private val INCOME_VERBS = listOf(
        "popolnenie", "пополнение", "postuplenie", "поступление",
        "zachislenie", "зачисление", "kirim", "credit account",
        "credit part", "refund", "vozvrat", "возврат",
        "vnesenie nalichnykh", "vnosenie nalichnykh",
        "получение перевода", "perevod na kartu", "перевод на карту",
        "tushdi", "kelib tushdi", "keldi", "kirib keldi", "yetib keldi"
    )

    // --- расход ---
    private val EXPENSE_VERBS = listOf(
        "oplata", "оплата", "spisanie", "списание", "pokupka", "покупка",
        "xarid", "snjatie nalichnih", "snyatiye", "снятие",
        "naqd yechish", "получение наличных", "poluchenie nalichnyh",
        "vidacha nalichnykh", "выдача наличных", "operaciya", "операция",
        "prinyata summa", "принята сумма", "otpravleno", "отправлено",
        "perevod s", "перевод с", "debit acc presentment",
        "yechildi", "yechib olindi", "списаны", "spisany"
    )

    // --- сильные признаки долга/напоминания (если нет глагола завершения) ---
    private val STRONG_DEBT = listOf(
        "задолженность", "задолженност", "qarzdorlik", "qarzdor",
        "qarzingiz", "qarz mavjud",
        "просим оплатить", "оплатите", "to'lang", "to’lang", "тўланг",
        "погасите",
        "недостаточно средств", "не хватает средств",
        "номер заблокирован", "vaш номер заблокирован", "bloklandi",
        "расторжение договора", "shartnoma bekor",
        "kechik", "muddatli to'lov kuni", "muddatli to'lovni amalga oshiring",
        "eslatamiz",
        "просрочен", "просрочк",
        "мвд", "mib", "исполнительный", "ijro ishi",
        "prokuratura", "прокуратура",
        "sudga", "суд", "принудительн", "ispolnitelniy"
    )

    // --- признаки завершённой транзакции (перебивают STRONG_DEBT) ---
    private val COMPLETION_VERBS = listOf(
        "spisano", "списано", "spisany", "списаны", "spisana", "списана",
        "yechildi", "yechib olindi", "вычтено", "снято",
        "zachisleno", "зачислено", "postupil", "поступило",
        "qabul qilindi", "amalga oshirildi", "выполнено"
    )

    // --- OTP-предупреждения (никогда не бывают в завершённых транзакциях) ---
    private val OTP_WARNINGS = listOf(
        "никому не передавайте", "никому не сообщайте", "никому не говорите",
        "nikomu ne peredavayte", "nikomu ne soobshchayte", "nikomu ne govorite",
        "hech kimga bermang", "hech kimga aytmang", "kodni hech kimga",
        "не сообщайте код", "не передавайте код", "don't share",
        "не давайте код", "faqat firibgarlar", "только мошенники",
        "osteregaytes moshennikov", "остерегайтесь мошенников"
    )

    private val OTP_MARKERS = listOf(
        "ваш код", "код подтверждения", "код авторизации", "код для входа",
        "kod podtverzhdeniya", "kod dlya vhoda", "kod dlya vxoda",
        "tasdiqlash kodi", "kirish kodi", "verification code",
        "your code", "is your code", "one-time code", "одноразовый код",
        "пароль", "parol", "password", "pin code", "pin-kod"
    )

    private val DEBT_MARKERS = STRONG_DEBT + listOf(
        "закончились минуты", "закончились мб", "закончился трафик",
        "пакет заканчивается", "баланс счета", "баланс счёта",
        "минуты закончились", "предстоящий платеж", "долг", "должник",
        "yaqinlashib kelayotgan", "маблағ етарли эмас", "yetarli emas"
    )

    private val AD_MARKERS = listOf(
        "скидка", "chegirma", "акция", "aksiya", "тариф", "tarif",
        "подарок", "sovg'a", "sovga", "супер vip", "super vip",
        "подключите опцию", "подключите пакет", "переходите на",
        "выберите пакет", "новый тариф", "распродажа", "промокод",
        "promokod", "бонус", "bonus", "купите", "shop", "магазин",
        "market", "красивых номеров", "красивый номер",
        "не упустите", "shoshiling", "торопитесь", "успейте",
        "только сегодня", "только сейчас", "faqat bugun",
        "последний шанс", "предложение", "taklif", "instagram",
        "facebook", "telegram", "подписка", "регистрация на",
        "курс", "марафон", "вебинар", "опрос", "so'rovnoma",
        "недвижимость", "turar-joy", "квартира", "kvartira",
        "ипотека", "ipoteka", "автокредит", "mikrozaym", "микрозайм",
        "микрокредит", "займ", "kredit oling", "ставка", "гарантия",
        "garantiya", "визит", "tashrif", "бесплатно", "bepul",
        "розыгрыш", "выиграйте", "yutib oling", "приз", "sovrin"
    )

    private val SERVICE_BLACKLIST = listOf(
        "sms info", "sms narxi", "стоимость sms", "sms o'chirildi",
        "sms o’chirildi", "sms-info"
    )

    // ====================================================================
    // Public API
    // ====================================================================

    fun classify(sender: String, body: String, timestamp: Long): Pair<SmsCategory, BankTx?> {
        val lower = body.lowercase()

        // 1. OTP-предупреждения — первыми (в завершённых банковских SMS их не бывает)
        if (OTP_WARNINGS.any { lower.contains(it) }) return SmsCategory.OTP to null

        // 2. Сильный долг БЕЗ глагола завершения
        val hasCompletion = COMPLETION_VERBS.any { lower.contains(it) }
        if (!hasCompletion && STRONG_DEBT.any { lower.contains(it) })
            return SmsCategory.DEBT_REMINDER to null

        // 3. Транзакция
        val tx = tryParseTransaction(sender, body, timestamp, lower)
        if (tx != null) return SmsCategory.TRANSACTION to tx

        // 4. Слабая OTP (маркер + короткий код, без признаков транзакции)
        if (isWeakOtp(lower)) return SmsCategory.OTP to null

        // 5. Долг (широкий набор)
        if (DEBT_MARKERS.any { lower.contains(it) }) return SmsCategory.DEBT_REMINDER to null

        // 6. Реклама (>=2 маркера)
        if (AD_MARKERS.count { lower.contains(it) } >= 2) return SmsCategory.ADVERTISING to null

        return SmsCategory.PERSONAL to null
    }

    fun scanInbox(context: Context, existing: List<Tx>): List<Tx> {
        val result = mutableListOf<Tx>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"), null, null, null, "date DESC"
        ) ?: return result
        cursor.use { c ->
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")
            val addrIdx = c.getColumnIndex("address")
            var n = 0
            while (c.moveToNext() && n < 500) {
                val body = c.getString(bodyIdx)
                if (body == null) { n++; continue }
                val date = c.getLong(dateIdx)
                val sender = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                val (cat, tx) = classify(sender, body, date)
                if (cat == SmsCategory.TRANSACTION && tx != null) {
                    val dup = existing.any {
                        it.amountMinor == tx.amountMinor &&
                        kotlin.math.abs(it.timestamp - date) < 60_000
                    }
                    if (!dup) result.add(tx.toTx("SMS"))
                }
                n++
            }
        }
        return result
    }

    /** Роутер: CSV (экспорт SMS) vs PDF (выписка банка). */
    fun parseImportedFile(text: String): List<Tx> {
        val clean = text.removePrefix("\uFEFF")
        val lines = clean.lines()
        val headerIdx = lines.indexOfFirst { line ->
            val l = line.trim().lowercase()
            l.startsWith("datetime") && l.contains("direction") && l.contains("content")
        }
        return if (headerIdx >= 0) {
            val csv = lines.subList(headerIdx, lines.size).joinToString("\n")
            parseCsvExport(csv)
        } else {
            parsePdfText(clean)
        }
    }

    fun parseForOcr(text: String): ParsedSms? {
        val (cat, tx) = classify("", text, System.currentTimeMillis())
        if (cat == SmsCategory.TRANSACTION && tx != null) {
            return ParsedSms(
                amountStr = BigDecimal(tx.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString(),
                card = tx.cardLast4,
                description = tx.merchant,
                category = tx.category
            )
        }
        // чек: итого / jami / total
        val totalRegex = Regex(
            """(?:jami|итого|total|к оплате|сумма)[:\s=]*([\d \u00A0.,]+)""",
            RegexOption.IGNORE_CASE
        )
        val m = totalRegex.find(text)
        if (m != null) {
            val amount = normalizeAmount(m.groupValues[1]) ?: return null
            return ParsedSms(
                BigDecimal(amount).movePointLeft(2).stripTrailingZeros().toPlainString(),
                null, text.take(60).replace("\n", " ").trim(), "📦 Другое"
            )
        }
        val first = extractAmounts(text).firstOrNull() ?: return null
        return ParsedSms(
            BigDecimal(first.value).movePointLeft(2).stripTrailingZeros().toPlainString(),
            null, text.take(60).replace("\n", " ").trim(), "📦 Другое"
        )
    }

    // ====================================================================
    // CSV
    // ====================================================================

    private fun parseCsvExport(csv: String): List<Tx> {
        val rows = parseCsvRecords(csv)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val idxDate = header.indexOf("datetime").takeIf { it >= 0 } ?: 0
        val idxDir = header.indexOf("direction").takeIf { it >= 0 } ?: 1
        val idxContact = header.indexOf("contact").takeIf { it >= 0 } ?: 2
        val idxContent = header.indexOf("content").takeIf { it >= 0 } ?: 4

        val result = mutableListOf<Tx>()
        val seen = mutableSetOf<String>()

        for (row in rows.drop(1)) {
            if (row.size <= idxContent) continue
            val dtStr = row.getOrNull(idxDate)?.trim() ?: continue
            val dir = row.getOrNull(idxDir)?.trim() ?: ""
            val sender = row.getOrNull(idxContact)?.trim() ?: ""
            val content = row.getOrNull(idxContent) ?: continue
            if (dir.isNotEmpty() && !dir.equals("Received", true)) continue
            if (content.isBlank()) continue

            val ts = parseDateTime(dtStr) ?: continue
            val (cat, tx) = classify(sender, content, ts)
            if (cat != SmsCategory.TRANSACTION || tx == null) continue

            val key = "${tx.amountMinor}_${tx.type}_${tx.cardLast4}_${ts / 60_000L}"
            if (!seen.add(key)) continue

            result.add(tx.toTx("IMPORT"))
        }
        return result
    }

    private fun parseCsvRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < n && text[i + 1] == '"') { sb.append('"'); i += 2 }
                        else { inQuotes = false; i++ }
                    } else { sb.append(ch); i++ }
                }
                ch == '"' -> { inQuotes = true; i++ }
                ch == ',' -> { current.add(sb.toString()); sb.clear(); i++ }
                ch == '\r' -> i++
                ch == '\n' -> {
                    current.add(sb.toString()); sb.clear()
                    records.add(current.toList()); current.clear(); i++
                }
                else -> { sb.append(ch); i++ }
            }
        }
        if (current.isNotEmpty() || sb.isNotEmpty()) {
            current.add(sb.toString()); records.add(current.toList())
        }
        return records.filter { it.isNotEmpty() }
    }

    // ====================================================================
    // PDF (Kapitalbank / Uzum Bank)
    // ====================================================================

    private fun parsePdfText(text: String): List<Tx> {
        val result = mutableListOf<Tx>()
        // Убираем лишние пробелы и переносы строк, чтобы склеить разорванный текст Uzum и Kapitalbank
        val normText = text.replace(Regex("\\s+"), " ")

        // Универсальная регулярка: ищет дату, время (опционально), номер документа (опционально), 
        // минус (опционально), сумму и описание до следующей даты.
        val txRegex = Regex(
            """(?<date>\d{2}\.\d{2}\.\d{4}|\d{4}-\d{2}-\d{2})(?:\s+\d{2}:\d{2}:\d{2})?(?:\s+\d{2}\.\d{2}\.\d{4})?(?:\s+\d+)?\s*(?<sign>[+-]?)\s*(?<amount>[\d\s\u00A0]+[.,]\d{2})\s*(?<desc>.+?)(?=(?:\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2}:\d{2}))|(?:\d{2}\.\d{2}\.\d{4})|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        for (match in txRegex.findAll(normText)) {
            val dateStr = match.groups["date"]?.value ?: continue
            val amountStr = match.groups["amount"]?.value ?: continue
            val desc = match.groups["desc"]?.value?.trim() ?: ""
            val sign = match.groups["sign"]?.value ?: ""

            // 1. Парсим дату
            val formatter = if (dateStr.contains("-")) DateTimeFormatter.ofPattern("yyyy-MM-dd") else DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val ts = try {
                LocalDate.parse(dateStr, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { System.currentTimeMillis() }

            // 2. Парсим сумму (убираем пробелы, меняем запятую на точку)
            val cleanAmount = amountStr.replace(" ", "").replace("\u00A0", "").replace(",", ".")
            val amountMinor = cleanAmount.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong() ?: 0L
            if (amountMinor == 0L) continue

            // 3. Определяем тип транзакции по ключевым словам
            val isExpense = sign == "-" || desc.contains("Списание", true) || desc.contains("Оплата", true) || 
                            desc.contains("Снятие", true) || desc.contains("Перевод с", true) || desc.contains("Покупка", true)
            val isIncome = sign == "+" || desc.contains("Зачисление", true) || desc.contains("Поступление", true) || 
                           desc.contains("Пополнение", true) || desc.contains("Перевод на", true) || desc.contains("Возврат", true)

            val type = when {
                isExpense -> TxType.EXPENSE
                isIncome -> TxType.INCOME
                desc.contains("Перевод", true) -> TxType.TRANSFER
                else -> TxType.EXPENSE
            }

            // 4. Вытаскиваем номер карты (последние 4 цифры) из текста описания
            val cardRegex = Regex("""(?:\*\*|\.\.)(\d{4})""")
            val cardLast4 = cardRegex.find(desc)?.groupValues?.get(1)
            val cardId = cardLast4?.let { "${it}_UZS" }

            // 5. Определяем категорию (более точная для банковских выписок)
            val category = when {
                desc.contains("Кешбек", true) || desc.contains("Cashback", true) -> "🎁 Подарок"
                desc.contains("Перевод", true) || desc.contains("P2P", true) -> "💸 Перевод"
                desc.contains("Снятие", true) || desc.contains("ATM", true) || desc.contains("наличных", true) -> "💸 Перевод"
                desc.contains("Оплата", true) || desc.contains("Покупка", true) -> "🛒 Покупки"
                else -> "📦 Другое"
            }

            result.add(Tx(
                amountMinor = amountMinor,
                type = type,
                cardId = cardId,
                description = desc.take(80), // Обрезаем слишком длинные описания
                category = category,
                timestamp = ts,
                source = "IMPORT"
            ))
        }
        return result
    }

    // ====================================================================
    // Ядро (для СМС)
    // ====================================================================

    private fun tryParseTransaction(
        sender: String, body: String, timestamp: Long, lower: String
    ): BankTx? {
        if (SERVICE_BLACKLIST.any { lower.contains(it) }) return null
        if (!listOf("karta", "карта", "*", "uzs", "sum", "сум", "balans",
                "dostupno", "ostatok", "qoldiq").any { lower.contains(it) }) return null

        val amounts = extractAmounts(body)
        if (amounts.isEmpty()) return null

        val balance = amounts
            .filter { a -> BALANCE_KEYWORDS.any { kw -> a.precedingLower.contains(kw) } }
            .firstOrNull()?.value

        val txAmount = amounts.firstOrNull { a ->
            a.precedingLower.contains("summa") || a.precedingLower.contains("сумма")
        } ?: amounts.firstOrNull { a ->
            (INCOME_VERBS + EXPENSE_VERBS).any { v -> a.precedingLower.contains(v) } &&
                BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) }
        } ?: amounts.firstOrNull { a ->
            a.sign != null && BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) }
        } ?: amounts.firstOrNull { a ->
            BALANCE_KEYWORDS.none { kw -> a.precedingLower.contains(kw) }
        } ?: return null

        val hasExpense = EXPENSE_VERBS.any { lower.contains(it) }
        val hasIncome = INCOME_VERBS.any { lower.contains(it) }
        val type = when {
            hasExpense && !hasIncome -> TxType.EXPENSE
            hasIncome && !hasExpense -> TxType.INCOME
            txAmount.sign == '-' -> TxType.EXPENSE
            txAmount.sign == '+' -> TxType.INCOME
            hasExpense -> TxType.EXPENSE
            hasIncome -> TxType.INCOME
            else -> TxType.EXPENSE
        }

        val card = extractCardLast4(body)
        val merchant = extractMerchant(body)
        val category = guessCategory(lower, merchant)

        return BankTx(
            amountMinor = txAmount.value,
            type = type,
            cardLast4 = card,
            balanceMinor = balance,
            merchant = merchant,
            category = category,
            timestamp = timestamp,
            sender = sender,
            rawBody = body
        )
    }

    private fun extractAmounts(text: String): List<AmountMatch> {
        val out = mutableListOf<AmountMatch>()
        for (m in AMOUNT_RE.findAll(text)) {
            val intPart = m.groupValues[2].replace(Regex("[ \u00A0]"), "")
            val frac = m.groupValues[3].ifEmpty { "00" }.padEnd(2, '0').take(2)
            val value = try { intPart.toLong() * 100L + frac.toLong() } catch (_: Exception) { continue }
            val preceding = text.substring(maxOf(0, m.range.first - 50), m.range.first).lowercase()
            out.add(AmountMatch(value, m.groupValues[1].firstOrNull(), m.range.first, preceding))
        }
        return out
    }

    private fun extractCardLast4(body: String): String? {
        val patterns = listOf(
            Regex("""\(\*(\d{4})\)"""),                 // (*1226)
            Regex("""\d*\*+\s*(\d{4})\b""")             // ****8126, *9321, 5477***0855
        )
        for (p in patterns) p.find(body)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractMerchant(body: String): String {
        val patterns = listOf(
            Regex("""(?:oplata|оплата|pokupka|покупка|xarid)\s+[\d\s.,]+\s*(?:UZS|so[''`]?m|сум)?\s*[;:,]\s*([^;,\n]{3,80})""", RegexOption.IGNORE_CASE),
            Regex("""(?:v terminale|в терминале)\s+([^;,\n]{3,80})""", RegexOption.IGNORE_CASE),
            Regex("""["«]([^"»\n]{3,80})["»]""")
        )
        for (p in patterns) {
            val s = p.find(body)?.groupValues?.get(1)?.trim() ?: continue
            if (s.length >= 3 && !s.all { it.isDigit() || it.isWhitespace() }) return s.take(60)
        }
        return body.replace(Regex("[\\r\\n]+"), " ").take(60).trim()
    }

    private fun guessCategory(lower: String, merchant: String): String {
        val t = "$lower ${merchant.lowercase()}"
        fun hit(vararg words: String) = words.any { t.contains(it) }
        return when {
            hit("yandex.go", "yandex go", "taxi", "taksi", "transport", "avtobus",
                "metan", "cng", "azs", "benzin", "auto") -> "🚕 Транспорт"
            hit("market", "korzinka", "xalq retail", "uzum market", "magnit",
                "apelsin", "makro", "dilzar", "torg", "idea", "texnomart",
                "mediapark", "electronics", "galaxy", "iphone") -> "🛒 Покупки"
            hit("food", "restoran", "restaurant", "burger", "pizza", "kfc",
                "mcdonald", "lavash", "shaurma", "anglesey", "osh", "havas",
                "somsa", "cafe", "coffee") -> "🍔 Еда"
            hit("pharm", "dorixona", "med", "clinic", "klinika", "hospital",
                "shifo") -> "💊 Здоровье"
            hit("atm", "nalichnyh", "nalichnykh", "vidacha", "выдача",
                "снятие", "naqd yechish", "perevod", "перевод", "transfer",
                "p2p", "card2card") -> "💸 Перевод"
            hit("salary", "zarplata", "maosh", "зарплата", "aliment") -> "💵 Зарплата"
            else -> "📦 Другое"
        }
    }

    private fun isWeakOtp(lower: String): Boolean {
        if (!OTP_MARKERS.any { lower.contains(it) }) return false
        return Regex("""\b\d{4,8}\b""").containsMatchIn(lower) ||
               Regex("""\b[A-Z0-9]{5,8}\b""").containsMatchIn(lower.uppercase())
    }

    private fun parseDateTime(str: String): Long? {
        val s = str.trim()
        for (fmt in DATE_FORMATS) try {
            return LocalDateTime.parse(s, fmt)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        return null
    }

    private fun normalizeAmount(str: String): Long? = try {
        BigDecimal(str.replace(Regex("[^\\d.,]"), "").replace(',', '.'))
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    } catch (_: Exception) { null }

    private fun BankTx.toTx(source: String) = Tx(
        amountMinor = amountMinor,
        type = type,
        cardId = cardLast4?.let { "${it}_UZS" },
        description = merchant,
        category = category,
        timestamp = timestamp,
        source = source,
        currency = currency
    )

    private data class AmountMatch(
        val value: Long,
        val sign: Char?,
        val start: Int,
        val precedingLower: String
    )
}
