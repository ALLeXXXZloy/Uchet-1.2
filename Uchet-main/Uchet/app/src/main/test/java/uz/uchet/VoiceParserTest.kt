package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {

    // ============================================================
    // Базовый парсинг (без правил)
    // ============================================================

    @Test
    fun `simple expense voice`() {
        val result = VoiceParser.parseTransaction("потратил 5000 сум на такси", emptyList(), emptyList())
        assertEquals(500_000L, result.amountMinor)
        assertEquals(TxType.EXPENSE, result.type)
    }

    @Test
    fun `voice amount in words`() {
        val result = VoiceParser.parseTransaction("потратил пять тысяч сум на такси", emptyList(), emptyList())
        assertEquals(500_000L, result.amountMinor)
        assertEquals(TxType.EXPENSE, result.type)
    }

    @Test
    fun `voice cash detected`() {
        val result = VoiceParser.parseTransaction("потратил 5000 сум наличными на еду", emptyList(), emptyList())
        assertTrue(result.isCash)
        assertEquals(TxType.EXPENSE, result.type)
    }

    @Test
    fun `voice income detected`() {
        val result = VoiceParser.parseTransaction("получил зарплату 500000 сум", emptyList(), emptyList())
        assertEquals(50_000_000L, result.amountMinor)
        assertEquals(TxType.INCOME, result.type)
    }

    // ============================================================
    // #1 — фильтр правил по типу операции
    // ============================================================

    @Test
    fun `voice custom rule overrides default category`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "kfc", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val result = VoiceParser.parseTransaction(
            "купил кофе 15000 сум kfc", emptyList(), emptyList(), rules
        )
        assertEquals(TxType.EXPENSE, result.type)
        assertEquals("🍔 Еда", result.categoryName)
    }

    @Test
    fun `voice rule type filter prevents cross-type match`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE)
        )
        val result = VoiceParser.parseTransaction(
            "получил зарплату 500000 сум uzum", emptyList(), emptyList(), rules
        )
        assertEquals(TxType.INCOME, result.type)
        // EXPENSE-правило не применилось, сработало стандартное правило "зарплат"
        assertNotEquals("🛒 Покупки", result.categoryName)
        assertEquals("💵 Зарплата", result.categoryName)
    }

    // ============================================================
    // #7 — при нескольких матчах побеждает самое длинное ключевое слово
    // ============================================================

    @Test
    fun `voice longest keyword wins`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "кофе", category = "🍔 Еда", type = TxType.EXPENSE),
            CustomKeywordRule(keyword = "кофе с собой", category = "🎉 Развлечения", type = TxType.EXPENSE)
        )
        val result = VoiceParser.parseTransaction(
            "потратил 5000 сум на кофе с собой", emptyList(), emptyList(), rules
        )
        assertEquals("🎉 Развлечения", result.categoryName)
    }

    // ============================================================
    // Негативные сценарии
    // ============================================================

    @Test
    fun `voice rule not applied when keyword absent`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "kfc", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val result = VoiceParser.parseTransaction(
            "потратил 5000 сум на такси", emptyList(), emptyList(), rules
        )
        assertNotEquals("🍔 Еда", result.categoryName)
        assertEquals("🚕 Транспорт", result.categoryName)
    }

    // ============================================================
    // Специальные случаи
    // ============================================================

    @Test
    fun `voice rule applied when type unknown`() {
        // Нет ни одного глагола типа операции, но есть ключевое слово из правила
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE)
        )
        val result = VoiceParser.parseTransaction(
            "5000 сум uzum", emptyList(), emptyList(), rules
        )
        // type = null, но правило всё равно применяется (не отсекается фильтром)
        assertNull(result.type)
        assertEquals("🛒 Покупки", result.categoryName)
    }
}
