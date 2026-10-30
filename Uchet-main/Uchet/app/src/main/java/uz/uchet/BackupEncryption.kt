package uz.uchet

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryption {
    private val MAGIC = byteArrayOf('U'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(), 'T'.code.toByte())
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_SIZE = 256
    private const val GCM_TAG_BITS = 128

    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < 4) return false
        return data[0] == MAGIC[0] && data[1] == MAGIC[1] && data[2] == MAGIC[2] && data[3] == MAGIC[3]
    }

    fun encrypt(plainText: String, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val out = ByteArray(4 + 4 + 4 + SALT_SIZE + IV_SIZE + ciphertext.size)
        var pos = 0
        MAGIC.copyInto(out, pos); pos += 4
        writeIntLE(out, pos, VERSION); pos += 4
        writeIntLE(out, pos, ITERATIONS); pos += 4
        salt.copyInto(out, pos); pos += SALT_SIZE
        iv.copyInto(out, pos); pos += IV_SIZE
        ciphertext.copyInto(out, pos)
        return out
    }

    fun decrypt(data: ByteArray, password: String): String {
        require(isEncrypted(data)) { "Not an encrypted backup" }
        var pos = 4
        val version = readIntLE(data, pos); pos += 4
        require(version == VERSION) { "Unsupported version $version" }
        val iterations = readIntLE(data, pos); pos += 4
        val salt = data.copyOfRange(pos, pos + SALT_SIZE); pos += SALT_SIZE
        val iv = data.copyOfRange(pos, pos + IV_SIZE); pos += IV_SIZE
        val ciphertext = data.copyOfRange(pos, data.size)

        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(ciphertext)
        return String(plain, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_SIZE)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun writeIntLE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    data class PasswordCheck(
        val valid: Boolean,
        val hasLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSpecial: Boolean
    )

    fun validatePassword(password: String): PasswordCheck {
        val hasLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return PasswordCheck(
            valid = hasLength && hasUppercase && hasLowercase && hasDigit && hasSpecial,
            hasLength = hasLength,
            hasUppercase = hasUppercase,
            hasLowercase = hasLowercase,
            hasDigit = hasDigit,
            hasSpecial = hasSpecial
        )
    }
}
