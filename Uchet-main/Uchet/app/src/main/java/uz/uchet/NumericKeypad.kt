package uz.uchet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

@Composable
fun NumericKeypad(
    amountStr: String,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Логика для встроенного калькулятора
    fun handleKey(key: String) {
        when (key) {
            "⌫" -> {
                if (amountStr.isNotEmpty()) {
                    onAmountChange(amountStr.dropLast(1))
                }
            }
            "=" -> {
                try {
                    val expression = amountStr.replace(" ", "").replace(',', '.')
                    val result = if (expression.contains("+")) {
                        val parts = expression.split("+")
                        if (parts.size == 2) parts[0].toBigDecimalOrNull()?.add(parts[1].toBigDecimalOrNull() ?: BigDecimal.ZERO)
                        else null
                    } else if (expression.contains("-") && !expression.startsWith("-")) {
                        val parts = expression.split("-")
                        if (parts.size == 2) parts[0].toBigDecimalOrNull()?.subtract(parts[1].toBigDecimalOrNull() ?: BigDecimal.ZERO)
                        else null
                    } else {
                        expression.toBigDecimalOrNull()
                    }
                    
                    if (result != null) {
                        onAmountChange(result.stripTrailingZeros().toPlainString())
                    }
                } catch (e: Exception) { /* Игнорируем ошибки парсинга */ }
            }
            "+", "-" -> {
                if (amountStr.isNotEmpty() && !amountStr.endsWith("+") && !amountStr.endsWith("-")) {
                    onAmountChange(amountStr + key)
                }
            }
            "," -> {
                if (!amountStr.contains(",") && !amountStr.contains("+") && !amountStr.contains("-")) {
                    onAmountChange(if (amountStr.isEmpty()) "0," else amountStr + ",")
                }
            }
            else -> {
                // Ограничение на количество символов
                if (amountStr.length < 12) {
                    onAmountChange(amountStr + key)
                }
            }
        }
    }

    val rows = listOf(
        listOf("1", "2", "3", "+"),
        listOf("4", "5", "6", "-"),
        listOf("7", "8", "9", "="),
        listOf(",", "0", "00", "⌫")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = { handleKey(key) },
                        isAction = key in listOf("+", "-", "=")
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isAction: Boolean = false
) {
    val bgColor = if (isAction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isAction) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text == "⌫") {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Удалить",
                tint = textColor
            )
        } else {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
