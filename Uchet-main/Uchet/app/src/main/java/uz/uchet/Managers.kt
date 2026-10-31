package uz.uchet

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

// ... (GoalManagerSheet, CardManagerSheet, BudgetManagerSheet, RecurringManagerSheet, CategoryManagerSheet остаются без изменений) ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(context: android.content.Context, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, currentTheme: MutableState<AppTheme>, currentLang: MutableState<AppLanguage>, vm: VM) {
    val prefs = SecurityHelper.getEncryptedPrefs(context)
    val settingsPrefs = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
    
    var oldPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }; var confirmPin by remember { mutableStateOf("") }; var useBiometric by remember { mutableStateOf(prefs.getBoolean("use_biometric", true)) }
    var smsDebug by remember { mutableStateOf(settingsPrefs.getBoolean("sms_debug_mode", false)) }
    
    // --- СОСТОЯНИЯ ДЛЯ УВЕДОМЛЕНИЙ ---
    var dailyReminder by remember { mutableStateOf(settingsPrefs.getBoolean("daily_reminder", false)) }
    var reminderHour by remember { mutableIntStateOf(settingsPrefs.getInt("reminder_hour", 21)) }
    var reminderMinute by remember { mutableIntStateOf(settingsPrefs.getInt("reminder_minute", 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    // ---------------------------------
    
    val savedPin = prefs.getString("pin", "0000") ?: "0000"; val t = { key: String -> Translations.t(key, currentLang.value) }

    // --- ДИАЛОГ ВЫБОРА ВРЕМЕНИ ---
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время напоминания") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeInput(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reminderHour = timePickerState.hour
                    reminderMinute = timePickerState.minute
                    settingsPrefs.edit()
                        .putInt("reminder_hour", reminderHour)
                        .putInt("reminder_minute", reminderMinute)
                        .apply()
                    
                    if (dailyReminder) {
                        NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            }
        )
    }
    // -------------------------------
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(t("settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text(t("language"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentLang.value == AppLanguage.RU, onClick = { currentLang.value = AppLanguage.RU; settingsPrefs.edit().putString("app_lang", "RU").apply() }, label = { Text("Русский") })
                FilterChip(selected = currentLang.value == AppLanguage.UZ, onClick = { currentLang.value = AppLanguage.UZ; settingsPrefs.edit().putString("app_lang", "UZ").apply() }, label = { Text("O'zbekcha") })
                FilterChip(selected = currentLang.value == AppLanguage.EN, onClick = { currentLang.value = AppLanguage.EN; settingsPrefs.edit().putString("app_lang", "EN").apply() }, label = { Text("English") })
                FilterChip(selected = currentLang.value == AppLanguage.TG, onClick = { currentLang.value = AppLanguage.TG; settingsPrefs.edit().putString("app_lang", "TG").apply() }, label = { Text("Тоҷикӣ") })
                FilterChip(selected = currentLang.value == AppLanguage.KAA, onClick = { currentLang.value = AppLanguage.KAA; settingsPrefs.edit().putString("app_lang", "KAA").apply() }, label = { Text("Qaraqalpaqsha") })
                FilterChip(selected = currentLang.value == AppLanguage.KK, onClick = { currentLang.value = AppLanguage.KK; settingsPrefs.edit().putString("app_lang", "KK").apply() }, label = { Text("Қазақша") })
            }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("theme"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = currentTheme.value == AppTheme.SYSTEM, onClick = { currentTheme.value = AppTheme.SYSTEM; settingsPrefs.edit().putString("app_theme", "SYSTEM").apply() }, label = { Text(t("sys")) })
                FilterChip(selected = currentTheme.value == AppTheme.LIGHT, onClick = { currentTheme.value = AppTheme.LIGHT; settingsPrefs.edit().putString("app_theme", "LIGHT").apply() }, label = { Text(t("light")) })
                FilterChip(selected = currentTheme.value == AppTheme.DARK, onClick = { currentTheme.value = AppTheme.DARK; settingsPrefs.edit().putString("app_theme", "DARK").apply() }, label = { Text(t("dark")) })
            }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.updateRates(); scope.launch { snackbarHostState.showSnackbar(t("rates_updated")) } }, modifier = Modifier.fillMaxWidth()) { Text(t("update_rates")) }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text(t("change_pin"), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = oldPin, onValueChange = { oldPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_new")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("pin_repeat")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            
            Button(onClick = { when { 
                oldPin != savedPin -> scope.launch { snackbarHostState.showSnackbar(t("wrong_pin")) }
                newPin.length != 4 -> scope.launch { snackbarHostState.showSnackbar(t("pin_error")) }
                newPin != confirmPin -> scope.launch { snackbarHostState.showSnackbar(t("pin_error")) }
                else -> { 
                    prefs.edit().putString("pin", newPin).apply()
                    oldPin = ""; newPin = ""; confirmPin = ""
                    scope.launch { snackbarHostState.showSnackbar(t("pin_ok")) } 
                } 
            } }, modifier = Modifier.fillMaxWidth()) { Text(t("change_pin")) }
            
            Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Text(t("biometric"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = useBiometric, onCheckedChange = { useBiometric = it; prefs.edit().putBoolean("use_biometric", it).apply() }) 
            }

            // --- ОБНОВЛЕННЫЙ БЛОК УВЕДОМЛЕНИЙ ---
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Column(Modifier.weight(1f)) {
                    Text("Ежедневные напоминания", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Каждый день в %02d:%02d".format(reminderHour, reminderMinute),
                        style = MaterialTheme.typography.bodySmall, 
                        color = if (dailyReminder) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.clickable { showTimePicker = true } // Открываем выбор времени
                    )
                }
                Switch(
                    checked = dailyReminder, 
                    onCheckedChange = { isChecked ->
                        dailyReminder = isChecked
                        settingsPrefs.edit().putBoolean("daily_reminder", isChecked).apply()
                        if (isChecked) {
                            NotificationHelper.scheduleDailyReminder(context, reminderHour, reminderMinute)
                        } else {
                            NotificationHelper.cancelDailyReminder(context)
                        }
                    }
                ) 
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                Text(t("sms_debug"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = smsDebug, onCheckedChange = { smsDebug = it; settingsPrefs.edit().putBoolean("sms_debug_mode", it).apply() }) 
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
