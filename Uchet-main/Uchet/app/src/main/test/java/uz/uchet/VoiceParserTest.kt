package uz.uchet

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceParserTest {
    @Test
    fun `test simple expense voice`() {
        val result = VoiceParser.parseTransaction("потратил 5000 сум на такси", emptyList(), emptyList())
        assertEquals(500_000L, result.amountMinor)
        assertEquals(TxType.EXPENSE, result.type)
    }
}
