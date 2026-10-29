package uz.uchet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SumRow(label: String, v: Long, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else null)
        Text("${v.money()} UZS", color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun TxRow(t: Tx, lang: AppLanguage, onClick: () -> Unit) {
    val (sign, color) = when (t.type) {
        TxType.INCOME -> "+" to GREEN
        TxType.EXPENSE -> "−" to RED
        TxType.TRANSFER -> "→" to BLUE
        TxType.ADJUSTMENT -> "⚙️" to Color.Gray
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val icon = when (t.source) { "AUTO" -> "🔄 "; "SMS" -> "✉️ "; "SAVINGS" -> "🏦 "; "IMPORT" -> "📥 "; "ADJUSTMENT" -> "⚙️ "; else -> "" }
            Text("$icon${Translations.translateCategory(t.category, lang)}  ${t.description}".trim(), maxLines = 2)

            val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val cardText = if (t.cardId.isNullOrEmpty()) Translations.t("cash", lang) else "*${t.cardId.split("_")[0]}"
            val toCardText = if (t.toCardId != null) " -> *${t.toCardId.split("_")[0]}" else ""

            Text(listOfNotNull(time, cardText + toCardText).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("$sign${t.amountMinor.money()}", color = color, fontWeight = FontWeight.Medium)
    }
}
