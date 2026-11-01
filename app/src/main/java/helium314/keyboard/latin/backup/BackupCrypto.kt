// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files: AES-256-GCM with a key derived from the password (PBKDF2).
 *
 * Format 2 (written since November 2026): [MAGIC_2], PBKDF2 iterations (4 bytes), salt, nonce prefix
 * (8 bytes), then segments of at most [SEGMENT_SIZE] bytes. Each segment is its length (4 bytes) and
 * its ciphertext with authentication tag; its nonce is the prefix and the segment number, and the
 * last segment is marked in the authenticated data. So segments can't be reordered, dropped or cut
 * off unnoticed, and neither writing nor reading needs memory for the whole file (the Android crypto
 * provider keeps all data of one GCM operation in memory).
 *
 * Format 1 (still read): [MAGIC_1], salt, iv, one ciphertext with authentication tag.
 */
object BackupCrypto {
    private val MAGIC_1 = "LTBAK01\n".toByteArray(Charsets.US_ASCII)
    private val MAGIC_2 = "LTBAK02\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val NONCE_PREFIX_LENGTH = 8
    private const val TAG_BITS = 128
    private const val TAG_LENGTH = TAG_BITS / 8
    private const val ITERATIONS_1 = 200_000
    /** OWASP recommendation for PBKDF2-HMAC-SHA256 (2023) */
    private const val ITERATIONS = 600_000
    /** accepted when reading: a damaged or crafted file must not keep the phone busy for hours */
    private val ITERATION_RANGE = 100_000..5_000_000
    const val SEGMENT_SIZE = 1 shl 20
    const val FILE_EXTENSION = "ltbak"

    class WrongPasswordException : IOException("wrong password or damaged file")

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, segment: Int): ByteArray =
        ByteBuffer.allocate(IV_LENGTH).put(prefix).putInt(segment).array()

    private fun aad(last: Boolean) = byteArrayOf(if (last) 1 else 0)

    /** Everything written to the returned stream is encrypted into [out]; close it to finish the file */
    fun encryptingStream(out: OutputStream, password: CharArray): OutputStream =
        encryptingStream(out, password, ITERATIONS, SEGMENT_SIZE)

    internal fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int, segmentSize: Int): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val prefix = ByteArray(NONCE_PREFIX_LENGTH).also { random.nextBytes(it) }
        val key = key(password, salt, iterations)
        out.write(MAGIC_2)
        out.write(ByteBuffer.allocate(4).putInt(iterations).array())
        out.write(salt)
        out.write(prefix)
        return SegmentedEncryptingStream(out, key, prefix, segmentSize)
    }

    private class SegmentedEncryptingStream(
        private val out: OutputStream, private val key: SecretKeySpec,
        private val prefix: ByteArray, segmentSize: Int
    ) : OutputStream() {
        private val buffer = ByteArray(segmentSize)
        private var length = 0
        private var segment = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("stream closed")
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // a full segment is written only when more data comes, so the last one is always marked
                if (length == buffer.size) writeSegment(last = false)
                val n = minOf(remaining, buffer.size - length)
                System.arraycopy(b, offset, buffer, length, n)
                length += n
                offset += n
                remaining -= n
            }
        }

        private fun writeSegment(last: Boolean) {
            if (segment == Int.MAX_VALUE) throw IOException("backup too large")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, segment)))
            cipher.updateAAD(aad(last))
            val encrypted = cipher.doFinal(buffer, 0, length)
            out.write(ByteBuffer.allocate(4).putInt(encrypted.size).array())
            out.write(encrypted)
            segment++
            length = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                writeSegment(last = true)
                buffer.fill(0)
            } finally {
                out.close()
            }
        }
    }

    /** Whether [header] (the first bytes of a file) belongs to an encrypted backup */
    fun isEncrypted(header: ByteArray): Boolean = startsWith(header, MAGIC_2) || startsWith(header, MAGIC_1)

    private fun startsWith(header: ByteArray, magic: ByteArray) =
        header.size >= magic.size && magic.indices.all { header[it] == magic[it] }

    /** Number of bytes needed for [isEncrypted] */
    val headerLength get() = MAGIC_2.size

    /**
     * Decrypts [input] (an encrypted backup, starting with the header) completely into [target].
     * Nothing is restored before the whole file is verified, so a wrong password can't destroy data.
     * @throws WrongPasswordException if the password is wrong or the file is damaged
     */
    fun decryptToFile(input: InputStream, password: CharArray, target: File) {
        val header = input.readNBytesCompat(MAGIC_2.size)
        try {
            when {
                startsWith(header, MAGIC_2) -> decryptSegments(DataInputStream(input), password, target)
                startsWith(header, MAGIC_1) -> decryptFormat1(input, password, target)
                else -> throw IOException("not an encrypted backup")
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            target.delete()
            throw WrongPasswordException()
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun decryptSegments(input: DataInputStream, password: CharArray, target: File) {
        val iterations = try { input.readInt() } catch (e: EOFException) { throw WrongPasswordException() }
        if (iterations !in ITERATION_RANGE) throw WrongPasswordException()
        val salt = input.readNBytesCompat(SALT_LENGTH)
        val prefix = input.readNBytesCompat(NONCE_PREFIX_LENGTH)
        if (salt.size != SALT_LENGTH || prefix.size != NONCE_PREFIX_LENGTH) throw WrongPasswordException()
        val key = key(password, salt, iterations)
        FileOutputStream(target).use { out ->
            var segment = 0
            var length = readLength(input) ?: throw WrongPasswordException() // there is at least the last segment
            while (true) {
                val encrypted = input.readNBytesCompat(length)
                if (encrypted.size != length) throw WrongPasswordException()
                // the segment is the last one if nothing follows
                val nextLength = readLength(input)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, segment)))
                cipher.updateAAD(aad(nextLength == null))
                out.write(cipher.doFinal(encrypted))
                if (nextLength == null) break
                if (segment == Int.MAX_VALUE) throw WrongPasswordException()
                segment++
                length = nextLength
            }
        }
    }

    /** length of the next segment, null at the end of the file */
    private fun readLength(input: DataInputStream): Int? {
        val first = input.read()
        if (first < 0) return null
        val rest = input.readNBytesCompat(3)
        if (rest.size != 3) throw WrongPasswordException()
        val length = (first shl 24) or ((rest[0].toInt() and 0xFF) shl 16) or ((rest[1].toInt() and 0xFF) shl 8) or (rest[2].toInt() and 0xFF)
        // also protects against huge allocations for a crafted file
        if (length < TAG_LENGTH || length > SEGMENT_SIZE + TAG_LENGTH) throw WrongPasswordException()
        return length
    }

    private fun decryptFormat1(input: InputStream, password: CharArray, target: File) {
        val salt = input.readNBytesCompat(SALT_LENGTH)
        val iv = input.readNBytesCompat(IV_LENGTH)
        if (salt.size != SALT_LENGTH || iv.size != IV_LENGTH) throw WrongPasswordException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, ITERATIONS_1), GCMParameterSpec(TAG_BITS, iv))
        FileOutputStream(target).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                cipher.update(buffer, 0, read)?.let { out.write(it) }
            }
            out.write(cipher.doFinal())
        }
    }

    /** Format 1, only for tests: backups of older versions */
    internal fun encryptingStreamFormat1(out: OutputStream, password: CharArray): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS_1), GCMParameterSpec(TAG_BITS, iv))
        out.write(MAGIC_1)
        out.write(salt)
        out.write(iv)
        return CipherOutputStream(out, cipher)
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
