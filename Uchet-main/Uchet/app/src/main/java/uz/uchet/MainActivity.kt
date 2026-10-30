package uz.uchet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : FragmentActivity() {
    private val pendingCheckTrigger = mutableStateOf(0)
    private val currentTheme = mutableStateOf(AppTheme.SYSTEM)
    private val currentLang = mutableStateOf(AppLanguage.RU)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- ИНИЦИАЛИЗАЦИЯ KOIN ---
        startKoin {
            androidContext(this@MainActivity)
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
            val colorScheme = when (theme) {
                AppTheme.LIGHT -> lightColorScheme()
                AppTheme.DARK -> darkColorScheme()
                AppTheme.SYSTEM -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    var isAuthenticated by remember { mutableStateOf(false) }
                    if (isAuthenticated) {
                        App(pendingCheckTrigger = pendingCheckTrigger, currentTheme = currentTheme, currentLang = currentLang)
                    } else {
                        LockScreen(activity = this@MainActivity, lang = lang, onUnlocked = { isAuthenticated = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCheckTrigger.value++
    }
}
