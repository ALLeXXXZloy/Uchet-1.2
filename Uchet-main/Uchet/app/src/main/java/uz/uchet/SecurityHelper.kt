package uz.uchet

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object SecurityHelper {
    private const val PREFS_NAME = "auth_prefs_enc"
    private const val BIOMETRIC_KEY_NAME = "biometric_key"
    private const val TAG = "BiometricDebug"

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Создание нового ключа с корректными параметрами для API 30+
    private fun createNewKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // На API 30+ обязательно. timeoutSeconds = 0 -> только STRONG-биометрия (отпечаток)
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(0)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    // Возвращает Cipher, готовый для BiometricPrompt.CryptoObject
    fun getCryptoObject(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (keyStore.containsAlias(BIOMETRIC_KEY_NAME)) {
            try {
                val key = (keyStore.getEntry(BIOMETRIC_KEY_NAME, null) as KeyStore.SecretKeyEntry).secretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                Log.d(TAG, "Existing key loaded OK")
                return cipher
            } catch (e: Exception) {
                // Ключ невалиден (сменили отпечаток, добавили новый палец и т.п.)
                // Удаляем и создаём новый
                Log.w(TAG, "Key invalidated, recreating: ${e.message}")
                try { keyStore.deleteEntry(BIOMETRIC_KEY_NAME) } catch (_: Exception) {}
            }
        }

        // Создаём свежий ключ
        val newKey = createNewKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKey)
        Log.d(TAG, "New key created OK")
        return cipher
    }
}
