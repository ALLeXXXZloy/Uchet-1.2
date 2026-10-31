package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategorizerTest {

    private val categories = listOf("🛒 Покупки", "🍔 Еда", "🚕 Транспорт", "💊 Здоровье")

    // ============================================================
    // Базовый парсинг (без правил)
    // ============================================================

    @Test
    fun `standard rule detects korzinka`() {
        val result = MerchantCategorizer.getCategory("Korzinka", categories, emptyList(), TxType.EXPENSE)
        assertEquals("🛒 Покупки", result)
    }

    @Test
    fun `standard rule detects kfc`() {
        val result = MerchantCategorizer.getCategory("KFC", categories, emptyList(), TxType.EXPENSE)
        assertEquals("🍔 Еда", result)
    }

    @Test
    fun `standard rule detects apteka`() {
        val result = MerchantCategorizer.getCategory("Apteka 24", categories, emptyList(), TxType.EXPENSE)
        assertEquals("💊 Здоровье", result)
    }

    @Test
    fun `returns null for unknown merchant`() {
        val result = MerchantCategorizer.getCategory("Some Random Shop", categories, emptyList(), TxType.EXPENSE)
        assertNull(result)
    }

    @Test
    fun `returns null for null merchant`() {
        assertNull(MerchantCategorizer.getCategory(null, categories, emptyList(), TxType.EXPENSE))
    }

    @Test
    fun `returns null for blank merchant`() {
        assertNull(MerchantCategorizer.getCategory("", categories, emptyList(), TxType.EXPENSE))
    }

    // ============================================================
    // #1 — фильтр правил по типу операции
    // ============================================================

    @Test
    fun `custom rule overrides standard category`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "korzinka", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val result = MerchantCategorizer.getCategory("Korzinka", categories, rules, TxType.EXPENSE)
        assertEquals("🍔 Еда", result)
    }

    @Test
    fun `custom rule not applied for different type`() {
        // Правило создано для INCOME, но транзакция — EXPENSE
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🍔 Еда", type = TxType.INCOME)
        )
        val result = MerchantCategorizer.getCategory("UZUM MARKET", categories, rules, TxType.EXPENSE)
        // правило не применилось, сработало стандартное "uzum" → 🛒 Покупки
        assertEquals("🛒 Покупки", result)
    }

    // ============================================================
    // #7 — при нескольких матчах побеждает самое длинное ключевое слово
    // ============================================================

    @Test
    fun `longest keyword wins`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "uzum", category = "🛒 Покупки", type = TxType.EXPENSE),
            CustomKeywordRule(keyword = "uzum market", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val result = MerchantCategorizer.getCategory("UZUM MARKET", categories, rules, TxType.EXPENSE)
        assertEquals("🍔 Еда", result)
    }

    // ============================================================
    // Негативные сценарии
    // ============================================================

    @Test
    fun `rule not applied when keyword absent`() {
        val rules = listOf(
            CustomKeywordRule(keyword = "kfc", category = "🍔 Еда", type = TxType.EXPENSE)
        )
        val result = MerchantCategorizer.getCategory("Korzinka", categories, rules, TxType.EXPENSE)
        // правило про KFC не сработало, стандартное правило про korzinka
        assertEquals("🛒 Покупки", result)
    }

    // ============================================================
    // Крайние случаи
    // ============================================================

    @Test
    fun `custom rule category not in available list returns rule category`() {
        // В availableCategories нет "🎁 Подарки", но правило именно туда направляет
        val rules = listOf(
            CustomKeywordRule(keyword = "korzinka", category = "🎁 Подарки", type = TxType.EXPENSE)
        )
        val result = MerchantCategorizer.getCategory("Korzinka", categories, rules, TxType.EXPENSE)
        // getCategory возвращает rule.category как fallback
        assertEquals("🎁 Подарки", result)
    }

    @Test
    fun `empty categories and no rules returns null`() {
        val result = MerchantCategorizer.getCategory("Korzinka", emptyList(), emptyList(), TxType.EXPENSE)
        assertNull(result)
    }
}
