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
        assertEquals(7_400_000L, result.amountMinor) // 74 000.00 в тийинах
    }

    @Test
    fun `test Smart Business receipt`() {
        val text = """
            POZITSIYALAR: 1
            JAMI TO'LOV: 78 000
            TO'LANDI: 78 000
            BANK KARTASI: 78 000
            JAMI QQS: 8 357.12
        """.trimIndent()

        val result = ReceiptParser.parse(text)
        assertEquals(7_800_000L, result.amountMinor)
    }

    @Test
    fun `test Old style receipt with equals sign`() {
        val text = """
            JAMI: =53190
            CHEGIRMA: =4740
            QAYTIM: =0
            BEZNALICHNЫЙ =53190
        """.trimIndent()

        val result = ReceiptParser.parse(text)
        assertEquals(5_319_000L, result.amountMinor)
    }

    @Test
    fun `test Large amount with cents`() {
        val text = """
            Jami 799 411.20
            Operatsiya PIN-kod bilan tasdiqlanadi
            To'lov uchun jami
            799 411.20 UZS
            Jami QQS 85 651.20
        """.trimIndent()

        val result = ReceiptParser.parse(text)
        assertEquals(79_941_120L, result.amountMinor)
    }

    @Test
    fun `test Receipt with NDS and Markirovka`() {
        val text = """
            JAMI =30000.00
            QQS qiymati =3214.29
            NAQD PUL So'm =30000.00
        """.trimIndent()

        val result = ReceiptParser.parse(text)
        assertEquals(3_000_000L, result.amountMinor)
    }

    @Test
    fun `test Fallback biggest amount`() {
        val text = """
            Kakoy-to tekst
            Summa: 15000
            Kakoy-to tekst
            45 000.00
            Terminal 1234567
        """.trimIndent()

        // Если маркеры Jami не найдены, парсер должен взять самое большое число в конце
        val result = ReceiptParser.parse(text)
        assertEquals(4_500_000L, result.amountMinor)
    }
}
