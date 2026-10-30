package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Test

class QrReceiptParserTest {
    @Test
    fun `test parse soliq url`() {
        val url = "https://ofd.soliq.uz/check?t=20231026&r=12345&e=74000"
        val result = QrReceiptParser.parse(url)
        assertEquals(7_400_000L, result?.amountMinor)
    }
}
