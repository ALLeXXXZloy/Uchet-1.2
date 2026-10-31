package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsParserTest {

    // ============================================================
    // Базовый парсинг (без правил)
    // ============================================================

    @Test
    fun `parses basic expense sms`() {
        val (cat, tx) = SmsParser.classify(
            "bank", "Oplata 5000 UZS karta *1234 UZUM MARKET", 0L, emptyList()
        )
        assertEquals(SmsCategory.TRANSACTION, cat)
        assertEquals(500_000L, tx?.amountMinor)
        assertEquals(TxType.EXPENSE, tx?.type)
        assertEquals("1234", tx?.cardLast4)
    }

    @Test
    fun `parses basic income sms`() {
        val (cat, tx) = SmsParser.classify(
            "bank", "Postuplenie 500000 UZS karta *1234 ot UZUM", 0L, emptyList()
        )
        assertEquals(SmsCategory.TRANSACTION, cat)
        assertEquals(50_000_000L, tx?.amountMinor)
        assertEquals(TxType.INCOME, tx?.type)
    }

    // ============================================================
    // #1 — фильтр правил по типу операции
    // ============================================================

    @Test
    fun `custom rule overrides default category`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum market", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Oplata 5000 UZS karta *1234 UZUM MARKET", 0L, rules
        )
        assertEquals("🍔 Еда", tx?.category)
    }

    @Test
    fun `rule type filter prevents cross-type match`() {
        // Правило создано для EXPENSE, но приходит INCOME с тем же merchant'ом
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Postuplenie 500000 UZS karta *1234 ot UZUM", 0L, rules
        )
        assertEquals(TxType.INCOME, tx?.type)
        assertNotEquals("🛒 Покупки", tx?.category)
    }

    @Test
    fun `rule with same keyword but different types picks correct one`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE),
            CustomKeywordRule(keyword = "uzum", category = "💵 Зарплата", type = TxType.INCOME)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Postuplenie 500000 UZS karta *1234 ot UZUM", 0L, rules
        )
        assertEquals("💵 Зарплата", tx?.category)
    }

    // ============================================================
    // #7 — при нескольких матчах побеждает самое длинное ключевое слово
    // ============================================================

    @Test
    fun `longest keyword wins among matching rules`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE),
            CustomKeywordRule(keyword = "uzum market", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Oplata 5000 UZS karta *1234 UZUM MARKET", 0L, rules
        )
        assertEquals("🍔 Еда", tx?.category)
    }

    @Test
    fun `shorter keyword used when longer one is absent`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE),
            CustomKeywordRule(keyword = "uzum market", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Oplata 5000 UZS karta *1234 UZUM", 0L, rules
        )
        assertEquals("🛒 Покупки", tx?.category)
    }

    // ============================================================
    // Негативные сценарии
    // ============================================================

    @Test
    fun `rule not applied when keyword absent`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "kfc", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val (_, tx) = SmsParser.classify(
            "bank", "Oplata 5000 UZS karta *1234 UZUM", 0L, rules
        )
        assertNotEquals("🍔 Еда", tx?.category)
    }

    // ============================================================
    // Интеграция с import (parseImportedFile)
    // ============================================================

    @Test
    fun `parseImportedFile respects custom rules`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE)
        )
        val csv = "DateTime,Direction,Contact,Content\n" +
                  "01.01.2026 12:00,Received,BANK,Oplata 5000 UZS karta *1234 UZUM"

        val result = SmsParser.parseImportedFile(csv, emptyList(), rules)

        assertEquals(1, result.size)
        assertEquals("🛒 Покупки", result.first().category)
        assertEquals(TxType.EXPENSE, result.first().type)
    }

    // ============================================================
    // Интеграция с OCR (parseForOcr)
    // ============================================================

    @Test
    fun `parseForOcr uses custom rules`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE)
        )
        val result = SmsParser.parseForOcr("Oplata 5000 UZS karta *1234 UZUM", rules)
        assertEquals("🛒 Покупки", result?.category)
    }
}
