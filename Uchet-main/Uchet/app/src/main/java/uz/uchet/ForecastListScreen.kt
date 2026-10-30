package uz.uchet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastListScreen(
    forecasts: List<ForecastPlanEntity>,
    currentLang: AppLanguage,
    onSave: (ForecastPlanEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (ForecastPlanEntity) -> Unit,
    onBack: () -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingForecast by remember { mutableStateOf<ForecastPlanEntity?>(null) }
    var forecastToDelete by remember { mutableStateOf<ForecastPlanEntity?>(null) }

    if (forecastToDelete != null) {
        AlertDialog(
            onDismissRequest = { forecastToDelete = null },
            title = { Text(t("delete_confirm_title")) },
            text = {
                Column {
                    Text(t("delete_confirm_text"))
                    Spacer(Modifier.height(8.dp))
                    Text(t("forecast_delete_warning"), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(forecastToDelete!!.id)
                    forecastToDelete = null
                }) { Text(t("yes"), color = RED) }
            },
            dismissButton = { TextButton(onClick = { forecastToDelete = null }) { Text(t("no")) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("forecast_list")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t("back")) }
                }
            )
        },
        floatingActionButton = {
            if (forecasts.size < 10) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, t("forecast_new"))
                }
            }
        }
    ) { pad ->
        if (forecasts.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.TrendingUp, null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    t("forecast_empty"),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(forecasts, key = { it.id }) { f ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onOpen(f) }
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${t("forecast_months_short")}: ${f.months} · ${t("forecast_start_short")}: ${f.startMonth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { editingForecast = f }) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { forecastToDelete = f }) {
                                Icon(Icons.Default.Delete, null, tint = RED)
                            }
                        }
                    }
                }

                if (forecasts.size >= 10) {
                    item {
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = YELLOW.copy(alpha = 0.15f))
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null, tint = YELLOW)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    t("forecast_limit_reached"),
                                    color = YELLOW,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingForecast != null) {
        ForecastDialog(
            initial = editingForecast,
            currentLang = currentLang,
            onDismiss = { showAddDialog = false; editingForecast = null },
            onSave = { onSave(it); showAddDialog = false; editingForecast = null }
        )
    }
}
