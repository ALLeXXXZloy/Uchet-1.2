package uz.uchet

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : FragmentActivity() {
    private val pendingCheckTrigger = mutableStateOf(0)
    private val widgetActionTrigger = mutableStateOf(0)
    private val currentTheme = mutableStateOf(AppTheme.SYSTEM)
    private val currentLang = mutableStateOf(AppLanguage.RU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)

        startKoin {
            androidContext(applicationContext)
            modules(appModule)
        }

        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)

        val oldPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val newPrefs = SecurityHelper.getEncryptedPrefs(this)
        if (oldPrefs.contains("pin") && !newPrefs.contains("pin")) {
            newPrefs.edit().putString("pin", oldPrefs.getString("pin", "0000")).apply()
            oldPrefs.edit().clear().apply()
        }

        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        currentTheme.value = try { AppTheme.valueOf(prefs.getString("app_theme", "SYSTEM") ?: "SYSTEM") } catch (e: Exception) { AppTheme.SYSTEM }
        currentLang.value = try { AppLanguage.valueOf(prefs.getString("app_lang", "RU") ?: "RU") } catch (e: Exception) { AppLanguage.RU }

        setContent {
            val theme by currentTheme
            val lang by currentLang
            val context = LocalContext.current
            val isDark = isSystemInDarkTheme()

            val colorScheme = when (theme) {
                AppTheme.LIGHT -> lightColorScheme()
                AppTheme.DARK -> darkColorScheme()
                AppTheme.SYSTEM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    } else {
                        if (isDark) darkColorScheme() else lightColorScheme()
                    }
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    var isAuthenticated by remember { mutableStateOf(false) }

                    // ★ Диалог запроса доступа к уведомлениям (показываем при первом запуске, если доступ не выдан)
                    var showNotifAccessDialog by remember {
                        mutableStateOf(!NotificationAccessHelper.isNotificationAccessGranted(context))
                    }

                    // Перепроверяем статус при возврате из системных настроек
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                if (NotificationAccessHelper.isNotificationAccessGranted(context)) {
                                    showNotifAccessDialog = false
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (isAuthenticated) {
                        App(
                            pendingCheckTrigger = pendingCheckTrigger,
                            widgetActionTrigger = widgetActionTrigger,
                            intent = intent,
                            currentTheme = currentTheme,
                            currentLang = currentLang
                        )
                    } else {
                        LockScreen(activity = this@MainActivity, lang = lang, onUnlocked = { isAuthenticated = true })
                    }

                    // Диалог показывается поверх всего — и LockScreen, и App
                    if (showNotifAccessDialog) {
                        AlertDialog(
                            onDismissRequest = { showNotifAccessDialog = false },
                            title = { Text(Translations.t("notif_access_title", lang)) },
                            text = { Text(Translations.t("notif_access_desc", lang)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    NotificationAccessHelper.requestNotificationAccess(context)
                                }) { Text(Translations.t("notif_access_open", lang)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNotifAccessDialog = false }) {
                                    Text(Translations.t("notif_access_later", lang))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCheckTrigger.value++
        widgetActionTrigger.value++
    }
}
