package uz.uchet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel(), pendingCheckTrigger: MutableState<Int>? = null, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>) {
    val forecasts by vm.forecasts.collectAsState()
    val pendingRecurring by vm.pendingRecurring.collectAsState()
    val t = { key: String -> Translations.t(key, currentLang.value) }

    var currentTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var openedForecastId by remember { mutableStateOf<Long?>(null) }

    if (openedForecastId != null) {
        val forecast = forecasts.find { it.id == openedForecastId }
        if (forecast != null) {
            BackHandler { openedForecastId = null }
            ForecastScreen(forecast = forecast, currentLang = currentLang.value, vm = vm, onBack = { openedForecastId = null })
            return
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(t("operations")) })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(t("analytics")) })
                NavigationBarItem(
                    selected = currentTab == 2, onClick = { currentTab = 2 },
                    icon = { BadgedBox(badge = { if (pendingRecurring.isNotEmpty()) Badge { Text("${pendingRecurring.size}") } }) { Icon(Icons.Default.Assignment, null) } },
                    label = { Text(t("standard_forecast")) }
                )
                NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.TrendingUp, null) }, label = { Text(t("forecast_list")) })
                NavigationBarItem(selected = currentTab == 4, onClick = { currentTab = 4; showSettings = true }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text(t("settings")) })
            }
        }
    ) { pad ->
        when (currentTab) {
            0 -> OperationsScreen(vm, pad, currentLang, onOpenForecast = { openedForecastId = it }, onSwitchTab = { currentTab = it })
            1 -> AnalyticsScreen(txs = vm.txs.collectAsState().value, allTxs = vm.allTxs.collectAsState().value, currentLang = currentLang.value, onBack = { currentTab = 0 })
            2 -> StandardForecastScreen(vm = vm, currentLang = currentLang.value, onManageRecurring = { /* TODO: открыть шторку */ }, onBack = { currentTab = 0 })
            3 -> ForecastListScreen(forecasts = forecasts, currentLang = currentLang.value, onSave = { vm.saveForecast(it) }, onDelete = { vm.deleteForecast(it) }, onOpen = { openedForecastId = it.id }, onBack = { currentTab = 0 })
            4 -> Box(Modifier.fillMaxSize())
        }
    }

    if (showSettings) SettingsSheet(vm.getApplication(), { showSettings = false }, remember { SnackbarHostState() }, rememberCoroutineScope(), currentTheme, currentLang, vm)
}
