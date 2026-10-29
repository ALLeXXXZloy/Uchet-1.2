package uz.uchet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

object SecurityHelper {
    private const val PREFS_NAME = "auth_prefs_enc"
    private const val BIOMETRIC_KEY_NAME = "biometric_key"

    // 1. Безопасное хранилище для PIN и настроек биометрии
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

    // 2. Создание или получение ключа для биометрии (CryptoObject)
    fun getOrCreateBiometricKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        
        // Если ключ уже есть — возвращаем его
        if (keyStore.containsAlias(BIOMETRIC_KEY_NAME)) {
            return (keyStore.getEntry(BIOMETRIC_KEY_NAME, null) as KeyStore.SecretKeyEntry).secretKey
        }

        // Иначе создаем новый
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true) // Требуется биометрия для использования ключа
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    // 3. Шифрование "пустышки" для проверки биометрии
    fun getCryptoObject(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getOrCreateBiometricKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }
}
