// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files: AES-256-GCM with a key derived from the password (PBKDF2).
 * File format: [MAGIC], salt, iv, ciphertext with authentication tag.
 */
object BackupCrypto {
    private val MAGIC = "LTBAK01\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 200_000
    const val FILE_EXTENSION = "ltbak"

    class WrongPasswordException : IOException("wrong password or damaged file")

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Everything written to the returned stream is encrypted into [out]; close it to finish the file */
    fun encryptingStream(out: OutputStream, password: CharArray): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        return CipherOutputStream(out, cipher)
    }

    /** Whether [header] (the first bytes of a file) belongs to an encrypted backup */
    fun isEncrypted(header: ByteArray): Boolean =
        header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }

    /** Number of bytes needed for [isEncrypted] */
    val headerLength get() = MAGIC.size

    /**
     * Decrypts [input] (an encrypted backup, starting with the header) completely into [target].
     * Nothing is restored before the whole file is verified, so a wrong password can't destroy data.
     * @throws WrongPasswordException if the password is wrong or the file is damaged
     */
    fun decryptToFile(input: InputStream, password: CharArray, target: File) {
        val header = input.readNBytesCompat(MAGIC.size)
        if (!isEncrypted(header)) throw IOException("not an encrypted backup")
        val salt = input.readNBytesCompat(SALT_LENGTH)
        val iv = input.readNBytesCompat(IV_LENGTH)
        if (salt.size != SALT_LENGTH || iv.size != IV_LENGTH) throw WrongPasswordException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        try {
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    cipher.update(buffer, 0, read)?.let { out.write(it) }
                }
                out.write(cipher.doFinal())
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            target.delete()
            throw WrongPasswordException()
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val result = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = read(result, offset, n - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == n) result else result.copyOf(offset)
    }
}
