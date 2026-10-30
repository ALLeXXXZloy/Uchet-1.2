package uz.uchet

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun LockScreen(activity: FragmentActivity, lang: AppLanguage, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val prefs = SecurityHelper.getEncryptedPrefs(activity)
    val savedPin = prefs.getString("pin", "0000") ?: "0000"
    val useBiometric = prefs.getBoolean("use_biometric", true)

    val biometricManager = BiometricManager.from(activity)
    val canUseBiometric = useBiometric &&
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val t = { key: String -> Translations.t(key, lang) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp), 
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(t("locked"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); error = false },
            label = { Text(t("pin")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = error,
            supportingText = { if (error) Text(t("wrong_pin"), color = RED) }
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (pin == savedPin) onUnlocked()
                else { pin = ""; error = true }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("login")) }

        if (canUseBiometric) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                biometricError = null
                val executor = ContextCompat.getMainExecutor(activity)

                val cryptoObject: BiometricPrompt.CryptoObject? = try {
                    BiometricPrompt.CryptoObject(SecurityHelper.getCryptoObject())
                } catch (e: Exception) {
                    Log.e("BiometricDebug", "cryptoObject creation failed: ${e.message}", e)
                    null
                }

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(t("login"))
                    .setSubtitle(t("fingerprint"))
                    .setNegativeButtonText(t("pin"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result); onUnlocked()
                    }
                    override fun onAuthenticationFailed() { super.onAuthenticationFailed() }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        biometricError = errString.toString()
                        Log.e("BiometricDebug", "auth error $errorCode: $errString")
                    }
                }

                val biometricPrompt = BiometricPrompt(activity, executor, callback)
                try {
                    if (cryptoObject != null) biometricPrompt.authenticate(promptInfo, cryptoObject)
                    else biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    Log.e("BiometricDebug", "authenticate() failed: ${e.message}", e)
                    biometricError = e.localizedMessage ?: "Ошибка биометрии"
                }
            }) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text(t("fingerprint"))
            }

            biometricError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it, 
                    color = RED, 
                    style = MaterialTheme.typography.bodySmall, 
                    textAlign = TextAlign.Center, 
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
