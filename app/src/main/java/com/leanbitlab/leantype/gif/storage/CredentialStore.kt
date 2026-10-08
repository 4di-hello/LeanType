package com.leanbitlab.leantype.gif.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(private val context: Context) {
    companion object {
        private const val TAG = "CredentialStore"
        private const val KEY_ALIAS = "leantype_gif_creds"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private val MAGIC = byteArrayOf('L'.code.toByte(), 'T'.code.toByte(), 'G'.code.toByte(), '1'.code.toByte())
        private const val IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val file = File(context.noBackupFilesDir, "credentials.enc")
    private val lock = Any()
    private val memoryCache = mutableMapOf<String, String>()
    private val warmDeferred = CompletableDeferred<Boolean>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            warmUp()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun resetStorage(ks: KeyStore?) {
        try {
            file.delete()
            ks?.deleteEntry(KEY_ALIAS)
            synchronized(lock) {
                memoryCache.clear()
            }
            Log.w(TAG, "Credential storage reset due to corruption or tag mismatch")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting storage", e)
        }
    }

    private fun warmUp() {
        val ks = try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load KeyStore", e)
            warmDeferred.complete(false)
            return
        }

        if (!file.exists() || file.length() < MAGIC.size + IV_LENGTH) {
            warmDeferred.complete(true)
            return
        }

        try {
            val bytes = file.readBytes()
            if (bytes.size < MAGIC.size + IV_LENGTH) {
                resetStorage(ks)
                warmDeferred.complete(true)
                return
            }
            for (i in MAGIC.indices) {
                if (bytes[i] != MAGIC[i]) {
                    resetStorage(ks)
                    warmDeferred.complete(true)
                    return
                }
            }

            val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
            val ciphertext = bytes.copyOfRange(MAGIC.size + IV_LENGTH, bytes.size)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(ciphertext)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))

            synchronized(lock) {
                memoryCache.clear()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    memoryCache[k] = json.getString(k)
                }
            }
            warmDeferred.complete(true)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error on warm up: ${e.message}")
            resetStorage(ks)
            warmDeferred.complete(true)
        }
    }

    suspend fun awaitWarm(timeoutMs: Long = 2000L): Boolean {
        return withTimeoutOrNull(timeoutMs) { warmDeferred.await() } ?: false
    }

    fun get(providerId: String, field: String): String? {
        synchronized(lock) {
            return memoryCache["$providerId:$field"]
        }
    }

    fun has(providerId: String): Boolean {
        synchronized(lock) {
            return memoryCache.keys.any { it.startsWith("$providerId:") }
        }
    }

    fun set(providerId: String, field: String, value: String): Boolean {
        synchronized(lock) {
            val updated = HashMap(memoryCache)
            updated["$providerId:$field"] = value
            val ok = persist(updated)
            if (ok) {
                memoryCache["$providerId:$field"] = value
            }
            return ok
        }
    }

    fun clear(providerId: String): Boolean {
        synchronized(lock) {
            val updated = HashMap(memoryCache)
            val toRemove = updated.keys.filter { it.startsWith("$providerId:") }
            toRemove.forEach { updated.remove(it) }
            val ok = persist(updated)
            if (ok) {
                toRemove.forEach { memoryCache.remove(it) }
            }
            return ok
        }
    }

    private fun persist(map: Map<String, String>): Boolean {
        return try {
            val json = JSONObject(map).toString()
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

            val tmpFile = File.createTempFile("creds", ".tmp", context.noBackupFilesDir)
            FileOutputStream(tmpFile).use { fos ->
                fos.write(MAGIC)
                fos.write(iv)
                fos.write(ciphertext)
                fos.fd.sync()
            }
            if (!tmpFile.renameTo(file)) {
                tmpFile.delete()
                file.delete()
                if (!tmpFile.renameTo(file)) return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist credentials", e)
            false
        }
    }
}
