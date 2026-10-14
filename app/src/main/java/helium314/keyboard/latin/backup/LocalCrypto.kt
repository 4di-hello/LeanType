// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import helium314.keyboard.latin.utils.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encryption of local data with a key in the Android Keystore. The key can't be read or copied
 * from the device, so data encrypted with it can only be read by this app on this device.
 * Data that must be moved to another device (backups) is written decrypted, or encrypted with a password.
 */
object LocalCrypto {
    private const val TAG = "LocalCrypto"
    private const val KEY_ALIAS = "leantype_local_data"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    /** marks encrypted data, plain data (JSON) never starts like this */
    private val MAGIC = byteArrayOf(0x4C, 0x54, 0x45, 0x01) // "LTE" + version

    private fun key(): SecretKey? = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    } catch (e: Exception) {
        Log.w(TAG, "keystore not available: ${e.javaClass.simpleName}")
        null
    }

    fun isEncrypted(data: ByteArray) = data.size > MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    /** encrypted [plain], or null if the keystore is not available (then nothing should be written) */
    fun encrypt(plain: ByteArray): ByteArray? {
        val key = key() ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            if (iv.size != IV_LENGTH) return null
            MAGIC + iv + cipher.doFinal(plain)
        } catch (e: Exception) {
            Log.w(TAG, "encryption failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** decrypted [data]; plain (old) data is returned as it is; null if it can't be decrypted */
    fun decrypt(data: ByteArray): ByteArray? {
        if (!isEncrypted(data)) return data
        val key = key() ?: return null
        return try {
            val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(data, MAGIC.size + IV_LENGTH, data.size - MAGIC.size - IV_LENGTH)
        } catch (e: Exception) {
            Log.w(TAG, "decryption failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun encryptString(plain: String): String? =
        encrypt(plain.toByteArray())?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

    fun decryptString(encrypted: String): String? = try {
        decrypt(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP))?.let { String(it) }
    } catch (e: IllegalArgumentException) { null }
}
