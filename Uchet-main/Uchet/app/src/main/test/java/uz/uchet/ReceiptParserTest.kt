package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParserTest {
    @Test
    fun `test Era POS standard receipt`() {
        val text = """
            Savdo raqami 16531610074034
            Jami 74 000.00
            To'lov uchun jami
            74 000.00
            Jami QQS 0.00
        """.trimIndent()
        val result = ReceiptParser.parse(text)
        assertEquals(7_400_000L, result.amountMinor)
    }

    @Test
    fun `test Smart Business receipt`() {
        val text = """
            POZITSIYALAR: 1
            JAMI TO'LOV: 78 000
            TO'LANDI: 78 000
            JAMI QQS: 8 357.12
        """.trimIndent()
        val result = ReceiptParser.parse(text)
        assertEquals(7_800_000L, result.amountMinor)
    }
}
